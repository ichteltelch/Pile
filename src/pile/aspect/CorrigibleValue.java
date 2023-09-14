package pile.aspect;

import java.util.function.Function;

/**
 * Interface for value wrappers that can apply a correction their contained value
 * before it is changed.
 * @author bb
 *
 * @param <E>
 */
public interface CorrigibleValue<E> {
	/**
	 * The correction method. If this method throws an {@link Exception}, 
	 * it should be logged and the change rejected. If, however, the {@link Exception}
	 * is a {@link VetoException}, it should not be logged. If the {@link VetoException#revalidate}
	 * flag is set and the context allows it, the Value should react to the veto be recomputing itself.
	 * @param value The value that was requested to be set
	 * @return the corrected values that will actually be set
	 * @throws VetoException to prevent changing the value
	 */
	public E applyCorrection(E value) throws VetoException;
	/**
	 * Add an elementary corrector step to this object.
	 * The corrector steps should be run in the order they were added.
	 * @param corrector A {@link Function} that accepts a value and returns either the 
	 * same value (possibly mutated) or a different value. 
	 * @see VetoException may be thrown by the corrector to prevent changing the value
	 */
	public void _addCorrector(Function<? super E, ? extends E> corrector);
}
