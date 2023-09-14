package pile.specialized_Comparable.combinations;

import pile.aspect.combinations.ReadWriteDependency;

public interface ReadWriteDependencyComparable<E extends Comparable<? super E>> extends 
ReadWriteValueComparable<E>, 
ReadDependencyComparable<E>,
ReadWriteDependency<E>
{
	@Override public default ReadWriteDependencyComparable<E> setNull() {
		set(null);
		return this;
	}

}
