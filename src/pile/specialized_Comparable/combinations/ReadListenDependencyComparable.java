package pile.specialized_Comparable.combinations;

import pile.aspect.combinations.ReadListenDependency;

public interface ReadListenDependencyComparable<E extends Comparable<? super E>> 
extends ReadListenDependency<E>, ReadListenValueComparable<E>, ReadDependencyComparable<E>{

}
