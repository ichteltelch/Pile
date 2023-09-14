package pile.specialized_String.combinations;

import pile.impl.Piles;
import pile.specialized_Comparable.combinations.ReadListenDependencyComparable;
import pile.specialized_String.SealString;

public interface ReadListenDependencyString extends ReadListenDependencyComparable<String>, ReadListenValueString, ReadDependencyString{
	public default SealString fallback(String v){
		return Piles.fallback(this, v);
	}
}
