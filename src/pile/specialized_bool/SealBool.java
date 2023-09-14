package pile.specialized_bool;

import pile.impl.SealPile;
import pile.specialized_bool.combinations.ReadWriteListenDependencyBool;

public class SealBool 
extends SealPile<Boolean> 
implements ReadWriteListenDependencyBool,
PileBool{
	@Override
	public SealBool setName(String name) {
		avName=name;
		return this;
	}

	@Override
	public SealBool setNull() {
		set(null);
		return this;
	}

}
