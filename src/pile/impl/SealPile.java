package pile.impl;

import java.util.function.BiPredicate;
import java.util.function.Consumer;
import java.util.function.Function;

import pile.aspect.Dependency;
import pile.aspect.Depender;
import pile.aspect.Sealable;
import pile.aspect.ValueBracket;
import pile.aspect.WriteValue;
import pile.aspect.combinations.ReadListenDependency;
import pile.aspect.combinations.WriteDepender;
import pile.aspect.recompute.Recomputer;
import pile.aspect.suppress.Suppressor;

public class SealPile<E> extends PileImpl<E> implements Sealable<E> {

	protected static final Consumer<Object> defaultInterceptor=o->{
		throw new IllegalStateException("Cannot call set() directly on a sealed SealableValue");
	};

	volatile Consumer<? super E> sealed;
	/**
	 * Whether it is allowed to call {@link #permaInvalidate()} when sealed.
	 */
	boolean allowInvalidation;
	@Override
	final public void seal() {seal(defaultInterceptor, false);}

	@Override
	final public void seal(Consumer<? super E> interceptor, boolean allowInvalidation) {
		if(interceptor==null)
			interceptor=defaultInterceptor;
		Consumer<? super E> oldInteceptor = sealed;
		if(oldInteceptor!=null) {
			if(oldInteceptor!=interceptor)
				throw new IllegalStateException("This SealableValue has already been sealed with a different interceptor!");
			return;
		}
		sealed = interceptor;
		this.allowInvalidation=allowInvalidation;
	}
	@Override
	final public boolean isSealed() {return sealed!=null;}
	@Override 
	final public boolean isDefaultSealed() {
		return sealed==defaultInterceptor;
	}
	protected WriteDepender<E> privi;
	@Override
	public WriteValue<E> makeSetter() {
		if(sealed != null)
			throw new IllegalStateException("Cannot call makeSetter() on a sealed SealableValue");
		if(privi==null)
			privi=new PrivilegedWriteDepender(); 
		return privi;
	}
	/**
	 * @return Whether {@link #getPrivilegedDepender()} or {@link #makeSetter()} has ever returned something
	 */
	@Override
	final public boolean setterExists() {
		return privi!=null;
	}
	/**
	 * @return Whether {@link #getPrivilegedDepender()} or {@link #makeSetter()} has ever returned something
	 */
	final public boolean privilegedDependerExists() {
		return privi!=null;
	}

	@Override
	public E set(E val) {
		Consumer<? super E> inteceptor = sealed;
		if(inteceptor!=null) {
			inteceptor.accept(val);
			return get();
		}
		return set0(val);
	}

	protected E set0(E val) {
		return super.set(val);
	}

	@Override
	public void addDependency(Dependency d, boolean invalidate) {
		if(sealed!=null)
			throw new IllegalStateException("Cannot add dependencies to a sealed SealableValue");
		super.addDependency(d, invalidate);
	}
	@Override
	public void removeDependency(Dependency d) {
		if(sealed!=null) {
			if(!isDestroyed() && !d.isDestroyed())
				throw new IllegalStateException("Cannot remove dependencies from a sealed SealableValue");
		}
		super.removeDependency(d);
	}
	public void addDependency(Dependency d, boolean invalidate, boolean recordChange) {
		if(sealed!=null)
			throw new IllegalStateException("Cannot add dependencies to a sealed SealableValue");
		super.addDependency(d, invalidate, recordChange);
	}
	@Override
	public void removeDependency(Dependency d, boolean invalidate, boolean recordChange) {
		if(sealed!=null) {
			if(!isDestroyed() && !d.isDestroyed())
				throw new IllegalStateException("Cannot remove dependencies from a sealed SealableValue");
		}
		super.removeDependency(d, invalidate, recordChange);
	}
	@Override
	public void setDependencyEssential(boolean essential, Dependency d) {
		if(sealed!=null) {
			throw new IllegalStateException("Cannot change essentiality of a dependency of a sealed SealableValue");
		}
		super.setDependencyEssential(essential, d);
	}
	@Override
	public Depender getPrivilegedDepender() {
		if(sealed!=null) {
			throw new IllegalStateException("Cannot get a privileged dependency access for a sealed SealableValue");
		}
		if(privi==null)
			privi = new PrivilegedWriteDepender();
		return privi;
	}
	@Override
	public void _setRecompute(Recomputer<E> recomputer) {
		if(sealed!=null) {
			throw new IllegalStateException("Cannot change the recomputer of a sealed SealableValue");
		}
		super._setRecompute(recomputer);
	}
	@Override
	public void _addValueBracket(boolean openNow, ValueBracket<? super E, ? super ReadListenDependency<? extends E>> b) {
		if(sealed!=null) {
			if(!(b instanceof ValueBracket.ValueOnlyBracket<?>))
				throw new IllegalStateException("Cannot change bracketing of a sealed SealableValue");
		}
		super._addValueBracket(openNow, b);
	}
	@Override
	public void _addOldValueBracket(boolean openNow, ValueBracket<? super E, ? super ReadListenDependency<? extends E>> b) {
		if(sealed!=null) {
			if(!(b instanceof ValueBracket.ValueOnlyBracket<?>))
				throw new IllegalStateException("Cannot change bracketing of a sealed SealableValue");
		}
		super._addOldValueBracket(openNow, b);
	}
	@Override
	public void _addAnyValueBracket(boolean openNow, ValueBracket<? super E, ? super ReadListenDependency<? extends E>> b) {
		if(sealed!=null) {
			if(!(b instanceof ValueBracket.ValueOnlyBracket<?>))
				throw new IllegalStateException("Cannot change bracketing of a sealed SealableValue");
		}
		super._addAnyValueBracket(openNow, b);
	}
	@Override
	public void permaInvalidate() {
		if(sealed!=null && ! allowInvalidation) {
			throw new IllegalStateException("Cannot invalidate a sealed SealableValue");
		}		
		super.permaInvalidate();
	}
	@Override
	public void _addCorrector(Function<? super E, ? extends E> corrector) {
		if(sealed!=null) {
			throw new IllegalStateException("Cannot change correctors of a sealed SealableValue");
		}
		super._addCorrector(corrector);
	}
//	@Override
//	public void _setCanRecomputeWithInvalidDependencies(boolean b) {
//		if(sealed!=null) {
//			throw new IllegalStateException("Cannot change recomputation behavior of a sealed SealableValue");
//		}
//		super._setCanRecomputeWithInvalidDependencies(b);
//	}
	@Override
	public void _setEquivalence(BiPredicate<? super E, ? super E> equivalence) {
		if(sealed!=null) {
			throw new IllegalStateException("Cannot change the equivalence relation of a sealed SealableValue");
		}
		super._setEquivalence(equivalence);
	}

