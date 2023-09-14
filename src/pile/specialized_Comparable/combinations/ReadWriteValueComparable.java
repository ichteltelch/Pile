package pile.specialized_Comparable.combinations;

import pile.aspect.combinations.ReadWriteValue;

public interface ReadWriteValueComparable<E extends Comparable<? super E>>
extends ReadValueComparable<E>, WriteValueComparable<E>, ReadWriteValue<E>{
	@Override public default ReadWriteValueComparable<E> setNull() {
		set(null);
		return this;
	}

}
