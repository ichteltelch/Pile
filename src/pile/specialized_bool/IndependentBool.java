package pile.specialized_bool;

import pile.impl.Independent;
import pile.specialized_bool.combinations.ReadWriteListenDependencyBool;

public class IndependentBool extends 
Independent<Boolean> 
implements 
ReadWriteListenDependencyBool{

	public IndependentBool(Boolean init) {
		super(init);
	}
	@Override
	public IndependentBool setName(String name) {
		super.setName(name);
		return this;
	}

	@Override
	public IndependentBool setNull() {
		set(null);
		return this;
	}

	
	private volatile ReadWriteListenDependencyBool not;
	@Override
	public ReadWriteListenDependencyBool not() {
		ReadWriteListenDependencyBool localRef = not;
		if (localRef == null) {
			ReadWriteListenDependencyBool newNot = ReadWriteListenDependencyBool.super.not();
			synchronized (mutex) {
				localRef = not;
				if (localRef == null) {
					localRef = newNot;
					not = localRef;
				}
			}
		}
		return localRef;

	}


}
