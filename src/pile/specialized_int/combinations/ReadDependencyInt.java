package pile.specialized_int.combinations;

import pile.aspect.Dependency;
import pile.aspect.WriteValue;
import pile.aspect.combinations.ReadDependency;
import pile.aspect.combinations.ReadWriteDependency;
import pile.impl.Piles;
import pile.impl.SealPile;
import pile.specialized_Comparable.combinations.ReadDependencyComparable;
import pile.specialized_String.SealString;
import pile.specialized_bool.SealBool;
import pile.specialized_double.PileDouble;
import pile.specialized_double.SealDouble;
import pile.specialized_double.combinations.ReadDependencyDouble;
import pile.specialized_int.PileInt;
import pile.specialized_int.PileIntImpl;
import pile.specialized_int.SealInt;

public interface ReadDependencyInt extends ReadValueInt, Dependency, ReadDependencyComparable<Integer>{
	/**
	 * Make a reactive double precision number that is computed from casting {@code this}
	 * reactive integer
	 * @return
	 */
	public default SealDouble toDouble() {
		return mapToDouble(i -> i==null?null:i.doubleValue());
	}
	/** 
	 * Delegates to {@link PileInt#negativeRO()}
	 * Subclasses that also implement {@link WriteValue} should delegate
	 * to {@link PileInt#negativeRW()} instead.
	 */
	public default SealInt negative() {
		return PileInt.negativeRO(this);
	}
	/**
	 * Delegates to {@link PileInt#negativeRO()}
	 * @return
	 */
	public default SealInt negativeRO() {
		return PileInt.negativeRO(this);
	}
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	/**
	 * Delegates to {@link PileInt#_choose(ReadDependency, ReadDependency, ReadDependency, ReadDependency, ReadDependency, SealPile)}.
	 * @param <E>
	 * @param ifNeg
	 * @param ifZero
	 * @param ifPos
	 * @param ifNull
	 * @return
	 */
	public default <E> SealPile<? extends E> choose(
			ReadDependency<? extends E> ifNeg, 
			ReadDependency<? extends E> ifZero, 
			ReadDependency<? extends E> ifPos, 
			ReadDependency<? extends E> ifNull){
		return _choose(ifNeg, ifZero, ifPos, ifNull, new SealPile<>());
	}
	/**
	 * Delegates to {@link PileInt#_choose(ReadDependency, ReadDependency, ReadDependency, ReadDependency, ReadDependency, SealPile)}.
	 * Whenever this {@link ReadDependencyInt} holds a <code>null</code> reference, the
	 * returned reactive value will also hold <code>null</code>. 
	 * @param <E>
	 * @param ifNeg
	 * @param ifZero
	 * @param ifPos
	 * @return
	 */
	public default <E> SealPile<? extends E> choose(
			ReadDependency<? extends E> ifNeg, 
			ReadDependency<? extends E> ifZero, 
			ReadDependency<? extends E> ifPos){
		return _choose(ifNeg, ifZero, ifPos, Piles.constNull(), new SealPile<>());
	}
	/**
	 * Delegates to {@link PileInt#_chooseWritable(ReadDependency, ReadWriteDependency, ReadWriteDependency, ReadWriteDependency, ReadWriteDependency, SealPile)}.
	 * @param <E>
	 * @param ifNeg
	 * @param ifZero
	 * @param ifPos
	 * @param ifNull
	 * @return
	 */
	public default <E> SealPile<? extends E> chooseWritable(
			ReadWriteDependency<E> ifNeg, 
			ReadWriteDependency<E> ifZero, 
			ReadWriteDependency<E> ifPos, 
			ReadWriteDependency<E> ifNull){
		return _chooseWritable(ifNeg, ifZero, ifPos, ifNull, new SealPile<>());
	}
	/**
	 * Delegates to {@link PileInt#_chooseWritable(ReadDependency, ReadWriteDependency, ReadWriteDependency, ReadWriteDependency, ReadWriteDependency, SealPile)}.
	 * When this {@link ReadDependencyInt} holds a <code>null</code> reference, the
	 * returned reactive value will also hold <code>null</code>. 
	 * @param <E>
	 * @param ifNeg
	 * @param ifZero
	 * @param ifPos
	 * @return
	 */
	public default <E> SealPile<? extends E> chooseWritable(
			ReadWriteDependency<E> ifNeg, 
			ReadWriteDependency<E> ifZero, 
			ReadWriteDependency<E> ifPos){
		return _chooseWritable(ifNeg, ifZero, ifPos, Piles.constNull(), new SealPile<>());
	}
	/**
	 * Delegates to {@link PileInt#_chooseConst(ReadDependency, Object, Object, Object, Object, Object,SealPile)}.
	 * When this {@link ReadDependencyInt} holds a <code>null</code> reference, the
	 * returned reactive value will also hold <code>null</code>. 	 * @param <E>
	 * @param ifNeg
	 * @param ifZero
	 * @param ifPos
	 * @param ifNull
	 * @return
	 */
	public default <E> SealPile<? extends E> chooseConst(
			E ifNeg, 
			E ifZero, 
			E ifPos, 
			E ifNull){
		return _chooseConst(ifNeg, ifZero, ifPos, ifNull, new SealPile<>());
	}
	/**
	 * Delegates to {@link PileInt#_chooseConst(ReadDependency, Object, Object, Object, Object, Object,SealPile)}
	 * @param <E>
	 * @param ifNeg
	 * @param ifZero
	 * @param ifPos
	 * @return
	 */
	public default <E> SealPile<? extends E> chooseConst(
			E ifNeg, 
			E ifZero, 
			E ifPos){
		return _chooseConst(ifNeg, ifZero, ifPos, null, new SealPile<>());
	}
	
