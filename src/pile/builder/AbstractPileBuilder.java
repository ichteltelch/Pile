package pile.builder;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.function.BiPredicate;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.logging.Level;
import java.util.logging.Logger;

import pile.aspect.Dependency;
import pile.aspect.Depender;
import pile.aspect.ValueBracket;
import pile.aspect.VetoException;
import pile.aspect.HasAssociations.NamedAssociationKey;
import pile.aspect.combinations.ReadListenDependency;
import pile.aspect.listen.ValueListener;
import pile.aspect.recompute.Recomputation;
import pile.aspect.recompute.Recomputations;
import pile.aspect.recompute.Recomputer;
import pile.aspect.suppress.SafeCloseable;
import pile.aspect.transform.TransformHandler;
import pile.impl.DebugCallback;
import pile.impl.PileImpl;
import pile.impl.Piles;
import pile.interop.exec.StandardExecutors;
import pile.utils.Functional;
import static pile.interop.debug.DebugEnabled.RENAME_RECOMPUTATION_THREADS;

/**
 * Abstract implementation of {@link IPileBuilder}  
 * @author bb
 *
 * @param <Self> The concrete implementing class. Because of this type parameter, 
 * the {@link AbstractPileBuilder} class needs to abstract
 * @param <V>
 * @param <E>
 */
