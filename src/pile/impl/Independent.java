package pile.impl;

import static pile.interop.debug.DebugEnabled.DE;

import java.lang.ref.ReferenceQueue;
import java.util.ArrayList;
import java.util.Objects;
import java.util.WeakHashMap;
import java.util.function.BiPredicate;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;

import pile.aspect.CorrigibleValue;
import pile.aspect.Dependency;
import pile.aspect.Depender;
import pile.aspect.HasAssociations;
import pile.aspect.HasInfluencers;
import pile.aspect.LastValueRememberer;
import pile.aspect.RemembersLastValue;
import pile.aspect.Sealable;
import pile.aspect.VetoException;
import pile.aspect.WriteValue;
import pile.aspect.bracket.ValueBracket;
import pile.aspect.bracket.ValueOnlyBracket;
import pile.aspect.combinations.ReadListenDependency;
import pile.aspect.combinations.ReadListenValue;
import pile.aspect.combinations.ReadWriteListenDependency;
import pile.aspect.listen.ValueListener;
import pile.aspect.recompute.DependencyRecorder;
import pile.aspect.recompute.Recomputation;
import pile.aspect.recompute.Recomputations;
import pile.aspect.suppress.Suppressor;
import pile.interop.debug.DebugEnabled;
import pile.interop.wait.WaitService;
import pile.specialized_bool.IndependentBool;
import pile.specialized_bool.combinations.ReadListenDependencyBool;
import pile.utils.Functional;

/**
 * Holds a value that is always valid and does not depend on any {@link Dependency}
 * and does not recompute itself. It can be set using its {@link #set(Object)} method.
 * This can be prohibited by calling the {@link #seal()}, after which setting the value is 
 * only possible through a {@link Consumer} previously obtained from 
 * the {@link #makeSetter()} method.
 * @author bb
 *
 * @param <E>
 */
