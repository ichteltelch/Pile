package pile.specialized_String.combinations;

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
}
