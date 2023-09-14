package pile.specialized_double;

import pile.specialized_Comparable.IndependentComparable;
import pile.specialized_double.combinations.ReadWriteListenDependencyDouble;

public class IndependentDouble extends 
IndependentComparable<Double> 
implements 
ReadWriteListenDependencyDouble{

	public IndependentDouble(Double init) {
		super(init);
	}
	@Override
	public IndependentDouble setName(String name) {
		super.setName(name);
		return this;
	}

	@Override
	public IndependentDouble setNull() {
		set(null);
		return this;
	}
//	@Override public IndependentDouble validBuffer() {return this;}
//	@Override public IndependentDouble validBuffer_memo() {return this;}
}
