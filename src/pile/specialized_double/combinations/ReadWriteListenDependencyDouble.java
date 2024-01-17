package pile.specialized_double.combinations;

import pile.aspect.combinations.Pile;
import pile.aspect.combinations.ReadListenDependency;
import pile.impl.Piles;
import pile.impl.SealPile;
import pile.specialized_Comparable.combinations.ReadWriteListenDependencyComparable;
import pile.specialized_double.SealDouble;

public interface ReadWriteListenDependencyDouble extends 
ReadWriteListenValueDouble, 
ReadListenDependencyDouble,
ReadWriteDependencyDouble,
ReadWriteListenDependencyComparable<Double>{
	@Override default ReadWriteListenDependencyDouble setNull() {
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
	public default SealDouble fallback(Double v){
		return Piles.fallback(this, v);
	}
}
