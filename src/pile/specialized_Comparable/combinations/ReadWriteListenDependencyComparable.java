package pile.specialized_Comparable.combinations;

import pile.aspect.combinations.ReadWriteListenDependency;

public interface ReadWriteListenDependencyComparable<E extends Comparable<? super E>> extends 
ReadWriteListenValueComparable<E>, 
ReadListenDependencyComparable<E>,
ReadWriteDependencyComparable<E>,
ReadWriteListenDependency<E>{
	@Override public default ReadWriteListenDependencyComparable<E> setNull() {
		set(null);
		return this;
	}

}
