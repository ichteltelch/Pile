package pile.specialized_Comparable.combinations;

import pile.aspect.Dependency;
import pile.aspect.combinations.ReadDependency;
import pile.builder.PileBuilder;
import pile.impl.Piles;
import pile.specialized_Comparable.PileComparableImpl;
import pile.specialized_Comparable.SealComparable;
import pile.specialized_bool.PileBool;
import pile.specialized_bool.SealBool;
import pile.specialized_int.PileInt;
import pile.specialized_int.SealInt;

public interface ReadDependencyComparable<E extends Comparable<? super E>> extends ReadValueComparable<E>, Dependency, ReadDependency<E>{
	/** Delegates to {@link PileInt#comparison(ReadDependency, ReadDependency, Boolean)} */
	public default SealInt compareTo(ReadDependency<? extends E> op2, Boolean nullIsLess) {
		return PileInt.comparison(this, op2, nullIsLess);
	}
	/** Delegates to {@link PileInt#comparison(ReadDependency, Comparable, Boolean) */
	public default SealInt compareToConst(E op2, Boolean nullIsLess) {
		return PileInt.comparison(this, op2, nullIsLess);
	}
	/** Delegates to {@link PileBool#greaterThan(ReadDependency, ReadDependency, Boolean)} */
	public default SealBool greaterThan(ReadDependency<? extends E> op2, Boolean nullIsLess) {
		return PileBool.greaterThan(this, op2, nullIsLess);
	}
	/** Delegates to {@link PileBool#greaterThan(ReadDependency, ReadDependency, Boolean)} */
	public default SealBool lessThan(ReadDependency<? extends E> op2, Boolean nullIsLess) {
		return PileBool.lessThan(this, op2, nullIsLess);
	}
	/** Delegates to {@link PileBool#lessThanOrEqual(ReadDependency, ReadDependency, Boolean)} */
	public default SealBool lessThanOrEqual(ReadDependency<? extends E> op2, Boolean nullIsLess) {
		return PileBool.lessThanOrEqual(this, op2, nullIsLess);
	}
	/** Delegates to {@link PileBool#greaterThanOrEqual(ReadDependency, ReadDependency, Boolean)} */
	public default SealBool greaterThanOrEqual(ReadDependency<? extends E> op2, Boolean nullIsLess) {
		return PileBool.greaterThanOrEqual(this, op2, nullIsLess);
	}
	/** Delegates to {@link PileBool#greaterThan(ReadDependency, Comparable, Boolean)} */
	public default SealBool greaterThanConst(E op2, Boolean nullIsLess) {
		return PileBool.greaterThan(this, op2, nullIsLess);
	}
	/** Delegates to {@link PileBool#lessThan(ReadDependency, Comparable, Boolean)} */
	public default SealBool lessThanConst(E op2, Boolean nullIsLess) {
		return PileBool.lessThan(this, op2, nullIsLess);
	}
	/** Delegates to {@link PileBool#lessThanOrEqual(ReadDependency, Comparable, Boolean)} */
	public default SealBool lessThanOrEqualConst(E op2, Boolean nullIsLess) {
		return PileBool.lessThanOrEqual(this, op2, nullIsLess);
	}
	/** Delegates to {@link PileBool#greaterThanOrEqual(ReadDependency, Comparable, Boolean)} */
	public default SealBool greaterThanOrEqualConst(E op2, Boolean nullIsLess) {
		return PileBool.greaterThanOrEqual(this, op2, nullIsLess);
	}
	public default SealComparable<E> readOnly(){
		return Piles.makeReadOnlyWrapper(this, new SealComparable<>());
	}
	public default PileComparableImpl<E> overridable(){
		return new PileBuilder<>(new PileComparableImpl<E>())
				.recompute(this::get)
				.name(dependencyName()+"*")
				.whenChanged(this);
	}
}