public class Independent<E> extends 
AbstractReadListenDependency_NoDepender<E>
implements 
Sealable<E>,
ReadWriteListenDependency<E>,
HasAssociations.Mixin,
CorrigibleValue<E>,
RemembersLastValue,
HasInfluencers
{
	final static Logger log=Logger.getLogger("Indepednent");

	/**
	 * 
	 * @param init Mandatory initial value
	 */
	public Independent(E init) {
		value=init;
	}

	@Override public Independent<E> setName(String name){
		avName = name;
		return this;
	}
	@Override public E getValid(WaitService ws) throws InterruptedException {return get();}

	@Override public E getValid(WaitService ws, long timeout) throws InterruptedException {return get();}

	@Override public E getValid() throws InterruptedException {return get();}

	@Override public E getValid(long timeout) throws InterruptedException {return get();}

	@Override public boolean isValid() {return true;}

	E value;
	E oldValue;
	boolean oldValid;
	/**
	 * How many times a Suppressor has been generated by {@link #suppressRememberLastValue()}
	 * that has not been {@link Suppressor#release() release}d.
	 */
	int storingSuppressors;
	/**
	 * It should not be possible to {@link #set(Object)} this value if it has been {@link #seal()}ed,
	 * except if a {@link #makeSetter() setter has been made}.
	 */
	protected static final Consumer<Object> defaultInterceptor=o->{
		throw new IllegalStateException("Cannot call set() directly on a sealed Independent value");
	};
	/**
	 * If this field is non-<code>null</code>, this {@link Independent} value is sealed;
	 * Several methods will throw an {@link IllegalStateException} and an attempt to {@link #set(Object)}
	 * it will be redirected to this field.
	 */
	volatile Consumer<? super E> sealed;
	@Override
	public void seal() {seal(defaultInterceptor, false);}

	@Override
	public void seal(Consumer<? super E> interceptor, boolean allowInvalidation) {
		if(interceptor==null)
			interceptor=defaultInterceptor;
		Consumer<? super E> oldInteceptor = sealed;
		if(oldInteceptor!=null) {
			if(oldInteceptor!=interceptor)
				throw new IllegalStateException("This Independent value has already been sealed with a different interceptor!");
			return;
		}
		sealed = interceptor;
	}
	@Override public boolean isSealed() {
		return sealed!=null;
	}
	@Override public boolean isDefaultSealed() {
		return sealed==defaultInterceptor;
	}
	@Override
	public E getValidOrThrow() throws InvalidValueException {
		recordRead();
		return value;
	}
	WriteValue<E> setter;
	@Override public WriteValue<E> makeSetter() {
		if(sealed!=null)
			throw new IllegalStateException("Cannot call makeSetter() on a sealed Independent value");
		if(setter==null)
			setter=new WriteValue<E>() {

				@Override
				public E set(E value) {
					return set0(value);
				}
				@Override
				public void accept(E value) {
					set0(value);
				}

				@Override
				public void permaInvalidate() {
				}

				@Override
				public void __beginTransaction(boolean b) {
					Independent.this.__beginTransaction(b);
				}

				@Override
				public void __endTransaction(boolean b) {
					Independent.this.__endTransaction(b);
				}

				@Override
				public void valueMutated() {
					Independent.this.valueMutated();
				}

				@Override
				public void _setEquivalence(BiPredicate<? super E, ? super E> equivalence) {
					Independent.super._setEquivalence(equivalence);
				}

				@Override
				public BiPredicate<? super E, ? super E> _getEquivalence() {
					return Independent.this._getEquivalence();
				}

				@Override
				public E applyCorrection(E v) {
					return Independent.this.applyCorrection(v);
				}

				@Override
				public void revalidate() {
				}
				@Override
				public boolean remembersLastValue() {
					return Independent.this.remembersLastValue();
				}
				@Override
				public void storeLastValueNow() {
					Independent.this.storeLastValueNow();
				}
				@Override
				public void resetToLastValue() {
					Independent.this.resetToLastValue();
				}
				@Override
				public Suppressor suppressRememberLastValue() {
					return Independent.this.suppressRememberLastValue();
				}
			};;
		return setter;
	}

	@Override
	public void _addValueBracket(boolean openNow, ValueBracket<? super E, ? super ReadListenDependency<? extends E>> b) {
		if(sealed!=null) {
			if(!(b instanceof ValueOnlyBracket<?>))
				throw new IllegalStateException("Cannot change bracketing of a sealed Independent value");
		}
		super._addValueBracket(openNow, b);
	}
	@Override
	public void _addOldValueBracket(boolean openNow, ValueBracket<? super E, ? super ReadListenDependency<? extends E>> b) {
		if(sealed!=null) {
			if(!(b instanceof ValueOnlyBracket<?>))
				throw new IllegalStateException("Cannot change bracketing of a sealed Independent value");
		}
		super._addOldValueBracket(openNow, b);
	}
	@Override
	public void _addAnyValueBracket(boolean openNow, ValueBracket<? super E, ? super ReadListenDependency<? extends E>> b) {
		if(sealed!=null) {
			if(!(b instanceof ValueOnlyBracket<?>))
				throw new IllegalStateException("Cannot change bracketing of a sealed Independent value");
		}
		super._addAnyValueBracket(openNow, b);
	}
	@Override
	public boolean setterExists() {
		return setter!=null;
	}
	@Override
	public E set(E val) {
		Consumer<? super E> interceptor = sealed;
		if(interceptor!=null) {
			interceptor.accept(val);
			return get();
		}


		return set0(val);
	}
	@Override public Independent<E> setNull() {
		set(null);
		return this;
	}
	private E set0(E val) {
		if(DE && dc!=null) dc.set(this, val);
		try {
			val = applyCorrection(val);
		}catch(VetoException x) {
			x.printStackTrace();
			return get();
		}catch (RuntimeException x) {
			log.log(Level.SEVERE, "Exception in applyCorrection", x);
			return get();
		}
		synchronized (mutex) {
			if(equivalence.test(val, value))
				return value;
		}
		try{
			__beginTransaction();
			fireDeepRevalidate();

			synchronized (mutex) {
				if(value!=val) {
					closeBrackets();
					value=val;
					openBrackets();
				}
			}
			setIsNullValue.accept(value==null);
			return val;
		}finally {
			__endTransaction(true);
			synchronized (mutex) {
				WaitService.get().notifyAll(mutex);
			}
		}
	}

	@Override
	public E get() {
		recordRead();
		synchronized (mutex) {
			return value;
		}
	}


	@Override
	public ReadListenDependencyBool validity() {
		return Piles.TRUE;
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
	protected void moveValueToOldValue() {
		synchronized(mutex) {
			if(oldValid)
				return;
			closeOldBrackets();
			oldValue=value;
			openOldBrackets();
		}		
	}

	@Override
	protected void __restoreValueFromOldValue() {
		synchronized(mutex) {
			if(!oldValid)
				return;
			if(value==oldValue)
				return;
			closeBrackets();
			value=oldValue;
			openBrackets();
			closeOldBrackets();
		}
	}

	@Override protected boolean __hasChangedDependencies() {return false;}

	@Override protected boolean __oldValid() {return oldValid;}

	@Override protected boolean __valid() {return true;}

	@Override protected E __oldValue() {return oldValue;}

	@Override protected E __value() {return value;}

	public String toString() {
		return avName+": <"+get()+">";
	}
	@Override public boolean isValidNull() {
		recordRead();
		return value==null;
	}
	@Override
	public boolean isDestroyed() {
		return destroyed;
	}



	@Override
	public void permaInvalidate() {
		//throw new UnsupportedOperationException("Independent values cannot be invalidated");
	}

	@Override
	public void valueMutated() {
		fireValueChange();
	}

	@Override
	public void __beginTransaction(boolean b) {
		super.beginTransaction(true);
	}

	@Override
	public void __endTransaction(boolean b) {
		super.__endTransaction(b);
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

	/**
	 * Corrector functions that can modify or replace the value before it is actually set.
	 * They can also throw a {@link VetoException} to prevent a change.
	 */
	volatile ArrayList<Function<? super E, ? extends E>> correctors;
	public void _addCorrector(Function<? super E, ? extends E> corrector) {
		
		Objects.requireNonNull(corrector);
		if(sealed!=null) {
			throw new IllegalStateException("Cannot change correctors of a sealed Independent value");
		}
		ArrayList<Function<? super E, ? extends E>> localRef = lazyInitCorrectors();
		synchronized (localRef) {
			localRef.add(corrector);
		}
	}
	/**
	 * lazily initialize the {@link #correctors} list.
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





	@Override
	public boolean remembersLastValue() {
		synchronized (mutex) {
			return storingSuppressors<=0;
		}
	}

	@Override
	public Suppressor suppressRememberLastValue() {
		synchronized (mutex) {
			++storingSuppressors;
			return Suppressor.wrap(()->{
				synchronized (mutex) {
					--storingSuppressors;
				}				
			});

		}
	}

	@Override
	public void storeLastValueNow() {
		LastValueRememberer<E> rem = getAssociation(LastValueRememberer.<E>key());
		if(rem!=null)
			rem.storeLastValue(get());
	}
	@Override
	public void resetToLastValue() {
		LastValueRememberer<E> rem = getAssociation(LastValueRememberer.<E>key());
		if(rem!=null)
			set(rem.recallLastValue());
	}
	@Override
	public boolean willNeverChange() {
		return isDefaultSealed() && !setterExists();
	}

	@Override
	public void _setEquivalence(BiPredicate<? super E, ? super E> equivalence) {
		if(sealed!=null) {
			throw new IllegalStateException("Cannot change the equivalence relation of a sealed Independen");
		}
		super._setEquivalence(equivalence);
	}


	@Override public void destroy() {
		if(destroyed)
			return;
		if(DebugEnabled.COUNT_BRACKET_LOCKS && DebugEnabled.WARN_ON_DESTROY_WHILE_LOCKED && DebugEnabled.lockedValueMutices.get().val>0) {
			DebugEnabled.warn(log, "Destroy while locked!");
		}
		destroyed=true;
		sealed=null;
		WeakHashMap<Depender, ?> ief;
		synchronized (mutex) {
			closeOldBrackets();
			closeBrackets();
			ief = isEssentialFor;
			isEssentialFor = null;
		}
		if(ief!=null) {
			synchronized (ief) {
				for(Depender d: ief.keySet())
					d.destroy();
			}
		}
		giveDependers(d->{
			if(d.isEssential(this))
				d.destroy();
			d.removeDependency(this);	
		});
		owner=null;
		__workInformQueue();
	}
	@Override protected void __clearChangedDependencies() {}
	@Override protected boolean isComputing() {return false;}
	@Override protected void __setValidity(boolean valid) {}
	@Override public E getAsync() {return value;}
	@Override public boolean isLazyValidating() {return false;}
	@Override public void lazyValidate() {}
	@Override public void setLazyValidating(boolean newState) {}
//	@Override public boolean couldBeValid(boolean onlyIfLazyValidating) {return true;}
	@Override public boolean is(E v) {
		if(!isValid())
			return false;
		else
			try {
				return equivalence.test(getValidOrThrow(), v);
			}catch(InvalidValueException x) {
				return false;
			}
	}
	@Override
	public boolean isValidAsync() {
		return true;
	}

	@Override public void revalidate() {}
	/**
	 * Cause this {@link Independent} to follow whatever value the leader has,
	 * using the {@link #transferFrom(pile.aspect.ReadValue, boolean)} method.
	 * @param leader
	 * @param alsoInvalidate
	 * @return
	 */
	public ValueListener follow(ReadListenValue<? extends E> leader, boolean alsoInvalidate) {
		ValueListener vl = e->{
			transferFrom(leader, alsoInvalidate);
		};
		ValueListener ret = leader.addWeakValueListener(vl);
		keepStrong(vl);		
		return ret;
	}
	volatile IndependentBool isNullValue;
	Consumer<? super Boolean> setIsNullValue=Functional.NOP;
	@Override
	public ReadListenDependencyBool nullOrInvalid() {
		IndependentBool localRef = isNullValue;
		if (localRef == null) {
			synchronized (mutex) {
				localRef = isNullValue;
				if (localRef == null) {
					localRef = new IndependentBool(!__valid() || value==null);
					Consumer<? super Boolean> setter = localRef.makeSetter();
					setIsNullValue=v->{
						assert !Thread.holdsLock(informQueue);
						synchronized (informQueue) {
							informQueue.add(()->setter.accept(v));
						}
					};
					localRef.setName((avName==null?"?":avName)+" == null");
					localRef.keepStrong(this);
					localRef.seal();
					
					isNullValue = localRef;
				}
			}
		}
		return localRef;

	}



	@Override
	protected final int __recomputerTransactions() {
		return 0;
	}

	@Override
	public final E getOldIfInvalid() {
		recordRead();
		return value;
	}
	@Override
	public final boolean observedValid() {
		return true;
	}
	@Override
	protected final void __scheduleRecomputation(boolean cancelOngoing) {
		//Independent values don't recompute
	}
//	@Override
//	protected final boolean canRecomputeWithInvalidDependencies() {
//		return false;
//	}
//	@Override
//	public Independent<E> validBuffer() {
//		return this;
//	}	
//	@Override
//	public Independent<E> validBuffer_memo() {
//		return this;
//	}
	@Override
	protected final Recomputation<E> __ongoingRecomputation() {
		return null;
	}
	@Override
	protected Dependency[] __dependencies() {return null;}

	@Override
	public void giveInfluencers(Consumer<? super Object> out) {
		if(owner!=null)
			out.accept(owner);
	}
	
	@Override
	public boolean destroyIfMarkedDisposable() {
		if(isMarkedDisposable()) {
			destroy();
			return true;
		}else {
			return false;
		}
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
	@Override
	protected void copyValueToOldValue() {
		moveValueToOldValue();
	}
}
