package pile.specialized_int;

import pile.specialized_Comparable.PileComparableImpl;

public class PileIntImpl 
extends PileComparableImpl<Integer>
implements PileInt{
	@Override
	public PileIntImpl setName(String name) {
		avName=name;
		return this;
	}

	@Override public PileIntImpl setNull() {
		set(null);
		return this;
	}

}
