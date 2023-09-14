package pile.specialized_double.combinations;

import pile.specialized_Comparable.combinations.ReadWriteListenDependencyComparable;

public interface ReadWriteListenDependencyDouble extends 
ReadWriteListenValueDouble, 
ReadListenDependencyDouble,
ReadWriteDependencyDouble,
ReadWriteListenDependencyComparable<Double>{
	@Override default ReadWriteListenDependencyDouble setNull() {
		set(null);
		return this;
	}

}
