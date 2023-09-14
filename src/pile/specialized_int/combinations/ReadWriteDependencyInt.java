package pile.specialized_int.combinations;

import pile.specialized_Comparable.combinations.ReadWriteDependencyComparable;
import pile.specialized_int.PileInt;
import pile.specialized_int.SealInt;

public interface ReadWriteDependencyInt extends 
ReadWriteValueInt, 
ReadDependencyInt,
ReadWriteDependencyComparable<Integer>
{
	/** Delegates to {@link PileInt#negativeRW(pile.aspect.combinations.ReadWriteDependency)}) */
	default public SealInt negative() {
		return PileInt.negativeRW(this);
	}
	/** Delegates to {@link PileInt#negativeRW(pile.aspect.combinations.ReadWriteDependency)}) */
	public default SealInt negativeRW() {
		return PileInt.negativeRW(this);
	}
	@Override default ReadWriteDependencyInt setNull() {
		set(null);
		return this;
	}

	/** Delegates to {@link PileInt#addRW(pile.aspect.combinations.ReadWriteDependency, int)} */
	public default SealInt plus(int op2) {
		return PileInt.addRW(this, op2);
	}
	/** Delegates to {@link PileInt#subtractRW(pile.aspect.combinations.ReadWriteDependency, int)} */
	public default SealInt minus(int op2) {
		return PileInt.subtractRW(this, op2);
	}

	/** Delegates to {@link PileInt#addRW(pile.aspect.combinations.ReadWriteDependency, int)} */
	public default SealInt plusRW(int op2) {
		return PileInt.addRW(this, op2);
	}
	/** Delegates to {@link PileInt#subtractRW(pile.aspect.combinations.ReadWriteDependency, int)} */
	public default SealInt minusRW(int op2) {
		return PileInt.subtractRW(this, op2);
	}

}
