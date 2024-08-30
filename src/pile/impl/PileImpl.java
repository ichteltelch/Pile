package pile.impl;

import static pile.interop.debug.DebugEnabled.DE;
import static pile.interop.debug.DebugEnabled.ET_TRACE;
import static pile.interop.debug.DebugEnabled.RENAME_RECOMPUTATION_THREADS;
import static pile.interop.debug.DebugEnabled.traceEnabledFor;

import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.Future;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.logging.Level;
import java.util.logging.Logger;

import pile.aspect.CanAutoValidate;
import pile.aspect.Dependency;
import pile.aspect.Depender;
import pile.aspect.HasAssociations;
import pile.aspect.LazyValidatable;
import pile.aspect.VetoException;
import pile.aspect.combinations.Pile;
import pile.aspect.listen.ListenValue;
import pile.aspect.listen.ValueEvent;
import pile.aspect.recompute.DependencyRecorder;
import pile.aspect.recompute.Recomputation;
import pile.aspect.recompute.Recomputation.WrapWeak;
import pile.aspect.recompute.Recomputations;
import pile.aspect.recompute.Recomputer;
import pile.aspect.suppress.MockBlock;
import pile.aspect.suppress.Suppressor;
import pile.aspect.transform.BehaviorDuringTransform;
import pile.aspect.transform.TransformHandler;
import pile.aspect.transform.TransformHandler.TypedReaction;
import pile.aspect.transform.TransformReaction;
import pile.aspect.transform.TransformValueEvent;
import pile.aspect.transform.TransformingException;
import pile.interop.debug.DebugEnabled;
import pile.interop.exec.StandardExecutors;
import pile.interop.wait.WaitService;
import pile.specialized_bool.IndependentBool;
import pile.specialized_bool.combinations.ReadListenDependencyBool;
import pile.specialized_int.MutInt;
import pile.utils.Functional;
import pile.utils.WeakCleanupWithRunnable;
import pile.utils.WeakIdentityCleanup;

/**
 * The default implementation of {@link Pile}.
 * It is not very efficient, but OK for a few hundred {@link Pile}s reacting at
 * interactive speeds.
 * @author bb
 *
 * @param <E>
 */
