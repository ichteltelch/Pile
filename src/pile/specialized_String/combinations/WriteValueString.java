package pile.specialized_String.combinations;

import pile.specialized_Comparable.combinations.WriteValueComparable;

public interface WriteValueString extends WriteValueComparable<String> {
	/**
	 * Set the value to an empty string.
	 */
	default void setEmpty() {
		set("");
	}
	@Override public default WriteValueString setNull() {
		set(null);
		return this;
	}

}