	/** Specialization of {@link #choose(ReadDependency, ReadDependency, ReadDependency, ReadDependency)} */
	public default SealInt chooseInt(
			ReadDependency<? extends Integer> ifNeg, 
			ReadDependency<? extends Integer> ifZero, 
			ReadDependency<? extends Integer> ifPos, 
			ReadDependency<? extends Integer> ifNull){
		return _choose(ifNeg, ifZero, ifPos, ifNull, new SealInt());
	}
	/** Specialization of {@link #choose(ReadDependency, ReadDependency, ReadDependency)} */
	public default SealInt chooseInt(
			ReadDependency<? extends Integer> ifNeg, 
			ReadDependency<? extends Integer> ifZero, 
			ReadDependency<? extends Integer> ifPos){
		return _choose(ifNeg, ifZero, ifPos, Piles.constNull(), new SealInt());
	}
	/** Specialization of {@link #chooseWritable(ReadWriteDependency, ReadWriteDependency, ReadWriteDependency, ReadWriteDependency)} */
	public default SealInt chooseWritableInt(
			ReadWriteDependency<Integer> ifNeg, 
			ReadWriteDependency<Integer> ifZero, 
			ReadWriteDependency<Integer> ifPos, 
			ReadWriteDependency<Integer> ifNull){
		return _chooseWritable(ifNeg, ifZero, ifPos, ifNull, new SealInt());
	}
	/** Specialization of {@link #chooseWritable(ReadWriteDependency, ReadWriteDependency, ReadWriteDependency)} */
	public default SealInt chooseWritableInt(
			ReadWriteDependency<Integer> ifNeg, 
			ReadWriteDependency<Integer> ifZero, 
			ReadWriteDependency<Integer> ifPos){
		return _chooseWritable(ifNeg, ifZero, ifPos, Piles.constNull(), new SealInt());
	}
	/** Specialization of {@link #chooseConst(Object, Object, Object, Object)} */
	public default SealInt chooseConstInt(
			Integer ifNeg, 
			Integer ifZero, 
			Integer ifPos, 
			Integer ifNull){
		return _chooseConst(ifNeg, ifZero, ifPos, ifNull, new SealInt());
	}
	/** Specialization of {@link #chooseConst(Object, Object, Object)} */
	public default SealInt chooseConstInt(
			Integer ifNeg, 
			Integer ifZero, 
			Integer ifPos){
		return _chooseConst(ifNeg, ifZero, ifPos, null, new SealInt());
	}
	
	
	/** Specialization of {@link #choose(ReadDependency, ReadDependency, ReadDependency, ReadDependency)} */
	public default SealBool chooseBool(
			ReadDependency<? extends Boolean> ifNeg, 
			ReadDependency<? extends Boolean> ifZero, 
			ReadDependency<? extends Boolean> ifPos, 
			ReadDependency<? extends Boolean> ifNull){
		return _choose(ifNeg, ifZero, ifPos, ifNull, new SealBool());
	}
	/** Specialization of {@link #choose(ReadDependency, ReadDependency, ReadDependency)} */
	public default SealBool chooseBool(
			ReadDependency<? extends Boolean> ifNeg, 
			ReadDependency<? extends Boolean> ifZero, 
			ReadDependency<? extends Boolean> ifPos){
		return _choose(ifNeg, ifZero, ifPos, Piles.constNull(), new SealBool());
	}
	/** Specialization of {@link #chooseWritable(ReadWriteDependency, ReadWriteDependency, ReadWriteDependency, ReadWriteDependency)} */
	public default SealBool chooseWritableBool(
			ReadWriteDependency<Boolean> ifNeg, 
			ReadWriteDependency<Boolean> ifZero, 
			ReadWriteDependency<Boolean> ifPos, 
			ReadWriteDependency<Boolean> ifNull){
		return _chooseWritable(ifNeg, ifZero, ifPos, ifNull, new SealBool());
	}
	/** Specialization of {@link #chooseWritable(ReadWriteDependency, ReadWriteDependency, ReadWriteDependency)} */
	public default SealBool chooseWritableBool(
			ReadWriteDependency<Boolean> ifNeg, 
			ReadWriteDependency<Boolean> ifZero, 
			ReadWriteDependency<Boolean> ifPos){
		return _chooseWritable(ifNeg, ifZero, ifPos, Piles.constNull(), new SealBool());
	}
	/** Specialization of {@link #chooseConst(Object, Object, Object, Object)} */
	public default SealBool chooseConstBool(
			Boolean ifNeg, 
			Boolean ifZero, 
			Boolean ifPos, 
			Boolean ifNull){
		return _chooseConst(ifNeg, ifZero, ifPos, ifNull, new SealBool());
	}
	/** Specialization of {@link #chooseConst(Object, Object, Object)} */
	public default SealBool chooseConstBool(
			Boolean ifNeg, 
			Boolean ifZero, 
			Boolean ifPos){
		return _chooseConst(ifNeg, ifZero, ifPos, null, new SealBool());
	}
	
