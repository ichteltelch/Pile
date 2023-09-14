package pile.specialized_double.combinations;

import pile.impl.Piles;
import pile.specialized_Comparable.combinations.ReadListenDependencyComparable;
import pile.specialized_double.SealDouble;

public interface ReadListenDependencyDouble extends ReadListenDependencyComparable<Double>, ReadListenValueDouble, ReadDependencyDouble{
	public default SealDouble fallback(Double v){
		return Piles.fallback(this, v);
	}
}
