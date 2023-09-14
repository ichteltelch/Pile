package pile.specialized_String;

import pile.specialized_Comparable.IndependentComparable;
import pile.specialized_String.combinations.ReadWriteListenDependencyString;

public class IndependentString extends 
IndependentComparable<String> 
implements 
ReadWriteListenDependencyString{

	public IndependentString(String init) {
		super(init);
	}
	@Override
	public IndependentString setName(String name) {
		super.setName(name);
		return this;
	}


	@Override public IndependentString setNull() {
		set(null);
		return this;
	}
//	@Override public IndependentString validBuffer() {return this;}
//	@Override public IndependentString validBuffer_memo() {return this;}

}