public abstract class AbstractPileBuilder<Self extends AbstractPileBuilder<Self, V, E>, V extends PileImpl<E>, E> 
implements IPileBuilder<Self, V, E>{
	static Logger log = Logger.getLogger("GenericValueBuilder");
	private Consumer<? super Recomputation<E>> recomputer;
	private Consumer<? super Recomputation<E>> immediateRecomputer;
	private Function<? super Recomputation<E>, ? extends Runnable> combinedRecomputer;
	long delay = -1;
	private Consumer<? super E> failHandler;
	private Predicate<? super Dependency> dependenciesThatTriggerScouting;
	boolean logAllExceptions;
	ExecutorService exec;

	private boolean dynamicDependencies;
	protected V value;
	public AbstractPileBuilder(V value) {
		this.value=value;
	}
	public Self name(String n) {
		value.avName=n;
		return self();
	};
	public Self nameIfUnnamed(String n) {
		if(value.avName==null)
			value.avName=n;
		return self();
	};
	public Self parent(Object o) {
		value.owner=o;
		return self();
	};

	@Override
	public Self recompute(Consumer<? super Recomputation<E>> recomputer) {
		this.recomputer = recomputer;
		return self();
	}
	@Override
	public Self recomputeImmediate(Consumer<? super Recomputation<E>> recomputer) {
		this.immediateRecomputer = recomputer;
		return self();
	}
	@Override
	public Self recomputeStaged(Function<? super Recomputation<E>, ? extends Runnable> recomputer) {
		this.combinedRecomputer = recomputer;
		return self();
	}


	@Override
	public Self delay(long millis) {
		delay=millis;
		return self();
	}

	@Override
	public Self bracket(boolean openNow, ValueBracket<? super E, ? super ReadListenDependency<? extends E>> bracket) {
		value._addValueBracket(openNow, bracket);
		return self();
	}

	@Override
	public Self oldBracket(boolean openNow, ValueBracket<? super E, ? super ReadListenDependency<? extends E>> bracket) {
		value._addOldValueBracket(openNow, bracket);
		return self();
	}
	@Override
	public Self anyBracket(boolean openNow, ValueBracket<? super E, ? super ReadListenDependency<? extends E>> bracket) {
		value._addAnyValueBracket(openNow, bracket);
		return self();
	}
	@Override
	public Self init(E val) {
		value.set(val);
		return self();
	}
	BiPredicate<? super Dependency, ? super Depender> mayRemoveDynamicDependency; 



	boolean unfullfilledGuard=true;
	private static final class MyRecomputerForStaged<E, V extends PileImpl<?>> extends MyRecomputer<E> {
		private final ScheduledExecutorService myExec;
		private final Function<? super Recomputation<E>, ? extends Runnable> combi;
		private final V value;
		private final boolean ug;
		private final BooleanSupplier delaySwitch;
		private final long delay;
		private final boolean logAllExceptions;
		private final boolean fov;

		private MyRecomputerForStaged(boolean dynamic, ScheduledExecutorService myExec,
				Function<? super Recomputation<E>, ? extends Runnable> combi, V value, boolean ug,
				BooleanSupplier delaySwitch, long delay, boolean logAllExceptions, boolean fov) {
			super(dynamic);
			this.myExec = myExec;
			this.combi = combi;
			this.value = value;
			this.ug = ug;
			this.delaySwitch = delaySwitch;
			this.delay = delay;
			this.logAllExceptions = logAllExceptions;
			this.fov = fov;
		}

		public void accept(Recomputation<E> re){					
			synchronized (re) {
				if(dynamic) re.activateDynamicDependencies();
				else if(re.isDependencyScout()) {re.fulfillInvalid(); return;}
				Runnable delayed;
				String oName1;
				if(RENAME_RECOMPUTATION_THREADS) {
					String sName = re.suggestThreadName();
					if(sName!=null) {
						oName1 = Thread.currentThread().getName();
						re.renameThread(sName + " - " + oName1);
					}else {
						oName1 = null;
					}
				}else {
					oName1=null;
				}
				try {
					assert Recomputations.getCurrentRecomputation()==re;
					delayed = combi.apply(re);
				}catch(FulfillInvalid x) {
					re.fulfillInvalid();
					return;
				}finally {
					if(RENAME_RECOMPUTATION_THREADS && oName1!=null) {
						Thread.currentThread().setName(oName1);
						re.renameThread(null);
					}
				}
				if(delayed == Piles.FULFILL_INVALID) {
					re.fulfillInvalid();
					return;
				}
				if(delayed == Piles.FULFILL_NULL) {
					re.fulfill(null);
					return;
				}
				if(re.isFinished())
					return;
				if(delayed==null) {
					combi.apply(re);
					throw new IllegalStateException("Staged recomputer did not return a valid continuation");
				}

				if(fov)re.forgetOldValue();					
				boolean doDelay = delaySwitch==null || !delaySwitch.getAsBoolean();

				Runnable runThis = ()->{
					if(doDelay && re.isFinished())
						return;
					String oName2=null;

					try (SafeCloseable _a = Recomputations.withCurrentRecomputation(re)){
						synchronized (re) {re.setThread();re.setInterruptible();}
						if(RENAME_RECOMPUTATION_THREADS) {
							String sName = re.suggestThreadName();
							if(sName!=null) {
								oName2 = Thread.currentThread().getName();
								re.renameThread(sName + " - " + oName2);
							}
						}
						delayed.run();
					}catch(Error x) {
						log.log(Level.WARNING, "Error in recomputer for "+value.avName+" (finished: "+re.isFinished()+")",	x);
						throw x;
					}catch(Throwable x) {
						if(logAllExceptions || re!=null && !re.isFinished())
							log.log(Level.WARNING, "Exception in recomputer for "+value.avName+" (finished: "+re.isFinished()+")",	x);
					}finally {
						if(ug) {
							unfulfilledWarning(value, re);
						}
						if(RENAME_RECOMPUTATION_THREADS && oName2!=null) {
							Thread.currentThread().setName(oName2);
							re.renameThread(null);
						}
					}
				};
				if(doDelay)
					synchronized (re) {
						re.enterDelayedMode();
						re.setThread(myExec.schedule(runThis, delay, TimeUnit.MILLISECONDS));
					}
				else 
					runThis.run();
			}
		}
	}
	private static final class MyRecomputerForStaged0Delay<E, V extends PileImpl<?>> extends MyRecomputer<E> {
		private final boolean ug;
		private final boolean fov;
		private final V value;
		private final BooleanSupplier delaySwitch;
		private final Function<? super Recomputation<E>, ? extends Runnable> combi;
		private final boolean logAllExceptions;
		private final ExecutorService myExec;

		private MyRecomputerForStaged0Delay(boolean dynamic, boolean ug, boolean fov, V value, BooleanSupplier delaySwitch,
				Function<? super Recomputation<E>, ? extends Runnable> combi, boolean logAllExceptions,
				ExecutorService myExec) {
			super(dynamic);
			this.ug = ug;
			this.fov = fov;
			this.value = value;
			this.delaySwitch = delaySwitch;
			this.combi = combi;
			this.logAllExceptions = logAllExceptions;
			this.myExec = myExec;
		}

		public void accept(Recomputation<E> re){					
			synchronized (re) {
				if(dynamic) re.activateDynamicDependencies();
				else if(re.isDependencyScout()) {re.fulfillInvalid(); return;}
				Runnable delayed;
				String oName1;
				if(RENAME_RECOMPUTATION_THREADS) {
					String sName = re.suggestThreadName();
					if(sName!=null) {
						oName1 = Thread.currentThread().getName();
						re.renameThread(sName + " - " + oName1);
					}else {
						oName1 = null;
					}
				}else {
					oName1=null;
				}
				try {
					assert Recomputations.getCurrentRecomputation()==re;
					delayed = combi.apply(re);
				}catch(FulfillInvalid x) {
					re.fulfillInvalid();
					return;
				}finally {
					if(RENAME_RECOMPUTATION_THREADS && oName1!=null) {
						Thread.currentThread().setName(oName1);
						re.renameThread(null);
					}
				}

				if(delayed == Piles.FULFILL_INVALID) {
					re.fulfillInvalid();
					return;
				}
				if(delayed == Piles.FULFILL_NULL) {
					re.fulfill(null);
					return;
				}
				if(re.isFinished())
					return;
				if(fov)
					re.forgetOldValue();	
				if(delayed==null) {
					throw new IllegalStateException("staged recomputer returned a null delayed computation but did not fulfill (name: "+value.dependencyName()+")");
				}
				boolean doDelay = delaySwitch==null || !delaySwitch.getAsBoolean();

				Runnable runThis = ()->{
					if(doDelay && re.isFinished())
						return;
					String oName2=null;

					try (SafeCloseable _a = Recomputations.withCurrentRecomputation(re)){
						if(doDelay)synchronized (re) {re.setThread();re.setInterruptible();}
						if(RENAME_RECOMPUTATION_THREADS) {
							String sName = re.suggestThreadName();
							if(sName!=null) {
								oName2 = Thread.currentThread().getName();
								re.renameThread(sName + " - " + oName2);
							}
						}
						delayed.run();
					}catch(FulfillInvalid x) {
						re.fulfillInvalid();
						return;
					}catch(Error x) {
						log.log(Level.WARNING, "Error in recomputer for "+value.avName+" (finished: "+re.isFinished()+")",	x);
						throw x;
					}catch(Throwable x) {
						if(logAllExceptions || re!=null && !re.isFinished())
							log.log(Level.WARNING, "Exception in recomputer for "+value.avName+" (finished: "+re.isFinished()+")",	x);
					}finally {
						if(ug) {
							unfulfilledWarning(value, re);
						}
						if(RENAME_RECOMPUTATION_THREADS && oName2!=null) {
							Thread.currentThread().setName(oName2);
							re.renameThread(null);
						}
					}
				};
				if(doDelay)
					synchronized (re) {
						re.enterDelayedMode();
						re.setThread(myExec.submit(runThis));
					}
				else 
					runThis.run();
			}
		}
	}
	private static final class MyRecomputeForDelayed<E, V extends PileImpl<?>> extends MyRecomputer<E> {
		private final boolean ug;
		private final BooleanSupplier delaySwitch;
		private final V value;
		private final boolean logAllExceptions;
		private final long delay;
		private final Consumer<? super Recomputation<E>> dreco;
		private final ScheduledExecutorService myExec;
		private final boolean fov;

		private MyRecomputeForDelayed(boolean dynamic, boolean ug, BooleanSupplier delaySwitch, V value,
				boolean logAllExceptions, long delay, Consumer<? super Recomputation<E>> dreco,
				ScheduledExecutorService myExec, boolean fov) {
			super(dynamic);
			this.ug = ug;
			this.delaySwitch = delaySwitch;
			this.value = value;
			this.logAllExceptions = logAllExceptions;
			this.delay = delay;
			this.dreco = dreco;
			this.myExec = myExec;
			this.fov = fov;
		}

		public void accept(Recomputation<E> re){	
			if(dynamic) re.activateDynamicDependencies();
			else if(re.isDependencyScout()) {re.fulfillInvalid(); return;}
			if(fov)re.forgetOldValue();	
			boolean doDelay = delaySwitch==null || !delaySwitch.getAsBoolean();
			Runnable runThis = ()->{
				if(re.isFinished())
					return;
				String oName2=null;

				try (SafeCloseable _a = Recomputations.withCurrentRecomputation(re)){
					if(doDelay) synchronized (re) {re.setThread();re.setInterruptible();}
					if(RENAME_RECOMPUTATION_THREADS) {
						String sName = re.suggestThreadName();
						if(sName!=null) {
							oName2 = Thread.currentThread().getName();
							re.renameThread(sName + " - " + oName2);
						}else {
							oName2 = null;
						}
					}
					dreco.accept(re);
				}catch(FulfillInvalid x) {
					re.fulfillInvalid();
					return;
				}catch(Error x) {
					log.log(Level.WARNING, "Error in recomputer for "+value.avName+" (finished: "+re.isFinished()+")",	x);
					throw x;
				}catch(Throwable x) {
					if(logAllExceptions || re!=null && !re.isFinished())
						log.log(Level.WARNING, "Exception in recomputer for "+value.avName+" (finished: "+re.isFinished()+")",	x);
				}finally {
					if(ug) {
						unfulfilledWarning(value, re);
					}
					if(RENAME_RECOMPUTATION_THREADS && oName2!=null) {
						Thread.currentThread().setName(oName2);
						re.renameThread(null);
					}
				}
			};
			if(doDelay)
				synchronized (re) {
					re.enterDelayedMode();
					re.setThread(myExec.schedule(runThis, delay, TimeUnit.MILLISECONDS));
				}
			else 
				runThis.run();
			
		}
	}
	private static final class MyRecomputerFor0Delay<E, V extends PileImpl<?>> extends MyRecomputer<E> {
		private final ExecutorService myExec;
		private final Consumer<? super Recomputation<E>> dreco;
		private final V value;
		private final boolean logAllExceptions;
		private final BooleanSupplier delaySwitch;
		private final boolean fov;
		private final boolean ug;

		private MyRecomputerFor0Delay(boolean dynamic, ExecutorService myExec, Consumer<? super Recomputation<E>> dreco,
				V value, boolean logAllExceptions, BooleanSupplier delaySwitch, boolean fov, boolean ug) {
			super(dynamic);
			this.myExec = myExec;
			this.dreco = dreco;
			this.value = value;
			this.logAllExceptions = logAllExceptions;
			this.delaySwitch = delaySwitch;
			this.fov = fov;
			this.ug = ug;
		}

		public void accept(Recomputation<E> re){	
			if(dynamic) re.activateDynamicDependencies();
			else if(re.isDependencyScout()) {re.fulfillInvalid(); return;}
			if(fov) re.forgetOldValue();
			boolean doDelay = delaySwitch==null || !delaySwitch.getAsBoolean();
			Runnable runThis = ()->{
				if(re.isFinished())
					return;
				String oName2 = null;

				try (SafeCloseable _a = Recomputations.withCurrentRecomputation(re)){
					if(doDelay)synchronized (re) {re.setThread();re.setInterruptible();}
					if(RENAME_RECOMPUTATION_THREADS) {
						String sName = re.suggestThreadName();
						if(sName!=null) {
							oName2 = Thread.currentThread().getName();
							re.renameThread(sName + " - " + oName2);
						}
					}
					dreco.accept(re);
				}catch(FulfillInvalid x) {
					re.fulfillInvalid();
					return;
				}catch(Error x) {
					log.log(Level.WARNING, "Error in recomputer for "+value.avName+" (finished: "+re.isFinished()+")",	x);
					throw x;
				}catch(Throwable x) {
					if(logAllExceptions || re!=null && !re.isFinished())
						log.log(Level.WARNING, "Exception in recomputer for "+value.avName+" (finished: "+re.isFinished()+")",	x);
				}finally {
					if(ug) {
						unfulfilledWarning(value, re);
					}
					if(RENAME_RECOMPUTATION_THREADS && oName2!=null) {
						Thread.currentThread().setName(oName2);
						re.renameThread(null);
					}
				}
			};
			if(doDelay) 
				synchronized (re) {
					re.enterDelayedMode();
					re.setThread(myExec.submit(runThis));
				}
			else 
				runThis.run();
			
		}
	}
	private static final class MyRecomputationForImmediate<E, V extends PileImpl<?>> extends MyRecomputer<E> {
		private final V value;
		private final boolean ug;
		private final Consumer<? super Recomputation<E>> dreco;
		private final boolean logAllExceptions;

		public MyRecomputationForImmediate(boolean dynamic, V value, boolean ug,
				Consumer<? super Recomputation<E>> dreco, boolean logAllExceptions) {
			super(dynamic);
			this.value = value;
			this.ug = ug;
			this.dreco = dreco;
			this.logAllExceptions = logAllExceptions;
		}

		public void accept(Recomputation<E> re){
			if(dynamic) re.activateDynamicDependencies();
			else if(re.isDependencyScout()) {re.fulfillInvalid(); return;}
			
			String oName1;
			if(RENAME_RECOMPUTATION_THREADS) {
				String sName = re.suggestThreadName();
				if(sName!=null) {
					oName1 = Thread.currentThread().getName();
					re.renameThread(sName + " - " + oName1);
				}else {
					oName1 = null;
				}
			}else {
				oName1=null;
			}
			try {
				assert Recomputations.getCurrentRecomputation()==re;
				
				dreco.accept(re);
			}catch(FulfillInvalid x) {
				re.fulfillInvalid();
				return;
			}catch(Error x) {
				log.log(Level.WARNING, "Error in recomputer for "+value.avName+" (finished: "+re.isFinished()+")",	x);
				throw x;
			}catch(Throwable x) {
				if(logAllExceptions || re!=null && !re.isFinished())
					log.log(Level.WARNING, "Exception in recomputer for "+value.avName+" (finished: "+re.isFinished()+")",	x);
			}finally {
				if(ug) {
					unfulfilledWarning(value, re);
				}
				if(RENAME_RECOMPUTATION_THREADS && oName1!=null) {
					Thread.currentThread().setName(oName1);
					re.renameThread(null);
				}
			}
		}
	}
//	private static final class MyRecomputerForImmediate<E, V extends PileImpl<?>> extends MyRecomputer<E> {
//		private final V value;
//		
//		private final boolean logAllExceptions;
//		private final Consumer<? super Recomputation<E>> ireco;
//
//		public MyRecomputerForImmediate(boolean dynamic, V value, boolean logAllExceptions,
//				Consumer<? super Recomputation<E>> ireco) {
//			super(dynamic);
//			this.value = value;
//			
//			this.logAllExceptions = logAllExceptions;
//			this.ireco = ireco;
//		}
//
//		public void accept(Recomputation<E> re){
//			if(dynamic) re.activateDynamicDependencies();
//			else if(re.isDependencyScout()) {re.fulfillInvalid(); return;}
//
//			try {
//				assert Recomputations.getCurrentRecomputation()==re;
//
//				ireco.accept(re);
//			}catch(FulfillInvalid x) {
//				re.fulfillInvalid();
//				return;
//			}catch(Error x) {
//				log.log(Level.WARNING, "Error in recomputer for "+value.avName+" (finished: "+re.isFinished()+")",	x);
//				throw x;
//			}catch(Throwable x) {
//				if(logAllExceptions || re!=null && !re.isFinished())
//					log.log(Level.WARNING, "Exception in recomputer for "+value.avName+" (finished: "+re.isFinished()+")",	x);
//			}finally {
//				unfulfilledWarning(value, re);
//			}
//		}
//	}

	static abstract class MyRecomputer<E> implements Recomputer<E>{
		final boolean dynamic;
		Predicate<? super Dependency> dependenciesThatTriggerScouting;
		Consumer<? super E> failHandler;
		BiPredicate<? super Dependency, ? super Depender> mayRemoveDynamicDependency; 


		public MyRecomputer(boolean dynamic) {
			this.dynamic=dynamic;
		}
		public boolean useDependencyScouting() {return dynamic;}
		@Override
		public boolean useDependencyScoutingIfInvalid(Dependency d) {
			if(dependenciesThatTriggerScouting==null || dependenciesThatTriggerScouting==Functional.CONST_FALSE)
				return false;
			if(d==null)
				return true;
			return dependenciesThatTriggerScouting.test(d);
		}
		@Override
		public boolean mayRemoveDynamicDependency(Dependency dy, Depender dr) {
			if(mayRemoveDynamicDependency==null)
				return Recomputer.super.mayRemoveDynamicDependency(dy, dr);
			else
				return mayRemoveDynamicDependency.test(dy, dr);
		}

	}
	@Override
	public V build() {
		ICorrigibleBuilder.applyBounds(value, lowerBounds, upperBounds, ordering);
		ReadListenDependency<? extends E> ub = ICorrigibleBuilder.getUpperBound(value);
		ReadListenDependency<? extends E> lb = ICorrigibleBuilder.getLowerBound(value);
		boolean ug = unfullfilledGuard;
		if(ub!=null)
			value.addDependency(ub);
		if(lb!=null)
			value.addDependency(lb);
		V value = this.value;
		//String name = this.name;
		Function<? super Recomputation<E>, ? extends Runnable> combi;
		long delay;
		MyRecomputer<E> reco=null;
		boolean fov=forgetOldValueOnDelayedRecompute;
		boolean dynamic = dynamicDependencies;

		boolean logAllExceptions = this.logAllExceptions;
		BooleanSupplier delaySwitch;
		if(this.delaySwitch==null) {
			delaySwitch=null;
		}else {
			switch(this.delaySwitch.size()) {
			case 0:	delaySwitch = null; break;
			case 1: delaySwitch = this.delaySwitch.get(0); break;
			default:
				BooleanSupplier[] items = this.delaySwitch.toArray(new BooleanSupplier[this.delaySwitch.size()]);
				delaySwitch = ()->{
					for(BooleanSupplier b: items)
						if(!b.getAsBoolean())
							return false;
					return true;
				};
			}
		}
		if(combinedRecomputer==null) {
			Consumer<? super Recomputation<E>> dreco=recomputer;
			Consumer<? super Recomputation<E>> ireco=immediateRecomputer;

			if(dreco!=null && ireco!=null)
				delay=Math.max(0, this.delay);
			else
				delay=this.delay;

			if(dreco==null) {
				if(ireco==null) 
					//no recomputation
					combi=null;
				else {
					//immediate recomputation
					reco=new MyRecomputationForImmediate<E, V>(dynamic, value, ug, ireco, logAllExceptions);
					combi=null;
				}
			}else if(ireco==null) {
				assert dreco!=null;
				if(delay<0) {
					//immediate recomputation
					reco=new MyRecomputationForImmediate<E, V>(dynamic, value, ug, dreco, logAllExceptions);
					combi=null;
				}else {
					combi=null;
					if(delay==0) {
						ExecutorService myExec = this.exec==null?
								StandardExecutors.unlimited()
								:this.exec;
						reco=new MyRecomputerFor0Delay<E, V>(dynamic, myExec, dreco, value, logAllExceptions, delaySwitch, fov, ug);
					}else {
						ScheduledExecutorService myExec = this.exec==null?
								StandardExecutors.delayed()
								:(ScheduledExecutorService) this.exec;
						reco=new MyRecomputeForDelayed<E, V>(dynamic, ug, delaySwitch, value, logAllExceptions, delay, dreco, myExec, fov);
					}
				}
			}else {
				//both immediate and delayed recomputation
				combi=re->{
					final Recomputation<E> fre = re;
					try{		
						assert Recomputations.getCurrentRecomputation()==re;
						ireco.accept(re);
						if(!re.isFinished()) {
							if(fov)re.forgetOldValue();					
							re=null;
							return ()->dreco.accept(fre);
						}else {
							re = null;
							return null;
						}
					}catch(FulfillInvalid x) {
						re.fulfillInvalid();
						return null;
					}catch(Error x) {
						log.log(Level.WARNING, "Error in recomputer for "+value.avName+" (finished: "+re.isFinished()+")",	x);
						throw x;
					}catch(Throwable x) {
						if(logAllExceptions || !fre.isFinished())
							log.log(Level.WARNING, "Exception in recomputer for "+value.avName+" (finished: "+fre.isFinished()+")",	x);
						return null;
					}finally {
						if(ug && re != null) {
							unfulfilledWarning(value, re);
						}
					}
				};	
			}


		}else {
			if(immediateRecomputer!=null || recomputer!=null) {
				throw new IllegalArgumentException("Cannot specify a staged recomputer toghether with an immediate or delayed one");
			}
			combi=combinedRecomputer;
			delay=Math.max(0, this.delay);
		}
		if(combi==null && reco==null)
			reco=null;
		else if(combi!=null) {
			assert reco==null;
			assert delay>=0;
			if(delay<=0) {
				ExecutorService myExec = this.exec==null?
						StandardExecutors.unlimited()
						:this.exec;
				reco=new MyRecomputerForStaged0Delay<E, V>(dynamic, ug, fov, value, delaySwitch, combi, logAllExceptions, myExec);

			}else {
				ScheduledExecutorService myExec = this.exec==null?
						StandardExecutors.delayed()
						:(ScheduledExecutorService) this.exec;
				reco=new MyRecomputerForStaged<E, V>(dynamic, myExec, combi, value, ug, delaySwitch, delay, logAllExceptions, fov);
			}
		}
		if(reco!=null) {
			reco.dependenciesThatTriggerScouting=
					dynamicDependencies && dependenciesThatTriggerScouting==null?
							Functional.CONST_TRUE:
								dependenciesThatTriggerScouting;
			reco.mayRemoveDynamicDependency=mayRemoveDynamicDependency;
			if(failHandler!=null) {
				reco.failHandler=failHandler;
			}
		}

		value._setRecompute(reco);

		return value;
	}
	/**
	 * Call {@link Recomputation#fulfillInvalid()} and log a warning if that actually did something
	 * because the recomputation has not been fulfilled yet.
	 * @param <V>
	 * @param value
	 * @param re
	 */
	private static <V extends PileImpl<?>> void unfulfilledWarning(V value, Recomputation<?> re) {
		if(re.fulfillInvalid()) {
			log.warning("Unfulfilled recomputation: "+value.avName);
			value._printConstructionStackTrace();
		}
	}

	@Override
	public Self dependOn(Dependency d) {
		value.addDependency(d, false);
		return self();
	}
	@Override
	public Self dependOn(Dependency... d) {
		value.addDependency(false, d);
		return self();
	}
	@Override
	public Self dependOn(boolean essential, Dependency d) {
		value.addDependency(d, false);
		if(essential)
			value.setDependencyEssential(true, d);
		return self();
	}
	@Override
	public Self dependOn(boolean essential, Dependency... d) {
		value.addDependency(false, d);
		if(essential)
			value.setDependencyEssential(true, d);
		return self();
	}

	//	@Override
	//	public Self canRecomputeWithInvalidDependencies() {
	////		value._setCanRecomputeWithInvalidDependencies(true);
	//		return self();
	//	}


	ArrayList<ReadListenDependency<? extends E>> upperBounds;
	ArrayList<ReadListenDependency<? extends E>> lowerBounds;
	Comparator<? super E> ordering;
	@Override public Comparator<? super E> getOrdering() {return ordering;}

	@Override
	public Self upperBound(ReadListenDependency<? extends E> bound) {
		if(upperBounds==null)
			upperBounds=new ArrayList<>(1);
		upperBounds.add(bound);
		return self();
	}
	@Override
	public Self lowerBound(ReadListenDependency<? extends E> bound) {
		if(lowerBounds==null)
			lowerBounds=new ArrayList<>(1);
		lowerBounds.add(bound);
		return self();
	}
	@Override
	public Self orderingRaw(Comparator<? super E> comp) {
		ordering=comp;
		return self();
	}
	@Override
	public Self ordering(Comparator<? super E> comp) {
		ordering=(v1, v2)->{
			if(v1==null || v2==null) {
				if(v1==v2)
					return 0;
				throw new VetoException("One of the values is null");
			}
			return comp.compare(v1, v2);
		};
		return self();
	}


	@Override
	public Self corrector(Function<? super E, ? extends E> corr) {
		value._addCorrector(corr);
		return self();
	}

	/**
	 * The association key for storing the upper bound that was put on a value
	 */
	protected static NamedAssociationKey<?> upperBound=new NamedAssociationKey<>("max");
	/**
	 * The association key for storing the lower bound that was put on a value
	 */
	protected static NamedAssociationKey<?> lowerBound=new NamedAssociationKey<>("min");

	@Override
	public Self onChange(ValueListener l) {
		value.addValueListener(l);
		return self();
	}


	@Override
	public Self onChange_weak_f(Function<? super V, ? extends ValueListener> l, BiConsumer<? super ValueListener, ? super ValueListener> out) {
		return onChange_weak(l.apply(valueBeingBuilt()), out);
	}

	@Override
	public Self debug(DebugCallback dc) {
		value._setDebugCallback(dc);
		return self();
	}
	//	@Override
	//	public Self dontRetry() {
	//		value.setDontRetry(true);
	//		return self();
	//	}
	@Override
	public V valueBeingBuilt() {
		return value;
	}
	@Override
	public Self lazy() {
		value.setLazyValidating(true);
		return self();
	}

	@Override
	public Self transformHandler(TransformHandler<E> th) {
		value._setTransformHandler(th);
		return self();
	}
	@Override
	public Self equivalence(BiPredicate<? super E, ? super E> equiv) {
		value._setEquivalence(equiv);
		return self();
	}
	boolean forgetOldValueOnDelayedRecompute=false;
	@Override
	public Self forgetOldValueOnDelayedRecompute() {
		forgetOldValueOnDelayedRecompute=true;
		return self();
	}
	@Override
	public Self noUnfulfilledGuard() {
		unfullfilledGuard=false;
		return self();
	}


	@Override
	public Self onFailedFulfill(Consumer<? super E> handler) {
		failHandler = handler;
		return self();
	}

	@Override
	public Self dynamicDependencies() {
		dynamicDependencies=true;
		return self();
	}

	@Override
	public Self pool(ExecutorService exec) {
		this.exec=exec;
		return self();
	}
	@Override
	public Self scoutIfInvalid(Predicate<? super Dependency> p){
		dependenciesThatTriggerScouting = p;
		return self();
	}
	@Override
	public Self mayRemoveDynamicDependency(BiPredicate<? super Dependency, ? super Depender> crit) {
		mayRemoveDynamicDependency = crit;
		return self();
	}
	ArrayList<BooleanSupplier> delaySwitch;
	@Override
	public Self setDelaySwitch(BooleanSupplier delaySwitch) {
		Objects.requireNonNull(delaySwitch);
		if(this.delaySwitch==null)
			this.delaySwitch=new ArrayList<>(2);
		this.delaySwitch.add(delaySwitch);
		return self();
	}

}