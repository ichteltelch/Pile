package pile.aspect.suppress;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.function.Function;

import pile.aspect.combinations.ReadListenValue;
import pile.aspect.listen.ListenValue;
import pile.aspect.listen.ValueListener;
import pile.impl.Piles;
import pile.specialized_bool.combinations.ReadValueBool;

/**
 * Utility that holds and releases {@link Suppressor}s obtained via some method
 * from one or more (or zero) objects.
 * It consists of
 * <ul>
 * <li>A {@link Collection} of objects</li>
 * <li>A {@link Function} that is invoked to actually generate {@link Suppressor}s for the objects</li>
 * <li>A reactive boolean indicating whether the objects should actually be suppressed</li>
 * <li></li>
 * </ul>
 * @author bb
 *
 * @param <E>
 */
public final class ReactiveSuppressionSwitcher<E> extends SuppressionSwitcher<E>{

	/**
	 * The rective boolean that controls the suppression state
	 */
	protected ReadListenValue<? extends Boolean> reactiveState = Piles.FALSE;
	
	/**
	 * The listener that reacts to changes in the suppression state
	 */
	private ValueListener updater = e->{
		synchronized(this){
			super.setSuppressedState(ReadValueBool.isTrue(reactiveState));
		}
	};
	
	/**
	 * The key that is needed to to remove the listener again (because it was added as a weak reference)
	 * @see ListenValue#addWeakValueListener(ValueListener)
	 */
	private ValueListener remove;
	
	public ReactiveSuppressionSwitcher(Function<? super E, ? extends Suppressor> method) {
		super(method);
	}
	
	/**
	 * Set the reactive boolean that controls the suppression state
	 * @param s <code>null</code> here means <code>false</code>
	 * @param update whether to update {@link SuppressionSwitcher#state}; if false is passed here, it the responsibility of the caller
	 * @return
	 */
	protected synchronized void _setSuppressedState(ReadListenValue<? extends Boolean> s, boolean update) {
		if(s==null)
			s=Piles.FALSE;
		if(s==reactiveState)
			return;
		reactiveState.removeValueListener(remove);
		reactiveState = s;
		remove = s.addWeakValueListener(updater);
		if(update)
			updater.runImmediately();
	}

	
	
	/**
	 * Change whether the objects are/should be actually suppressed without changing the objects
	 * @param b
	 * @return {@code this}
	 */
	public synchronized ReactiveSuppressionSwitcher<E> setSuppressedState(boolean newState) {
		return setSuppressedState(Piles.getConstant(newState));
	}
	/**
	 * Change whether the objects are/should be actually suppressed without changing the objects
	 * @param b
	 * @return {@code this}
	 */
	public synchronized ReactiveSuppressionSwitcher<E> setSuppressedState(ReadListenValue<? extends Boolean> newState) {
		_setSuppressedState(newState, true);
		return this;
	}




	/**
	 * Do not suppress any objects, and change whether future objects should be suppressed.
	 * @return {@code this}
	 */
	public synchronized ReactiveSuppressionSwitcher<E> setSuppressed(boolean newState) {
		return setSuppressed(Piles.getConstant(newState));
	}
	/**
	 * Do not suppress any objects, and change whether future objects should be suppressed.
	 * @return {@code this}
	 */
	public synchronized ReactiveSuppressionSwitcher<E> setSuppressed(ReadListenValue<? extends Boolean> newState) {
		super.setSuppressed(state);
		_setSuppressedState(newState, true);
		return this;
	}


	/**
	 * Change the suppressed objects, and change whether the objects are/should be suppressed.
	 * Any previously suppressed objects are released after the new {@link Suppressor}s have been created.
	 * <br>If {@code these} equals the currently suppressed collection of objects, nothing is done. 
	 * Call this method only if you're exclusively using collections that are not modified.
	 * @param newState
	 * @param these
	 * @return {@code this}
	 */
	public synchronized ReactiveSuppressionSwitcher<E> setSuppressed(ReadListenValue<? extends Boolean> newState, Collection<? extends E> these) {
		return setSuppressed(newState, these, true);
	}
	/**
	 * Change the suppressed objects, and change whether the objects are/should be suppressed.
	 * Any previously suppressed objects are released after the new {@link Suppressor}s have been created.
	 * @param these
	 * @param newState
	 * @param equalsTest if this is <code>true</code> and {@code these} equals the currently suppressed
	 * collection of objects, nothing is done. Set this to <code>true</code> omly if you're exclusively 
	 * using collections
	 * @return {@code this}
	 */
	public synchronized ReactiveSuppressionSwitcher<E> setSuppressed(boolean newState, Collection<? extends E> these, boolean equalsTest) {
		return setSuppressed(Piles.getConstant(newState), these, equalsTest);
	}
	/**
	 * Change the suppressed objects, and change whether the objects are/should be suppressed.
	 * Any previously suppressed objects are released after the new {@link Suppressor}s have been created.
	 * @param these
	 * @param newState
	 * @param equalsTest if this is <code>true</code> and {@code these} equals the currently suppressed
	 * collection of objects, nothing is done. Set this to <code>true</code> omly if you're exclusively 
	 * using collections
	 * @return {@code this}
	 */
	public synchronized ReactiveSuppressionSwitcher<E> setSuppressed(ReadListenValue<? extends Boolean> newState, Collection<? extends E> these, boolean equalsTest) {
		boolean s = newState!=null && ReadValueBool.isTrue(newState);
		super.setSuppressed(s, these, equalsTest);
		_setSuppressedState(newState, true);
		return this;
	}
	/**
	 * Change the suppressed objects, and change whether the objects are/should be suppressed.
	 * Any previously suppressed objects are released after the new {@link Suppressor}s have been created.
	 * <br>If {@code these} equals the currently suppressed collection of objects, nothing is done. 
	 * Call this method only if you're exclusively using collections that are not modified.
	 * @param newState
	 * @param these
	 * @see Arrays#asList(Object...)
	 * @return {@code this}
	 */
	@SafeVarargs
	public final synchronized ReactiveSuppressionSwitcher<E> setSuppressed(ReadListenValue<? extends Boolean> newState, E... these) {
		return setSuppressed(newState, Arrays.asList(these));
	}
	/**
	 * Change the suppressed objects, and change whether the objects are/should be suppressed.
	 * Any previously suppressed objects are released after the new {@link Suppressor}s have been created.
	 * <br>If {@code that} as a singleton equals the currently suppressed collection of objects, nothing is done. 
	 * Call this method only if you're exclusively using collections that are not modified.
	 * @param newState
	 * @param that (Possibly) suppress only this object
	 * @see Collections#singleton(Object)
	 * @return {@code this}
	 */
	public synchronized ReactiveSuppressionSwitcher<E> setSuppressed(boolean newState, E that) {
		return setSuppressed(Piles.getConstant(newState), that);
	}
	/**
	 * Change the suppressed objects, and change whether the objects are/should be suppressed.
	 * Any previously suppressed objects are released after the new {@link Suppressor}s have been created.
	 * <br>If {@code that} as a singleton equals the currently suppressed collection of objects, nothing is done. 
	 * Call this method only if you're exclusively using collections that are not modified.
	 * @param newState
	 * @param that (Possibly) suppress only this object
	 * @see Collections#singleton(Object)
	 * @return {@code this}
	 */
	public synchronized ReactiveSuppressionSwitcher<E> setSuppressed(ReadListenValue<? extends Boolean> newState, E that) {
		boolean s = newState!=null && ReadValueBool.isTrue(newState);
		super.setSuppressed(s, that);
		_setSuppressedState(newState, true);
		return this;
	}
	


}
