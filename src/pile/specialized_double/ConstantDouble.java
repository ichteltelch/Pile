package pile.specialized_double;

import pile.specialized_Comparable.ConstantComparable;
import pile.specialized_double.combinations.ReadWriteListenDependencyDouble;

public class ConstantDouble extends ConstantComparable<Double> implements ReadWriteListenDependencyDouble {
	public ConstantDouble(Double init) {super(init);}		
	@Override public ConstantDouble setNull() {return this;}

}