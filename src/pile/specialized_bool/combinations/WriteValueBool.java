package pile.specialized_bool.combinations;

import pile.aspect.WriteValue;

public interface WriteValueBool extends WriteValue<Boolean> {
	default void setFalse() {
		set(Boolean.FALSE);
	}
	default void setTrue() {
		set(Boolean.TRUE);
	}
}
