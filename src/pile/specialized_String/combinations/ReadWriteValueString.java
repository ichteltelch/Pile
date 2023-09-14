package pile.specialized_String.combinations;

import pile.specialized_Comparable.combinations.ReadWriteValueComparable;

public interface ReadWriteValueString extends ReadValueString, WriteValueString, ReadWriteValueComparable<String>{
	@Override public default ReadWriteValueString setNull() {
		set(null);
		return this;
	}

}
