package pile.specialized_double.combinations;

import pile.specialized_Comparable.combinations.WriteValueComparable;

public interface WriteValueDouble extends WriteValueComparable<Double> {
	default void setZero() {
		set(0.0);
	}
	default void setOne() {
		set(1.0);
	}
	default void setPositiveInfinte() {
		set(Double.POSITIVE_INFINITY);
	}
	default void setNegativeInfinite() {
		set(Double.NEGATIVE_INFINITY);
	}
	@Override default WriteValueDouble setNull() {
		set(null);
		return this;
	}

}
