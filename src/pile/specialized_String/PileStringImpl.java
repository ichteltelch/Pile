package pile.specialized_String;

import pile.specialized_Comparable.PileComparableImpl;

public class PileStringImpl 
extends PileComparableImpl<String>
implements PileString{
	@Override
	public PileStringImpl setName(String name) {
		avName=name;
		return this;
	}

	@Override public PileStringImpl setNull() {
		set(null);
		return this;
	}

}
