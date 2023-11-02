package pile.impl;

import static pile.interop.debug.DebugEnabled.DE;
import static pile.interop.debug.DebugEnabled.ET_TRACE;
import static pile.interop.debug.DebugEnabled.lockedValueMutices;
import static pile.interop.debug.DebugEnabled.traceEnabledFor;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Objects;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.function.BiPredicate;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

import pile.aspect.Dependency;
import pile.aspect.Depender;
import pile.aspect.HasBrackets;
import pile.aspect.ValueBracket;
import pile.aspect.WriteValue;
import pile.aspect.combinations.Pile;
import pile.aspect.combinations.ReadListenDependency;
import pile.aspect.listen.ListenValue;
import pile.aspect.listen.ValueListener;
import pile.aspect.recompute.Recomputation;
import pile.aspect.recompute.Recomputations;
import pile.aspect.recompute.Recomputer;
import pile.aspect.suppress.MockBlock;
import pile.aspect.suppress.Suppressor;
import pile.interop.debug.DebugEnabled;
import pile.interop.exec.StandardExecutors;
import pile.interop.wait.WaitService;
import pile.specialized_bool.IndependentBool;
import pile.specialized_bool.combinations.ReadListenDependencyBool;
import pile.utils.Functional;
import pile.utils.WeakIdentityCleanup;

/**
 * This class contains most of the transaction logic common to {@link PileImpl}s and {@link Independent}s.
 * It uses protected methods to communicate with these subclasses.
 * @author bb
 *
 * @param <E>
 */
