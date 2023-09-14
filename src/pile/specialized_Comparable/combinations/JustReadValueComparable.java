package pile.specialized_Comparable.combinations;

import pile.aspect.JustReadValue;

public interface JustReadValueComparable<E extends Comparable<? super E>> extends ReadValueComparable<E>, JustReadValue<E>{

}