	/** Specialization of {@link #choose(ReadDependency, ReadDependency, ReadDependency, ReadDependency)} */
	public default SealDouble chooseDouble(
			ReadDependency<? extends Double> ifNeg, 
			ReadDependency<? extends Double> ifZero, 
			ReadDependency<? extends Double> ifPos, 
			ReadDependency<? extends Double> ifNull){
		return _choose(ifNeg, ifZero, ifPos, ifNull, new SealDouble());
	}
	/** Specialization of {@link #choose(ReadDependency, ReadDependency, ReadDependency)} */
	public default SealDouble chooseDouble(
			ReadDependency<? extends Double> ifNeg, 
			ReadDependency<? extends Double> ifZero, 
			ReadDependency<? extends Double> ifPos){
		return _choose(ifNeg, ifZero, ifPos, Piles.constNull(), new SealDouble());
	}
	/** Specialization of {@link #chooseWritable(ReadWriteDependency, ReadWriteDependency, ReadWriteDependency, ReadWriteDependency)} */
	public default SealDouble chooseWritableDouble(
			ReadWriteDependency<Double> ifNeg, 
			ReadWriteDependency<Double> ifZero, 
			ReadWriteDependency<Double> ifPos, 
			ReadWriteDependency<Double> ifNull){
		return _chooseWritable(ifNeg, ifZero, ifPos, ifNull, new SealDouble());
	}
	/** Specialization of {@link #chooseWritable(ReadWriteDependency, ReadWriteDependency, ReadWriteDependency)} */
	public default SealDouble chooseWritableDouble(
			ReadWriteDependency<Double> ifNeg, 
			ReadWriteDependency<Double> ifZero, 
			ReadWriteDependency<Double> ifPos){
		return _chooseWritable(ifNeg, ifZero, ifPos, Piles.constNull(), new SealDouble());
	}
	/** Specialization of {@link #chooseConst(Object, Object, Object, Object)} */
	public default SealDouble chooseConstDouble(
			Double ifNeg, 
			Double ifZero, 
			Double ifPos, 
			Double ifNull){
		return _chooseConst(ifNeg, ifZero, ifPos, ifNull, new SealDouble());
	}
	/** Specialization of {@link #chooseConst(Object, Object, Object)} */
	public default SealDouble chooseConstDouble(
			Double ifNeg, 
			Double ifZero, 
			Double ifPos){
		return _chooseConst(ifNeg, ifZero, ifPos, null, new SealDouble());
	}
	