public abstract class AbstractReadListenDependency<E> 
implements ReadListenDependency<E>, 
ListenValue.Managed{
	private final static Logger log=Logger.getLogger("Value");



	/**
	 * A log entry in the {@link AbstractReadListenDependency#trace trace}
	 * field of a {@link AbstractReadListenDependency}.
	 * This is meant to be inspected during debugging to see what went wrong
	 * @author bb
	 *
	 */
	static class TraceItem{
		/**
		 * Some indication of what happened
		 */
		Object msg;
		/**
		 * When did it happen?
		 */
		long time;
		/**
		 * Used to lazily generate the stack trace
		 */
		RuntimeException x;
		/**
		 * 
		 * @param msg
		 * @param x Whether to generate a stack trace. The stack trace is stored in 
		 * the {@link RuntimeException} {@link #x}. To inspect it in the debugger,
		 * the exception's {@link RuntimeException#getStackTrace() getStackTrace()} 
		 * method must be called because
		 * the stack trace is initialized lazily. Since {@link TraceItem#toString()} calls that method,
		 * simply inspecting the {@link TraceItem} in the debugger should do the trick.
		 */
		public TraceItem(Object msg, boolean x) {
			if(!ET_TRACE)
				throw new IllegalStateException("Please only create TraceItems if DebugEnabled.ET_TRACE && traceEnabledFor(this) is true!");
			this.msg=msg;
			time=System.currentTimeMillis();
			if(x) {
				try {
					throw new RuntimeException("trace");
				}catch(RuntimeException tx) {
					//					tx.getStackTrace();
					this.x=tx;
				}
			}
		}
		/**
		 * The {@link DateFormat} used by {@link #toString()} to format the {@link #time} field
		 */
		static DateFormat format = ET_TRACE?new SimpleDateFormat("HH:mm:ss;sss"):null;
		@Override
		public String toString() {
			if(x!=null)
				x.getStackTrace();
			return format.format(new Date(time))+": "+msg;
		}
	}
	/**
	 * A log of what happened to this object: events, decisions, variable values and the like.
	 * This slows down the program considerably and is only used if
	 * the {@link DebugEnabled#ET_TRACE} flag is set and 
	 * {@link DebugEnabled#traceEnabledFor(Dependency) DebugEnabled.traceEnabledFor(this)} 
	 * is <code>true</code>.
	 */
	protected ArrayList<Object> trace=ET_TRACE?new ArrayList<Object>() {
		public String toString() {
			StringBuilder sb = new StringBuilder();
			for(Object o: this)
				sb.append(o).append('\n');
			return sb.toString();
		}
	}:null;
	/**
	 * Insert an item into the {@link #trace}, recording a stack trace
	 * @param o
	 */
	protected final void trace(Object o) {
		assert trace!=null;
		synchronized(trace) {trace.add(new TraceItem(o, true));}
	}
	/**
	 * Insert an item into the {@link #trace}
	 * @param o
	 * @param x whether to record a stack trace
	 */
	protected final void trace(Object o, boolean x) {
		assert trace!=null;
		synchronized(trace) {trace.add(new TraceItem(o, x));}
	}

	/**
	 * All {@link Depender}s that currently depend on this {@link Dependency}.
	 * We keep only weak references to them so they can effectively be removed
	 * by the garbage collector if no one else is interested in them.
	 * Access to this field must be synchronized using {@link #mutex}.
	 */
	Set<WeakIdentityCleanup<Depender>> dependOnThis;
	/**
	 * How many transactions are currently open.
	 * Access to this field must be synchronized using {@link #mutex}.
	 * Transactions are opened on the following occasions:
	 * - A dependency is invalid
	 * - A recomputation is pending
	 * - A recomputation is ongoing
	 * - The value is being set explicitly (See {@link WriteValue#set(Object)})
	 * - The value is being invalidated explicitly (See {@link WriteValue#permaInvalidate()})
	 * - The transaction is requested by external code
	 * While transactions are active that are not due to a pending recomputation, 
	 * no {@link Recomputation}s will be started.
	 * When a transaction is opened, the current value (if it is valid) should be moved or 
	 * copied to a field holding the old value. 
	 * This "old value" field is used to detect whether the value has changed.
	 */
	private int openTransactions;
	/**
	 * A queue of things to run outside the locking provided by {@link #mutex}.
	 * This includes updates to {@link Depender}s informing 
	 * them that this {@link Dependency} has begun or ended changing.
	 * Access to this field must be synchronized using itself
	 */
	final protected ArrayDeque<Runnable> informQueue=new ArrayDeque<>();



	/**
	 * The main mutex and monitor of this object.
	 */
	final protected Object mutex=new Object();

	/**
	 * This lazily initialized {@link ListenerManager} managed the {@link ValueListener}s registered with this
	 * object.
	 */
	volatile ListenValue.ListenerManager listeners;
	/**
	 * The lazily initialized set of {@link Depender}s that have been notified that this {@link Dependency}
	 * has {@link Depender#dependencyBeginsChanging(Dependency, boolean) begun changing}, but not 
	 * that it {@link Depender#dependencyEndsChanging(Dependency, boolean) ended changing}.
	 * This field may only be accessed from code submitted to the {@link #informQueue}.
	 * It is vital that these status changing events are tracked correctly because the {@link Depender}s
	 * keep a transaction open while the dependency is changing. 
	 */
	HashSet<Depender> informed;

	/**
	 * Whether this value has been destroyed, or at least its destruction has begun
	 */
	volatile boolean destroyed;
	/**
	 * The default equivalence relation used to decide whether the the value has changed 
	 * after a recomputation or when setting it explicitly.
	 * @see #equivalence
	 */
	public final static BiPredicate<Object, Object> DEFAULT_EQUIVALENCE=(a, b)->a==null?b==null:b!=null && a.equals(b);
	/**
	 * The equivalence relation used to decide whether the the value has changed 
	 * after a recomputation or when setting it explicitly.
	 */
	protected BiPredicate<? super E, ? super E> equivalence=DEFAULT_EQUIVALENCE;
	/**
	 * This field is purely for debugging purposes. If you don't give names to your
	 * values, it can be very hard to tell what you are dealing with in the debugger.
	 */
	public String avName;
	/**
	 * This field should contain a reference to the parent structure of the value, or to something
	 * that it is derived from. This is mostly for navigating your data structures during debugging,
	 * but in the case of stuff like {@link Piles#validBuffer_ro(pile.aspect.combinations.ReadListenValue)
	 * validBuffer}s, it also prevents the garbage collector from claiming an object that may otherwise have no
	 * references to it.
	 * You should never read the contents of this field programmatically (except for debugging)
	 * of modify it after this {@link AbstractReadListenDependency} has been created and configured.
	 */
	public Object owner;

	@Override
	public ListenerManager getListenerManager() {
		ListenerManager localRef = listeners;
		if (localRef == null) {
			synchronized (mutex) {
				localRef = listeners;
				if (localRef == null) {
					listeners = localRef = new ListenerManager(this);
				}
			}
		}
		return localRef;
	}

	@Override
	public void fireValueChange() {
		if(isDestroyed())
			return;
		if(DE && dc!=null) dc.fireValueChange(this);

		if(listeners==null) {
			//If we don't have any listeners, we don't want to call
			//getListenerManager() because that would unnecessarily allocate an empty
			//ListenerManager
			return;
		}
		getListenerManager().fireValueChange();
	}
	@Override
	public void __addDepender(Depender d, boolean propagateInvalidity) {
		if(d==this)
			throw new IllegalArgumentException("Trivial dependency cycle detected!");
		boolean wiq = false;
		synchronized (mutex) {
			//			boolean wasValid=valid();
			if(destroyed)
				throw new IllegalStateException("This value has been destroyed: "+dependencyName());
			if(dependOnThis==null)
				dependOnThis=Collections.synchronizedSet(new HashSet<>());
			if(ET_TRACE && traceEnabledFor(this))trace("Add Depender "+d);
			boolean wasValid=__valid();
			WeakIdentityCleanup<Depender> ref = new WeakIdentityCleanup<Depender>(d) {
				@Override
				public void run() {
					dependOnThis.remove(this);
				}
			};
			if(dependOnThis.add(ref)) {
				if(!__valid()) {
					assert !Thread.holdsLock(informQueue);
					// assert !Thread.holdsLock(informRunnerMutex);
					synchronized (informQueue) {
						informQueue.add(()->{
							if(informed==null)
								informed=new HashSet<>();
							if(informed.contains(d))
								return;
							if(ET_TRACE && traceEnabledFor(this))trace("Inform new depender "+d);
							d.dependencyBeginsChanging(this, wasValid, propagateInvalidity);						
							informed.add(d);	
						});
					}
					wiq=true;

				}
			}
		}
		if(wiq)
			__workInformQueue();
	}

	/**
	 * A monitor used to ensure that at most one Thread executes the main part of {@link #__workInformQueue(boolean)}.
	 * It is not used directly to guard that critical section, but as part of 
	 * a more complicated lock.
	 */
	Object informRunnerMutex = new Object();
	/**
	 * The thread currently executing the main part of {@link #__workInformQueue(boolean)}.
	 * Access to this field needs to be synchronized using {@link #informRunnerMutex}
	 */
	Thread someThreadIsWorkingInformQueue;
	/**
	 * Execute the elements in the {@link #informQueue} until it is empty.
	 * This method calls {@link #__workInformQueue(boolean) workInformQueue(false)}.
	 */
	public void __workInformQueue() {
		__workInformQueue(false);
	}
	private void workInformQueueDelayed() {
		long delay = (long) (Math.random()*100);
		try {
			WaitService.get().sleep(delay);
		} catch (InterruptedException e) {
//			e.printStackTrace();
			WaitService.get().interruptSelf(e);
			return;
		}
		__workInformQueue();
	}
	/**
	 * Execute the elements in the {@link #informQueue} until it is empty.
	 * The current thread must not be in the monitor of {@link #mutex}.
	 * @param evade If this is <code>true</code> and another thread is currently
	 * executing the critical section, return immediately. Use this if the code following the call
	 * to this methods doesn't care whether the {@link #informQueue} has been emptied.
	 * If this parameter is false, a deadlock may arise; if that happens, the method returns after 
	 * some time. The risk here is that some update are not sent in the right way, but I', not sure
	 * if that'S ever relevant anyway. And at least we don't get a game-breaking deadlock forever. 
	 */
	public void __workInformQueue(boolean evade) {
		assert !Thread.holdsLock(mutex);
		if(Thread.holdsLock(informRunnerMutex))
			return;
		boolean amRunning = false;
		try {
			while(true) {
				synchronized (informRunnerMutex) {
					if(!amRunning && someThreadIsWorkingInformQueue==Thread.currentThread())
						return;
					if(!amRunning && someThreadIsWorkingInformQueue!=null) {
						try {
							long t0 = System.currentTimeMillis();
							boolean didntWarnYet=true;
							while(someThreadIsWorkingInformQueue!=null) {
								if(evade)
									return;
								//								if(ET_TRACE && traceEnabledFor(this))
								//									trace("inform queue already running");
								WaitService.get().wait(informRunnerMutex, 100);
								long timeElapsed=System.currentTimeMillis()-t0;

								if(timeElapsed>2500 & didntWarnYet) {
									didntWarnYet=false;
									try {
										throw new RuntimeException("Stack trace");
									}catch(RuntimeException x) {
										log.log(Level.WARNING, "Likely informQueue deadlock involving "+avName+" in Thread "+Thread.currentThread().getName(), x);
									}		
								}
								if(timeElapsed>5000) {
									log.severe("Resolved a likely informQueue deadlock involving "+avName);
									//Ensure that we don't end up with long-term unprocessed items in the informQueue
									if(Thread.currentThread().getName().startsWith("walkInformQueue deadlock resolver")) {
										if(timeElapsed>10000)
											return;
									}else {
										Thread resolveLater = new Thread(this::workInformQueueDelayed);
										resolveLater.setDaemon(true);
										resolveLater.setName("walkInformQueue deadlock resolver");
										resolveLater.start();
										return;
									}
								}
							}

						}catch(InterruptedException x) {
							return;
						}
					}
					amRunning=true;
					someThreadIsWorkingInformQueue=Thread.currentThread();
				}
				while(true) {
					Runnable run;
					assert !Thread.holdsLock(informQueue);
					// assert !Thread.holdsLock(informRunnerMutex);

					synchronized(informQueue) {
						run = informQueue.poll();
					}
					if(run==null) {
						assert amRunning;
						synchronized (informRunnerMutex) {
//							if(!amRunning && someThreadIsWorkingInformQueue==Thread.currentThread())
//								System.out.println();

							amRunning=false;
							assert someThreadIsWorkingInformQueue==Thread.currentThread();
							someThreadIsWorkingInformQueue=null;
							WaitService.get().notifyAll(informRunnerMutex);
//							if(!amRunning && someThreadIsWorkingInformQueue==Thread.currentThread())
//								System.out.println();

						}	
						break;
					}
					if(ET_TRACE && traceEnabledFor(this))synchronized(trace) {trace.add("process informQueue item");}
					assert !Thread.holdsLock(mutex);
					try {
						run.run();
					}catch(Throwable t) {
						log.log(Level.INFO, "Isolated an error", t);
					}		
				}
//				synchronized (informRunnerMutex) {
//					if(!amRunning && someThreadIsWorkingInformQueue==Thread.currentThread())
//						System.out.println();
//				}

				synchronized(informQueue) {
					if(informQueue.isEmpty())
						return;
				}
			}
		}finally {
			assert !amRunning;
			if(amRunning) {
				synchronized (informRunnerMutex) {
					amRunning=false;
					assert someThreadIsWorkingInformQueue==Thread.currentThread();
					someThreadIsWorkingInformQueue=null;
					WaitService.get().notifyAll(informRunnerMutex);
				}	
			}
		}
	}
	@Override
	public void __removeDepender(Depender d) {
		boolean wiq;
		synchronized (mutex) {
			if(dependOnThis==null)
				throw new IllegalArgumentException("That does not depend on me!");
			if(!dependOnThis.remove(new WeakIdentityCleanup<>(d))) {
				throw new IllegalArgumentException("That does not depend on me!");	
			}
			if(ET_TRACE && traceEnabledFor(this))trace("Remove Depender "+d);


			wiq = !d.isDestroyed();
			assert !Thread.holdsLock(informQueue);
			// assert !Thread.holdsLock(informRunnerMutex);
			if(wiq) {
				synchronized (informQueue) {
					informQueue.add(()->{
						if(informed==null)
							return;


						if(informed!=null && informed.contains(d)) {
							if(ET_TRACE && traceEnabledFor(this))trace("Un-inform removed Depender "+d);
							d.dependencyEndsChanging(this, true);
							if(informed!=null)
								informed.remove(d);
						}else {
							if(ET_TRACE && traceEnabledFor(this))trace("Was not informed: Removed Depender "+d);

							//							System.out.println("Dependency not in informed list!");
						}
					});
				}

			}
		}
		if(wiq)
			__workInformQueue();
	}

	/**
	 * Begins a new transaction, calling {@link #beginTransaction(boolean, boolean)
	 * beginTransaction(workInformQueue, true)
	 * }
	 * 
	 * 
	 * @param workInformQueue Whether to call {@link #__workInformQueue()} afterwards.
	 * Pass <code>false</code> here if the current {@link Thread} has locked {@link #mutex};
	 * in this case, you must make sure to call {@link #__workInformQueue(boolean)} 
	 * after the lock is released. 
	 * @return
	 */
	protected boolean beginTransaction(boolean workInformQueue) {
		return beginTransaction(workInformQueue, true, false);
		
	}
	/**
	 * Begins a new transaction.
	 * 
	 * 
	 * @param workInformQueue Whether to call {@link #__workInformQueue()} afterwards.
	 * Pass <code>false</code> here if the current {@link Thread} has locked {@link #mutex};
	 * in this case, you must make sure to call {@link #__workInformQueue(boolean)}
	 * @param moveValueToOldValue Whether to call {@link #moveValueToOldValue()}. 
	 * after the lock is released. 
	 * @return
	 */
	protected boolean beginTransaction(boolean workInformQueue, boolean moveValueToOldValue, boolean scout) {
		if(DE && dc!=null) dc.beginTransactionCalled(this);
		boolean wasValid;
//		moveValueToOldValue &=! scout;
		try {
			boolean inform;

			synchronized (mutex) {
				if(ET_TRACE && traceEnabledFor(this))trace("(begin transaction)");

				wasValid=__valid();
				if(ET_TRACE && traceEnabledFor(this))trace("was valid: "+wasValid);

				inform = wasValid;

				assert openTransactions>=0;
				if(openTransactions<=0) {
					//enter transaction mode
					openTransactions=1;
					if(DE) __checkTransactionReasonCount(openTransactions);
					setInTransaction.accept(Boolean.TRUE);
					if(ET_TRACE && traceEnabledFor(this))trace("entered transaction mode");


					inform=true;
					//Remember the value at the start of the transaction
					if(moveValueToOldValue)
						if(scout)
							copyValueToOldValue();
						else
							moveValueToOldValue();
				}else {
					//Already in a transaction
					//Just increase the transaction counter
					++openTransactions;
					if(DE) __checkTransactionReasonCount(openTransactions);

					if(ET_TRACE && traceEnabledFor(this))trace("open another transaction");

					//inform=!valid();
					inform=true;

					if(moveValueToOldValue && __valid()) {
						moveValueToOldValue();
					}

				}
				
				inform &=! scout;

				//collect the Dependers that should also be in 
				//transaction mode while this is
				if(inform && dependOnThis!=null) {
					assert !Thread.holdsLock(informQueue);
					// assert !Thread.holdsLock(informRunnerMutex);
					if(ET_TRACE && traceEnabledFor(this))trace("schedule informing of dependers that I have become invalid");
//					StackTraceElement[] cause = Thread.currentThread().getStackTrace();
					synchronized (informQueue) {
						informQueue.add(()->{
							if(ET_TRACE && traceEnabledFor(this))trace("now informing dependers that I have become invalid");
							if(informed==null)
								informed=new HashSet<>();
							else {
								//assert informed.isEmpty();
							}
//							cause.clone();
							WeakIdentityCleanup<Depender>[] deparr;
							synchronized (mutex) {
								if(dependOnThis==null || dependOnThis.isEmpty())
									return;
								@SuppressWarnings("unchecked")
								WeakIdentityCleanup<Depender>[] arr = dependOnThis.toArray(new WeakIdentityCleanup[dependOnThis.size()]);
								deparr=arr;
							}
							assert !Thread.holdsLock(mutex);

							for(WeakIdentityCleanup<Depender> dr: deparr) {
								Depender d=dr.get();
								if(d!=null) {
									if(informed.add(d)) {
										if(ET_TRACE && traceEnabledFor(this))trace("now informing dependers "+d);
										d.dependencyBeginsChanging(this, wasValid, moveValueToOldValue);
									}else {
										if(ET_TRACE && traceEnabledFor(this))trace("was already informed dependers "+d);
									}
								}
							}

						});
					}

				}else {
					if(ET_TRACE && traceEnabledFor(this))trace("not informing dependers");
				}


			}
			//Actually propagate the transaction to the dependers
		}finally {
			if(workInformQueue) {
				assert !Thread.holdsLock(mutex);
				__workInformQueue();
			}
		}
		return wasValid;
	}
	/**
	 * For debugging
	 * @param openTransactions2
	 */
	protected void __checkTransactionReasonCount(int openTransactions2) {		
	}
	/**
	 * Called by {@link #beginTransaction(boolean)}. 
	 * This method should move or copy the "current" value to the "old value", 
	 * but possibly only if the old value is currently invalid
	 */
	protected abstract void moveValueToOldValue();
	protected abstract void copyValueToOldValue();
	/**
	 * @return Whether there is an ongoing recomputation
	 */
	protected abstract boolean isComputing();



	//	DoubleTimeRateLimiter notTransactionInvalidityInformerItt;

	/**
	 * End a transaction. If this causes the only remaining transactions to be due to
	 * a pending recomputation (see {@link #__recomputerTransactions()}, an attempt is made
	 * to start recomputation (which may fail because the value is not auto-validating, is lazy-validating,
	 * has no {@link Recomputer} defined or similar reasons.)
	 * @param changedIfOldInvalid Whether to consider it a change of value when the 
	 * {@link #__oldValue()} was {@link #__oldValid() invalid}. I'm not sure whether this distinction
	 * is relevant, but as long as it works, I don't want to change it.
	 * 
	 */
	public void __endTransaction(boolean changedIfOldInvalid) {

		boolean changed;
		assert !Thread.holdsLock(mutex);
		if(DE && dc!=null) dc.endTransactionCalled(this);
		boolean wasLongTermInvalid=false;
		boolean becameLongTermValid;
		//		boolean wasValid;
		//		boolean recomputationScheduled=false;
		boolean startRecomputation=false;
		boolean cancelRecomputation=false;
		boolean inform;

		//		boolean changedToInvalid=false;
		//		boolean firedChange=false;
		try {
			//cancelPendingRecomputation();
			synchronized (mutex) {

				//				wasValid=valid();
				wasLongTermInvalid=!observedValid();
				if(ET_TRACE && traceEnabledFor(this))synchronized(trace) {trace.clear();}
				if(openTransactions==0) {
					if(ET_TRACE && traceEnabledFor(this))trace("No running transaction, ");
					throw new IllegalStateException("No running transaction");
				}
				--openTransactions;
				if(DE) __checkTransactionReasonCount(openTransactions);


				try {
					if(openTransactions<=0) {
						if(ET_TRACE && traceEnabledFor(this))trace("All transactions closed");
					}else {
						if(ET_TRACE && traceEnabledFor(this))trace(openTransactions+"open transactions remaining");
						if(openTransactions<=__recomputerTransactions())
							if(ET_TRACE && traceEnabledFor(this))trace("(all of them for recomputation)");

					}
					if(__valid()) {
						inform=true;
						startRecomputation=false;
						cancelRecomputation=true;
						if(ET_TRACE && traceEnabledFor(this))trace("Valid on transaction close");
						__clearChangedDependencies();
						changed=noChangedDependencies(changedIfOldInvalid);
						if(ET_TRACE && traceEnabledFor(this))trace("Changed: "+changed);
						//						changedToInvalid=false;
					}else {
						if(ET_TRACE && traceEnabledFor(this))trace("Invalid on transaction close");
						if(__shouldRemainInvalid()) {
							if(ET_TRACE && traceEnabledFor(this))trace("Should remain invalid");
							startRecomputation=false;
							inform=false;
							changed=false;
						}else if(openTransactions>0){
							startRecomputation=openTransactions<=__recomputerTransactions() && __hasChangedDependencies();
							cancelRecomputation=!startRecomputation;
							inform=false;
							changed=false;
						}else{
							if(!__hasChangedDependencies()) {
								if(ET_TRACE && traceEnabledFor(this))trace("no changed dependencies");
								changed=noChangedDependencies(changedIfOldInvalid);
								startRecomputation=true;
							}else {
								if(ET_TRACE && traceEnabledFor(this))trace("changed dependencies");
								if(openTransactions<=__recomputerTransactions()) {
									if(ET_TRACE && traceEnabledFor(this))trace("recomputing: "+openTransactions+" <= "+__recomputerTransactions());
									startRecomputation=true;
								}
								if(openTransactions==0)
									changed=true;
								else
									changed=false;
							}
							inform=__valid();
						}
						//						changedToInvalid = !valid() && openTransactions==0;

					}
					if(ET_TRACE && traceEnabledFor(this))trace("changed: "+changed+", startRecomputation: "+startRecomputation);


					if(openTransactions<=0 && !startRecomputation) {
						closeOldBrackets();
					}
					assert !Thread.holdsLock(informQueue);


					if(inform) {
						synchronized (informQueue) {
							//StackTraceElement[] cause = Thread.currentThread().getStackTrace();
							informQueue.add(()->{
								if(informed==null)
									return;
								//cause.clone();
								Depender[] notify = informed.toArray(new Depender[informed.size()]);
								informed.clear();
								for(Depender d: notify) {
									if(ET_TRACE && traceEnabledFor(this))trace("un-inform: "+d);
									d.dependencyEndsChanging(this, changed);
								}
							});
						}
					}


				}finally {
					if(openTransactions==0) {
						setInTransaction.accept(false);


					}
				}
				becameLongTermValid = wasLongTermInvalid && __valid();
				//				startRecomputation |= canRecomputeWithInvalidDependencies() && !valid();

			}
			assert !Thread.holdsLock(mutex);


			__workInformQueue();
			if((!startRecomputation && changed) || becameLongTermValid) {
				fireValueChange();
				becameLongTermValid=false;
			}
		}finally {
			__workInformQueue();
			//			if(isAutoValidating()) {
			//
			//				startPendingRecompute(false);
			//			}else {
			//				if(ET_TRACE && traceEnabledFor(this))synchronized(trace) {trace.append("recomputation not started: Not autovalidating, ");}
			//
			//			}
			if(startRecomputation && !StandardExecutors.interrupted()) {
				//				if(canRecomputeWithInvalidDependencies())
				//					System.out.println();
				__scheduleRecomputation(false);
				__startPendingRecompute(false);
			}else if(cancelRecomputation) {
				cancelPendingRecomputation(true);
			}

			synchronized (mutex) {
				if(ET_TRACE && traceEnabledFor(this) && __valid() && informed!=null && !informed.isEmpty()) {
					synchronized (trace) {
						System.out.println(avName +" :===========:\n "+trace);
					}
					//				beginTransaction();
					//				endTransaction();
				}
			}
			boolean ilti;
			synchronized (mutex) {
				ilti = !__valid()	&& (
						openTransactions<=__recomputerTransactions()
						|| __ongoingRecomputation()!=null
						)
						; 
				//true; 
				//!valid() && (openTransactions==0 || isComputing() || isAutoValidating())  ;
			}
			if(ilti)
				informLongTermInvalid();
			//			synchronized (mutex) {
			//				if(ET_TRACE && traceEnabledFor(this) && valid()) {
			//					ReadListenDependencyBool validity = __validity();
			//					if(validity!=null && !Boolean.TRUE.equals(validity.getAsync()))
			//						System.out.println(trace);
			//				}
			//			}
			//			if(isValid())
			//				cancelPendingRecomputation(true);
			//			if(startRecomputation) {
			//				becameLongTermValid = wasLongTermInvalid && observedValid();
			//				if(becameLongTermValid)
			//					fireValueChange();
			//			}else {
			////				if(changedToInvalid && !valid())
			////					fireValueChange();
			//			}
		}
	}
	/**
	 * Invalidate if necessary and immediately attempt to recompute (which may not happen for various reasons)
	 */
	protected abstract void revalidate();
	/**
	 * The current thread must have locked the {@link #mutex}
	 * @return the ongoing {@link Recomputation}, if any
	 */
	protected abstract Recomputation<E> __ongoingRecomputation();

	/**
	 * The current thread must have locked the {@link #mutex}
	 * @return the number of transactions that are due to pending recomutations
	 */
	protected abstract int __recomputerTransactions();


	/**
	 * Inform the {@link Depender}s that this value's invalidity is now observable, and cause
	 * setting the observed validity to false (using {@link #__setValidity(boolean)} 
	 * If this value is not actually invalid, none of that is done, also if
	 * this runs during a {@link Recomputation} in dependency scouting mode.
	 *
	 */
	protected void informLongTermInvalid() {
		assert !Thread.holdsLock(mutex);
		if(Recomputations.isScouting())
			return;
		HashSet<Object> informing = Depender.informingLongTermInvalid.get();
		boolean starter;
		if(informing==null || informing.isEmpty()) {
			starter=true;
			Depender.informingLongTermInvalid.set(informing=new HashSet<>());
		}else {
			starter=false;
		}
		try(MockBlock mb = Recomputations.withCurrentRecomputation(null)){
			boolean propagate=!informing.contains(this);


			boolean longTermInvalid;
			synchronized (mutex) {
				{

					longTermInvalid = !__valid();
					//				System.out.println("transaction ended: "+this);
					//				System.out.println("Longterm invalid: "+longTermInvalid);
					if(longTermInvalid) {
						if(ET_TRACE && traceEnabledFor(this))trace("observed long term invalidity, ");
						assert !Thread.holdsLock(informQueue);
						// assert !Thread.holdsLock(informRunnerMutex);
						informing.add(this);
						if(propagate) {
							synchronized (informQueue) {
								//						RuntimeException xtrace=null;
								//						try {
								//							throw new RuntimeException();
								//						}catch(RuntimeException x) {
								//							x.getStackTrace();
								//							xtrace = x;
								//						}
								//						RuntimeException fxtrace = xtrace;
								informQueue.add(()->{
									synchronized(mutex) {
										boolean valid = __valid();
										if(!valid) {
											if(ET_TRACE && traceEnabledFor(this))trace("setValidity(false) invoked from informLongTermInvalid, ");
											//										if(fxtrace!=null & !false)
											//											fxtrace.printStackTrace();
											__setValidity(false);
										}
										if(valid)
											return;
									}
								});

							}

						}

					}else {
						propagate=false;
					}
				}
			}
			if(propagate) {
				__workInformQueue(true);
				//workInformQueue();
			}
		}finally {
			if(starter)
				Depender.informingLongTermInvalid.set(null);
		}
	}
	/**
	 * If the subclass keeps track of which or whether {@link Dependency Dependencies} of 
	 * the value have changed, make if clear that status now.
	 */
	protected abstract void __clearChangedDependencies();

	/**
	 * Invoked by {@link #__endTransaction(boolean)} if there were no changed dependencies.
	 * @param changedIfOldInvalid
	 * @return whether the value has changed relative to the old value
	 */
	private boolean noChangedDependencies(boolean changedIfOldInvalid) {
		assert Thread.holdsLock(mutex);
		boolean changed;
		if(__valid()) {
			//Apparently the value was set manually during the transaction
			if(__oldValid()) {
				
				changed = !equivalence.test(__value(), __oldValue());
				if(changed)
					if(ET_TRACE && traceEnabledFor(this))trace("old differs from new");

				closeOldBrackets();
			}else {
				changed = changedIfOldInvalid;
				if(changed)
					if(ET_TRACE && traceEnabledFor(this))trace("changed because old was invalid");

			}

		}else {
			//None of the dependencies have changed. Restore the value
			if(__oldValid()) {
				__restoreValueFromOldValue();
				//changed=mutated;  //this doesn't work right
				changed=false;
				if(ET_TRACE && traceEnabledFor(this))trace("not changed but restored");
				WaitService.get().notifyAll(mutex);
			}else {
				changed=false;
				if(ET_TRACE && traceEnabledFor(this))trace("not changed but remained invalid");
			}

		}
		return changed;
	}
	@Override
	public void giveDependers(Consumer<? super Depender> out) {
		if(Thread.holdsLock(mutex)) {
			if(dependOnThis==null || dependOnThis.isEmpty())
				return;
			for(Iterator<WeakIdentityCleanup<Depender>> i=dependOnThis.iterator(); i.hasNext(); ) {
				WeakIdentityCleanup<Depender> ref = i.next();
				Depender d = ref.get();
				if(d==null)
					i.remove();
				else
					out.accept(d);
			}
		}else {
			Depender[] arr; 
			int count;
			synchronized (mutex) {
				if(dependOnThis==null || dependOnThis.isEmpty())
					return;
				count=0;
				arr=new Depender[dependOnThis.size()];
				for(Iterator<WeakIdentityCleanup<Depender>> i=dependOnThis.iterator(); i.hasNext(); ) {
					WeakIdentityCleanup<Depender> ref = i.next();
					Depender d = ref.get();
					if(d==null)
						i.remove();
					else
						arr[count++]=d;
				}
			}
			for(int i=0; i<count; ++i)
				out.accept(arr[i]);	
		}
	}


	@Override
	public boolean isInTransaction() {
		synchronized (mutex) {
			return openTransactions>0;	
		}
	}

	/**
	 * Lazily initialized {@link IndependentBool} that allows other parts of the program to observe
	 * whether this value is in a transaction.
	 */
	volatile IndependentBool inTransaction;
	/**
	 * This is called to change the value of {@link #inTransaction}, if that field is not <code>null</code>
	 * It enqueues the action in the {@link #informQueue}, so make sure to
	 * invoke {@link #__workInformQueue(boolean)} after this.
	 */
	Consumer<? super Boolean> setInTransaction=Functional.NOP;
	@Override
	public ReadListenDependencyBool inTransactionValue() {
		IndependentBool localRef = inTransaction;
		if (localRef == null) {
			synchronized (mutex) {
				localRef = inTransaction;
				if (localRef == null) {
					localRef = new IndependentBool(isInTransaction());
					Consumer<? super Boolean> setter = localRef.makeSetter();
					setInTransaction=v->{
						assert !Thread.holdsLock(informQueue);
						// assert !Thread.holdsLock(informRunnerMutex);
						synchronized (informQueue) {
							informQueue.add(()->setter.accept(v));
						}
					};
					localRef.setName((avName==null?"?":avName)+" in transaction");
					localRef.keepStrong(this);
					localRef.seal();
					localRef.owner=this;
					inTransaction = localRef;
				}
			}
		}
		return localRef;

	}
	@Override
	public boolean isDestroyed() {
		return destroyed;
	}
	/**
	 * Restore the old value to be the current value.
	 * This should make the old value invalid.
	 * The current thread must have locked {@link #mutex}.
	 */
	protected abstract void __restoreValueFromOldValue();
	/**
	 * The current thread must have locked {@link #mutex}.
	 * @return Whether there are changed dependencies, necessitating a recomputation of this value
	 */
	protected abstract boolean __hasChangedDependencies();
	/**
	 * The current thread must have locked {@link #mutex}.
	 * @return Whether the "old value" is valid
	 */
	protected abstract boolean __oldValid();
	/**
	 * The current thread must have locked {@link #mutex}.
	 * @return Whether the "current value" is valid
	 */
	protected abstract boolean __valid();
	/**
	 * The current thread must have locked {@link #mutex}.
	 * @return The "old value", or unspecified if the old value is not valid
	 */
	protected abstract E __oldValue();
	/**
	 * The current thread must have locked {@link #mutex}.
	 * @return The "current value", or unspecified if the old value is not valid
	 */
	protected abstract E __value();
	/**
	 * Called from {@link #__endTransaction(boolean)}.
	 * The current thread must have locked {@link #mutex}.
	 * @return Whether the value should remain invalid if it was invalid when the transaction ended.
	 */
	protected abstract boolean __shouldRemainInvalid();
	/**
	 * Cancel any pending non-scouting recomputation, and possibly also an ongoing one.
	 * The current thread must <em>not</em> have locked {@link #mutex}.
	 * @param cancelOngoing cancel the recomputation even if it is already running and not just pending
	 * @return
	 */
	protected boolean cancelPendingRecomputation(boolean cancelOngoing) {
		return cancelPendingRecomputation(cancelOngoing, true);

	}
	
	/**
	 * Cancel any pending non-scouting recomputation, and possibly also an ongoing one.
	 * The current thread must <em>not</em> have locked {@link #mutex}.
	 * @param cancelOngoing cancel the recomputation even if it is already running and not just pending
	 * @param notIfScout Don't cancel the recomputation if it is in dependency scouting mode
	 * @return
	 */
	protected abstract boolean cancelPendingRecomputation(boolean cancelOngoing, boolean notIfScout);
	/**
	 * 
	 * @return Whether this value is currently auto-validating
	 */
	protected abstract boolean isAutoValidating();
	/**
	 * Start a pending recomputation.
	 * This may not actually happen due to various reasons (See {@link PileImpl#__startPendingRecompute(boolean)}}
	 * The current thread must <em>not</em> have locked {@link #mutex}.
	 * @param force
	 */
	protected abstract void __startPendingRecompute(boolean force);
	/**
	 * The current thread should have locked {@link #mutex}
	 * @return the number of open transactions on this value
	 */
	protected final int __openTransactions() {
		return openTransactions;
	}
	/**
	 * Set the equivalence used to decide whether the value has changed.
	 * @param equivalence
	 */
	public void _setEquivalence(BiPredicate<? super E, ? super E> equivalence) {
		this.equivalence = equivalence;
	}
	@Override
	public BiPredicate<? super E, ? super E> _getEquivalence() {
		return equivalence;
	}
	/**
	 * @return Whether the value is valid or at least its invalidity has not been observed yet.
	 */
	public abstract boolean observedValid();
	/**
	 * Called from {@link #informLongTermInvalid()} to actually change the observed validtiy status
	 * @param valid
	 */
	protected abstract void __setValidity(boolean valid);
	/**
	 * Open brackets on the {@linkplain #__value() current value}. This opens all {@link #brackets}.
	 * If the current value is not identical to the {@linkplain #__oldValue() old value},
	 * the {@link #anyBrackets} are also opened.
	 * The current thread must have locked the {@link #mutex}.
	 */
	protected void openBrackets() {
		assert Thread.holdsLock(mutex);
		try {
			if(DebugEnabled.COUNT_BRACKET_LOCKS)
				++lockedValueMutices.get().val;

			closeBrackets();
			E value = __value();
			if(brackets!=null) {
				for(ValueBracket<? super E, ? super ReadListenDependency<? extends E>> b: brackets) {
					try {
						b.open(value, this);
						activeBrackets.add(b);
					}catch(Exception|AssertionError x) {
						log.log(Level.WARNING, "Exception while opening bracket: ", x);
					}
				}
			}
			if(anyBrackets!=null && (!__oldValid() || value!=__oldValue())) {
				for(ValueBracket<? super E, ? super ReadListenDependency<? extends E>> b: anyBrackets) {
					try {
						b.open(value, this);
						activeAnyBrackets.add(b);
					}catch(Exception|AssertionError x) {
						log.log(Level.WARNING, "Exception while opening bracket: ", x);
					}
				}
			}
			if(ET_TRACE && traceEnabledFor(this))trace("openend brackets, ");
		}finally {
			if(DebugEnabled.COUNT_BRACKET_LOCKS)
				--lockedValueMutices.get().val;
		}

	}
	/**
	 * Close brackets on the {@linkplain #__value() current value}. This closes all {@link #brackets}.
	 * If the current value is not identical to the {@linkplain #__oldValue() old value},
	 * the {@link #anyBrackets} are also closed. If the two values are identical, then
	 * the {@linkplain #activeAnyBrackets open brackets} are transfered 
	 * {@linkplain #activeAnyBracketsOnOld to the old value}.
	 * The current thread must have locked the {@link #mutex}.
	 */
	protected boolean closeBrackets() {
		assert Thread.holdsLock(mutex);
		try {
			if(DebugEnabled.COUNT_BRACKET_LOCKS)
				++lockedValueMutices.get().val;


			boolean keep=false;
			E value = __value();
			if(activeBrackets!=null) {
				while(!activeBrackets.isEmpty()) {
					try {
						keep |= activeBrackets.remove(activeBrackets.size()-1).close(value, this);
					}catch(Exception|AssertionError x) {
						log.log(Level.WARNING, "Exception while closing bracket: ", x);
					}
				}
			}
			if(activeAnyBrackets!=null) {
				if(__oldValid() && value==__oldValue()) {	
					activeAnyBracketsOnOld.addAll(activeAnyBrackets);
					activeAnyBrackets.clear();
				}else {	
					while(!activeAnyBrackets.isEmpty()) {
						try {
							activeAnyBrackets.remove(activeAnyBrackets.size()-1).close(value, this);
						}catch(Exception|AssertionError x) {
							log.log(Level.WARNING, "Exception while closing bracket: ", x);
						}
					}
				}
			}
			if(ET_TRACE && traceEnabledFor(this))trace("closed brackets, ");
			return keep;

		}finally {
			if(DebugEnabled.COUNT_BRACKET_LOCKS)
				--lockedValueMutices.get().val;

		}
	}
	/**
	 * Open brackets on the {@linkplain #__oldValue() old value}. This opens all {@link #oldBrackets}.
	 * If the old value is not identical to the {@linkplain #__value() current value},
	 * the {@link #anyBrackets} are also opened.
	 * The current thread must have locked the {@link #mutex}.
	 */
	protected void openOldBrackets() {
		assert Thread.holdsLock(mutex);
		try {
			if(DebugEnabled.COUNT_BRACKET_LOCKS)
				++lockedValueMutices.get().val;

			closeOldBrackets();
			E oldValue = __oldValue();
			if(oldBrackets!=null) {
				for(ValueBracket<? super E, ? super ReadListenDependency<? extends E>> b: oldBrackets) {
					try {
						b.open(oldValue, this);
						activeOldBrackets.add(b);
					}catch(Exception|AssertionError x) {
						log.log(Level.WARNING, "Exception while opening old bracket: ", x);
					}
				}
			}
			if(anyBrackets!=null && (!__valid() || __value()!=oldValue)) {
				for(ValueBracket<? super E, ? super ReadListenDependency<? extends E>> b: anyBrackets) {
					try {
						b.open(oldValue, this);
						activeAnyBracketsOnOld.add(b);
					}catch(Exception|AssertionError x) {
						log.log(Level.WARNING, "Exception while opening bracket: ", x);
					}
				}
			}
			if(ET_TRACE && traceEnabledFor(this))trace("openend old brackets, ");
		}finally {
			if(DebugEnabled.COUNT_BRACKET_LOCKS)
				--lockedValueMutices.get().val;

		}
	}
	/**
	 * Close brackets on the {@linkplain #__oldValue() old value}. This closes all {@link #oldBrackets}.
	 * If the old value is not identical to the {@linkplain #__value() current value},
	 * the {@link #anyBrackets} are also closed. If the two values are identical, then
	 * the {@linkplain #activeAnyBracketsOnOld open brackets} are transfered 
	 * {@linkplain #activeAnyBrackets to the old value}.
	 * The current thread must have locked the {@link #mutex}.
	 */
	protected boolean closeOldBrackets() {
		assert Thread.holdsLock(mutex);
		try {
			if(DebugEnabled.COUNT_BRACKET_LOCKS)
				++lockedValueMutices.get().val;

			E oldValue = __oldValue();
			boolean keep=false;
			if(activeOldBrackets!=null) {
				while(!activeOldBrackets.isEmpty()) {
					try {
						keep |= activeOldBrackets.remove(activeOldBrackets.size()-1).close(oldValue, this);
					}catch(Exception|AssertionError x) {
						log.log(Level.WARNING, "Exception while closing old bracket: ", x);
					}
				}
			}
			if(activeAnyBracketsOnOld!=null) {
				if(__valid() && oldValue==__value()) {	
					activeAnyBrackets.addAll(activeAnyBracketsOnOld);
					activeAnyBracketsOnOld.clear();
				}else {	
					while(!activeAnyBracketsOnOld.isEmpty()) {
						try {
							activeAnyBracketsOnOld.remove(activeAnyBracketsOnOld.size()-1).close(oldValue, this);
						}catch(Exception|AssertionError x) {
							log.log(Level.WARNING, "Exception while closing bracket: ", x);
						}
					}
				}
			}
			if(ET_TRACE && traceEnabledFor(this))trace("closed old brackets, ");

			return keep;
		}finally {
			if(DebugEnabled.COUNT_BRACKET_LOCKS)
				--lockedValueMutices.get().val;

		}
	}

	@Override
	public void addValueListener(ValueListener l) {
		synchronized (mutex) { //FIX?
			ListenValue.Managed.super.addValueListener(l);
		}
	}

	@Override
	public void _addValueBracket(boolean openNow, ValueBracket<? super E, ? super ReadListenDependency<? extends E>> b) {
		synchronized (mutex) {
			if(destroyed)
				return;
			if(brackets==null) {
				brackets=new ArrayList<>();
				activeBrackets=new ArrayList<>();
			}
			brackets.add(b);
			if(openNow && __valid()) {
				try {
					b.open(__value(), this);
					activeBrackets.add(b);
				}catch(Exception|AssertionError x) {
					log.log(Level.WARNING, "Exception while opening bracket: ",x);
				}
			}
		}
	}
	@Override
	public void _addOldValueBracket(boolean openNow, ValueBracket<? super E, ? super ReadListenDependency<? extends E>> b) {
		synchronized (mutex) {
			if(destroyed)
				return;
			if(oldBrackets==null) {
				oldBrackets=new ArrayList<>();
				activeOldBrackets=new ArrayList<>();
			}
			oldBrackets.add(b);
			if(openNow && __oldValid()) {
				try {
					b.open(__oldValue(), this);
					activeOldBrackets.add(b);
				}catch(Exception|AssertionError x) {
					log.log(Level.WARNING, "Exception while opening bracket: ",x);
				}
			}
		}
	}
	@Override
	public void _addAnyValueBracket(boolean openNow, ValueBracket<? super E, ? super ReadListenDependency<? extends E>> b) {
		synchronized (mutex) {
			if(destroyed)
				return;
			if(anyBrackets==null) {
				anyBrackets=new ArrayList<>();
				activeAnyBrackets=new ArrayList<>();
				activeAnyBracketsOnOld=new ArrayList<>();
			}
			anyBrackets.add(b);
			if(openNow) { 
				boolean valid = __valid();
				boolean oldValid = __oldValid();
				if(valid || oldValid) {
					if(valid && oldValid) {
						E value = __value();
						b.open(value, this);
						try {
							if(valid)
								activeAnyBrackets.add(b);
						}catch(Exception|AssertionError x) {
							log.log(Level.WARNING, "Exception while opening bracket: ",x);
						}
						
						E oldValue = __oldValue();
						if(value!=oldValue)
							b.open(oldValue, this);
						try {
							if(oldValid)
								activeAnyBracketsOnOld.add(b);
						}catch(Exception|AssertionError x) {
							log.log(Level.WARNING, "Exception while opening bracket: ",x);
						}
					}

				}else if(valid) {
					E value = __value();
					b.open(value, this);
					try {
						if(valid)
							activeAnyBrackets.add(b);
					}catch(Exception|AssertionError x) {
						log.log(Level.WARNING, "Exception while opening bracket: ",x);
					}

				}else {
					assert oldValid;
					E oldValue = __oldValue();
					b.open(oldValue, this);
					try {
						if(valid)
							activeAnyBracketsOnOld.add(b);
					}catch(Exception|AssertionError x) {
						log.log(Level.WARNING, "Exception while opening bracket: ",x);
					}
				}
			}
		}
	}
	/**
	 * The {@link #brackets} that are open on the {@linkplain #__value() current value}. 
	 * Access to this field should be synchronized using the {@link #mutex}.
	 */
	private ArrayList<ValueBracket<? super E, ? super ReadListenDependency<? extends E>>> activeBrackets;
	/**
	 * The {@link #oldBrackets} that are open on the {@linkplain #__oldValue() old value}. 
	 * Access to this field should be synchronized using the {@link #mutex}.
	 */
	private ArrayList<ValueBracket<? super E, ? super ReadListenDependency<? extends E>>> activeOldBrackets;
	/**
	 * The {@link #anyBrackets} that are open on the {@linkplain #__value() current value}. 
	 * Access to this field should be synchronized using the {@link #mutex}.
	 */
	private ArrayList<ValueBracket<? super E, ? super ReadListenDependency<? extends E>>> activeAnyBrackets;
	/**
	 * The {@link #anyBrackets} that are open on the {@linkplain #__oldValue() old value}. 
	 * Access to this field should be synchronized using the {@link #mutex}.
	 */
	private ArrayList<ValueBracket<? super E, ? super ReadListenDependency<? extends E>>> activeAnyBracketsOnOld;
	/**
	 * The {@link ValueBracket}s that should affect the current value
	 * Access to this field should be synchronized using the {@link #mutex}.
	 */
	private ArrayList<ValueBracket<? super E, ? super ReadListenDependency<? extends E>>> brackets;
	/**
	 * The {@link ValueBracket}s that should affect the old value
	 * Access to this field should be synchronized using the {@link #mutex}.
	 */
	private ArrayList<ValueBracket<? super E, ? super ReadListenDependency<? extends E>>> oldBrackets;
	/**
	 * The {@link ValueBracket}s that should affect the current value and the old value, 
	 * but only once if the two are identical
	 * Access to this field should be synchronized using the {@link #mutex}.
	 */
	private ArrayList<ValueBracket<? super E, ? super ReadListenDependency<? extends E>>> anyBrackets;

	@Override 
	public void bequeathBrackets(boolean openNow, HasBrackets<ReadListenDependency<? extends E>, ? extends E> v) {
		synchronized (mutex) {
			if(brackets!=null)
				for(ValueBracket<? super E, ? super ReadListenDependency<? extends E>> b: brackets)
					if(b.isInheritable())
						v._addValueBracket(openNow, b);
			if(oldBrackets!=null)
				for(ValueBracket<? super E, ? super ReadListenDependency<? extends E>> b: oldBrackets)
					if(b.isInheritable())
						v._addOldValueBracket(openNow, b);

			if(anyBrackets!=null)
				for(ValueBracket<? super E, ? super ReadListenDependency<? extends E>> b: anyBrackets)
					if(b.isInheritable())
						v._addAnyValueBracket(openNow, b);

		}
	}
	/**
	 * The debug callback that you can use to better track what happens to this object.
	 * It should be ignored if {@link DebugEnabled#DE} is <code>false</code>.
	 */
	public DebugCallback dc=null;
	/**
	 * Set the {@link #dc} field
	 * @param dc
	 */
	public void _setDebugCallback(DebugCallback dc) {
		this.dc = dc;
	}
	/**
	 * An exception that was thrown when this object was created.
	 * This is used for debugging in case you forgot to {@link #avName name} an object,
	 * so you can see where it was allocated.
	 * This field will be <code>null</code> if debugging is not enabled.
	 * This is a {@link Throwable} for convenience of displaying it, 
	 * although using a {@link StackTraceElement} array
	 * obtained from the {@link Throwable#getStackTrace()} would be a bit more efficient.
	 * @see DebugEnabled#DE 
	 */
	public final Throwable creationTrace;

	{
		//Initialize the creationTrace
		if(DE) {
			try {
				throw new RuntimeException("Object constructed here: ");
			}catch(RuntimeException x) {
				x.getStackTrace();
				creationTrace=x;
			}
		}else
			creationTrace=null;
	}
	/**
	 * Print the stack trace of the {@link #creationTrace} exception, if present.
	 */
	public void _printConstructionStackTrace() {
		if(DE && creationTrace!=null)
			creationTrace.printStackTrace();
	}

	/**
	 * A set of {@link Depender}s of this {@link Dependency} that have requested to be
	 * {@link Depender#deepRevalidate(Dependency) deepRevalidated} when something happens to this
	 * value.
	 */
	HashSet<WeakIdentityCleanup<Depender>> dependersNeedingDeepRevalidate;

	@Override
	public void __dependerNeedsDeepRevalidate(Depender d, boolean needs) {
		Objects.requireNonNull(d);
		WeakIdentityCleanup<Depender> dr=new WeakIdentityCleanup<Depender>(d);
		Dependency[] deps;
		Depender self;
		if(needs) {
			synchronized (mutex) {
				if(!dependOnThis.contains(dr))
					return;
				if(dependersNeedingDeepRevalidate!=null) {
					if(dependersNeedingDeepRevalidate.contains(dr))
						return;
				}else {
					dependersNeedingDeepRevalidate=new HashSet<>();
				}
				dependersNeedingDeepRevalidate.add(dr);
				if(!(this instanceof Depender))
					return;
				deps = __dependencies();

			}
			if(deps==null)
				return;
			self=(Depender) this;
			for(Dependency dep: deps)
				dep.__dependerNeedsDeepRevalidate(self, true);	
		}else {
			synchronized (mutex) {
				if(dependersNeedingDeepRevalidate==null || !dependersNeedingDeepRevalidate.contains(dr))
					return;
				dependersNeedingDeepRevalidate.remove(dr);
				if(!(this instanceof Depender))
					return;
				if(thisNeedsDeepRevalidate || !dependersNeedingDeepRevalidate.isEmpty())
					return;
				deps = __dependencies();

			}
			if(deps==null)
				return;
			self=(Depender) this;
			for(Dependency dep: deps)
				dep.__dependerNeedsDeepRevalidate(self, false);
		}
	}
	/**
	 * Whether this object needs {@link deepRevalidated deep revalidation} if one of its 
	 * (transitive) {@link Dependency Dependencies} changes.
	 * This is the case when it is valid (for example because it has been 
	 * {@link WriteValue #set} explicitly) while some of its 
	 * {@link Dependency Dependencies} weren't.
	 * Note that if this field is false, but there are {@link #dependersNeedingDeepRevalidate}, 
	 * this object will still be {@link Depender#deepRevalidate(Dependency) deepRevalidate}d
	 */
	boolean thisNeedsDeepRevalidate;
	/**
	 * Change {@link #thisNeedsDeepRevalidate} and inform the {@link Dependency Dependencies}
	 * if that changes whether this object should be 
	 * {@link Depender#deepRevalidate(Dependency) deepRevalidate}d.
	 * @param needs
	 */
	protected void __thisNeedsDeepRevalidate(boolean needs) {
		if(!(this instanceof Depender))
			return;
		Dependency[] deps;
		Depender self;
		if(needs) {
			synchronized (mutex) {
				thisNeedsDeepRevalidate=true;
				deps = __dependencies();

			}
			if(deps==null)
				return;
			self=(Depender) this;
			for(Dependency dep: deps)
				dep.__dependerNeedsDeepRevalidate(self, true);
		}else {
			synchronized (mutex) {
				thisNeedsDeepRevalidate=false;
				if(dependersNeedingDeepRevalidate!=null) {
					for(Iterator<WeakIdentityCleanup<Depender>> i = dependersNeedingDeepRevalidate.iterator(); i.hasNext(); ) {
						WeakIdentityCleanup<Depender> ref = i.next();
						Depender d = ref.get();
						if(d==null)
							i.remove();
						else if(!dependOnThis.contains(ref))
							i.remove();
					}
					if(!dependersNeedingDeepRevalidate.isEmpty())
						return;
				}
				deps = __dependencies();

			}
			if(deps==null)
				return;
			self=(Depender) this;
			for(Dependency dep: deps)
				dep.__dependerNeedsDeepRevalidate(self, false);
		}
	}
	/**
	 * 
	 * @return An array containing all current {@link Dependency Dependencies} that this value depends on,
	 * or <code>null</code> if there are none.
	 */
	abstract protected Dependency[] __dependencies();

	/**
	 * Schedule a recomputation.
	 * The current Thread must not have blocked {@link #mutex}.
	 * This opens a transaction (if there isn't one already) that will be ended once the
	 * {@link Recomputation} is eventually fulfilled or {@link Recomputation#cancel() cancel}ed.
	 * @param cancelOngoing Whether to cancel any {@link Recomputation} that may already be ongoing.
	 * 
	 */
	boolean shouldFireDeepRevalidateOnSet = true;

	public void __shouldFireDeepRevalidateOnSet(boolean b) {
		shouldFireDeepRevalidateOnSet = b;	
	}
	
	protected void fireDeepRevalidateOnSet() {
		if(!shouldFireDeepRevalidateOnSet)
			return;
		if(!Piles.shouldFireDeepRevalidateOnSet())
			return;
		
		fireDeepRevalidate();
	}
	
	abstract protected void __scheduleRecomputation(boolean cancelOngoing);
	public void __fireDeepRevalidate() {fireDeepRevalidate();}
	/**
	 * Deeply revalidate all {@link Depender}s of this {@link Pile} that are marked as need deep revalidation.
	 * This method should only be called from within the default implementation of
	 * {@link #deepRevalidate()} 

	 */
	protected void fireDeepRevalidate() {
		if(DE && dc!=null)
			dc.fireDeepRevalidate(this);
		ArrayList<Depender> fireTo;
		synchronized (mutex) {
			if(deepRevalidationSuppressors>0)
				return;
			if(__valid())
				return;
			if(dependersNeedingDeepRevalidate==null || dependersNeedingDeepRevalidate.isEmpty())
				return;
			if(dependOnThis==null || dependOnThis.isEmpty())
				return;
			fireTo=new ArrayList<>(dependersNeedingDeepRevalidate.size());
			for(WeakIdentityCleanup<Depender> ref: dependersNeedingDeepRevalidate) {
				if(!dependOnThis.contains(ref))
					continue;
				Depender d=ref.get();
				if(d==null)
					continue;
				fireTo.add(d);
			}
			dependersNeedingDeepRevalidate.clear();
		}
		if(!fireTo.isEmpty())
			for(Depender d: fireTo)
				d.deepRevalidate(this);
	}
	int deepRevalidationSuppressors;
	@Override
	public boolean isDeepRevalidationSuppressed() {
		synchronized (mutex) {
			return deepRevalidationSuppressors>0;
		}
	}
	@Override
	public Suppressor suppressDeepRevalidation() {
		Suppressor ret = Suppressor.wrap(()->{
			synchronized (mutex) {
				deepRevalidationSuppressors--;
			}
		});
		synchronized (mutex) {
			deepRevalidationSuppressors++;	
		}
		return ret;
	}

	@Override
	public void await(WaitService ws, BooleanSupplier c) throws InterruptedException {
		while(!c.getAsBoolean()) {
			synchronized (mutex) {
				ws.wait(mutex, 1000);
			}
		}
	}
	@Override
	public boolean await(WaitService ws, BooleanSupplier c, long millis) throws InterruptedException {
		long t0 = System.currentTimeMillis();
		while(!c.getAsBoolean()) {
			synchronized (mutex) {
				long left = millis - (System.currentTimeMillis()-t0);
				if(left<=0)
					return false;
				ws.wait(mutex, Math.min(1000, left));
			}
		}
		return true;
	}
	@Override
	final public String dependencyName() {
		return avName==null?"?":avName;
	}

	/**
	 * The {@link Depender}s of this reactive value that it is essential for. 
	 * <code>null</code> means empty. access to this map must be synchronized using itself.
	 * Initialization of the reference must be synchronized using {@link #mutex}
	 */
	protected volatile WeakHashMap<Depender,?> isEssentialFor;
	@Override
	public void __setEssentialFor(Depender value, boolean essential) {
		if(isEssentialFor==null) {
			if(!essential)
				return;
			synchronized (mutex) {
				if(destroyed)
					return;
				WeakHashMap<Depender, ?> localRef = isEssentialFor;
				if(localRef==null) {
					localRef = new WeakHashMap<>();
					this.isEssentialFor = localRef;
				}
			}
		}
		synchronized (isEssentialFor) {
			if(destroyed)
				return;
			if(essential)
				isEssentialFor.put(value, null);
			else
				isEssentialFor.remove(value);
		}

	}
}
