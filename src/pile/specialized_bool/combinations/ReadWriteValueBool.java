package pile.specialized_bool.combinations;

import pile.aspect.combinations.ReadWriteValue;

public interface ReadWriteValueBool extends ReadValueBool, WriteValueBool, ReadWriteValue<Boolean>{
	/**
	 * Flip the currently held value.
	 * Not thread safe.
	 */
	default void flip() {
		Boolean v = get();
		if(v==null)
			return;
		set(!v);
	}
}
