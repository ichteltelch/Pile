package pile.specialized_bool.combinations;

import java.util.function.Function;

import pile.aspect.Dependency;
import pile.aspect.WriteValue;
import pile.aspect.combinations.ReadDependency;
import pile.aspect.combinations.ReadWriteDependency;
import pile.impl.Piles;
import pile.impl.SealPile;
import pile.specialized_String.SealString;
import pile.specialized_bool.PileBool;
import pile.specialized_bool.PileBoolImpl;
import pile.specialized_bool.SealBool;
import pile.specialized_double.SealDouble;
import pile.specialized_int.SealInt;

public interface ReadDependencyBool extends ReadValueBool, Dependency, ReadDependency<Boolean>{
	/**
	 * Delegates to {@link PileBool#and(ReadDependency, ReadDependency)}
	 * @param op2
	 * @return
	 */
	public default SealBool and(ReadDependency<? extends Boolean> op2) {
		return PileBool.and(this, op2);
	}
	/**
	 * Delegates to {@link PileBool#andScd(ReadDependency, ReadDependency)}
	 * @param op2
	 * @return
	 */
	public default SealBool andScd(ReadDependencyBool op2) {
		return PileBool.andScd(this, op2);
	}
	/**
	 * Delegates to {@link PileBool#or(ReadDependency, ReadDependency)}
	 * @param op2
	 * @return
	 */

	public default SealBool or(ReadDependency<? extends Boolean> op2) {
		return PileBool.or(this, op2);
	}
	/**
	 * Delegates to {@link PileBool#orScd(ReadDependency, ReadDependency)}
	 * @param op2
	 * @return
	 */
	public default SealBool orScd(ReadDependencyBool op2) {
		return PileBool.orScd(this, op2);
	}
	/**
	 * Delegates to {@link PileBool#notRO(ReadDependency)}.
	 * Subclasses also implementing {@link WriteValue} should delegate to
	 * {@link PileBool#notRW()} instead.
	 * @param op2
	 * @return
	 */

	public default ReadListenDependencyBool not() {
		return PileBool.notRO(this);
	}
	/**
	 * Delegates to {@link PileBool#notRO(ReadDependency)}.
	 * @return
	 */
	public default ReadListenDependencyBool notRO() {
		return PileBool.notRO(this);
	}

	/**
	 * @see PileBool#_choose(ReadDependency, ReadDependency, ReadDependency, ReadDependency, SealPile)
	 * @param <E>
	 * @param ifTrue
	 * @param ifFalse
	 * @param ifNull
	 * @return
	 */
	public default <E> SealPile<E> choose(
			ReadDependency<? extends E> ifTrue, 
			ReadDependency<? extends E> ifFalse, 
			ReadDependency<? extends E> ifNull){
		return _choose(ifTrue, ifFalse, ifNull, new SealPile<>());
	}
	/**
	 * @see PileBool#_choose(ReadDependency, ReadDependency, ReadDependency, ReadDependency, SealPile)
	 * Whenever this {@link ReadDependencyBool} holds a <code>null</code> reference, the
	 * returned reactive value will also hold <code>null</code>. 
	 * @param <E>
	 * @param ifTrue
	 * @param ifFalse
	 * @return
	 */
	public default <E> SealPile<E> choose(
			ReadDependency<? extends E> ifTrue, 
			ReadDependency<? extends E> ifFalse){
		return _choose(ifTrue, ifFalse, Piles.constNull(), new SealPile<>());
	}
	/**
	 * @see PileBool#_chooseWritable(ReadDependency, ReadWriteDependency, ReadWriteDependency, ReadWriteDependency, SealPile)
	 * @param <E>
	 * @param ifTrue
	 * @param ifFalse
	 * @param ifNull
	 * @return
	 */
	public default <E> SealPile<? extends E> chooseWritable(
			ReadWriteDependency<E> ifTrue, 
			ReadWriteDependency<E> ifFalse, 
			ReadWriteDependency<E> ifNull){
		return _chooseWritable(ifTrue, ifFalse, ifNull, new SealPile<>());
	}
	/**
	 * @see PileBool#_chooseWritable(ReadDependency, ReadWriteDependency, ReadWriteDependency, ReadWriteDependency, SealPile)
	 * Whenever this {@link ReadDependencyBool} holds a <code>null</code> reference, the
	 * returned reactive value will also hold <code>null</code>. 
	 * @param <E>
	 * @param ifTrue
	 * @param ifFalse
	 * @return
	 */
	public default <E> SealPile<E> chooseWritable(
			ReadWriteDependency<E> ifTrue, 
			ReadWriteDependency<E> ifFalse){
		return _chooseWritable(ifTrue, ifFalse, Piles.constNull(), new SealPile<>());
	}
	/**
	 * @see PileBool#_chooseConst(ReadDependency, Object, Object, Object, SealPile)
	 * @param <E>
	 * @param ifTrue
	 * @param ifFalse
	 * @param ifNull
	 * @return
	 */
	public default <E> SealPile<E> chooseConst(
			E ifTrue, 
			E ifFalse, 
			E ifNull){
		return _chooseConst(ifTrue, ifFalse, ifNull, new SealPile<>());
	}
	/**
	 * @see PileBool#_chooseConst(ReadDependency, Object, Object, Object, SealPile)
	 * Whenever this {@link ReadDependencyBool} holds a <code>null</code> reference, the
	 * returned reactive value will also hold <code>null</code>. 
	 * @param <E>
	 * @param ifTrue
	 * @param ifFalse
	 * @return
	 */
	public default <E> SealPile<E> chooseConst(
			E ifTrue, 
			E ifFalse){
		return _chooseConst(ifTrue, ifFalse, null, new SealPile<>());
	}
	
