package pile.specialized_Comparable;

import pile.impl.PileImpl;

public class PileComparableImpl<E extends Comparable<? super E>> 
extends PileImpl<E>
implements PileComparable<E>{
	@Override
	public PileComparableImpl<E> setName(String name) {
		avName=name;
		return this;
	}

	@Override public PileComparableImpl<E> setNull() {
		set(null);
		return this;
	}

}
