package pile.specialized_Comparable;

import pile.impl.Independent;
import pile.specialized_Comparable.combinations.ReadWriteListenDependencyComparable;

public class IndependentComparable<E extends Comparable<? super E>> extends 
Independent<E> 
implements 
ReadWriteListenDependencyComparable<E>{

	public IndependentComparable(E init) {
		super(init);
	}
	@Override
	public IndependentComparable<E> setName(String name) {
		super.setName(name);
		return this;
	}


	@Override public IndependentComparable<E> setNull() {
		set(null);
		return this;
	}

}
