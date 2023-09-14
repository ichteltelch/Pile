package pile.specialized_Comparable;

import pile.impl.Constant;
import pile.specialized_Comparable.combinations.ReadWriteListenDependencyComparable;

public class ConstantComparable<E extends Comparable<? super E>> extends Constant<E> implements ReadWriteListenDependencyComparable<E> {
	public ConstantComparable(E init) {super(init);}
	@Override public ConstantComparable<E> setNull() {return this;}


}