package pile.specialized_String.combinations;

import pile.aspect.Dependency;
import pile.impl.Piles;
import pile.specialized_Comparable.combinations.ReadDependencyComparable;
import pile.specialized_String.PileStringImpl;
import pile.specialized_String.SealString;

public interface ReadDependencyString extends ReadValueString, Dependency, ReadDependencyComparable<String>{
	public default SealString readOnly(){
		return Piles.makeReadOnlyWrapper(this, new SealString());
	}

	public default PileStringImpl overridable() {
		return Piles.computeString(this).name(dependencyName()+"*").whenChanged(this);
	}

}
