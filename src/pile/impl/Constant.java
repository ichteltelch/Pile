package pile.impl;

import java.util.function.BiPredicate;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

import pile.aspect.Dependency;
import pile.aspect.Depender;
import pile.aspect.WriteValue;
import pile.aspect.bracket.HasBrackets;
import pile.aspect.bracket.ValueBracket;
import pile.aspect.combinations.ReadListenDependency;
import pile.aspect.combinations.ReadWriteListenDependency;
import pile.aspect.listen.ListenValue;
import pile.aspect.listen.ValueListenenerUnregisterer;
import pile.aspect.listen.ValueListener;
import pile.aspect.suppress.Suppressor;
import pile.interop.wait.WaitService;
import pile.specialized_bool.combinations.ReadListenDependencyBool;


/**
 * This implements the methods of {@link ReadWriteListenDependency} (mostly) trivially
 * in order to wrap a constant value.
 * Note that {@link Constant}s implement {@link WriteValue}; however, all attempts to alter
 * the value have no effect. 
 * {@link Constant}s also implement {@link ListenValue} and {@link Dependency}, but because they never change,
 * this will never affect any {@link ValueListener} or {@link Depender}. For this reason, the {@link Constant}
 * does not manage any references to the objects that have been registered as its (inconsequential)
 * {@link ValueListener}s or {@link Depender}s.
 * @author bb
 *
 * @param <E>
 */
public class Constant<E> implements ReadWriteListenDependency<E>{
	/**
	 * The value. Never changes.
	 */
	final E value;

	public Constant(E init) {
		value=init;
	}
	@Override public E getValid() throws InterruptedException { return value; }

	@Override public E getValid(long timeout) throws InterruptedException { return value; }

	@Override public E getValid(WaitService ws) throws InterruptedException { return value; }

	@Override public E getValid(WaitService ws, long timeout) throws InterruptedException { return value; }

	@Override
	public E getAsync() {
		return value;
	}
	@Override
	public E getValidOrThrow() throws InvalidValueException {
		return value;
	}

	@Override
	public boolean isValid() {
		return true;
	}

	@Override
	public E get() {
		return value;
	}



	@Override
	public void addValueListener(ValueListener cl) {
	}

	@Override
	public void removeValueListener(ValueListener cl) {
	}
	
	@Override
	public ValueListener addWeakValueListener(ValueListenenerUnregisterer u, ValueListener l) {
		return l;
	}
	@Override
	public ValueListener addWeakValueListener(ValueListener listener) {
		return listener;
	}

	@Override
	public boolean hasValueListener(ValueListener cl) {
		return false;
	}

	@Override
	public void fireValueChange() {
	}
	@Override
	public void __addDepender(Depender d, boolean propagateInvalidity) {


	}
	@Override
	public void giveDependers(Consumer<? super Depender> out) {

	}
	@Override
	public void __removeDepender(Depender d) {

	}
	@Override
	public String dependencyName() {
		return toString();
	}
	@Override
	public String toString() {
		return "Constant <"+value+">";
	}
	
	@Override
	public void autoValidate() {		
	}
	
	@Override
	public ReadListenDependencyBool validity() {
		return Piles.TRUE;
	}
	@Override public boolean isInTransaction() {return false;}
	@Override public ReadListenDependencyBool inTransactionValue() {return Piles.FALSE;}
	@Override public boolean isValidNull() {return value==null;}
	@Override public boolean isDestroyed() {return false;}
//	@Override public Maybe<E> getWithValidity() {return Maybe.just(value);}
	@Override
	public ReadListenDependencyBool nullOrInvalid() {
		return isNull()?Piles.TRUE:Piles.FALSE;
	}
	@Override public E set(E value) {return this.value;}
	@Override public Constant<E> setNull() {return this;}
	@Override public void permaInvalidate() {}
	@Override public void __beginTransaction(boolean b) {}
	@Override public void __endTransaction(boolean b) {}
	@Override public void valueMutated() {}
	@Override public boolean willNeverChange() {return true;}
	@Override public void destroy() {}
	@Override public void _setEquivalence(BiPredicate<? super E, ? super E> equivalence) {}
	@Override public void _setDebugCallback(DebugCallback dc) {}
	@Override public boolean isLazyValidating() {return false;}
	@Override public void lazyValidate() {}
	@Override public void setLazyValidating(boolean newState) {}
//	@Override public boolean couldBeValid(boolean onlyIfLazyValidating) {return true;}
	@Override
	public void _printConstructionStackTrace() {
		System.err.println("(constants don't get consturction stack traces)");
	}
	@Override public boolean isValidAsync() {return true;}
	
