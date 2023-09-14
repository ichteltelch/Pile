package pile.specialized_double.combinations;

import pile.aspect.Dependency;
import pile.aspect.WriteValue;
import pile.aspect.combinations.ReadDependency;
import pile.impl.Piles;
import pile.specialized_Comparable.combinations.ReadDependencyComparable;
import pile.specialized_double.PileDouble;
import pile.specialized_double.PileDoubleImpl;
import pile.specialized_double.SealDouble;
import pile.specialized_int.SealInt;

public interface ReadDependencyDouble extends ReadValueDouble, Dependency, ReadDependencyComparable<Double>{
	/**
	 * Make a reactive integer that holds the result of rounding this reactive double.
	 * @see Math#round(double)
	 * @return
	 */
	public default SealInt round() {
		return mapToInt(i -> i==null?null:(int)Math.round(i.doubleValue()));
	}
	/** 
	 * Delegates to {@link PileDouble#negativeRO()}
	 * Subclasses that also implement {@link WriteValue} should delegate
	 * to {@link PileDoubleImpl#negativeRW()} instead.
	 */
	public default SealDouble negative() {
		return PileDouble.negativeRO(this);
	}
	/** Delegates to {@link PileDouble#negativeRO()} */
	public default SealDouble negativeRO() {
		return PileDouble.negativeRO(this);
	}
	/**
	 * Delegates to {@link PileDouble#inverseRO()}
	 * Subclasses that also implement {@link WriteValue} should delegate to 
	 * {@link PileDouble#negativeRW()} instead.
	 * @return
	 */
	public default SealDouble inverse() {
		return PileDouble.inverseRO(this);
	}
	/** Delegates to {@link PileDouble#inverseRO()} */
	public default SealDouble inverseRO() {
		return PileDouble.inverseRO(this);
	}
	/** Delegates to {@link PileDouble#add(ReadDependency, ReadDependency)} */
	public default SealDouble plus(ReadDependency<? extends Number> op2) {
		return PileDouble.add(this, op2);
	}
	/** Delegates to {@link PileDouble#subtract(ReadDependency, ReadDependency)} */
	public default SealDouble minus(ReadDependency<? extends Number> op2) {
		return PileDouble.subtract(this, op2);
	}
	/** Delegates to {@link PileDouble#multiply(ReadDependency, ReadDependency)} */
	public default SealDouble times(ReadDependency<? extends Number> op2) {
		return PileDouble.multiply(this, op2);
	}
	/** Delegates to {@link PileDouble#divide(ReadDependency, ReadDependency)} */
	public default SealDouble over(ReadDependency<? extends Number> op2) {
		return PileDouble.divide(this, op2);
	}
	/** 
	 * Delegates to {@link PileDouble#addRO(ReadDependency, double)}
	 * Subclasses that also implement {@link WriteValue} should delegate to 
	 * {@link PileDouble#addRW(ReadDependency, double)} instead.
	 */
	public default SealDouble plus(double op2) {
		return PileDouble.addRO(this, op2);
	}
	/**
	 * Delegates to {@link PileDouble#subtractRO(ReadDependency, double)}
	 * Subclasses that also implement {@link WriteValue} should delegate to 
	 * {@link PileDouble#subtractRW(ReadDependency, double)} instead.
	 * @param op2
	 * @return
	 */
	public default SealDouble minus(double op2) {
		return PileDouble.subtractRO(this, op2);
	}
	/**
	 * Delegates to {@link PileDouble#multiplyRO(ReadDependency, double)}
	 * Subclasses that also implement {@link WriteValue} should delegate to 
	 * {@link PileDouble#multiplyRW(ReadDependency, double)} instead.
	 * @param op2
	 * @return
	 */
	public default SealDouble times(double op2) {
		return PileDouble.multiplyRO(this, op2);
	}
	/**
	 * Delegates to {@link PileDouble#divideRO(ReadDependency, double)}
	 * Subclasses that also implement {@link WriteValue} should delegate to 
	 * {@link PileDouble#divideRW(ReadDependency, double)} instead.
	 * @param op2
	 * @return
	 */
	public default SealDouble over(double op2) {
		return PileDouble.divideRO(this, op2);
	}
	/** Delegates to {@link PileDouble#addRO(ReadDependency, double)} */
	public default SealDouble plusRO(double op2) {
		return PileDouble.addRO(this, op2);
	}
	/** Delegates to {@link PileDouble#subtractRO(ReadDependency, double)} */
	public default SealDouble minusRO(double op2) {
		return PileDouble.subtractRO(this, op2);
	}
	/** Delegates to {@link PileDouble#multiplyRO(ReadDependency, double)} */
	public default SealDouble timesRO(double op2) {
		return PileDouble.multiplyRO(this, op2);
	}
	/** Delegates to {@link PileDouble#divideRO(ReadDependency, double)} */
	public default SealDouble overRO(double op2) {
		return PileDouble.divideRO(this, op2);
	}
	
	/**
	 * Delegates to {@link PileDouble#min(ReadDependency, ReadDependency)}
	 * @param op2
	 * @return
	 */
	public default SealDouble min(ReadDependency<? extends Number> op2) {
		return PileDouble.min(this, op2);
	}
	/**
	 * Delegates to {@link PileDouble#max(ReadDependency, ReadDependency)}
	 * @param op2
	 * @return
	 */
	public default SealDouble max(ReadDependency<? extends Number> op2) {
		return PileDouble.max(this, op2);
	}
	/**
     * Delegates to {@link PileDouble#min(ReadDependency, double)}
     * @param op2
     * @return
     */
	public default SealDouble min(double op2) {
		return PileDouble.min(this, op2);
	}
	/**
	 * Delegates to {@link PileDouble#max(ReadDependency, double)}
	 * @param op2
	 * @return
	 */
	public default SealDouble max(double op2) {
		return PileDouble.max(this, op2);
	}
	public default SealDouble readOnly(){
		return Piles.makeReadOnlyWrapper(this, new SealDouble());
	}

	public default PileDoubleImpl overridable() {
		return Piles.computeDouble(this).name(dependencyName()+"*").whenChanged(this);
	}
}