	/** Specialization of {@link #choose(ReadDependency, ReadDependency, ReadDependency)} */
	public default SealInt chooseInt(
			ReadDependency<? extends Integer> ifTrue, 
			ReadDependency<? extends Integer> ifFalse, 
			ReadDependency<? extends Integer> ifNull){
		return _choose(ifTrue, ifFalse, ifNull, new SealInt());
	}
	/** Specialization of {@link #choose(ReadDependency, ReadDependency)} */
	public default SealInt chooseInt(
			ReadDependency<? extends Integer> ifTrue, 
			ReadDependency<? extends Integer> ifFalse){
		return _choose(ifTrue, ifFalse, Piles.constNull(), new SealInt());
	}
	/** Specialization of {@link #chooseWritable(ReadWriteDependency, ReadWriteDependency, ReadWriteDependency)} */
	public default SealInt chooseWritableInt(
			ReadWriteDependency<Integer> ifTrue, 
			ReadWriteDependency<Integer> ifFalse, 
			ReadWriteDependency<Integer> ifNull){
		return _chooseWritable(ifTrue, ifFalse, ifNull, new SealInt());
	}
	/** Specialization of {@link #chooseWritable(ReadWriteDependency, ReadWriteDependency)} */
	public default SealInt chooseWritableInt(
			ReadWriteDependency<Integer> ifTrue, 
			ReadWriteDependency<Integer> ifFalse){
		return _chooseWritable(ifTrue, ifFalse, Piles.constNull(), new SealInt());
	}
	/** Specialization of {@link #chooseConst(Object, Object, Object)} */
	public default SealInt chooseConstInt(
			Integer ifTrue, 
			Integer ifFalse, 
			Integer ifNull){
		return _chooseConst(ifTrue, ifFalse, ifNull, new SealInt());
	}
	/** Specialization of {@link #chooseConst(Object, Object)} */
	public default SealInt chooseConstInt(
			Integer ifTrue, 
			Integer ifFalse){
		return _chooseConst(ifTrue, ifFalse, null, new SealInt());
	}
	