	/** Specialization of {@link #choose(ReadDependency, ReadDependency, ReadDependency, ReadDependency)} */
	public default SealString chooseString(
			ReadDependency<? extends String> ifNeg, 
			ReadDependency<? extends String> ifZero, 
			ReadDependency<? extends String> ifPos, 
			ReadDependency<? extends String> ifNull){
		return _choose(ifNeg, ifZero, ifPos, ifNull, new SealString());
	}
	/** Specialization of {@link #choose(ReadDependency, ReadDependency, ReadDependency)} */
	public default SealString chooseString(
			ReadDependency<? extends String> ifNeg, 
			ReadDependency<? extends String> ifZero, 
			ReadDependency<? extends String> ifPos){
		return _choose(ifNeg, ifZero, ifPos, Piles.constNull(), new SealString());
	}
	/** Specialization of {@link #chooseWritable(ReadWriteDependency, ReadWriteDependency, ReadWriteDependency, ReadWriteDependency)} */
	public default SealString chooseWritableString(
			ReadWriteDependency<String> ifNeg, 
			ReadWriteDependency<String> ifZero, 
			ReadWriteDependency<String> ifPos, 
			ReadWriteDependency<String> ifNull){
		return _chooseWritable(ifNeg, ifZero, ifPos, ifNull, new SealString());
	}
	/** Specialization of {@link #chooseWritable(ReadWriteDependency, ReadWriteDependency, ReadWriteDependency)} */
	public default SealString chooseWritableString(
			ReadWriteDependency<String> ifNeg, 
			ReadWriteDependency<String> ifZero, 
			ReadWriteDependency<String> ifPos){
		return _chooseWritable(ifNeg, ifZero, ifPos, Piles.constNull(), new SealString());
	}
	/** Specialization of {@link #chooseConst(Object, Object, Object, Object)} */
	public default SealString chooseConstString(
			String ifNeg, 
			String ifZero, 
			String ifPos, 
			String ifNull){
		return _chooseConst(ifNeg, ifZero, ifPos, ifNull, new SealString());
	}
	/** Specialization of {@link #chooseConst(Object, Object, Object)} */
	public default SealString chooseConstString(
			String ifNeg, 
			String ifZero, 
			String ifPos){
		return _chooseConst(ifNeg, ifZero, ifPos, null, new SealString());
	}

