package pile.specialized_int;

import pile.specialized_Comparable.SealComparable;
import pile.specialized_int.combinations.ReadWriteListenDependencyInt;

public class SealInt
extends SealComparable<Integer> 
implements ReadWriteListenDependencyInt,
PileInt{
	@Override
	public SealInt setName(String name) {
		avName=name;
		return this;
	}

	@Override public SealInt setNull() {
		set(null);
		return this;
	}

}
