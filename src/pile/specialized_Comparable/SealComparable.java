package pile.specialized_Comparable;

import pile.impl.SealPile;
import pile.specialized_Comparable.combinations.ReadWriteListenDependencyComparable;

public class SealComparable<E extends Comparable<? super E>>
extends SealPile<E> 
implements ReadWriteListenDependencyComparable<E>,
PileComparable<E>{
	@Override
	public SealComparable<E> setName(String name) {
		avName=name;
		return this;
	}

	@Override public SealComparable<E> setNull() {
		set(null);
		return this;
	}

}
