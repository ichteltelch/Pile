package pile.specialized_int.combinations;

import pile.specialized_Comparable.combinations.WriteValueComparable;

public interface WriteValueInt extends WriteValueComparable<Integer> {
	default void setZero() {
		set(0);
	}
	default void setOne() {
		set(1);
	}
	@Override default WriteValueInt setNull() {
		set(null);
		return this;
	}

}
