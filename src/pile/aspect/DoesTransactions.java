package pile.aspect;

import pile.aspect.combinations.Pile;
import pile.aspect.suppress.Suppressor;
import pile.impl.Independent;
import pile.impl.PileImpl;

/**
 * Interface for things like {@link Pile}s that can be in a <q>transaction ongoing</q> state.
 * @author bb
 *
 */
public interface DoesTransactions {
	/**
	 * Start a transaction and end it when the returned {@link Suppressor} is
	 * {@link Suppressor#release() released}. This may or may not make the value invalid until the transaction
	 * ends.
	 * ({@link PileImpl} makes it invalid; {@link Independent} keeps it valid)
	 * As long as transactions are active (that are not due to pending recomputations),
	 * the value must not recompute itself.
	 * At the beginning of the transaction, the value should me copied to a field that remembers the old value
	 * (unless that field is already occupied). When all transactions have ended, the then current value 
	 * should be compared (see {@link #_getEquivalence()}) to the stored old value in order to 
	 * decide whether there was a change.
	 * @param invalidate If the reactive value supports invalidity, and there were no transactions currently open, 
	 * invalidate the value for the duration of the transaction or until it is set explicitly
	 * @return
	 * @see #__beginTransaction(boolean) Override this to define how a transaction is begun
	 * @see #__endTransaction() Override this to define how a transaction is ended
	 */
	default public Suppressor transaction(boolean invalidate) {
		Suppressor ret = Suppressor.wrap(this::__endTransaction);
		__beginTransaction(invalidate);
		return ret;
	}
	/**
	 * Calls {@link #transaction(boolean) transaction(true)}
	 * @return
	 */
	default public Suppressor transaction() {
		return transaction(true);
	}
	/**
	 * Make sure to call __endTransaction() when the transaction is ended!
	 * For more comfort but less efficiency, consider calling {@link #transaction()} instead.
	 * @see #transaction()
	 */
	default void __beginTransaction() {
		__beginTransaction(true);
	}
	/**
	 * Make sure to call __endTransaction() when the transaction is ended!
	 * For more comfort but less efficiency, consider calling {@link #transaction(boolean)} instead.
	 * @see #transaction(boolean)
	 */
	void __beginTransaction(boolean invalidate);
	/**
	 * Calls {@link #__endTransaction(boolean)}
	 */
	void __endTransaction();
}
