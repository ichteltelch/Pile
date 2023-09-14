package pile.specialized_double;

import pile.specialized_Comparable.SealComparable;
import pile.specialized_double.combinations.ReadWriteListenDependencyDouble;

public class SealDouble
extends SealComparable<Double> 
implements ReadWriteListenDependencyDouble,
PileDouble{
	@Override
	public SealDouble setName(String name) {
		avName=name;
		return this;
	}

	@Override
	public SealDouble setNull() {
		set(null);
		return this;
	}
}
