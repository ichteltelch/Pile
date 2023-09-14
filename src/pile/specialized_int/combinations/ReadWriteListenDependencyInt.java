package pile.specialized_int.combinations;

import pile.specialized_Comparable.combinations.ReadWriteListenDependencyComparable;

public interface ReadWriteListenDependencyInt extends 
ReadWriteListenValueInt, 
ReadListenDependencyInt,
ReadWriteDependencyInt,
ReadWriteListenDependencyComparable<Integer>{
	@Override default ReadWriteListenDependencyInt setNull() {
		set(null);
		return this;
	}

}
