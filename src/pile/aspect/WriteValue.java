package pile.aspect;

import java.util.function.BiPredicate;
import java.util.function.Consumer;
import java.util.function.Function;

import pile.aspect.ReadValue.InvalidValueException;
import pile.aspect.listen.ValueEvent;
import pile.aspect.suppress.Suppressor;

/**
 * A value that can be written to.
 * @author bb
 *
 * @param <E>
 */
public interface WriteValue<E> extends 
Consumer<E>, 
DoesTransactions, 
RemembersLastValue{
	/**
	 * A method handle for the {@link #transaction()} method
	 */
	public static final Function<WriteValue<?>, Suppressor> TRANSACTION_METHOD = WriteValue::transaction;
	/**
	 * A method handle for the {@link #transaction(boolena) transaction(false)} method
	 */
	public static final Function<WriteValue<?>, Suppressor> TRANSACTION_METHOD_NO_INVALIDATE = v->v.transaction(false);
	/**
	 * Set the value.
	 * The value actually being set may b different because corrections may be applied (@see {@link CorrigibleValue}
	 * <br>
	 * @return The actual current value that was set (because it may have been changed by corrections)
	 */
	public E set(E value);
	/**
	 * Delegats to {@link #set(Object)}, so you can use a {@link WriteValue} as a {@link Consumer}
	 */
	default public void accept(E value) {set(value);}
	/**
	 * Make the value invalid and prevent it from being recomputed again immediately.
	 */
	public void permaInvalidate();

	/**
	 * Call this if the value held by this object has been mutated.
	 * This should fire a {@link ValueEvent}.
	 * Note: It will not cause {@link Depender}s to recompute themselves. It is better not to mutate
	 * values.
	 */
	public void valueMutated();
	/**
	 * Set the value to <code>null</code>.
	 * <br>
	 * @return this. Subclasses and -interfaces should adapt the return type to be themselves 
	 */
	public default WriteValue<E> setNull() {
		set(null);
		return this;
	}
	/**
	 * Copy the contents from another value
	 * @param v the source value
	 * @param alsoInvalidate Whether to {@link WriteValue#revalidate()} the target value if this
	 * {@link ReadValue} is invalid
	 */
	default void transferFrom(ReadValue<? extends E> v, boolean alsoInvalidate) {
		if(v.isValid()) {
			try {
				set(v.getValidOrThrow());
				return;
			} catch (InvalidValueException e) {
			}
		}
		if(alsoInvalidate)
			revalidate();
	}

	/**
	 * Change the equivalence relation that is used to decide whether a change has occurred during a transaction
	 */
	public void _setEquivalence(BiPredicate<? super E, ? super E> equivalence);
	/**
	 * @return the equivalence relation that is used to decide whether a change has occurred during a transaction
	 */
	public BiPredicate<? super E, ? super E> _getEquivalence();

	/**
	 * Invalidate the value and recompute it as soon as allowed by the rules.
	 * This method should do nothing if this object has no concept of invalidity.
_	 */
	public void revalidate();
	/**
	 * Calls {@link #__endTransaction(boolean)}
	 */
	default void __endTransaction() {__endTransaction(true);}
	/**
	 * @see #transaction()
	 * @param changedIfOldInvalid
	 */
	void __endTransaction(boolean changedIfOldInvalid);
	
	/**
	 * @see CorrigibleValue#applyCorrection(Object)
	 */
	
	public E applyCorrection(E v);
}
