package pile.specialized_String.combinations;

import pile.impl.Piles;
import pile.specialized_Comparable.combinations.ReadWriteListenDependencyComparable;
import pile.specialized_String.PileString;
import pile.specialized_String.SealString;

public interface ReadWriteListenDependencyString extends 
ReadWriteListenValueString, 
ReadListenDependencyString,
ReadWriteDependencyString,
ReadWriteListenDependencyComparable<String>{
	@Override public default ReadWriteListenDependencyString setNull() {
		set(null);
		return this;
	}
	/**
	 * Delegates to {@link PileString#nullableWrapper(ReadWriteListenDependencyString)}.
	 * @return
	 */
	default SealString nullableWrapper() {
		return PileString.nullableWrapper(this);
	}
	/**
	 * Make a {@link Pile} that takes on the given constant value whenever 
	 * this {@link ReadListenDependency} is invalid.
	 * Writes to the returned {@link SealPile} will be redirected to this reactive value.
	 * @param v
	 * @return
	 */
	public default SealString fallback(String v){
		return Piles.fallback(this, v);
	}
}
