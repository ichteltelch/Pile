package pile.specialized_int;

import pile.specialized_Comparable.ConstantComparable;
import pile.specialized_int.ConstantInt;
import pile.specialized_int.combinations.ReadWriteListenDependencyInt;

public class ConstantInt extends ConstantComparable<Integer> implements ReadWriteListenDependencyInt {
	public ConstantInt(Integer init) {super(init);}
	@Override public ConstantInt setNull() {return this;}

}