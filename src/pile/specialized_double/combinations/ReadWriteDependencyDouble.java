package pile.specialized_double.combinations;

import pile.specialized_Comparable.combinations.ReadWriteDependencyComparable;
import pile.specialized_double.PileDouble;
import pile.specialized_double.SealDouble;

public interface ReadWriteDependencyDouble extends 
ReadWriteValueDouble, 
ReadDependencyDouble,
ReadWriteDependencyComparable<Double>
{
	/** Delegates to {@link PileDouble#negativeRW(pile.aspect.combinations.ReadWriteDependency)}) */
	default public SealDouble negative() {
		return PileDouble.negativeRW(this);
	}
	/** Delegates to {@link PileDouble#inverseRW(pile.aspect.combinations.ReadWriteDependency)} */
	default public SealDouble inverse() {
		return PileDouble.inverseRW(this);
	}
	/** Delegates to {@link PileDouble#negativeRW(pile.aspect.combinations.ReadWriteDependency)} */
	default public SealDouble negativeRW() {
		return PileDouble.negativeRW(this);
	}
	/** Delegates to {@link PileDouble#inverseRW(pile.aspect.combinations.ReadWriteDependency)} */
	default public SealDouble inverseRW() {
		return PileDouble.inverseRW(this);
	}
	@Override default ReadWriteDependencyDouble setNull() {
		set(null);
		return this;
	}
	/** Delegates to {@link PileDouble#addRW(pile.aspect.combinations.ReadWriteDependency, double)} */
	public default SealDouble plus(double op2) {
		return PileDouble.addRW(this, op2);
	}
	/** Delegates to {@link PileDouble#subtractRW(pile.aspect.combinations.ReadWriteDependency, double)} */
	public default SealDouble minus(double op2) {
		return PileDouble.subtractRW(this, op2);
	}
	/** Delegates to {@link PileDouble#multiplyRW(pile.aspect.combinations.ReadWriteDependency, double)} */
	public default SealDouble times(double op2) {
		return PileDouble.multiplyRW(this, op2);
	}
	/** Delegates to {@link PileDouble#divideRW(pile.aspect.combinations.ReadWriteDependency, double)} */
	public default SealDouble over(double op2) {
		return PileDouble.divideRW(this, op2);
	}
	/** Delegates to {@link PileDouble#addRW(pile.aspect.combinations.ReadWriteDependency, double)} */
	public default SealDouble plusRW(double op2) {
		return PileDouble.addRW(this, op2);
	}
	/** Delegates to {@link PileDouble#subtractRW(pile.aspect.combinations.ReadWriteDependency, double)} */
	public default SealDouble minusRW(double op2) {
		return PileDouble.subtractRW(this, op2);
	}
	/** Delegates to {@link PileDouble#multiplyRW(pile.aspect.combinations.ReadWriteDependency, double)} */
	public default SealDouble timesRW(double op2) {
		return PileDouble.multiplyRW(this, op2);
	}
	/** Delegates to {@link PileDouble#divideRW(pile.aspect.combinations.ReadWriteDependency, double)} */
	public default SealDouble overRW(double op2) {
		return PileDouble.divideRW(this, op2);
	}
}
