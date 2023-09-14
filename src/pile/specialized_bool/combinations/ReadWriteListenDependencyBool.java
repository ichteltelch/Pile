package pile.specialized_bool.combinations;

import pile.aspect.combinations.ReadWriteListenDependency;

public interface ReadWriteListenDependencyBool extends 
ReadWriteListenValueBool, 
ReadListenDependencyBool,
ReadWriteDependencyBool,
ReadWriteListenDependency<Boolean>{
	public default ReadWriteListenDependencyBool setNull() {
		set(null);
		return this;
	}
}
