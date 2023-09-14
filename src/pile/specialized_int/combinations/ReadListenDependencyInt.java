package pile.specialized_int.combinations;

import pile.impl.Piles;
import pile.specialized_Comparable.combinations.ReadListenDependencyComparable;
import pile.specialized_int.SealInt;

public interface ReadListenDependencyInt extends ReadListenDependencyComparable<Integer>, ReadListenValueInt, ReadDependencyInt{
	public default SealInt fallback(Integer v){
		return Piles.fallback(this, v);
	}
}