	@Override
	public SealPile<E> setName(String name) {
		avName=name;
		return this;
	}
	@Override
	public void destroy() {
		//unseal the value so removing the dependencies works
		sealed=null;
		super.destroy();
	}
	@Override
	public boolean willNeverChange() {
		return isDefaultSealed() && !privilegedDependerExists()  && !setterExists() && 
				(recompute==null || _thisDependsOn==null);
	}
//	@Override
//	public void setDontRetry(boolean dont) {
//		if(sealed!=null) {
//			throw new IllegalStateException("Cannot change the recomputation behavior a sealed SealableValue");
//		}
//		super.setDontRetry(dont);
//	}
//	

	private class PrivilegedWriteDepender implements WriteDepender<E>{

		@Override
		public E set(E value) {
			return set0(value);
		}
		@Override
		public void accept(E value) {
			set0(value);
		}
		@Override
		public boolean isDestroyed() {
			return SealPile.this.isDestroyed();
		}


		@Override
		public void permaInvalidate() {
			SealPile.super.permaInvalidate();
		}

		@Override
		public void __beginTransaction(boolean b) {
			SealPile.this.__beginTransaction(b);
		}

		@Override
		public void __endTransaction(boolean b) {
			SealPile.this.__endTransaction(b);
		}

		@Override
		public void valueMutated() {
			SealPile.this.valueMutated();
		}

		@Override
		public void _setEquivalence(BiPredicate<? super E, ? super E> equivalence) {
			SealPile.super._setEquivalence(equivalence);
		}

		@Override
		public BiPredicate<? super E, ? super E> _getEquivalence() {
			return SealPile.this._getEquivalence();
		}

		@Override
		public void revalidate() {
			SealPile.this.revalidate();					
		}
		@Override
		public void setDependencyEssential(boolean essential, Dependency d) {
			SealPile.super.setDependencyEssential(essential, d);
		}

		@Override
		public void removeDependency(Dependency d, boolean recompute) {
			SealPile.super.removeDependency(d, recompute, recompute);
		}

		@Override
		public Depender getPrivilegedDepender() {
			return this;
		}

		@Override
		public void giveDependencies(Consumer<? super Dependency> out) {
			SealPile.this.giveDependencies(out);
		}

		@Override
		public boolean dependsOn(Dependency d) {
			return SealPile.this.dependsOn(d);
		}

		@Override
		public void dependencyEndsChanging(Dependency d, boolean changed) {
			SealPile.this.dependencyEndsChanging(d, changed);
		}

		@Override
		public void dependencyBeginsChanging(Dependency d, boolean wasValid, boolean invalidate) {
			SealPile.this.dependencyBeginsChanging(d, wasValid, invalidate);				
		}
		
		@Override
		public void escalateDependencyChange(Dependency newlyInvalidDependency) {
			SealPile.this.escalateDependencyChange(newlyInvalidDependency);
		}

		@Override
		public void deepDestroy() {
			SealPile.this.deepDestroy();
		}

		@Override
		public void addDependency(Dependency d, boolean recompute) {
			SealPile.super.addDependency(d, recompute);
		}

		@Override
		public void __dependencyBecameLongTermInvalid(Dependency d) {
			SealPile.this.__dependencyBecameLongTermInvalid(d);
		}

		@Override
		public void destroy() {
			SealPile.this.destroy();
		}

		@Override
		public boolean isEssential(Dependency value) {
			return SealPile.this.isEssential(value);
		}

		@Override
		public void __dependencyIsNowValid(Dependency d) {
			SealPile.this.__dependencyIsNowValid(d);
		}
		@Override
		public void deepRevalidate(Dependency d) {
			SealPile.this.deepRevalidate(d);
			
		}

		@Override
		public void addDependency(Dependency d, boolean recompute, boolean recordChange) {
			SealPile.super.addDependency(d, recompute, recordChange);
		}

		@Override
		public void removeDependency(Dependency d, boolean recompute, boolean recordChange) {
			SealPile.super.removeDependency(d, recompute, recordChange);			
		}
		@Override
		public E applyCorrection(E v) {
			return SealPile.this.applyCorrection(v);
		}
		@Override
		public boolean remembersLastValue() {
			return SealPile.this.remembersLastValue();
		}
		@Override
		public void storeLastValueNow() {
			SealPile.this.storeLastValueNow();
		}
		@Override
		public void resetToLastValue() {
			SealPile.this.resetToLastValue();
		}
		@Override
		public Suppressor suppressRememberLastValue() {
			return SealPile.this.suppressRememberLastValue();
		}
		@Override
		public Dependency[] getDependencies() {
			return SealPile.this.getDependencies();
		}
	}



}