	/** Delegates to {@link PileInt#_choose(ReadDependency, ReadDependency, ReadDependency, ReadDependency, ReadDependency, SealPile)} */
	public default <E, V extends SealPile<E>> V _choose(
			ReadDependency<? extends E> ifNeg, 
			ReadDependency<? extends E> ifZero, 
			ReadDependency<? extends E> ifPos, 
			ReadDependency<? extends E> ifNull,
			V template
			) {
		return PileInt._choose(this, ifNeg,  ifZero,  ifPos, ifNull, template);
	}
	/** Delegates to {@link PileInt#_chooseWritable(ReadDependency, ReadWriteDependency, ReadWriteDependency, ReadWriteDependency, ReadWriteDependency, SealPile)} */
	public default <E, V extends SealPile<E>> V _chooseWritable(
			ReadWriteDependency<E> ifNeg, 
			ReadWriteDependency<E> ifZero, 
			ReadWriteDependency<E> ifPos, 
			ReadWriteDependency<E> ifNull,
			V template
			) {
		return PileInt._chooseWritable(this, ifNeg,  ifZero,  ifPos, ifNull, template);
	}
	/** Delegates to {@link PileInt#_chooseConst(ReadDependency, Object, Object, Object, Object, SealPile)} */
	public default <E, V extends SealPile<E>> V _chooseConst(
			E ifNeg, 
			E ifZero, 
			E ifPos, 
			E ifNull,
			V template
			) {
		return PileInt._chooseConst(this, ifNeg,  ifZero,  ifPos, ifNull, template);
	}
	
	
	
	
	
	
	
	
	
	
	/** Delegates to {@link PileInt#add(ReadDependency, ReadDependency)} */
	public default SealInt plus(ReadDependency<? extends Integer> op2) {
		return PileInt.add(this, op2);
	}
	/** Delegates to {@link PileInt#subtract(ReadDependency, ReadDependency)} */
	public default SealInt minus(ReadDependency<? extends Integer> op2) {
		return PileInt.subtract(this, op2);
	}
	/** Delegates to {@link PileInt#multiply(ReadDependency, ReadDependency)} */
	public default SealInt times(ReadDependency<? extends Integer> op2) {
		return PileInt.multiply(this, op2);
	}
    /** Delegates to {@link PileDouble#divide(ReadDependency, ReadDependency)} */
	public default SealDouble over(ReadDependency<? extends Number> op2) {
		return PileDouble.divide(this, op2);
	}
	/** Delegates to {@link PileInt#integerDivide(ReadDependency, ReadDependency)} */
	public default SealInt integerDivide(ReadDependency<? extends Integer> op2) {
		return PileInt.integerDivide(this, op2);
	}
	/** Delegates to {@link PileInt#remainder(ReadDependency, ReadDependency)} */
	public default SealInt remainder(ReadDependency<? extends Integer> op2) {
		return PileInt.remainder(this, op2);
	}
	/** Delegates to {@link PileInt#modulo(ReadDependency, ReadDependency)} */
	public default SealInt modulo(ReadDependency<? extends Integer> op2) {
		return PileInt.modulo(this, op2);
	}
	/** Delegates to {@link PileDouble#add(ReadDependency, ReadDependency)} */
	public default SealDouble plus(ReadDependencyDouble op2) {
		return PileDouble.add(this, op2);
	}
	/** Delegates to {@link PileDouble#subtract(ReadDependency, ReadDependency)} */
	public default SealDouble minus(ReadDependencyDouble op2) {
		return PileDouble.subtract(this, op2);
	}
	/** Delegates to {@link PileDouble#multiply(ReadDependency, ReadDependency)} */
	public default SealDouble times(ReadDependencyDouble op2) {
		return PileDouble.multiply(this, op2);
	}

	/** Delegates to {@link PileDouble#addRO(ReadDependency, double)} */
	public default SealDouble plus(double op2) {
		return PileDouble.addRO(this, op2);
	}
	/** Delegates to {@link PileDouble#subtractRO(ReadDependency, double)} */
	public default SealDouble minus(double op2) {
		return PileDouble.subtractRO(this, op2);
	}
	/** Delegates to {@link PileDouble#multiplyRO(ReadDependency, double)} */
	public default SealDouble times(double op2) {
		return PileDouble.multiplyRO(this, op2);
	}
	/** Delegates to {@link PileDouble#divideRO(ReadDependency, double)} */
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
	
