package pile.specialized_Comparable.combinations;

import pile.aspect.combinations.ReadWriteListenValue;

public interface ReadWriteListenValueComparable<E extends Comparable<? super E>> extends 
ReadListenValueComparable<E>, 
ReadWriteValueComparable<E>,
ReadWriteListenValue<E>
{
	@Override public default ReadWriteListenValueComparable<E> setNull() {
		set(null);
		return this;
	}

}
