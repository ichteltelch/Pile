package pile.specialized_double.combinations;

import pile.specialized_Comparable.combinations.ReadWriteValueComparable;

public interface ReadWriteValueDouble extends ReadValueDouble, WriteValueDouble, ReadWriteValueComparable<Double>{
	/**
	 * flip the sign of the held value
	 */
	default void flip() {
		Double v = get();
		if(v==null)
			return;
		set(-v);
	}
	@Override default ReadWriteValueDouble setNull() {
		set(null);
		return this;
	}

}
