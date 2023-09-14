package pile.specialized_Comparable.combinations;

import pile.aspect.WriteValue;

public interface WriteValueComparable<E extends Comparable<? super E>> extends WriteValue<E> {
	@Override public default WriteValueComparable<E> setNull() {
		set(null);
		return this;
	}

}
