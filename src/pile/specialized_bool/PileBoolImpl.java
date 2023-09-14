package pile.specialized_bool;

import pile.impl.PileImpl;
import pile.specialized_bool.combinations.ReadWriteListenDependencyBool;

public class PileBoolImpl 
extends PileImpl<Boolean>
implements PileBool{
	@Override
	public PileBoolImpl setName(String name) {
		avName=name;
		return this;
	}

	@Override
	public PileBoolImpl setNull() {
		set(null);
		return this;
	}
	private volatile ReadWriteListenDependencyBool not;
	@Override
	public ReadWriteListenDependencyBool not() {
		ReadWriteListenDependencyBool localRef = not;
		if (localRef == null) {
			ReadWriteListenDependencyBool newRef = PileBool.super.not();
			synchronized (mutex) {
				localRef = not;
				if (localRef == null) {
					localRef = newRef;
					not = localRef;
				}
			}
		}
		return localRef;

	}

}