public class PileImpl<E> extends 
AbstractReadListenDependency<E>
implements Pile<E>, HasAssociations.Mixin
{
	private final static Logger log=Logger.getLogger("Value");

	//	private boolean dontRetry = false;

	//Value fields
	E __value;
	volatile boolean valid;
	boolean observedValid;
	@Override
	public final boolean observedValid() {
		synchronized (mutex) {
			return observedValid;
		}
	}

	//old value fields
	E oldValue;
	boolean oldValid;
	volatile boolean lazyValidating;

	BehaviorDuringTransform bdt=BehaviorDuringTransform.NOP;

	//dependency fields
	HashSet<Dependency> _thisDependsOn;
	Object invalidDependenciesMutex=new Object();
	HashSet<Dependency> invalidDependencies;
	HashSet<Dependency> changingDependencies;
	HashSet<Dependency> changedDependencies;
	volatile Set<Dependency> changedDependenciesReadOnly;

	int autoValidationSuppressors;


	/**
	 * True while a transaction is open because a recomputation is running or might 
	 * in principle be started. Reasons why the recomputation might not have been started
	 * may be that this Value is not autovalidating, has invalid dependencies, or has other open transactions.
	 * Reasons why this field might be <code>false</code> are that the Value is {@link #valid},
	 * no {@link #recompute} code has been defined or this {@link PileImpl} 
	 * has been {@link #permaInvalidate() perma-invaldated} 
	 */
	int recomputationTransactions;
	MyRecomputation<E> ongoingRecomputation;


	volatile ArrayList<Function<? super E, ? extends E>> correctors;

	//Consumer<? super Recomputation<E>> recompute;
	Recomputer<E> recompute;





	//	@Override
	//	public Maybe<E> getWithValidity() {
	//		assert !Thread.holdsLock(mutex);
	//		checkForTransformEnd();
	//		if(lazyValidating)
	//			lazyValidate();
	//		informLongTermInvalid();
	//		synchronized (mutex) {
	//			if(destroyed)
	//				throw new IllegalStateException("This value has been destroyed: "+avName);
	//			if(valid())
	//				return Maybe.just(value);
	//			else
	//				return Maybe.nothing();
	//		}
	//	}

	@Override
	public E get() {
		assert !Thread.holdsLock(mutex);
		checkForTransformEnd();
		if(lazyValidating)
			lazyValidate();
		informLongTermInvalid();
		synchronized (mutex) {
			if(destroyed) {
				//	throw new IllegalStateException("This value has been destroyed: "+avName);
				return null;
			}
			recordRead();
			if(__valid())
				return __value;
			else
				return null;
		}
	}

	@Override
	public E getValid(WaitService ws) throws InterruptedException {
		assert !Thread.holdsLock(mutex);
		checkForTransformEnd();
		if(lazyValidating)
			lazyValidate();
		//		informLongTermInvalid();
		synchronized (mutex) {
			while(!__valid()) {
				if(destroyed)
					throw new IllegalStateException("This value has been destroyed: "+avName);
				ws.wait(mutex, 1000);
			}
			recordRead();
			return __value;
		}
	}
	@Override
	public E getOldIfInvalid() {
		assert !Thread.holdsLock(mutex);
		checkForTransformEnd();
		if(lazyValidating)
			lazyValidate();
		informLongTermInvalid();
		synchronized (mutex) {
			if(!__valid()) {
				if(destroyed)
					throw new IllegalStateException("This value has been destroyed: "+avName);
				recordRead();
				if(oldValid)
					return oldValue;
				return null;
			}
			recordRead();
			return __value;
		}
	}
	@Override
	public E getValidOrThrow() throws InvalidValueException {
		assert !Thread.holdsLock(mutex);
		checkForTransformEnd();
		if(lazyValidating)
			lazyValidate();
		informLongTermInvalid();
		recordRead();
		synchronized (mutex) {
			if(!__valid())
				throw new InvalidValueException(avName);
			return __value;
		}
	}

	@Override
	public E getValid(WaitService ws, long timeout) throws InterruptedException {
		assert !Thread.holdsLock(mutex);
		checkForTransformEnd();
		if(lazyValidating)
			lazyValidate();
		try {
			synchronized (mutex) {
				while(!__valid() && timeout>0) {
					if(destroyed)
						throw new IllegalStateException("This value has been destroyed: "+avName);
					long t0 = System.currentTimeMillis(); 
					ws.wait(mutex, Math.min(1000, timeout));
					long t1 = System.currentTimeMillis();
					timeout -= t1-t0;
				}
				recordRead();

				return __value;
			}
		}finally {
			informLongTermInvalid();
		}
	}

	@Override
	public boolean isValid() {
		assert !Thread.holdsLock(mutex);
		informLongTermInvalid();

		if(Recomputations.getCurrentRecorder()!=null) {
			validity().recordRead();
		}


		//		recordRead();
		synchronized (mutex) {
			return __valid();
		}
	}
	@Override
	public boolean isValidNull() {
		assert !Thread.holdsLock(mutex);
		informLongTermInvalid();
		recordRead();

		synchronized (mutex) {
			return __valid() & __value==null;
		}
	}
	@Override
	public boolean isDestroyed() {
		return destroyed;
	}



	HashSet<Dependency> essentialDependencies;

	//	private Object scoutMutex;
	@Override
	public void setDependencyEssential(boolean essential, Dependency d) {
		//		if(d==null)
		//			throw new IllegalArgumentException();
		if(essential) {
			d.__setEssentialFor(this, true);
			synchronized (mutex) {
				if(essentialDependencies==null)
					essentialDependencies=new HashSet<>();
				essentialDependencies.add(d);
				d.__setEssentialFor(this, true);
			}
		}else {
			d.__setEssentialFor(this, false);
			synchronized (mutex) {
				if(essentialDependencies!=null)
					essentialDependencies.remove(d);
				d.__setEssentialFor(this, false);
			}
		}

	}

	@Override
	public boolean isEssential(Dependency value) {
		synchronized (mutex) {
			if(essentialDependencies==null)
				return false;
			else
				return essentialDependencies.contains(value);
		}

	}
	@Override
	public void addDependency(Dependency d, boolean invalidate) {
		addDependency0(d, invalidate, invalidate, true);
	}
	@Override
	public void addDependency(Dependency d, boolean invalidate, boolean recordChange) {
		addDependency0(d, invalidate, recordChange, true);
	}
	private void addDependency0(Dependency d, boolean invalidate, boolean recordChange, boolean cancel) {
		if(d==null) {
			return;
		}
		if(d==this) {
			throw new IllegalArgumentException("What are you trying to do? Cannot add itself as a dependency!");
		}
		if(d.isDestroyed()) {
			throw new IllegalArgumentException("Dependency is already destroyed");
		}
		boolean wasValid;
		boolean recompute;
		boolean actuallyAdded;
		Objects.requireNonNull(d);
		synchronized (mutex) {
			if(destroyed) {
				throw new IllegalStateException("This value has been destroyed: "+avName);
			}
			wasValid=__valid();
			if(_thisDependsOn==null)
				_thisDependsOn=new HashSet<>();
			if(_thisDependsOn.add(d)) {
				if(recordChange) {
					if(changedDependencies==null)
						changedDependencies=new HashSet<Dependency>();
					changedDependencies.add(d);
				}
				synchronized (invalidDependenciesMutex) {
					//					checkForDestroyedDeps();
					if(!d.isValidAsync()) {
						if(invalidDependencies==null)
							invalidDependencies=new HashSet<>();
						invalidDependencies.add(d);
					}
					//					checkForDestroyedDeps();

				}
				actuallyAdded=true;

				recompute = invalidate && wasValid;
			}else {
				recompute=false;
				actuallyAdded=false;
			}

		}
		if(actuallyAdded) {
			d.__addDepender(this, invalidate);			
			dependencyAdded(d);
		}else {

		}
		if(recompute) {
			revalidate();
			//			startPendingRecompute(false);	
		}
	}

	//	protected void checkForDestroyedDeps() {
	//		if(invalidDependencies!=null) {
	//			for(Dependency d2: invalidDependencies)
	//				if(
	//						//					d2.isDestroyed() && 
	//						d2.dependencyName()=="mmTransform") {
	//					HashSet<Dependency> tdo = thisDependsOn;
	//					if(tdo!=null && !tdo.contains(d2))	
	//						System.out.println();
	//				}
	//		}
	//	}
	/**
	 * Called after a dependency has been added. Subclasses might find this interesting.
	 * @param d
	 */
	protected void dependencyAdded(Dependency d) {}
	/**
	 * Called after a dependency has been removed. Subclasses might find this interesting.
	 * @param d
	 */
	protected void dependencyRemoved(Dependency d) {}
	@Override
	public void removeDependency(Dependency d, boolean invalidate, boolean recordChange) {
		removeDependency0(d, invalidate, recordChange);	
	}
	private void removeDependency0(Dependency d, boolean invalidate, boolean recordChange) {
		//		boolean wasValid;
		//		boolean recompute;
		boolean destroy;
		boolean actuallyRemoved;


		synchronized (mutex) {
			if(!destroyed && essentialDependencies!=null && essentialDependencies.contains(d)) {
				if(d.isDestroyed())
					destroy=true;
				else {
					if(recompute == null || !recompute.mayRemoveDynamicDependency(d, this))
						throw new IllegalArgumentException("Cannot remove essential dependencies!");
					destroy=false;
				}
			}else {
				destroy=false;
			}
		}
		if(destroy) {
			destroy();
			return;
		}
		synchronized (mutex) {
			//			wasValid=valid();
			if(_thisDependsOn==null)
				return;
			if(_thisDependsOn.remove(d)) {
				if(recordChange) {
					if(changedDependencies==null)
						changedDependencies=new HashSet<Dependency>();
					changedDependencies.add(d);
				}
				synchronized (invalidDependenciesMutex) {

					if(invalidDependencies!=null)
						invalidDependencies.remove(d);
					//					checkForDestroyedDeps();

				}
				actuallyRemoved=true;
				//				recompute = invalidate && wasValid && allDependenciesValid() && openTransactions()==0 && isAutoValidating() && !isDestroyed();
				//					
				//				if(recompute) {
				//					cancel=pendingRecompute;
				////					pendingRecompute=new MyRecomputation<>(this);
				//				}else
				//					cancel=null;
			}else {
				//				recompute=false;
				actuallyRemoved=false;
			}

		}


		//		if(cancel!=null)
		//			cancel.cancel();
		//		if(cancel!=null && cancel.transactionActive)
		//			System.out.println("what?");


		if(actuallyRemoved) {
			d.__removeDepender(this);			
			dependencyRemoved(d);
		}
		if(invalidate && !destroyed)
			revalidate();
		//		if(recompute)
		//			startPendingRecompute(false);
	}

	/**
	 * 
	 * @return Whether all dependencies are valid and not currently changing
	 */
	protected boolean allDependenciesValid() {
		synchronized (invalidDependenciesMutex) {
			//			checkForDestroyedDeps();
			return (invalidDependencies==null || invalidDependencies.isEmpty())
					&& (changingDependencies==null || changingDependencies.isEmpty());
		}
	}

	//	boolean canRecomputeWithInvalidDependencies;
	//	public void _setCanRecomputeWithInvalidDependencies(boolean b) {
	//		synchronized(mutex) {
	//			canRecomputeWithInvalidDependencies=b;
	//		}
	//		startPendingRecompute(true);
	//	}	
	@Override
	protected void __startPendingRecompute(boolean force) {
		__startPendingRecompute(force, false);
	}

	private void __startPendingRecompute(boolean force, boolean _scout) {
		if(!_scout && Recomputations.areRecomputationsSuspended()) {
			boolean avs;
			synchronized (mutex) {
				avs = autoValidationSuppressors > 0;
			}
			if(avs) {
				Recomputations.possiblySuspendRecomputation(()->___startPendingRecompute(force, _scout));
				return;
			}
		}
		___startPendingRecompute(force, _scout);
	}

	/**
	 * This tracks how deeply calls to _startRecomputation are nested and logs a warning if it becomes too deep
	 * TODO: Make this a debugging feature not enabled by default
	 */
	static final ThreadLocal<MutInt> recomputationDepth = new ThreadLocal<>();
	private void ___startPendingRecompute(boolean force, boolean _scout) {
		ListenValue.DEFER.run(()->___startPendingRecompute_undeferred(force, _scout));
	}
	private void ___startPendingRecompute_undeferred(boolean force, boolean _scout) {
		final Consumer<? super Recomputation<E>> rc;
		WrapWeak<E> ww;
		int deactivate;
		//		MyRecomputation<?> ongoing = currentlyRevalidating.get();

		boolean scout;
		deactivate:synchronized (mutex) {
			if(fulfillNesting>20) {
				log.log(Level.WARNING, "Recomputation restarted too often!");
			}
			scout = _scout && nextRecomputationIsScout;
			//			if(scout && scoutMutex==null)
			//				scoutMutex=new Object();

			if(ongoingRecomputation!=null) {
				if(!ongoingRecomputation.isFinished()) {
					if(ET_TRACE && traceEnabledFor(this))trace("recomputation not started: already ongoing");
					return;
				}else {
					ongoingRecomputation=null;
				}
			}
			assert recomputationTransactions>=0;
			if(recomputationTransactions==0) {
				if(ET_TRACE && traceEnabledFor(this))trace("recomputation not started: not active");

				return;
			}
			rc = recompute;
			if(rc==null | destroyed) {
				if(ET_TRACE && traceEnabledFor(this))trace("recomputation not started: no recomputer");

				return;
			}
			if(!force && autoValidationSuppressors>0) {
				if(ET_TRACE && traceEnabledFor(this))trace("recomputation not started: autovalidation supressed");

				return;
			}
			if(//!canRecomputeWithInvalidDependencies && 
					!allDependenciesValid() && !scout) {
				if(ET_TRACE && traceEnabledFor(this))trace("recomputation not started: invalid dependencies "+invalidDependencies);

				return;
			}
			if(__openTransactions()>recomputationTransactions && !scout) {
				//				if(!canRecomputeWithInvalidDependencies) {
				if(ET_TRACE && traceEnabledFor(this))trace("recomputation not started: open transactions");

				return;
				//				}
			}
			if(lazyValidating && !force) {

				if(ET_TRACE && traceEnabledFor(this))trace("recomputation not started: lazyValidating");
				return;
			}
			//			if(mr==null) {
			//				if(force && ongoing==null && !valid)
			//					mr=pendingRecompute=new MyRecomputation<>(this);
			//				else {
			//					if(ET_TRACE && traceEnabledFor(this))synchronized(trace) {trace.append("recomputation not started: didn't want to force it, ");}
			//
			//					return;
			//				}
			//			}


			//			if(mr.isFinished()) {
			//				if(ET_TRACE && traceEnabledFor(this))synchronized(trace) {trace.append("recomputation not started: already finished, ");}
			//
			//				return;
			//			}
			//			if(!mr.getThread().isNull()) {
			//				if(ET_TRACE && traceEnabledFor(this))synchronized(trace) {trace.append("recomputation not started: already running, ");}
			//
			//				return;
			//			}

			if(__valid()/* && dontRetry*/ && !scout) {
				deactivate=recomputationTransactions;
				recomputationTransactions=0;
				ww=null;
				ccd();
				if(ET_TRACE && traceEnabledFor(this))trace("recomputation not started: already valid, ");

				break deactivate;

			}else {
				deactivate=0;
			}
			if(deactivate==0) {
				MyRecomputation<E> mr = new MyRecomputation<>(this, true, scout);
				isComputing=true;
				setComputing.accept(!scout);
				mr.t=Thread.currentThread();
				//synchronized (schedulingMutex) 
				{

					if(DE) {
						//					if(!transactionReasons.remove(new TransactionTracker(this, "pending recomputation")))
						//						System.out.println("what?");
						changeTransactionReason(new TransactionTracker(this, "pending recomputation", null), new TransactionTracker(this, "ongoing recomputation", null));
					}
				}
				if(DE && dc!=null) dc.startPendingRecomputation(this);
				recomputationTransactions--;
				ongoingRecomputation=mr;
				ww=mr.wrapWeak(dependencyName());
				WaitService.get().notifyAll(mutex);
			}else {
				//				if(scout)
				//					System.out.println();
				ww=null;
				assert ongoingRecomputation==null;
			}
		}
		__workInformQueue();
		if(deactivate>0) {
			assert deactivate==1;
			while(--deactivate>=0) {
				//synchronized (schedulingMutex) 
				{
					if(DE) {
						removeTransactionReason(new TransactionTracker(this, "pending recomputation", null));
					}
					__endTransaction(false);
				}
			}
			//			if(scout)
			//				System.out.println();

			return;
		}



		try {
			//			if(ongoing!=null 
			//					//					&& !ongoing.finished
			//					) {
			//				printConstructionStackTrace();
			//				//				if(scout)
			//				//					System.out.println();
			//				//ww.cancel();
			//				//ww.cancel();
			//				throw new IllegalStateException("Already recomputing!");
			//			}
			HashSet<Object> informing = Depender.informingLongTermInvalid.get();
			if(informing==null) {
				try (MockBlock b = Recomputations.withCurrentRecomputation(ww)){
					if(ET_TRACE && traceEnabledFor(this))trace("recomputation started");

					//				if(scout) {
					//					synchronized (scoutMutex) {
					//						rc.accept(ww);
					//					}
					//				}else {
					MutInt rd = recomputationDepth.get();
					if(rd == null)
						recomputationDepth.set(rd = new MutInt(0));

					rd.val++;
					boolean interrupted = WaitService.get().interrupted();
					try {
						if(rd.val>100) {
							log.warning("very deeply nested recomputation in Pile '"+dependencyName()+"'");
						}
						rc.accept(ww);
					}finally {
						rd.val--;
						if(interrupted)
							WaitService.get().interruptSelf();
						else
							WaitService.get().interrupted();
					}
					//				}
				}finally {
					//				currentlyRevalidating.set(ongoing);
				}
			}
			else
				StandardExecutors.unlimited().execute(()->{
					ww.setThread();
					try (MockBlock b = Recomputations.withCurrentRecomputation(ww)){
						if(ET_TRACE && traceEnabledFor(this))trace("recomputation started in different Thread (because informing of long term invalidity)");
						rc.accept(ww);
					}
				});

		}catch(Throwable t) {
			log.log(Level.INFO, "Isolated an error in the recomputer", t);
		}


	}
	//	ThreadLocal<MyRecomputation<?>> currentlyRevalidating=new ThreadLocal<>();

	private void ccd() {
		if(changedDependencies!=null)
			changedDependencies.clear();
	}
	@Override
	public boolean isAutoValidating() {
		synchronized (mutex) {
			return !destroyed && autoValidationSuppressors<=0;
		}
	}
	@Override
	public E applyCorrection(E value) {
		ArrayList<Function<? super E, ? extends E>> correctors = this.correctors;
		if(correctors==null)
			return value;
		synchronized (correctors) {
			for(Function<? super E, ? extends E> c: correctors)
				value = c.apply(value);
		}
		return value;
	}
	/**
	 * Lazily initialize the {@link #correctors} field
	 * @return
	 */
	protected ArrayList<Function<? super E, ? extends E>> lazyInitCorrectors() {
		ArrayList<Function<? super E, ? extends E>> localRef = correctors;
		if (localRef == null) {
			synchronized (mutex) {
				localRef = correctors;
				if (localRef == null) {
					correctors = localRef = new ArrayList<>();
				}
			}
		}
		return localRef;
	}
	@Override
	public void _addCorrector(Function<? super E, ? extends E> corrector) {
		Objects.requireNonNull(corrector);
		ArrayList<Function<? super E, ? extends E>> localRef = lazyInitCorrectors();
		synchronized (localRef) {
			localRef.add(corrector);
		}
	}
	/**
	 * Track how deeply calls that fulfill {@link Recomputations} for this
	 * {@link Pile} are nested, so that a warning can be output if that is too deep.
	 * TODO: Make this a debugging feature not enabled by default
	 */
	int fulfillNesting;
	private static final class MyRecomputation<E> implements Recomputation<E>{

		//		static AtomicInteger counter = new AtomicInteger();
		volatile Thread t;
		Future<?> f;
		boolean finished;
		boolean scout;
		boolean finishing;
		WeakReference<PileImpl<E>> outer;

		Consumer<? super E> failHandler;
		Consumer<? super E> successHandler;
		Predicate<? super Dependency> dependencyVeto = Functional.CONST_FALSE;

		boolean recording, delayedMode;
		HashSet<Dependency> recorded;

		public MyRecomputation(PileImpl<E> outer, boolean ta, boolean scout) {
			//			if(scout)
			//				System.out.println("Started scouting recomputation #"+counter.incrementAndGet()+ ", this one for "+outer.dependencyName());
			this.outer=new WeakCleanupWithRunnable<PileImpl<E>>(outer, this::cancel);
			transactionActive=ta;
			this.scout = scout;
		}

		boolean transactionActive;
		//		MyRecomputation activate() {
		//			synchronized (this) {
		//				assert !transactionActive;
		//				transactionActive=true;
		//			}
		//			beginTransaction();
		//			boolean cancel=false;
		//			synchronized (mutex) {
		//				if(pendingRecompute!=this)
		//					cancel=true;
		//			}
		//			if(cancel)
		//				cancel();
		//			return this;
		//		}
		boolean deactivate() {
			try {

				//			if(recorded!=null) {
				//				System.out.println("recorder deactivated");
				//			}
				boolean et, ret;
				synchronized (this) {
					if(RENAME_RECOMPUTATION_THREADS && threadNameBeforeRenaming!=null && t!=null) {
						try {
							t.setName(threadNameBeforeRenaming);
							threadNameBeforeRenaming = null;
						}catch(SecurityException x) {
							log.log(Level.WARNING, "could not restore thread name", x);
						}
					}
					ret = !finished;
					finished=true;
					if(transactionActive) {
						transactionActive=false;
						WaitService.get().notifyAll(this);
						et=true;
					}else {
						et=false;
					}

				}
				PileImpl<E> outer=this.outer.get();
				if(outer==null) 
					return ret;

				if(et) {

					//					Object schedulingMutex = outer.schedulingMutex;
					//synchronized(schedulingMutex) 
					{
						if(DE) {
							outer.removeTransactionReason(new TransactionTracker(outer, "ongoing recomputation", null));
						}
						outer.__endTransaction(!scout);
					}

				}
				outer.__workInformQueue();
				boolean owiq;
				synchronized (outer.mutex) {
					if(this==outer.ongoingRecomputation) {
						//					if(DE && outer.pendingRecompute.transactionActive)
						//						System.out.println();
						outer.ongoingRecomputation=null;
						outer.isComputing=false;
						owiq=true;
						outer.setComputing.accept(Boolean.FALSE);
					}else {
						owiq=false;
					}
					WaitService.get().notifyAll(outer.mutex);
				}
				if(owiq)
					outer.__workInformQueue();
				return ret;
			}finally {
				f=null;
				t=null;
				failHandler=null;
				recording=false;
				recorded=null;
			}

		}
		//		private boolean deactivateLocked() {
		//			//			boolean et;
		//			boolean ret;
		//			synchronized (this) {
		//				ret = !finished;
		//				finished=true;
		//				if(transactionActive) {
		//					transactionActive=false;
		//					notifyAll();
		//					//					et = true;
		//				}else {
		//					//					et = false;
		//				}	
		//
		//			}
		//			Value<E> outer=this.outer.get();
		//			if(outer==null) return ret;
		//			assert Thread.holdsLock(outer.mutex);
		//			if(this==outer.ongoingRecomputation) {
		//				//				if(outer.pendingRecompute.transactionActive)
		//				//					System.out.println();
		//				outer.ongoingRecomputation=null;
		//				outer.isComputing=false;
		//				outer.setComputing.accept(Boolean.FALSE);
		//				outer.recomputationTransactionActive=false;
		//			}
		//			//			if(et)
		//			//				outer.endTransaction();
		//			//outer.workInformQueue();
		//			t=null;
		//			f=null;
		//			return ret;
		//
		//		}
		@Override
		public void forgetOldValue() {
			if(t==null || isDependencyScout())
				return;
			if(Thread.currentThread()!=t)
				throw new IllegalStateException("forgetOldValue() may only be called from the recomputatin step");
			PileImpl<E> outer=this.outer.get();
			if(outer==null) return;

			synchronized (outer.mutex) {
				if(this!=outer.ongoingRecomputation)
					return;
				if(outer.oldValid)
					outer.closeOldBrackets();
			}
		}

		@Override
		public boolean fulfill(E val, Runnable onSuccess) {
			finishing=true;
			recording=false;
			boolean restart=false;
			Thread lt = t;
			if(lt==null)
				return false;
			if(Thread.currentThread()!=lt)
				throw new IllegalStateException("fulfill() may only be called from the recomputating step");
			PileImpl<E> outer=this.outer.get();
			if(outer==null) return false;

			if(DE && outer.dc!=null) outer.dc.fulfill(outer, val);
			try {
				synchronized (outer.mutex) {++outer.fulfillNesting;}

				HashSet<Dependency> rec;
				if(recorded!=null) {
					recording=false;
					rec = diffRecorded(outer);	
					if(rec!=null && !rec.isEmpty()) {
						scout=true;
					}
				}else {
					rec=null;
				}
				if(!scout) {
					outer.checkForTransformEnd(BehaviorDuringTransform.THROW_TRANSFORMINGEXCEPTION);

					try {
						val=outer.applyCorrection(val);
					}catch(VetoException x) {
						if(fulfillInvalid() && x.revalidate) {
							outer.revalidate();
						}
						throw x;
					}catch(RuntimeException x) {
						log.log(Level.SEVERE, "Exception in applyCorrection", x);
						fulfillInvalid();
						throw x;
					}
				}
				boolean depend = false;
				boolean fail = false;
				try {
					if(!scout) {
						if(successHandler!=null) {
							E fval = val;
							StandardExecutors.safe(()->successHandler.accept(fval));
						}
					}
					synchronized (outer.mutex) {
						if(this!=outer.ongoingRecomputation || scout || StandardExecutors.interrupted()) {
							fail = failHandler!=null;

							return depend = this==outer.ongoingRecomputation;
						}

						//					if(outer.pendingRecompute.transactionActive)
						//						System.out.println();

						if(!scout) {
							outer.__clearChangedDependencies();
							outer.isComputing=false;
							outer.setComputing.accept(Boolean.FALSE);
							if(outer.__valid()) 
								outer.closeBrackets();
							outer.__value=val;
							outer.openBrackets();

							outer.ccd();
							outer.invalidated=false;
						}

					}
					if(rec!=null) {
						//						System.out.println("recorded: "+recorded.stream().map(Dependency::dependencyName).collect(Collectors.toList()));
						dependOnRecorded(outer, rec);
					}
					recording=false;
					if(!scout)
						StandardExecutors.safe(onSuccess);
					restart=scout && !outer.isValid();
					return true;
				}finally {
					if(fail)
						failHandler.accept(val);

					if(depend && rec!=null) {
						dependOnRecorded(outer, rec);
					}
				}
			}finally {
				deactivate();
				if(restart) {
					outer.__scheduleRecomputation(false, false);
					outer.__startPendingRecompute(false, false);
				}
				//workInformQueue(); not necessary; is done by deactivate
				synchronized (outer.mutex) {--outer.fulfillNesting;}

			}

		}

		@Override
		public boolean fulfillInvalid(Runnable onSuccess) {
			boolean restart=false;
			finishing=true;
			recording=false;
			if(t==null)
				return false;
			if(Thread.currentThread()!=t)
				if(t==null)
					return false;
				else
					throw new IllegalStateException("fulfillInvalid() may only be called from the recomputatin step");
			PileImpl<E> outer=this.outer.get();
			if(outer==null) return false;
			if(DE && outer.dc!=null) outer.dc.fulfillInvalid(outer);

			try {
				synchronized (outer.mutex) {++outer.fulfillNesting;}

				HashSet<Dependency> rec;
				if(recorded!=null) {
					recording=false;
					rec = diffRecorded(outer);	
					if(rec!=null && !rec.isEmpty()) {
						scout=true;
					}
				}else {
					rec=null;
				}	
				boolean depend = false;
				try {

					if(!scout) {
						outer.checkForTransformEnd(BehaviorDuringTransform.THROW_TRANSFORMINGEXCEPTION);
						if(successHandler!=null) {
							StandardExecutors.safe(()->successHandler.accept(null));
						}
						synchronized (outer.mutex) {
							if(this!=outer.ongoingRecomputation)
								return false;
							outer.__clearChangedDependencies();

							//					if(outer.pendingRecompute.transactionActive)
							//						System.out.println();
							outer.isComputing=false;
							outer.setComputing.accept(Boolean.FALSE);
							//assert !outer.valid;
							if(outer.__valid()) 
								outer.closeBrackets();
							if(ET_TRACE && traceEnabledFor(outer))outer.trace("setValidity(false) called from fulfillInvalid, ");
							outer.setValidity.accept(Boolean.FALSE);
							outer.invalidated=true;
							if(DE && outer.dc!=null) outer.dc.explicitlyInvalidate(outer, true);

						}
					}
					if(rec!=null) {
						//						System.out.println("recorded: "+recorded.stream().map(Dependency::dependencyName).collect(Collectors.toList()));
						dependOnRecorded(outer, rec);
					}
					restart=scout && !outer.isValidAsync();
					if(!scout)
						StandardExecutors.safe(onSuccess);
					return true;
				}finally {
					if(depend && rec!=null) {
						dependOnRecorded(outer, rec);
					}
				}
			}finally {
				deactivate();
				//workInformQueue(); not necessary; is done by deactivate
				if(restart) {
					outer.__scheduleRecomputation(false, false);
					outer.__startPendingRecompute(true, false);
				}
				synchronized (outer.mutex) {--outer.fulfillNesting;}

			}
		}

		@Override
		public void fulfillRetry() {
			fulfillInvalid();

		}
		volatile boolean interruptible=false;
		@Override
		public void setInterruptible(boolean status) {
			interruptible=status;
		}
		@Override
		public boolean cancel() {
			//			{
			//				Value<E> v = outer.get();
			//			}

			synchronized (this) {
				if(finishing &!finished)
					return false;
				finishing=true;
				if(transactionActive) {
					if(interruptible && t!=null && t!=Thread.currentThread())
						WaitService.get().interrupt(t);
					if(f!=null)
						f.cancel(true);
				}
			}
			if(DE) {
				PileImpl<E> o = outer.get();
				if(o!=null && o.dc!=null) o.dc.cancellingOngoingRecomputation(outer.get());
			}
			boolean ret = deactivate();
			//			if(ret)
			//				synchronized (mutex) {
			//					if(changedDependencies!=null)
			//						ccd();		
			//				}
			return ret;
		}
		//		private boolean cancelLocked() {
		//			synchronized (this) {
		//				if(transactionActive) {
		//					if(t!=null && t!=Thread.currentThread() && started)
		//						t.interrupt();
		//					if(f!=null)
		//						f.cancel(true);
		//				}
		//			}
		//			boolean ret = deactivateLocked();
		//			return ret;			
		//		}
		@Override
		public synchronized boolean isFinished() {
			return finished;
		}
		@Override
		public boolean isFinishedAsync() {
			return finished;
		}

		@Override
		public synchronized Object getThread() {
			return f!=null?f:t;
		}
		//boolean threadSet = false;
		@Override
		public synchronized void setThread(Thread t) {
			if(t==null || !t.isAlive())
				throw new IllegalArgumentException();
			Objects.requireNonNull(t);
			//			threadSet=true;
			if(RENAME_RECOMPUTATION_THREADS && threadNameBeforeRenaming!=null) {
				try {
					Thread.currentThread().setName(threadNameBeforeRenaming);
					threadNameBeforeRenaming = null;
				}catch(SecurityException x) {
					log.log(Level.WARNING, "could not restore thread name", x);
				}
			}

			this.f=null;
			this.t=t;
			if(RENAME_RECOMPUTATION_THREADS && threadName!=null) {
				try {
					threadNameBeforeRenaming = t.getName();
					t.setName(threadName);
				}catch(SecurityException x) {
					log.log(Level.WARNING, "could not transfer thread name", x);
				}
			}
		}
		@Override
		public synchronized void setThread(Future<?> f) {
			if(RENAME_RECOMPUTATION_THREADS && threadNameBeforeRenaming!=null) {
				try {
					Thread.currentThread().setName(threadNameBeforeRenaming);
					threadNameBeforeRenaming = null;
				}catch(SecurityException x) {
					log.log(Level.WARNING, "could not restore thread name", x);
				}
			}

			//			threadSet=true;
			Objects.requireNonNull(f);
			this.f=f;
			this.t=null;
		}

		@Override
		public Set<? extends Dependency> queryChangedDependencies(boolean copy) {
			if(isFinished())
				return null;
			PileImpl<E> outer=this.outer.get();
			if(outer==null) return Collections.emptySet();
			if(copy) {
				synchronized (outer.mutex) {
					if(outer.changedDependencies==null)
						return Collections.emptySet();
					return new HashSet<>(outer.changedDependencies);
				}
			}
			return outer.changedDependencies();
		}

		@Override
		public synchronized void join(WaitService ws) throws InterruptedException {
			while(!isFinished())
				ws.wait(this, 1000);

		}

		@Override
		public synchronized void join(WaitService ws, long timeout) throws InterruptedException {
			while(!isFinished() && timeout>0) {
				long t0 = System.currentTimeMillis(); 
				ws.wait(this, Math.min(1000, timeout));
				long t1 = System.currentTimeMillis();
				timeout -= t1-t0;
			}
		}
		@Override
		public void addDependency(Dependency d) {
			if(t==null)
				return;
			if(Thread.currentThread()!=t)
				throw new IllegalStateException("addDependency() may only be called from the recomputatin step");
			if(isFinished())
				return;
			PileImpl<E> outer=this.outer.get();
			if(outer==null) return;

			outer.addDependency0(d, false, false, false);
		}
		@Override
		public void removeDependency(Dependency d) {
			if(t==null)
				return;
			if(Thread.currentThread()!=t)
				throw new IllegalStateException("removeDependency() may only be called from the recomputatin step");
			if(isFinished())
				return;
			PileImpl<E> outer=this.outer.get();
			if(outer==null) return;
			outer.removeDependency0(d, false, false);
		};
		@Override
		public boolean dependsOn(Dependency d) {
			PileImpl<E> outer=this.outer.get();
			if(outer==null) return false;
			return outer.dependsOn(d);
		};

		@Override
		public E oldValue() {
			PileImpl<E> outer=this.outer.get();
			if(outer==null) return null;
			return outer.oldValue;
		}
		@Override
		public boolean hasOldValue() {
			PileImpl<E> outer=this.outer.get();
			if(outer==null) return false;
			return outer.oldValid;
		}
		@Override
		public boolean fulfillRestoreOldValue() {
			PileImpl<E> outer=this.outer.get();
			if(outer==null) 
				return false;
			E oldValue = outer.oldValue;
			if(!outer.oldValid)
				return false;
			return fulfill(oldValue);
		}
		@Override
		public boolean fulfillRestoreOldValue(Runnable onSuccess) {
			PileImpl<E> outer=this.outer.get();
			if(outer==null) 
				return false;
			E oldValue = outer.oldValue;
			if(!outer.oldValid)
				return false;
			return fulfill(oldValue, onSuccess);
		}
		@Override
		public void setFailHandler(Consumer<? super E> handler) {
			failHandler=handler;
		}
		@Override
		public void setSuccessHandler(Consumer<? super E> handler) {
			successHandler=handler;
		}
		@Override
		public void activateDynamicDependencies() {
			recorded=new HashSet<>();
			recording=true;
		}

		@Override
		public boolean isDynamicRecording() {
			return recording;// || recorded!=null;
		}
		@Override
		public void recordDependency(Dependency d) {
			if(d==null)
				return;
			boolean localRecording = recording;
			HashSet<Dependency> localRecorded = recorded;
			if(!localRecording || localRecorded==null)
				return;
			if(d==outer.get())
				return;
			if(DebugEnabled.DE && dependencyVeto.test(d))
				throw new IllegalArgumentException("The current recomputation must not depend on "+d.dependencyName());
			if(d!=null && !d.isDestroyed()) {
				localRecorded.add(d);
				if(!scout) {
					PileImpl<?> o = outer.get();
					if(!o.dependsOn(d)) {
						scout=true;
						if(delayedMode) {
							log.warning("Delayed recording of dependency '"+d.dependencyName()+"' of "+o.avName);
						}
					}
				}
			}
		}
		ArrayList<Dependency> remove;
		private HashSet<Dependency> diffRecorded(PileImpl<?> outer){
			HashSet<Dependency> rec = recorded;
			synchronized (this) {
				if(finished)
					return null;
				rec = recorded;
				if(rec==null)
					return null;
			}
			for(Dependency d: outer.getDependencies()) {

				if(rec.contains(d)) {
					//					System.out.println("Recorded dependency already active: "+d.dependencyName());
					rec.remove(d);

				}else {
					if(outer.recompute==null || !outer.recompute.mayRemoveDynamicDependency(d, outer)) {
						//					System.out.println("Recorded dependency is essential: "+d.dependencyName());
						continue;
					}
					if(remove==null)
						remove=new ArrayList<>();
					remove.add(d);
				}
			}

			rec.removeIf(Dependency::isDestroyed);
			return rec;

		}
		private void dependOnRecorded(PileImpl<?> outer, HashSet<Dependency> rec){
			if(rec==null)
				return;			
			synchronized (this) {
				if(finished)
					return;
			}
			/*
			StandardExecutors.unlimited().execute(()->{
				__dependOnRecorded(outer, rec);
			});
			 */
			__dependOnRecorded(outer, rec);

		}
		private void __dependOnRecorded(PileImpl<?> outer, HashSet<Dependency> rec) {
			if(remove!=null) {
				for(Dependency d: remove) {
					if(outer.recompute==null || !outer.recompute.mayRemoveDynamicDependency(d, outer)) {
						//					System.out.println("Recorded dependency is essential: "+d.dependencyName());
						continue;
					}
					outer.removeDependency0(d, false, false);
				}

			}

			for(Dependency d: rec)
				if(!d.isDestroyed()) {
					outer.addDependency0(d, false, false, false);
					//					System.out.println("Recorded dependency activated: "+d.dependencyName());
				}else {
					//					System.out.println("Recorded dependency was destroyed: "+d.dependencyName());
				}
			if(!scout){
				boolean needDeepRevalidate;
				synchronized (outer.mutex) {
					synchronized (outer.invalidDependenciesMutex) {
						needDeepRevalidate = 
								(outer.invalidDependencies!=null && !outer.invalidDependencies.isEmpty())
								||
								(outer.changingDependencies!=null && !outer.changingDependencies.isEmpty())
								;
					}
				}
				outer.__thisNeedsDeepRevalidate(needDeepRevalidate);
			}
		}
		@Override
		public void enterDelayedMode() {
			delayedMode=true;
		}
		@Override
		public boolean isDependencyScout() {
			return scout;
		}
		private boolean isFinishing() {
			return finishing;
		}
		@Override
		public void setDependencyVeto(Predicate<Dependency> mayNotDependOn) {
			Objects.requireNonNull(mayNotDependOn);
			dependencyVeto=mayNotDependOn;
		}
		@Override
		public String suggestThreadName() {
			PileImpl<E> o = outer.get();
			if(o==null)
				return null;
			if(o.avName==null)
				return "recomputing ?";
			return "recomputung "+o.avName;
		}
		String threadName;
		String threadNameBeforeRenaming;

		@Override
		public synchronized boolean renameThread(String name) {
			Thread ct = Thread.currentThread();
			if(ct!=t)
				return false;
			threadName = name;
			if(name==null) {
				if(threadNameBeforeRenaming!=null) {
					try {
						ct.setName(threadNameBeforeRenaming);
					}catch(SecurityException x) {
						log.log(Level.WARNING, "could not restore thread name", x);
					}
					threadNameBeforeRenaming = null;
				}
			}else {
				if(threadNameBeforeRenaming==null)
					threadNameBeforeRenaming = ct.getName();
				try {
					ct.setName(name);
				}catch(SecurityException x) {
					log.log(Level.WARNING, "could not restore thread name", x);
				}
			}
			return true;

		}


		@Override
		public String toString() {
			StringBuilder ret = new StringBuilder();
			PileImpl<E> o = outer.get();
			boolean scout = isDependencyScout();
			if(scout)
				ret.append("scouting ");
			if(isFinished())
				ret.append("finished ");
			if(o==null)
				ret.append("orphan Recomputation");
			else
				ret.append("Recomputation for '")
				.append(o.dependencyName())
				.append('\'');


			return ret.toString();
		}

		//		@Override
		//		public void setThreadNameOnFinish(String name) {
		//			restoreThreadName = name;
		//		}

	}

	/**
	 * Number of transactions started due to {@link Dependency Dependencies} changing.
	 */
	int dependencyTransactions;
	//	HashSet<Dependency> latecomers;
	@Override
	public void dependencyBeginsChanging(Dependency d, boolean wasValid, boolean invalidate) {
		if(DE && dc!=null) dc.dependencyBeginsChanging(this, d, isValidAsync());			
		//		boolean selWasValid = 

		assert !Thread.holdsLock(mutex);
		////synchronized (schedulingMutex) 

		{

			if(DE) {
				addTransactionReason(new TransactionTracker(d, "dependency changing", null));
			}
			__beginTransaction(invalidate);
		}

		if(wasValid)
			__scheduleRecomputation(true);

		boolean recomputationWasScheduledOrOngoing;
		boolean recomputationWasScout;
		Recomputer<E> recompute;
		try {
			ListenValue.DEFER.__incrementSuppressors();
			synchronized (mutex) {
				if(_thisDependsOn==null && !_thisDependsOn.contains(d)) {
					System.err.println(d.dependencyName()+" is not a dependency of "+dependencyName());
				}
				//			if(selWasValid && !wasValid) {
				//				if(latecomers==null)
				//					latecomers=new HashSet<>();
				//				latecomers.add(d);
				//			}else {
				//				if(latecomers!=null)
				//					latecomers.remove(d);
				//			}

				invalidated=false;
				if(changingDependencies==null)
					changingDependencies=new HashSet<Dependency>();
				if(!changingDependencies.add(d)) {
					try {
						throw new IllegalStateException("Dependency was already changing");
					}catch(IllegalStateException x) {
						x.printStackTrace();
					}
				}
				dependencyTransactions++;
				recomputationWasScheduledOrOngoing = recomputationTransactions>=1;
				recomputationWasScout = nextRecomputationIsScout || (ongoingRecomputation!=null && ongoingRecomputation.isDependencyScout());
				recompute = this.recompute;
			}
		}finally {
			ListenValue.DEFER.__decrementSuppressors();
		}
		if(wasValid) {
			fireDeepRevalidate();
		}

		cancelPendingRecomputation(true);

		if(recomputationWasScheduledOrOngoing)
			__scheduleRecomputation(true, recomputationWasScout || (recompute!=null && recompute.useDependencyScouting()) && !wasValid);

		//		boolean scout = false;
		//		if(recompute!=null && recompute.useDependencyScoutingOnBeginningChange() && recompute.useDependencyScoutingIfInvalid(null)) {
		//			synchronized (mutex) {
		//				for(Dependency d2: thisDependsOn) {
		//					if(d2.isValidAsync())
		//						continue;
		//					if(essentialDependencies!=null && essentialDependencies.contains(d2))
		//						continue;
		//					if(recompute.useDependencyScoutingIfInvalid(d2)) {
		//						scout=true;
		//						break;
		//					}
		//				}
		//			}
		//		}
		//		if(scout) {
		//			__scheduleRecomputation(false, true);
		//			__startPendingRecompute(true, true);
		//		}
		//		if(canRecomputeWithInvalidDependencies) {
		////			scheduleRecomputation(false);
		//			startPendingRecompute(true);
		//		}


	}
	@Override
	public void escalateDependencyChange(Dependency d) {
		//		if(DE && dc!=null) dc.dependencyBeginsChanging(this, d, isValidAsync());			
		//		boolean selWasValid = 

		assert !Thread.holdsLock(mutex);
		////synchronized (schedulingMutex) 


		boolean recomputationWasScheduledOrOngoing;
		//		boolean recomputationWasScout;
		boolean wasValid;
		synchronized (mutex) {
			if(_thisDependsOn==null && !_thisDependsOn.contains(d)) {
				System.err.println(d.dependencyName()+" is not a dependency of "+dependencyName());
			}
			if(changingDependencies == null || !changingDependencies.contains(d)) {
				try {
					throw new IllegalStateException("Dependency was not changing");
				}catch(IllegalStateException x) {
					x.printStackTrace();
				}
				return;
			}
			wasValid = valid;
			if(valid)
				moveValueToOldValue();

			invalidated=false;

			recomputationWasScheduledOrOngoing = recomputationTransactions>=1;
			//			recomputationWasScout = nextRecomputationIsScout || (ongoingRecomputation!=null && ongoingRecomputation.isDependencyScout());

			if(informed!=null && wasValid) {
				assert !Thread.holdsLock(informQueue);
				// assert !Thread.holdsLock(informRunnerMutex);
				if(ET_TRACE && traceEnabledFor(this))trace("schedule informing of dependers that I have become invalid");
				//			StackTraceElement[] cause = Thread.currentThread().getStackTrace();
				synchronized (informQueue) {
					informQueue.add(()->{
						if(ET_TRACE && traceEnabledFor(this))trace("now informing dependers that I have become invalid");
						if(informed==null)
							return;
						//					cause.clone();
						for(Depender d2: informed) {
							d2.escalateDependencyChange(this);
						}

					});
				}
			}

		}
		fireDeepRevalidate();

		cancelPendingRecomputation(true);

		if(recomputationWasScheduledOrOngoing)
			__scheduleRecomputation(true);


	}


	@Override
	public Set<Dependency> changedDependencies() {
		Set<Dependency> localRef = changedDependenciesReadOnly;
		if (localRef == null) {
			HashSet<Dependency> changedDependencies2;
			synchronized (mutex) {
				changedDependencies2 = changedDependencies;
				if(changedDependencies2==null)
					return Collections.emptySet();
			}
			synchronized (changedDependencies2) {
				localRef = changedDependenciesReadOnly;
				if (localRef == null) {
					localRef = Collections.unmodifiableSet(changedDependencies2);
					changedDependenciesReadOnly = localRef;
				}
			}
		}
		return localRef;

	}

	@Override
	public void dependencyEndsChanging(Dependency d, boolean changed) {
		//		boolean success=false;

		//		if(avName=="implantsActivity") {
		//			System.err.println("dependency ends changing: "+d+", changed="+changed);
		//			if(((ReadValue<IndicatorStatus>)d).get()==IndicatorStatus.COMPLETE){
		//				if(changed)
		//					System.out.println();
		//			}
		//		}
		boolean scout = false;
		try {
			if(DE && dc!=null) dc.dependencyEndsChanging(this, d);

			boolean wasValid;

			try {
				ListenValue.DEFER.__incrementSuppressors();
				synchronized (mutex) {
					if(_thisDependsOn==null && !_thisDependsOn.contains(d)) {
						System.err.println(d.dependencyName()+" is not a dependency of "+dependencyName());
					}
					if(changingDependencies==null)
						throw new IllegalStateException("There are no changing dependencies!");

					if(changingDependencies.remove(d)) {
						if(changed) {
							if(changedDependencies==null)
								changedDependencies=new HashSet<>();
							changedDependencies.add(d);
						}
						dependencyTransactions--;
						invalidated=false;
					}else {
						_printConstructionStackTrace();
						d._printConstructionStackTrace();
						throw new IllegalStateException("This was not a changing dependency!");
					}
					changed |= changedDependencies!=null && !changedDependencies.isEmpty();
					synchronized(invalidDependenciesMutex) {
						//					checkForDestroyedDeps();

						if(invalidDependencies==null)
							invalidDependencies=new HashSet<>();
						if(d.isValidAsync() || !dependsOn(d))
							invalidDependencies.remove(d);
						else
							invalidDependencies.add(d);
						//					checkForDestroyedDeps();

					}
					wasValid=valid;
					//				changed |= this.canRecomputeWithInvalidDependencies && !valid;
				}
				//			success=true;

				if(recompute!=null && recompute.useDependencyScouting() 
						//					&& recompute.useDependencyScoutingIfInvalid(null)
						) {
					scout=true;
					synchronized (mutex) {
						for(Dependency d2: _thisDependsOn) {
							//						if(d2!=d && d2.isValidAsync())
							//							continue;
							if(!d2.isValidAsync()) {
								//							if(essentialDependencies!=null && essentialDependencies.contains(d2)) {
								if(!recompute.mayRemoveDynamicDependency(d2, this)) {
									//We have an invalid dependency that may not be removed
									// -> scouting would not work
									scout=false;
									break;
								}
								//							}
							}
							//						if(recompute.useDependencyScoutingIfInvalid(d2)) {
							//							scout=true;
							//							break;
							//						}
						}
					}
				}
			}finally {
				ListenValue.DEFER.__decrementSuppressors();
			}
			if(scout) {
				__scheduleRecomputation(true, true);
				__startPendingRecompute(true, true);
			}else if(changed) {
				__scheduleRecomputation(true);
			}

			//synchronized (schedulingMutex) 
			{
				if(DE) {
					removeTransactionReason(new TransactionTracker(d, "dependency changing", null));
				}
				__endTransaction(!wasValid);
			}

		}finally {

			//			if(!success)
			//				System.out.println();
		}


	}
	@Override
	public Suppressor suppressAutoValidation() {
		Suppressor r;
		if(DE) {
			MutRef<Suppressor> rr = new MutRef<>();
			r = rr.val = Suppressor.wrap(()->{
				if(dc!=null)
					dc.autoValidationSuppressorReleased(this, rr.val);
				releaseAutoValidationSuppressor();
			});
		}else
			r = Suppressor.wrap(this::releaseAutoValidationSuppressor);
		synchronized (mutex) {
			++autoValidationSuppressors;
		}
		if(DE && dc!=null)
			dc.autoValidationSuppressorCreated(this, r);
		setAutoValidating.accept(isAutoValidating());

		return r;
	}

	private volatile IndependentBool autoValidatingR;
	Consumer<? super Boolean> setAutoValidating=Functional.NOP;
	@Override
	public ReadListenDependencyBool autoValidating() {
		IndependentBool localRef = autoValidatingR;
		if (localRef == null) {
			synchronized (mutex) {
				localRef = autoValidatingR;
				if (localRef == null) {
					localRef = new IndependentBool(isAutoValidating());
					Consumer<? super Boolean> setter = localRef.makeSetter();
					setAutoValidating=setter;
					localRef.setName((avName==null?"?":avName)+" autoValidating");
					localRef.keepStrong(this);
					localRef.owner=this;
					localRef.seal();
					autoValidatingR = localRef;
				}
			}
			setAutoValidating.accept(isAutoValidating());
		}
		return localRef;

	}

	/**
	 * Decrement {@link #autoValidationSuppressors} and possibly trigger recomputation
	 */
	protected void releaseAutoValidationSuppressor() {
		boolean recompute;
		boolean needsRecompute;
		synchronized (mutex) {
			--autoValidationSuppressors;

			if(autoValidationSuppressors<0) {
				autoValidationSuppressors=0;
				throw new IllegalStateException("autoValueSuppressors is negative!");
			}

			needsRecompute = !valid && (changedDependencies!=null && !changedDependencies.isEmpty());
			recompute = autoValidationSuppressors==0;
		}
		setAutoValidating.accept(isAutoValidating());
		if(needsRecompute) {
			if(recompute) {
				__scheduleRecomputation(false);
				if(Recomputations.areRecomputationsSuspended()) {
					Recomputations.possiblySuspendRecomputation(()->{
						___startPendingRecompute(false, false);
					});
				} else {
					__startPendingRecompute(false);
				}
			}
		}else{
			cancelPendingRecomputation(false);
		}

	}
	@Override
	public boolean dependsOn(Dependency d) {
		synchronized (mutex) {
			return _thisDependsOn!=null && _thisDependsOn.contains(d);
		}
	}
	@Override
	public void giveDependencies(Consumer<? super Dependency> out) {
		if(Thread.holdsLock(mutex)) {
			if(_thisDependsOn==null || _thisDependsOn.isEmpty())
				return;
			_thisDependsOn.forEach(out);
		} else {
			Dependency[] arr = getDependencies();
			for(Dependency d: arr)
				out.accept(d);
		}
	}
	@Override
	public Dependency[] getDependencies() {
		synchronized (mutex) {
			if(_thisDependsOn==null || _thisDependsOn.isEmpty())
				return NO_DEPENDENCIES;
			return _thisDependsOn.toArray(new Dependency[_thisDependsOn.size()]);
		}
	}
	@Override
	public void valueMutated() {
		if(listeners==null)
			return;
		_getListenerManager().fireValueChange(new ValueEvent(this));
	}
	@Override
	public void valueTransformMutated() {
		if(listeners==null)
			return;
		_getListenerManager().fireValueChange(new TransformValueEvent(this));
	}
	/**
	 * Create an unconfigured, initially invalid {@link Pile}
	 */
	public PileImpl() {


	}
	@Override
	protected void __scheduleRecomputation(boolean cancelOngoing) {
		__scheduleRecomputation(cancelOngoing, false);
	}
	/**
	 * Whether to make the next {@link Recomputation} that is started a dependency scout
	 * because the set of relevant dependencies may have changed.
	 */
	private boolean nextRecomputationIsScout;
	private void __scheduleRecomputation(boolean cancelOngoing, boolean scout) {

		Recomputation<?> cancel;
		synchronized (mutex) {
			if(cancelOngoing && ongoingRecomputation!=null) {
				cancel=ongoingRecomputation;
			}else {
				cancel=null;
			}			
		}
		if(cancel!=null) {
			cancel.cancel();
		}

		boolean bt=false;
		try {
			//synchronized (schedulingMutex) 
			{

				synchronized (mutex) {
					nextRecomputationIsScout=scout;
					assert recomputationTransactions>=0;
					if((!invalidated && !valid || scout) && recompute!=null ) {
						bt=recomputationTransactions==0;
						if(bt)
							recomputationTransactions++;
					}else {
						bt=false;
					}
					assert recomputationTransactions>=0;
					if(bt) {
						//synchronized (schedulingMutex) 
						{
							if(DE) addTransactionReason(new TransactionTracker(this, "pending recomputation", null));
							if(DE && dc!=null) dc.newlyScheduledRecomputation(this); 
							beginTransaction(false, false, scout);
						}
					}
				}

			}
		}finally {
			if(bt)
				__workInformQueue();
		}


	}
	private void addTransactionReason(TransactionTracker t) {
		assert DE;
		synchronized (mutex) {
			if(_transactionReasons.size()<__openTransactions())
				System.err.println("Wrong transaction count!");
			if(!_transactionReasons.add(t))
				System.err.println("Transaction added twice!");
		}

	}
	private void removeTransactionReason(TransactionTracker t) {
		assert DE;
		synchronized (mutex) {
			if(_transactionReasons.size()<__openTransactions())
				System.err.println("Wrong transaction count!");
			if(!_transactionReasons.remove(t))
				System.err.println("Transaction was not tracked!");
		}
	}
	private void changeTransactionReason(TransactionTracker old, TransactionTracker nevv) {
		assert DE;
		synchronized (mutex) {
			if(_transactionReasons.size()<__openTransactions())
				System.err.println("Wrong transaction count!");
			if(!_transactionReasons.remove(old))
				System.err.println("Transaction was not tracked!");
			if(!_transactionReasons.add(nevv))
				System.err.println("Transaction added twice!");

		}
	}
	protected void __checkTransactionReasonCount(int openTransactions2) {		
		assert DE;
		synchronized (this) {
			if(_transactionReasons.size()<__openTransactions())
				// Note: this may be triggered due to lack of synchronization
				System.err.println("Wrong transaction count!");
		}
	}
	/**
	 * Whether the {@link Pile} has been invalidated and should not automatically recompute its value.
	 */
	boolean invalidated;
	@Override
	public void permaInvalidate() {
		if(DE && dc!=null) dc.explicitlyInvalidate(this, false);
		checkForTransformEnd(BehaviorDuringTransform.BLOCK);
		try {
			//synchronized (schedulingMutex) 
			{
				if(DE) {
					addTransactionReason(new TransactionTracker(this, "invalidating", Thread.currentThread()));
				}
				__beginTransaction();
			}

			if(recompute!=null && recompute.useDependencyScouting()) {
				__scheduleRecomputation(false, true);
				__startPendingRecompute(true, true);
			}
			synchronized (mutex) {
				invalidated=true;
			}

			fireDeepRevalidate();
		}finally {
			//synchronized (schedulingMutex) 
			{

				if(DE) {
					removeTransactionReason(new TransactionTracker(this, "invalidating", Thread.currentThread()));
				}
				__endTransaction(false);
			}
			synchronized (mutex) {
				WaitService.get().notifyAll(mutex);
			}
		}
	}
	protected void invalidate0(int expectedTransactionsForRevalidation) {

		if(DE && dc!=null) dc.explicitlyInvalidate(this, false);

		try {
			//synchronized (schedulingMutex) 
			{
				if(DE) {
					addTransactionReason((new TransactionTracker(this, ("invalidating0"), Thread.currentThread())));
				}
				__beginTransaction();
			}

			synchronized (mutex) {
				invalidated=true;
			}
		}finally {
			//synchronized (schedulingMutex) 
			{

				if(DE) {
					removeTransactionReason(new TransactionTracker(this, ("invalidating0"), Thread.currentThread()));
				}
				__endTransaction(false);
			}
		}
	}
	@Override
	public void revalidate() {
		revalidate(true);
	}
	/**
	 * Invalidate if necessary and immediately attempt to recompute (which may not happen for various reasons)
	 * @param waitForTransform whether to wait for a possibly ongoing transform to complete before revalidating
	 */
	protected void revalidate(boolean waitForTransform) {
		if(DE && dc!=null) dc.revalidateCalled(this);

		try{
			//synchronized (schedulingMutex) 
			{

				if(DE) addTransactionReason(new TransactionTracker(this, ("revalidating"), Thread.currentThread()));
				__beginTransaction();
			}

			synchronized (mutex) {
				if(changedDependencies==null)
					changedDependencies=new HashSet<>();
				changedDependencies.add(this);

				invalidated=false;
			}
			__scheduleRecomputation(true);
		}finally {
			//synchronized (schedulingMutex) 
			{

				if(DE) removeTransactionReason(new TransactionTracker(this, ("revalidating"), Thread.currentThread()));
				__endTransaction(false);
			}
		}
	}

	@Override
	public void autoValidate() {
		HashSet<CanAutoValidate> validating = autoValidationInProgress.get();
		boolean starter = validating==null || validating.isEmpty();
		try {
			if(starter) {
				if(validating==null)
					autoValidationInProgress.set(validating=new HashSet<>());
			}else {
				if(validating.contains(this))
					return;
			}
			validating.add(this);
			boolean wasValid;
			synchronized (mutex) {
				if(lazyValidating)
					return;
				wasValid=__valid();
				if(wasValid)
					return;
				invalidated=false;
			}
			__scheduleRecomputation(false);
			if(isAutoValidating())
				__startPendingRecompute(true);
			if(!allDependenciesValid()) {
				giveDependencies(Dependency::autoValidate);
			}
		}finally{
			if(starter)
				autoValidationInProgress.set(null);
		}
	}

	@Override
	public E set(E val) {
		if(DE && dc!=null) dc.set(this, val);

		try {
			val = applyCorrection(val);
		}catch (VetoException x) {
			if(x.revalidate)
				revalidate();
			else if(recompute!=null && recompute.useDependencyScouting()) {
				__scheduleRecomputation(false, true);
				__startPendingRecompute(true, true);
			}

			return get();
		}catch (RuntimeException x) {
			log.log(Level.SEVERE, "Exception in applyCorrection", x);
			return get();
		}
		checkForTransformEnd(BehaviorDuringTransform.BLOCK);
		synchronized (mutex) {
			if(valid && equivalence.test(val, __value))
				return __value;
		}
		//synchronized (schedulingMutex) 
		{
			if(DE) {
				addTransactionReason(new TransactionTracker(this, ("setting"), Thread.currentThread()));
			}
			__beginTransaction();
		}
		try {
			cancelPendingRecomputation(true);
			if(recompute!=null && recompute.useDependencyScouting()) {
				synchronized (mutex) {
					closeOldBrackets();
					oldValue = val;
					openOldBrackets();
				}
				__scheduleRecomputation(false, true);
				__startPendingRecompute(true, true);
			}				
			fireDeepRevalidateOnSet();
			boolean needDeepRevalidate;
			synchronized (mutex) {
				synchronized (invalidDependenciesMutex) {
					needDeepRevalidate = 
							(invalidDependencies!=null && !invalidDependencies.isEmpty())
							||
							(changingDependencies!=null && !changingDependencies.isEmpty())
							;
				}
				invalidated=false;
			}
			__thisNeedsDeepRevalidate(needDeepRevalidate);

			__workInformQueue();
			//			Dependency[] detach;
			synchronized (mutex) {
				closeBrackets();
				__value=val;
				openBrackets();
				//				if(true && thisDependsOn!=null) {
				//					detach = thisDependsOn.toArray(new Dependency[thisDependsOn.size()]);
				//				}else {
				//					detach = null;
				//				}
				if(valid && changedDependencies!=null)
					changedDependencies.clear();

			}

			//			if(detach!=null) {
			//
			//				synchronized (invalidDependenciesMutex) {
			//					//					checkForDestroyedDeps();
			//
			//					if(invalidDependencies!=null)
			//						for(Dependency dep: detach) {
			//							invalidDependencies.remove(dep);
			//						}
			//					//					checkForDestroyedDeps();
			//
			//				}
			//				for(Dependency dep: detach) {
			//					dep.detachDepender(this);
			//				}
			//
			//				synchronized (mutex) {
			//					if(valid && changedDependencies!=null)
			//						changedDependencies.clear();
			//				}
			//
			//			}


			__workInformQueue();

		} finally {
			//			if(openTransactions()!=1)
			//				System.out.println();
			//synchronized (schedulingMutex) 
			{

				if(DE) {
					removeTransactionReason(new TransactionTracker(this, ("setting"), Thread.currentThread()));
				}
				__endTransaction(true);
			}
			synchronized (mutex) {
				WaitService.get().notifyAll(mutex);
			}
		}


		return val;
		//fireChange();


	}
	@Override public PileImpl<E> setNull() {		
		set(null);
		return this;
	}
	@Override
	public void _setRecompute(Recomputer<E> recomputer) {
		if(recomputer==null)
			cancelPendingRecomputation(true);
		synchronized (mutex) {
			recompute=recomputer;
		}
		if(recomputer!=null) {
			__scheduleRecomputation(true);
			__startPendingRecompute(false);
		}
	}
	@Override
	public void destroy() {
		assert !Thread.holdsLock(mutex);
		if(DebugEnabled.COUNT_BRACKET_LOCKS && DebugEnabled.WARN_ON_DESTROY_WHILE_LOCKED && DebugEnabled.lockedValueMutices.get().val>0) {
			DebugEnabled.warn(log, "Destroy while locked!");
		}
		Recomputation<E> pr;
		WeakHashMap<Depender, ?> ief;
		HashSet<Dependency> essDep = null;
		synchronized (mutex) {
			if(destroyed)
				return;
			destroyed=true;
			pr=ongoingRecomputation;
			ief = isEssentialFor;
			isEssentialFor = null;
			essDep = essentialDependencies;
			essentialDependencies=null;
		}
		if(ief!=null) {
			synchronized (ief) {
				for(Depender d: ief.keySet())
					d.destroy();
			}
		}
		if(pr!=null)
			pr.cancel();

		giveDependencies(this::removeDependency);
		if(essDep !=null) {
			for(Dependency d: essDep)
				d.__setEssentialFor(this, false);
			essDep.clear();
		}


		giveDependers(d->{
			if(d.isEssential(this))
				d.destroy();
			//			StandardExecutors.unlimited()
			d.removeDependency(this);	
		});
		synchronized (mutex) {
			moveValueToOldValue();
			closeBrackets();
			closeOldBrackets();
		}
		setAutoValidating.accept(isAutoValidating());

		recompute=null;
		owner=null;
		__workInformQueue();


	}
	public void deepDestroy() {
		assert !Thread.holdsLock(mutex);
		Recomputation<E> pr;
		synchronized (mutex) {
			if(destroyed)
				return;
			destroyed=true;
			pr=ongoingRecomputation;
		}
		if(pr!=null)
			pr.cancel();
		giveDependers(Depender::deepDestroy);
		giveDependencies(this::removeDependency);
		synchronized (mutex) {
			assert dependOnThis==null || dependOnThis.isEmpty();
			closeBrackets();
			closeOldBrackets();
		}
		__workInformQueue();
	}
	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder();
		sb.append(avName).append(": ");
		if(valid) {
			sb.append('<').append(__value).append('>');
		}else if (oldValid) {
			sb.append("old <"+oldValue+">");
		}else {
			sb.append("invalid");
		}
		if(__openTransactions()>0) {
			sb.append(" ta: ").append(__openTransactions());
		}
		if(recomputationTransactions>0)
			sb.append(" pending recompute: ").append(recomputationTransactions);
		if(ongoingRecomputation!=null) {
			sb.append(" ongoing recompute: ").append(!ongoingRecomputation.isFinished());
		}
		return sb.toString();
	}

	@Override
	protected void moveValueToOldValue() {
		assert Thread.holdsLock(mutex);
		if(oldValid) {
			if(valid) {
				if(__value==oldValue){
					closeBrackets();
					return;
				}else {

				}
			}else {
				return;
			}
		}
		if(valid) {
			if(oldValid) {
				closeOldBrackets();
			}
			oldValue=__value;
			openOldBrackets();

			closeBrackets();
		}
	}
	@Override
	protected void copyValueToOldValue() {
		assert Thread.holdsLock(mutex);
		if(oldValid) {
			if(valid) {
				if(__value==oldValue){
					return;
				}else {

				}
			}else {
				return;
			}
		}
		if(valid) {
			if(oldValid) {
				closeOldBrackets();
			}
			oldValue=__value;
			openOldBrackets();
		}
	}
	//	protected String openTransactionCheckAssertionErrorMessage() {
	//		return "There are no current transactions but there are changing dependencies";
	//	}
	//	protected boolean openTransactionCheck(){
	//		assert Thread.holdsLock(mutex);
	//		return changingDependencies==null || changingDependencies.isEmpty();
	//	}
	//	protected String closeTransactionCheckAssertionErrorMessage() {
	//		return "All transactions ended but there still are changing dependencies";
	//	}
	//	protected boolean closeTransactionCheck(){
	//		assert Thread.holdsLock(mutex);
	//		return changingDependencies==null || changingDependencies.isEmpty();
	//	}
	@Override
	protected boolean __shouldRemainInvalid(){
		assert Thread.holdsLock(mutex);

		if(ET_TRACE && traceEnabledFor(this)) {
			if(invalidated)
				trace("Should remain invalid bc manual invalidation, ");
			synchronized (invalidDependenciesMutex) {
				if((
						//					!canRecomputeWithInvalidDependencies && 
						!allDependenciesValid()))
					trace("Should remain invalid bc invalid dependencies: , "+invalidDependencies);
			}
		}

		return invalidated ;// || (
		//				!canRecomputeWithInvalidDependencies &&
		//				!allDependenciesValid() /*&& !oldValid*/);
	}

	@Override
	protected boolean __hasChangedDependencies(){
		assert Thread.holdsLock(mutex);
		return changedDependencies!=null && !(changedDependencies.isEmpty()
				//|| latecomers!=null && latecomers.containsAll(changedDependencies)
				);
	}
	@Override
	protected int __recomputerTransactions() {
		assert Thread.holdsLock(mutex);
		assert recomputationTransactions>=0;
		return recomputationTransactions;
	}
	@Override
	public boolean _isRecomputationPendingOrOngoing() {
		synchronized (mutex) {
			return recomputationTransactions>0 || ongoingRecomputation!=null;
		}
	}

	@Override
	protected boolean __oldValid() {
		assert Thread.holdsLock(mutex);
		return oldValid;
	}
	@Override
	protected final boolean __valid() {
		assert Thread.holdsLock(mutex);
		return valid;
	}
	@Override
	protected E __oldValue() {
		assert Thread.holdsLock(mutex);
		return oldValue;
	}
	@Override
	protected E __value() {
		assert Thread.holdsLock(mutex);
		return __value;
	}
	@Override
	protected void __restoreValueFromOldValue() {
		assert Thread.holdsLock(mutex);
		__value = oldValue;
		openBrackets();
		closeOldBrackets();
	}

	@Override
	public boolean cancelPendingRecomputation(boolean cancelOngoing) {
		return cancelPendingRecomputation(cancelOngoing, true);
	}
	//Object schedulingMutex = new Object();
	@Override
	protected boolean cancelPendingRecomputation(boolean cancelOngoing, boolean notIfScout) {
		assert !Thread.holdsLock(mutex);
		Recomputation<E> pr;
		boolean ret=false;
		if(cancelOngoing) {
			synchronized (mutex) {
				pr = ongoingRecomputation;			
			}
			//		boolean wasInterrupted=Thread.interrupted();
			if(pr!=null && !(pr.isDependencyScout() && notIfScout)) {
				ret = pr.cancel();
			}
		}

		//synchronized (schedulingMutex) 
		{

			int et;
			synchronized (mutex) {
				assert recomputationTransactions>=0;
				et=recomputationTransactions;
				recomputationTransactions=0;
				assert recomputationTransactions>=0;
			}

			while(--et>=0) {
				if(DE && dc!=null) dc.unschedulePendingRecomputation(this);
				//synchronized (schedulingMutex) 
				{
					if(DE) removeTransactionReason(new TransactionTracker(this, "pending recomputation", null));
					__endTransaction(false);
				}
			}
		}

		//		if(wasInterrupted)
		//			Thread.currentThread().interrupt();
		//		else
		//			Thread.interrupted();
		return ret;
	}


	//	@Override
	//	protected boolean scheduleRecomputationWithActivatedTransaction() {
	//		assert Thread.holdsLock(mutex);
	//		MyRecomputation<E> pr = pendingRecompute;
	//		if(pr!=null)
	//			pr.cancelLocked();
	//		if(dontRetry && valid()) {
	//			return false;
	//		}
	//		if(recompute==null)
	//			return false;
	//		MyRecomputation<?> ongoing = currentlyRevalidating.get();
	//		if(ongoing!=null) {
	//			System.err.println("Already recomputing!");
	//			return false;
	//		}
	//
	//		MyRecomputation<E> reco=new MyRecomputation<>(this);
	//		reco.transactionActive=true;
	//		if(DE) {
	//			if(!transactionReasons.add(new TransactionTracker(reco, "start recomputer")))
	//				System.out.println("What?");
	//		}
	//		//		if(pendingRecompute!=null && pendingRecompute.transactionActive)
	//		//			System.out.println("what?");
	//		pendingRecompute=reco;
	//		return true;
	//	}
	@Override
	protected void __clearChangedDependencies() {
		assert Thread.holdsLock(mutex);
		ccd();
	}
	private volatile IndependentBool validity;
	Consumer<? super Boolean> setValidity=v->{
		if(ET_TRACE && traceEnabledFor(this))trace("setValidity invoked with "+v);

		assert !Thread.holdsLock(informQueue);
		synchronized (informQueue) {
			informQueue.add(()->{
				//				boolean becameObservablyInvalid;
				//				becameObservablyInvalid =!v && observedValid;
				boolean propagate;
				boolean fire;
				synchronized (mutex) {
					propagate= !v;// && openTransactions()<=(recomputerTransactionActive()?1:0);
					fire = !v & !__valid() && observedValid;
					observedValid=v;
				}
				if(ET_TRACE && traceEnabledFor(this))trace("observedValid set to "+v);
				if(fire)
					fireValueChange();

				if(propagate) {
					if(informed==null || informed.isEmpty())
						return;
					Depender[] notify = informed.toArray(new Depender[informed.size()]);
					for(Depender d: notify) {
						d.__dependencyBecameLongTermInvalid(this);
					}
				}


			});
		}
	};
	;
	@Override
	public ReadListenDependencyBool validity() {
		IndependentBool localRef = validity;
		if (localRef == null) {
			synchronized (mutex) {
				localRef = validity;
				if (localRef == null) {
					try(MockBlock b = Recomputations.withoutRecomputation()) {
						localRef = new IndependentBool(__valid());
					}
					Consumer<? super Boolean> setter = localRef.makeSetter();
					setValidity=v->{
						if(ET_TRACE && traceEnabledFor(this))trace("setValidity invoked with"+v);

						assert !Thread.holdsLock(informQueue);
						synchronized (informQueue) {
							informQueue.add(()->{
								//boolean becameObservablyInvalid =!v && observedValid;
								boolean propagate;
								boolean fire;
								synchronized (mutex) {
									propagate= !v;// && openTransactions()<=(recomputerTransactionActive()?1:0);
									fire = !v && observedValid;
									observedValid=v;
								}
								setter.accept(v);
								if(fire)
									fireValueChange();
								if(ET_TRACE && traceEnabledFor(this))trace("validity set to "+v);
								if(propagate) {
									if(informed==null || informed.isEmpty())
										return;
									Depender[] notify = informed.toArray(new Depender[informed.size()]);
									HashSet<Object> informing = Depender.informingLongTermInvalid.get();
									boolean starter;
									if(informing==null || informing.isEmpty()) {
										starter=true;
										Depender.informingLongTermInvalid.set(informing=new HashSet<>());
									}else {
										starter=false;
									}
									try {
										for(Depender d: notify) {
											d.__dependencyBecameLongTermInvalid(this);
										}
									}finally {
										if(starter)
											Depender.informingLongTermInvalid.set(null);
									}
								}

								//								if(becameObservablyInvalid)
								//									fireValueChange();
							});
						}
					};
					localRef.setName((avName==null?"?":avName)+" validity");
					localRef.keepStrong(this);
					localRef.owner=this;
					localRef.seal();
					validity = localRef;
				}
			}
		}
		return localRef;

	}
	volatile IndependentBool computing;
	Consumer<? super Boolean> setComputing=Functional.NOP;
	boolean isComputing;

	public ReadListenDependencyBool computing() {
		IndependentBool localRef = computing;
		if (localRef == null) {
			synchronized (mutex) {
				localRef = computing;
				if (localRef == null) {
					try(MockBlock b = Recomputations.withoutRecomputation()) {
						localRef = new IndependentBool(isComputing);
					}
					Consumer<? super Boolean> setter = localRef.makeSetter();
					setComputing=v->{
						assert !Thread.holdsLock(informQueue);
						synchronized (informQueue) {
							informQueue.add(()->setter.accept(v));
						}
					};
					localRef.setName((avName==null?"?":avName)+" is computing");
					localRef.keepStrong(this);
					localRef.owner=this;
					localRef.seal();
					computing = localRef;
				}
			}
		}
		return localRef;
	}
	@Override
	public boolean isComputing() {
		synchronized (mutex) {
			return isComputing;
		}
	}


	@Override
	public void __beginTransaction(boolean invalidate) {
		assert !Thread.holdsLock(mutex);
		MyRecomputation<?> orec;
		synchronized (mutex) {
			orec=ongoingRecomputation;
			ongoingRecomputation=null;
			isComputing=false;
			setComputing.accept(Boolean.FALSE);
		}
		try {
			//boolean ret = 
			beginTransaction(true, invalidate, false);

		}finally {
			boolean reval;
			if(orec!=null) {
				reval = orec.cancel() && !orec.isFinishing() && orec.transactionActive && !orec.isDependencyScout();
			}else
				reval = false;
			if(reval)
				revalidate();
		}
	}
	@Override
	public Suppressor transaction(boolean b) {
		Object id = DE?new Object():null;
		Suppressor ret = Suppressor.wrap(()->{
			//synchronized (schedulingMutex) 
			{
				if(DE)
					removeTransactionReason(new TransactionTracker(this, ("Manual"), id));
				__endTransaction();
			}
		});
		//synchronized (schedulingMutex) 
		{
			if(DE)
				addTransactionReason(new TransactionTracker(this, ("Manual"), id));
			__beginTransaction(b);
		}
		return ret;
	}

	@Override
	public void __endTransaction(boolean changedIfOldInvalid) {
		super.__endTransaction(changedIfOldInvalid);
		try {
			ListenValue.DEFER.__incrementSuppressors();
			boolean bv;
			boolean needDeepRevalidate;
			synchronized (mutex) {
				bv=valid;

				if(ET_TRACE && traceEnabledFor(this) && bv) {
					ReadListenDependencyBool validity = this.validity;
					if(validity!=null && !Boolean.TRUE.equals(validity.getAsync()))
						synchronized(trace) {System.out.println(trace);}
				}
				if(bv) {
					if(dependOnThis==null || dependOnThis.isEmpty()) {
						;
					}else {
						for(WeakIdentityCleanup<Depender> dd: dependOnThis) {
							Depender d =dd.get();
							if(d!=null)
								d.__dependencyIsNowValid(this);
						}
					}
				}else{
				}
				synchronized (invalidDependenciesMutex) {
					needDeepRevalidate = valid && (
							(invalidDependencies!=null && !invalidDependencies.isEmpty())
							||
							(changingDependencies!=null && !changingDependencies.isEmpty())
							)
							;
				}	
			}
			//		if(!needDeepRevalidate)
			__thisNeedsDeepRevalidate(needDeepRevalidate);
		}finally {
			ListenValue.DEFER.__decrementSuppressors();
		}
	}

	//	@Override
	//	protected boolean canRecomputeWithInvalidDependencies() {
	//		return canRecomputeWithInvalidDependencies;
	//	}




	volatile IndependentBool validNull;
	Consumer<? super Boolean> setValidNull=Functional.NOP;
	@Override
	public ReadListenDependencyBool validNull() {
		IndependentBool localRef = validNull;
		if (localRef == null) {
			synchronized (mutex) {
				localRef = validNull;
				if (localRef == null) {
					localRef = new IndependentBool(__valid() && __value==null);
					Consumer<? super Boolean> setter = localRef.makeSetter();
					setValidNull=v->{
						assert !Thread.holdsLock(informQueue);
						synchronized (informQueue) {
							informQueue.add(()->setter.accept(v));
						}
					};
					localRef.setName((avName==null?"?":avName)+" == null");
					localRef.keepStrong(this);
					localRef.owner=this;
					localRef.seal();

					validNull = localRef;
				}
			}
		}
		return localRef;

	}



	//	@Override public Maybe<E> getWithValidity() {return Maybe.just(get());}
	private WeakHashMap<Object, Object> associations;
	private ReferenceQueue<Object> associationRq;
	@Override
	public Object __HasAssocitations_Mixin_getMutex() {
		return mutex;
	}
	@Override
	public WeakHashMap<Object, Object> __HasAssocitations_Mixin_getMap() {
		return associations;
	}
	@Override
	public ReferenceQueue<Object> __HasAssocitations_Mixin_getQueue() {
		return associationRq;
	}

	@Override
	public void __HasAssocitations_Mixin_setMap(WeakHashMap<Object, Object> map) {
		associations = map;
	}
	@Override
	public void __HasAssocitations_Mixin_setQueue(ReferenceQueue<Object> queue) {
		associationRq = queue;
	}


	@Override
	public PileImpl<E> setName(String name) {
		avName=name;
		return this;
	}
	@Override
	public void joinRecomputation() throws InterruptedException {
		Recomputation<?> reco;
		synchronized (mutex) {
			reco = ongoingRecomputation;
		}
		if(reco==null)
			return;
		reco.join();
	}
	@Override
	public void joinRecomputation(long timeoutMillis) throws InterruptedException {
		Recomputation<?> reco;
		synchronized (mutex) {
			reco = ongoingRecomputation;
		}
		if(reco==null)
			return;
		reco.join(timeoutMillis);
	}

	@Override
	public boolean willNeverChange() {
		return false;
	}


	@Override
	protected void openBrackets() {
		assert Thread.holdsLock(mutex);
		super.openBrackets();
		//		if(avName == "baseNormalShift_minValue" && __value==null)
		//			System.out.println();
		valid=true;
		if(ET_TRACE && traceEnabledFor(this))trace("valid set to true");

		setValidNull.accept(__value==null);
		setValidity.accept(Boolean.TRUE);
		if(ET_TRACE && traceEnabledFor(this))trace("setValidity(true) invoked from openBrackets");


	}
	@Override
	protected boolean closeBrackets() {
		assert Thread.holdsLock(mutex);
		if(!__valid())
			return true;
		valid=false;
		if(ET_TRACE && traceEnabledFor(this))trace("valid set to false");
		setValidNull.accept(false);

		//		setValidity.accept(Boolean.FALSE);
		boolean keep=super.closeBrackets();
		if(!keep)
			__value=null;
		return keep;
	}
	@Override
	protected void __setValidity(boolean valid) {
		setValidity.accept(valid);
	}
	@Override
	protected void openOldBrackets() {
		assert Thread.holdsLock(mutex);
		super.openOldBrackets();
		oldValid=true;
	}
	@Override
	protected boolean closeOldBrackets() {
		assert Thread.holdsLock(mutex);
		if(!oldValid)
			return true;

		oldValid=false;

		boolean keep=super.closeOldBrackets();
		if(!keep)
			oldValue=null;
		return keep;

	}
	@Override
	public Depender getPrivilegedDepender() {
		return this;
	}

	//	/**
	//	 * Set the "don't retry" property. If it is set to <code>true</code>, 
	//	 * a new recomputation will not be started if this {@link Value} is already valid.
	//	 * This can happen when a {@link Dependency} is added without triggering recomputation 
	//	 * but before the current recomputation is fulfilled, 
	//	 * for example by means of calling {@link Recomputation#addDependency(Dependency)}.
	//	 * Use this if the {@link Recomputation} that changed the dependencies was already
	//	 * fulfilled using the value it would have yielded it the dependencies ad already been there
	//	 * at the beginning.
	//	 * 
	//	 * NOTE: This is currently without effect
	//	 * @param dont
	//	 */
	//	public void setDontRetry(boolean dont) {
	//		//		dontRetry=dont;
	//	}

	@Override
	public void __dependencyBecameLongTermInvalid(Dependency d) {
		//		System.out.println("__dependencyBecameLongTermInvalid: "+d+" -> "+this);
		informLongTermInvalid();
	}
	@Override
	public E getAsync() {
		return __value;
	}
	@Override
	public void setLazyValidating(boolean newState) {
		final boolean autoValidate;
		synchronized (mutex) {
			if(lazyValidating==newState)
				return;
			lazyValidating=newState;
			if(newState) {
				++autoValidationSuppressors;
				autoValidate=false;
			}else {
				--autoValidationSuppressors;
				if(autoValidationSuppressors==0)
					autoValidate=true;
				else
					autoValidate=false;
			}
		}
		if(autoValidate)
			autoValidate();
	}
	@Override
	public boolean isLazyValidating() {
		synchronized (mutex) {
			return lazyValidating;
		}
	}


	@Override
	public void lazyValidate() {

		HashSet<LazyValidatable> validating = lazyValidatingItt.get();
		boolean starter = validating==null || validating.isEmpty();
		try {
			if(starter) {
				if(validating==null)
					lazyValidatingItt.set(validating=new HashSet<>());
			}else {
				if(validating.contains(this))
					return;
			}
			validating.add(this);
			boolean wasValid;

			synchronized (mutex) {

				wasValid=__valid();
				if(wasValid)
					return;
				if(ongoingRecomputation!=null && !ongoingRecomputation.isFinished())
					return;

				invalidated=false;
			}
			__scheduleRecomputation(false);

			if(!allDependenciesValid()) {
				giveDependencies(Dependency::lazyValidate);
			}	
			__startPendingRecompute(true);
		}finally{
			if(starter)
				lazyValidatingItt.set(null);
		}




	}
	//	@Override public boolean couldBeValid(boolean onlyIfLazyValidating) {
	//		if(isValid())
	//			return true;
	//		if(recompute==null)
	//			return false;
	//		if(onlyIfLazyValidating && !lazyValidating)
	//			return false;
	//		if(allDependenciesValid())
	//			return true;
	//		else {
	//			Dependency[] id;
	//			synchronized (invalidDependenciesMutex) {
	//				//				checkForDestroyedDeps();
	//
	//				if(invalidDependencies==null || invalidDependencies.isEmpty())
	//					return true;
	//				id = invalidDependencies.toArray(new Dependency[invalidDependencies.size()]);
	//			}
	//			for(Dependency d: id)
	//				if(!d.couldBeValid(false))
	//					return false;
	//			return true;
	//		}
	//
	//	}
	@Override public boolean is(E v) {
		if(isValid())
			try {
				return equivalence.test(getValidOrThrow(), v);
			}catch(InvalidValueException x) {
			}
		return false;
	}



	@Override
	public boolean isValidAsync() {
		return valid;
	}


	static TransformHandler<Object> DEFAULT_TRANSFORM_HANDLER=(v, o)->TransformReaction.IGNORE;
	@SuppressWarnings("unchecked")
	TransformHandler<E> th=(TransformHandler<E>)DEFAULT_TRANSFORM_HANDLER;

	@SuppressWarnings("unchecked")
	public void _setTransformHandler(TransformHandler<E> t) {
		if(t==null)
			t=(TransformHandler<E>)DEFAULT_TRANSFORM_HANDLER;
		th=t;
	}

	@Override
	public TransformHandler<E> getTransformHandler(Object transform) {
		return th;
	}

	@Override
	public Dependency asDependency() {
		return this;
	}

	@Override
	public Depender asDepender() {
		return this;
	}
	/**
	 * This documents the reasons for why transactions were started.
	 * Only used if {@link DebugEnabled#DE} is set to <code>true</code>.
	 */
	Set<TransactionTracker> _transactionReasons=DE?Collections.synchronizedSet(new HashSet<>()):null;


	int transformTransactions;
	Thread transformTransactionStarterThread;

	@Override
	public void beginTransformTransaction() throws InterruptedException{
		//		boolean wasOngoing = cancelPendingRecomputation(true);

		Object tm = getTransformMutex();
		synchronized (tm) {
			if(transformTransactions>1)
				throw new TransformingException();
			while(transformTransactions>0) {
				WaitService.get().wait(getTransformMutex(), 1000);
			}
			{
				transformTransactions=1;
				transformTransactionStarterThread=Thread.currentThread();
				WaitService.get().notifyAll(getTransformMutex());
			}
		}
		//		synchronized (mutex) {
		//			if(oldValid)
		//				closeOldBrackets();
		//			if(valid) {
		//				oldValue=value;
		//				openOldBrackets();
		//				closeBrackets();
		//			}
		//			
		//		}
		//		if(DE) {
		//			if(!transactionReasons.add(new TransactionTracker(this, "transforming")))
		//				System.out.println("What?");
		//
		//		}
		//		
		//		beginTransaction();


	}

	@Override
	public void endTransformTransaction() {
		//		boolean et;
		synchronized (getTransformMutex()) {
			--transformTransactions;
			if(transformTransactions==0) {
				WaitService.get().notifyAll(getTransformMutex());
				//				et=true;
			}else {
				//				et=false;
			}
			transformTransactionStarterThread=null;
		}


		//		if(et) {
		//			if(DE) {
		//				transactionReasons.remove(new TransactionTracker(this, "transforming"));
		//			}
		//			endTransaction(false);
		//		}

	}


	/**
	 * Sneakily set the value, but only if the {@link #oldValue} is valid and identical
	 * to the second argument, doing it without triggering any transactions, 
	 * {@link ValueEvent}s or {@link Depender} recomputaitons.
	 * @param v
	 * @param oldMustBe
	 */
	public void __conditionalSecretSet(E v, E oldMustBe) {
		synchronized (mutex) {
			if(oldMustBe!=oldValue || !oldValid)
				return;
			if(__valid()){
				if(equivalence.test(__value, v))
					return;
			}
			closeBrackets();
			__value=v;
			openBrackets();
		}
		fireValueChange();
	}

	Thread transformThread;
	@Override
	public void runTransformRevalidate() {
		Object transformMutex2 = getTransformMutex();
		try {
			synchronized (transformMutex2) {
				while(transformThread!=null)
					try {
						WaitService.get().wait(transformMutex2, 1000);
					} catch (InterruptedException e) {
						StandardExecutors.interruptSelf();
						return;
					}
				transformThread=Thread.currentThread();
			}

			revalidate(false);

		}finally {
			synchronized (transformMutex2) {
				if(transformThread==Thread.currentThread()) {
					transformThread=null;
					WaitService.get().notifyAll(transformMutex2);
				}

			}
		}
	}

	@Override
	public void runTransform(TypedReaction<E> reaction) {
		Object transformMutex2 = getTransformMutex();
		try {
			synchronized (transformMutex2) {
				while(transformThread!=null)
					try {
						WaitService.get().wait(transformMutex2, 1000);
					} catch (InterruptedException e) {
						StandardExecutors.interruptSelf();
						return;
					}
				transformThread=Thread.currentThread();
			}
			boolean ovalid;
			E ovalue;
			synchronized (mutex) {
				ovalid = oldValid;
				ovalue = oldValue;
			}
			switch(reaction.getType()) {
			case IGNORE:
			case JUST_PROPAGATE_NO_TRANSACTION:
			case JUST_PROPAGATE_WITH_TRANSACTION:
			case RECOMPUTE:
				throw new IllegalStateException("MutateReaction and ReplaceReaction should not have type "+reaction.getType());
			case MUTATE:
				if(ovalid) {
					try {
						reaction.apply(ovalue);
						valueTransformMutated();
					} catch (InvalidValueException e) {
						e.printStackTrace();
					}
				}else {
					reaction.cancel();
				}
				break;
			case REPLACE:
				if(ovalid) {
					E result;
					try {
						result = reaction.apply(ovalue);
						synchronized (transformMutex2) {
							if(transformThread==Thread.currentThread())
								__conditionalSecretSet(result, ovalue);
						}
						valueTransformMutated();
					} catch (InvalidValueException e) {
						e.printStackTrace();
					}
				}else {
					reaction.cancel();
				}

				break;	
			}
		}finally {
			synchronized (transformMutex2) {
				if(transformThread==Thread.currentThread()) {
					transformThread=null;
					WaitService.get().notifyAll(transformMutex2);
				}

			}
		}
	}
	private volatile Object transformMutex;
	//	private void checkForTransformEnd() {}

	@Override
	public void checkForTransformEnd() {
		BehaviorDuringTransform bdt2 = bdt;
		checkForTransformEnd(bdt2);
	}

	@Override
	public void checkForTransformEnd(BehaviorDuringTransform bdt2) {
		if(bdt2==BehaviorDuringTransform.NOP)
			return;
		if(destroyed)
			return;
		synchronized (getTransformMutex()) {
			if(transformTransactionStarterThread==Thread.currentThread())
				return;
			while(transformThread!=null || transformTransactions>0 ) {
				if(bdt==BehaviorDuringTransform.THROW_TRANSFORMINGEXCEPTION)
					throw new TransformingException();
				try {
					WaitService.get().wait(getTransformMutex(), 1000);
				} catch (InterruptedException e) {
					StandardExecutors.interruptSelf();
					return;
				}
			}
		}

	}

	/**
	 * Lazily initialize the {@link #transformMutex} and return it
	 * @return
	 */
	protected Object getTransformMutex() {
		if(transformMutex==null) {
			synchronized (mutex) {
				if(transformMutex==null)
					transformMutex=new Object();
			}
		}
		return transformMutex;
	}
	@Override
	public void __dependencyIsNowValid(Dependency d) {
		synchronized (invalidDependenciesMutex) {
			if(invalidDependencies!=null)
				invalidDependencies.remove(d);
		}

	}


	@Override
	protected Recomputation<E> __ongoingRecomputation() {
		assert Thread.holdsLock(mutex);
		return ongoingRecomputation;
	}


	@Override
	public void deepRevalidate(Dependency d) {
		//		if(valid) {
		//			if(value instanceof MeshKey && ((MeshKey) value).isDummy())
		//				;
		//			else if(avName!=null && avName.endsWith("gingResultsSnag"))
		//				;
		//			else
		//				System.out.println();
		//		}
		//		System.out.println("DeepRevalidate: "+this);
		revalidate();
		fireDeepRevalidate();
	}
	@Override
	protected Dependency[] __dependencies() {
		synchronized (mutex) {
			if(_thisDependsOn==null || _thisDependsOn.isEmpty())
				return null;
			return _thisDependsOn.toArray(new Dependency[_thisDependsOn.size()]);
		}
	}
	@Override public boolean remembersLastValue() {return false;}
	@Override public void resetToLastValue() {}
	@Override public void storeLastValueNow() {}
	@Override public Suppressor suppressRememberLastValue() {return Suppressor.NOP;}

	@Override
	public void giveInfluencers(Consumer<? super Object> out) {
		if(owner!=null)
			out.accept(owner);
	}

	@Override
	public boolean _isRecomputerDefined() {
		return recompute!=null;
	}

	@Override
	public void _setOwner(Object o) {
		owner = o;
	}

	@Override
	public boolean isSealed() {
		return false;
	}
	{
		if(DebugEnabled.ERROR_ON_CREATE_IN_DYNAMIC_RECOMPUTATION) {
			DependencyRecorder recorder = Recomputations.getCurrentRecorder();
			if(recorder!=null) {
				Recomputation<?> recomp = recorder.getReceivingRecomputation();
				if(recomp != null && recomp.isDynamicRecording() && !recomp.isFinished()) {
					String msg = "Reactive value created durinc dynamic dependency recording";
					log.log(Level.WARNING, msg);
					throw new IllegalStateException(msg);
				}
			}
		}
	}



}