	/** Specialization of {@link #choose(ReadDependency, ReadDependency, ReadDependency)} */
	public default SealBool chooseBool(
			ReadDependency<? extends Boolean> ifTrue, 
			ReadDependency<? extends Boolean> ifFalse, 
			ReadDependency<? extends Boolean> ifNull){
		return _choose(ifTrue, ifFalse, ifNull, new SealBool());
	}
	/** Specialization of {@link #choose(ReadDependency, ReadDependency)} */
	public default SealBool chooseBool(
			ReadDependency<? extends Boolean> ifTrue, 
			ReadDependency<? extends Boolean> ifFalse){
		return _choose(ifTrue, ifFalse, Piles.constNull(), new SealBool());
	}
	/** Specialization of {@link #chooseWritable(ReadWriteDependency, ReadWriteDependency, ReadWriteDependency)} */
	public default SealBool chooseWritableBool(
			ReadWriteDependency<Boolean> ifTrue, 
			ReadWriteDependency<Boolean> ifFalse, 
			ReadWriteDependency<Boolean> ifNull){
		return _chooseWritable(ifTrue, ifFalse, ifNull, new SealBool());
	}
	/** Specialization of {@link #chooseWritable(ReadWriteDependency, ReadWriteDependency)} */
	public default SealBool chooseWritableBool(
			ReadWriteDependency<Boolean> ifTrue, 
			ReadWriteDependency<Boolean> ifFalse){
		return _chooseWritable(ifTrue, ifFalse, Piles.constNull(), new SealBool());
	}
	/** Specialization of {@link #chooseConst(Object, Object, Object)} */
	public default SealBool chooseConstBool(
			Boolean ifTrue, 
			Boolean ifFalse, 
			Boolean ifNull){
		return _chooseConst(ifTrue, ifFalse, ifNull, new SealBool());
	}
	/** Specialization of {@link #chooseConst(Object, Object, Object)} */
	public default SealBool chooseConstBool(
			Boolean ifTrue, 
			Boolean ifFalse){
		return _chooseConst(ifTrue, ifFalse, null, new SealBool());
	}
	
	/** Specialization of {@link #choose(ReadDependency, ReadDependency, ReadDependency)} */
	public default SealDouble chooseDouble(
			ReadDependency<? extends Double> ifTrue, 
			ReadDependency<? extends Double> ifFalse, 
			ReadDependency<? extends Double> ifNull){
		return _choose(ifTrue, ifFalse, ifNull, new SealDouble());
	}
	/** Specialization of {@link #choose(ReadDependency, ReadDependency)} */
	public default SealDouble chooseDouble(
			ReadDependency<? extends Double> ifTrue, 
			ReadDependency<? extends Double> ifFalse){
		return _choose(ifTrue, ifFalse, Piles.constNull(), new SealDouble());
	}
	/** Specialization of {@link #chooseWritable(ReadWriteDependency, ReadWriteDependency, ReadWriteDependency)} */
	public default SealDouble chooseWritableDouble(
			ReadWriteDependency<Double> ifTrue, 
			ReadWriteDependency<Double> ifFalse, 
			ReadWriteDependency<Double> ifNull){
		return _chooseWritable(ifTrue, ifFalse, ifNull, new SealDouble());
	}
	/** Specialization of {@link #chooseWritable(ReadWriteDependency, ReadWriteDependency)} */
	public default SealDouble chooseWritableDouble(
			ReadWriteDependency<Double> ifTrue, 
			ReadWriteDependency<Double> ifFalse){
		return _chooseWritable(ifTrue, ifFalse, Piles.constNull(), new SealDouble());
	}
	/** Specialization of {@link #chooseConst(Object, Object, Object)} */
	public default SealDouble chooseConstDouble(
			Double ifTrue, 
			Double ifFalse, 
			Double ifNull){
		return _chooseConst(ifTrue, ifFalse, ifNull, new SealDouble());
	}
	/** Specialization of {@link #chooseConst(Object, Object, Object)} */
	public default SealDouble chooseConstDouble(
			Double ifTrue, 
			Double ifFalse){
		return _chooseConst(ifTrue, ifFalse, null, new SealDouble());
	}
	
