package pile.specialized_String;

import pile.specialized_Comparable.ConstantComparable;
import pile.specialized_String.combinations.ReadWriteListenDependencyString;

public class ConstantString extends ConstantComparable<String> implements ReadWriteListenDependencyString {
	public ConstantString(String init) {super(init);}
	@Override public ConstantString setNull() {return this;}

}