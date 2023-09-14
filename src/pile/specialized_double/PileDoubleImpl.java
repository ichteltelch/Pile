package pile.specialized_double;

import pile.specialized_Comparable.PileComparableImpl;

public class PileDoubleImpl 
extends PileComparableImpl<Double>
implements PileDouble{
	@Override
	public PileDoubleImpl setName(String name) {
		avName=name;
		return this;
	}

	@Override
	public PileDoubleImpl setNull() {
		set(null);
		return this;
	}
	

}
