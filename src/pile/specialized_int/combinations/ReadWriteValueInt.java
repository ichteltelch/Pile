package pile.specialized_int.combinations;

import pile.specialized_Comparable.combinations.ReadWriteValueComparable;

public interface ReadWriteValueInt extends ReadValueInt, WriteValueInt, ReadWriteValueComparable<Integer>{
	/**
	 * flip the sign of the held value
	 */
	default void flip() {
		Integer v = get();
		if(v==null)
			return;
		set(-v);
	}
	@Override default ReadWriteValueInt setNull() {
		set(null);
		return this;
	}

}