	/**
	 * Delegates to {@link PileInt#addRO(ReadDependency, int)}.
	 * Subclasses that also implement {@link WriteValue} should delegate to {@link PileInt#addRW(ReadDependency, int)} instead.
	 */
	public default SealInt plus(int op2) {
		return PileInt.addRO(this, op2);
	}
	/** 
	 * Delegates to {@link PileInt#subtractRO(ReadDependency, int)} 
	 * Subclasses that also implement {@link WriteValue} should delegate to {@link PileInt#subtractRW(ReadDependency, int)} instead.
	 */
	public default SealInt minus(int op2) {
		return PileInt.subtractRO(this, op2);
	}
	/** Delegates to {@link PileInt#multiplyRO(ReadDependency, int)} */
	public default SealInt times(int op2) {
		return PileInt.multiply(this, op2);
	}
	/** Delegates to {@link PileDouble#divideRO(ReadDependency, int)} */
	public default SealDouble over(int op2) {
		return PileDouble.divideRO(this, op2);
	}
	/** Delegates to {@link PileInt#integerDivide(ReadDependency, int)} */
	public default SealInt integerDivide(int op2) {
		return PileInt.integerDivide(this, op2);
	}
	/** Delegates to {@link PileInt#remainder(ReadDependency, int)} */
	public default SealInt remainder(int op2) {
		return PileInt.remainder(this, op2);
	}
	/** Delegates to {@link PileInt#modulo(ReadDependency, int)} */
	public default SealInt modulo(int op2) {
		return PileInt.modulo(this, op2);
	}
	/** Delegates to {@link PileInt#addRO(ReadDependency, int)} */
	public default SealInt plusRO(int op2) {
		return PileInt.addRO(this, op2);
	}
	/** Delegates to {@link PileInt#subtractRO(ReadDependency, int)} */
	public default SealInt minusRO(int op2) {
		return PileInt.subtractRO(this, op2);
	}

	
	/** Delegates to {@link PileInt#min(ReadDependency, ReadDependency)} */
	public default SealInt min(ReadDependency<? extends Integer> op2) {
		return PileInt.min(this, op2);
	}
	/** Delegates to {@link PileInt#max(ReadDependency, ReadDependency)} */
	public default SealInt max(ReadDependency<? extends Integer> op2) {
		return PileInt.max(this, op2);
	}
	/** Delegates to {@link PileDouble#min(ReadDependency, ReadDependency)} */
	public default SealDouble min(ReadDependencyDouble op2) {
		return PileDouble.min(this, op2);
	}
	/** Delegates to {@link PileDouble#max(ReadDependency, ReadDependency)} */
	public default SealDouble max(ReadDependencyDouble op2) {
		return PileDouble.max(this, op2);
	}
	/** Delegates to {@link PileDouble#min(ReadDependency, double)} */
	public default SealDouble min(double op2) {
		return PileDouble.min(this, op2);
	}
	/** Delegates to {@link PileDouble#max(ReadDependency, double)} */
	public default SealDouble max(double op2) {
		return PileDouble.max(this, op2);
	}
	/** Delegates to {@link PileInt#min(ReadDependency, int)} */
	public default SealInt min(int op2) {
		return PileInt.min(this, op2);
	}
	/** Delegates to {@link PileInt#max(ReadDependency, int)} */
	public default SealInt max(int op2) {
		return PileInt.max(this, op2);
	}
	
	
	
	public default SealInt readOnly(){
		return Piles.makeReadOnlyWrapper(this, new SealInt());
	}
	

	
	public default PileIntImpl overridable() {
		return Piles.computeInt(this).name(dependencyName()+"*").whenChanged(this);
	}
}
