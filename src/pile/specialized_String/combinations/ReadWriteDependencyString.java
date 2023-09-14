package pile.specialized_String.combinations;

import pile.specialized_Comparable.combinations.ReadWriteDependencyComparable;

public interface ReadWriteDependencyString extends 
ReadWriteValueString, 
ReadDependencyString,
ReadWriteDependencyComparable<String>
{
	@Override public default ReadWriteDependencyString setNull() {
		set(null);
		return this;
	}

}