	/** Specialization of {@link #choose(ReadDependency, ReadDependency, ReadDependency)} */
	public default SealString chooseString(
			ReadDependency<? extends String> ifTrue, 
			ReadDependency<? extends String> ifFalse, 
			ReadDependency<? extends String> ifNull){
		return _choose(ifTrue, ifFalse, ifNull, new SealString());
	}
	/** Specialization of {@link #choose(ReadDependency, ReadDependency)} */
	public default SealString chooseString(
			ReadDependency<? extends String> ifTrue, 
			ReadDependency<? extends String> ifFalse){
		return _choose(ifTrue, ifFalse, Piles.constNull(), new SealString());
	}
	/** Specialization of {@link #chooseWritable(ReadWriteDependency, ReadWriteDependency, ReadWriteDependency)} */
	public default SealString chooseWritableString(
			ReadWriteDependency<String> ifTrue, 
			ReadWriteDependency<String> ifFalse, 
			ReadWriteDependency<String> ifNull){
		return _chooseWritable(ifTrue, ifFalse, ifNull, new SealString());
	}
	/** Specialization of {@link #chooseWritable(ReadWriteDependency, ReadWriteDependency)} */
	public default SealString chooseWritableString(
			ReadWriteDependency<String> ifTrue, 
			ReadWriteDependency<String> ifFalse){
		return _chooseWritable(ifTrue, ifFalse, Piles.constNull(), new SealString());
	}
	/** Specialization of {@link #chooseConst(Object, Object, Object)} */
	public default SealString chooseConstString(
			String ifTrue, 
			String ifFalse, 
			String ifNull){
		return _chooseConst(ifTrue, ifFalse, ifNull, new SealString());
	}
	/** Specialization of {@link #chooseConst(Object, Object, Object)} */
	public default SealString chooseConstString(
			String ifTrue, 
			String ifFalse){
		return _chooseConst(ifTrue, ifFalse, null, new SealString());
	}

	/**
	 * Delegates to {@link PileBool#_choose(ReadDependency, ReadDependency, ReadDependency, ReadDependency, SealPile)}
	 * @param <E>
	 * @param <V>
	 * @param ifTrue
	 * @param ifFalse
	 * @param ifNull
	 * @param template
	 * @return
	 */
	public default <E, V extends SealPile<E>> V _choose(
			ReadDependency<? extends E> ifTrue, 
			ReadDependency<? extends E> ifFalse, 
			ReadDependency<? extends E> ifNull,
			V template
			) {
		return PileBool._choose(this, ifTrue, ifFalse, ifNull, template);
	}
	/**
	 * Delegates to {@link PileBool#_chooseWritable(ReadDependency, ReadWriteDependency, ReadWriteDependency, ReadWriteDependency, SealPile)}
	 * @param <E>
	 * @param <V>
	 * @param ifTrue
	 * @param ifFalse
	 * @param ifNull
	 * @param template
	 * @return
	 */
	public default <E, V extends SealPile<E>> V _chooseWritable(
			ReadWriteDependency<E> ifTrue, 
			ReadWriteDependency<E> ifFalse, 
			ReadWriteDependency<E> ifNull,
			V template
			) {
		return PileBool._chooseWritable(this, ifTrue, ifFalse, ifNull, template);
	}
	/**
	 * Delegates to {@link PileBool#_chooseConst(ReadDependency, Object, Object, Object, SealPile)}
	 * @param <E>
	 * @param <V>
	 * @param ifTrue
	 * @param ifFalse
	 * @param ifNull
	 * @param template
	 * @return
	 */
	public default <E, V extends SealPile<E>> V _chooseConst(
			E ifTrue, 
			E ifFalse, 
			E ifNull,
			V template
			) {
		return PileBool._chooseConst(this, ifTrue, ifFalse, ifNull, template);
	}

	public default SealBool readOnly(){
		return Piles.makeReadOnlyWrapper(this, new SealBool());
	}

	
	public default PileBoolImpl overridable() {
		return Piles.computeBool(this).name(dependencyName()+"*").whenChanged(this);
	}
	/**
	 * Make a reactive integer that is {@code 1} when {@code this} is <code>true</code>
	 * and {@code 0} when {@code this} is <code>false</code> or <code>null</code>.
	 * @return
	 */
	public default SealInt mapToInt() {return mapToInt(__BOOL_TO_INT);}
	/**
	 * The mapping function used by {@link #mapToInt()}.
	 */
	static Function<? super Boolean, ? extends Integer> __BOOL_TO_INT = b->Boolean.TRUE.equals(b)?1:0;
	
	public default Dependency validIfTrue() {
		return PileBool.validIfTrue(this);
	}

}