	public static BiPredicate<Object, Object> EQUIVALENCE=(a, b)->a==b; 
	@Override
	public BiPredicate<? super E, ? super E> _getEquivalence() {
		return EQUIVALENCE;
	}
	@Override public void revalidate() {}
//	@Override
//	public void detachDepender(Depender value) {
//		
//	}
	@Override
	public E getOldIfInvalid() {
		return value;
	}
	@Override
	public void __dependerNeedsDeepRevalidate(Depender d, boolean needs) {}
	
	@Override
	public void await(WaitService ws, BooleanSupplier c) throws InterruptedException {
		boolean notYetWarned=true;
		synchronized (this) {
			while(!c.getAsBoolean()) {
				if(notYetWarned) {
					notYetWarned=false;
					log.warning("Waiting for a false Condition on a Constant!");
				}
				ws.wait(this, 1000);
			}
		}		
	}
	@Override
	public boolean await(WaitService ws, BooleanSupplier c, long millis) throws InterruptedException {
		boolean notYetWarned=true;
		long t0 = System.currentTimeMillis();
		synchronized (this) {
			while(!c.getAsBoolean()) {
				if(notYetWarned) {
					notYetWarned=false;
					log.warning("Waiting for a false Condition on a Constant!");
				}

				long left = millis - (System.currentTimeMillis()-t0);
				
				if(left<=0)
					return false;
				ws.wait(this, Math.min(1000, left));
			}
		}
		return true;
	}
	public Constant<E> setName(String s) {
		return this;
//		throw new UnsupportedOperationException("Constants don't have names!");
	}
	@Override public void removeWeakValueListener(ValueListener wrapped) {}
	@Override
	public Suppressor transaction() {
		return Suppressor.NOP;
	}
	@Override
	public E applyCorrection(E v) {
		return value;
	}

	/**
	 * @return Since deep revalidation is meaningless for {@link Constant}s,
	 * this always returns <code>false</code>
	 */
	@Override
	public boolean isDeepRevalidationSuppressed() {
		return false;
	}
	@Override
	public Suppressor suppressDeepRevalidation() {
		return Suppressor.NOP;
	}

	@Override
	public boolean remembersLastValue() {
		return false;
	}
	@Override
	public void storeLastValueNow() {		
	}
	@Override
	public void resetToLastValue() {		
	}
	@Override
	public Suppressor suppressRememberLastValue() {
		return Suppressor.NOP;
	}
	@Override
	public void __setEssentialFor(Depender value, boolean essential) {
	}
	@Override
	public void recordRead() {}
	
	@Override
	public void _addAnyValueBracket(boolean openNow, ValueBracket<? super E, ? super ReadListenDependency<? extends E>> b) {
	}
	@Override
	public void _addOldValueBracket(boolean openNow, ValueBracket<? super E, ? super ReadListenDependency<? extends E>> b) {
	}
	@Override
	public void _addValueBracket(boolean openNow, ValueBracket<? super E, ? super ReadListenDependency<? extends E>> b) {
	}
	@Override
	public void bequeathBrackets(boolean openNow, HasBrackets<ReadListenDependency<? extends E>, ? extends E> v) {
	}
	@Override
	public boolean destroyIfMarkedDisposable() {
		return false;
	}
}
