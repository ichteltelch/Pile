package pile.specialized_String;

import pile.specialized_Comparable.SealComparable;
import pile.specialized_String.combinations.ReadWriteListenDependencyString;

public class SealString
extends SealComparable<String> 
implements ReadWriteListenDependencyString,
PileString{
	@Override
	public SealString setName(String name) {
		avName=name;
		return this;
	}
	@Override public SealString setNull() {
		set(null);
		return this;
	}

}
