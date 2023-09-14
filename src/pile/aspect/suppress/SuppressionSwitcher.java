package pile.aspect.suppress;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Objects;
import java.util.function.Function;

/**
 * Utility that holds and releases {@link Suppressor}s obtained via some method
 * from one or more (or zero) objects.
 * It consists of
 * <ul>
 * <li>A {@link Collection} of objects</li>
 * <li>A {@link Function} that is invoked to actually generate {@link Suppressor}s for the objects</li>
 * <li>A boolean indicating whether the objects should actually be suppressed</li>
 * <li></li>
 * </ul>
 * @author bb
 *
 * @param <E>
 */
public class SuppressionSwitcher<E> {
	/**
	 * A subclass with no extra functionality, but it is final and therefore slightly more efficient.
	 * @author bb
	 *
	 * @param <E>
	 */
	public static final class Final<E> extends SuppressionSwitcher<E>{
		public Final(Function<? super E, ? extends Suppressor> method) {
			super(method);
		}
	}
	/**
	 * The {@link Suppressor} for the current set of objects
	 */
	private Suppressor current=Suppressor.NOP;
	/**
	 * The objects that are in some way suppressed
	 */
	private Collection<? extends E> suppressThese;
	/**
	 * Whether the objects are actually suppressed
	 */
	protected boolean state;
	/**
	 * How to suppress the objects
	 */
	private final Function<? super E, ? extends Suppressor> method;
	/**
	 * Make a new {@link SuppressionSwitcher} that uses the specified method to suppress
	 * @param method
	 */
	protected SuppressionSwitcher(Function<? super E, ? extends Suppressor> method) {
		Objects.requireNonNull(method);
		this.method=method;
	}
	/**
	 * Test whether the objects are/should be actually suppressed
	 * @return
	 */
	public boolean getSuppressedState() {
		return state;
	}
	/**
	 * Change whether the objects are/should be actually suppressed without changing the objects
	 * @param b
	 * @return {@code this}
	 */
	public synchronized SuppressionSwitcher<E> setSuppressedState(boolean b) {
		if(b==state)
			return this;
		if(b) {
			suppress();
		}else {
			current.release();
		}
		state=b;
		return this;
	}
	/**
	 * Change the suppressed objects without changing whether the objects are/should be actually suppressed.
	 * Any previously suppressed objects are released after the new {@link Suppressor}s have been created.
	 * <br>If {@code these} equals the currently suppressed collection of objects, nothing is done. 
	 * Call this method only if you're exclusively using collections that are not modified.
	 * @param these
	 * @return {@code this}
	 * 
	 */
	public synchronized SuppressionSwitcher<E> setSuppressedItems(Collection<? extends E> these) {
		return setSuppressedItems(these, true);
	}
	/**
	 * Change the suppressed objects without changing whether the objects are/should be actually suppressed.
	 * Any previously suppressed objects are released after the new {@link Suppressor}s have been created.
	 * @param these
	 * @param equalsTest if this is <code>true</code> and {@code these} equals the currently suppressed
	 * collection of objects, nothing is done. Set this to <code>true</code> omly if you're exclusively 
	 * using collections
	 * that are not modified.
	 * @return {@code this}
	 */
	public synchronized SuppressionSwitcher<E> setSuppressedItems(Collection<? extends E> these, boolean equalsTest) {
		if(equalsTest && Objects.equals(these, suppressThese))
			return this;
		suppressThese=these;
		if(state) {
			suppress();
		}
		return this;
	}
	/**
	 * Change the suppressed objects without changing whether the objects are/should be actually suppressed.
	 * Any previously suppressed objects are released after the new {@link Suppressor}s have been created.
	 * <br>If {@code these} as a list equals the currently suppressed collection of objects, nothing is done. 
	 * Call this method only if you're exclusively using collections that are not modified.
	 * @param these
	 * @see Arrays#asList(Object...)
	 * @return {@code this}
	 */
	@SafeVarargs
	public final synchronized SuppressionSwitcher<E> setSuppressedItems(E... these) {
		return setSuppressedItems(Arrays.asList(these));
	}
	/**
	 * Do not suppress any objects, without changing whether future objects should be suppressed.
	 * @return {@code this}
	 */
	public synchronized SuppressionSwitcher<E> setSuppressedItems() {
		suppressThese=null;
		current.release();
		return this;
	}
	/**
	 * Do not suppress any objects, and change whether future objects should be suppressed.
	 * @return {@code this}
	 */
	public synchronized SuppressionSwitcher<E> setSuppressed(boolean newState) {
		suppressThese=null;
		current.release();
		state=newState;
		return this;
	}
	/**
	 * Change the suppressed objects without changing whether the objects are/should be actually suppressed.
	 * Any previously suppressed objects are released after the new {@link Suppressor}s have been created.
	 * <br>If {@code that} as a singleton equals the currently suppressed collection of objects, nothing is done. 
	 * Call this method only if you're exclusively using collections that are not modified.
	 * @param that (Possibly) suppress only this object
	 * @see Collections#singleton(Object)
	 * @return {@code this}
	 */
	public synchronized SuppressionSwitcher<E> setSuppressedItems(E that) {
		if(that==null) {
			suppressThese=null;
			current.release();
		}else {
			if(suppressThese!=null && suppressThese.size()==1 && suppressThese.iterator().next()==that)
				return this;
			return setSuppressedItems((Collection<? extends E>)Collections.singleton(that));
		}
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
	public synchronized SuppressionSwitcher<E> setSuppressed(boolean newState, Collection<? extends E> these) {
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
	public synchronized SuppressionSwitcher<E> setSuppressed(boolean newState, Collection<? extends E> these, boolean equalsTest) {
		if(equalsTest && Objects.equals(these, suppressThese))
			return setSuppressedState(newState);
		suppressThese=these;
		if(newState) {
			suppress();	
		}else {
			current.release();
		}
		state=newState;
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
	public final synchronized SuppressionSwitcher<E> setSuppressed(boolean newState, E... these) {
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
	public synchronized SuppressionSwitcher<E> setSuppressed(boolean newState, E that) {
		if(that==null) {
			suppressThese=null;
			current.release();
			state=newState;
		}else {
			if(suppressThese!=null && suppressThese.size()==1 && suppressThese.iterator().next()==that)
				return setSuppressedState(newState);
			return setSuppressed(newState, (Collection<? extends E>)Collections.singleton(that));
		}
		return this;

	}
	
	/**
	 * Adjust the suppression status.
	 */
	private void suppress() {
		if(suppressThese==null || suppressThese.isEmpty())
			current.release();
		else if(suppressThese.size()==1) {
			Suppressor ns = method.apply(suppressThese.iterator().next()).wrapWeak();
			current.release();
			current=ns;
		}else {
			Suppressor ns = Suppressor.many(method, suppressThese).wrapWeak();
			current.release();
			current=ns;
		}
	}

}
