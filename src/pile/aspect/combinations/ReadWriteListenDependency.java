package pile.aspect.combinations;

import pile.aspect.Dependency;
import pile.aspect.ReadValue;
import pile.aspect.WriteValue;
import pile.aspect.listen.ListenValue;
import pile.impl.DebugCallback;
import pile.impl.Piles;
import pile.impl.SealPile;
import pile.interop.debug.DebugEnabled;

/**
 * Combination of {@link ReadValue}, {@link WriteValue}, {@link ListenValue} and {@link Dependency}
 * @author bb
 *
 * @param <E>
 */
public interface ReadWriteListenDependency<E> extends 
ReadWriteListenValue<E>, 
ReadListenDependency<E>,
ReadWriteDependency<E>{
	/**
	 * Set a callback that can be used to monitor what's going on with this object for debugging purposes.
	 * @see DebugEnabled#DE
	 * @param dc
	 */
	public void _setDebugCallback(DebugCallback dc);
	@Override E set(E v);
	@Override public default ReadWriteListenDependency<E> setNull() {
		set(null);
		return this;
	}
	/**
	 * Make a {@link Pile} that takes on the given constant value whenever 
	 * this {@link ReadListenDependency} is invalid.
	 * Writes to the returned {@link SealPile} will be redirected to this reactive value.
	 * @param v
	 * @return
	 */
	public default SealPile<E> fallback(E v){
		return Piles.fallback(this, v);
	}
}
