package pile.specialized_bool.combinations;

import pile.aspect.combinations.Pile;
import pile.aspect.combinations.ReadListenDependency;
import pile.aspect.combinations.ReadWriteListenDependency;
import pile.impl.Piles;
import pile.impl.SealPile;
import pile.specialized_bool.SealBool;

public interface ReadWriteListenDependencyBool extends 
ReadWriteListenValueBool, 
ReadListenDependencyBool,
ReadWriteDependencyBool,
ReadWriteListenDependency<Boolean>{
	public default ReadWriteListenDependencyBool setNull() {
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
	public default SealBool fallback(Boolean v){
		return Piles.fallback(this, v);
	}
}
