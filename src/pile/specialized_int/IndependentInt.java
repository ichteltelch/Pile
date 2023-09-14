package pile.specialized_int;

import pile.specialized_Comparable.IndependentComparable;
import pile.specialized_int.combinations.ReadWriteListenDependencyInt;

public class IndependentInt extends 
IndependentComparable<Integer> 
implements 
ReadWriteListenDependencyInt{

	public IndependentInt(Integer init) {
		super(init);
	}
	@Override
	public IndependentInt setName(String name) {
		super.setName(name);
		return this;
	}

	@Override public IndependentInt setNull() {
		set(null);
		return this;
	}
	
//	@Override public IndependentInt validBuffer() {return this;}
//	@Override public IndependentInt validBuffer_memo() {return this;}

}
