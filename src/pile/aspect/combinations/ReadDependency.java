package pile.aspect.combinations;

import java.util.Comparator;
import java.util.function.BiPredicate;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.ToDoubleFunction;
import java.util.function.ToIntFunction;

import pile.aspect.Dependency;
import pile.aspect.Depender;
import pile.aspect.HasAssociations;
import pile.aspect.ReadValue;
import pile.aspect.Sealable;
import pile.aspect.WriteValue;
import pile.builder.ISealPileBuilder;
import pile.builder.SealPileBuilder;
import pile.impl.Constant;
import pile.impl.PileImpl;
import pile.impl.Piles;
import pile.impl.SealPile;
import pile.specialized_String.PileString;
import pile.specialized_String.SealString;
import pile.specialized_bool.PileBool;
import pile.specialized_bool.SealBool;
import pile.specialized_bool.combinations.ReadListenDependencyBool;
import pile.specialized_double.PileDouble;
import pile.specialized_double.SealDouble;
import pile.specialized_int.PileInt;
import pile.specialized_int.SealInt;

/**
 * A combination of a {@link ReadValue} and a {@link Dependency}.
 * This allows us to define {@link Pile}s that depend on instances of this class
 * and recompute themselves based on their values.
 * @author bb
 *
 * @param <E>
 */
public interface ReadDependency<E> extends ReadValue<E>, Dependency{


	/**
	 * Configure an un-sealed {@link SealPile} to represent the result of mapping this
	 * {@link ReadDependency} through a function
	 * @param <F>
	 * @param <V>
	 * @param v
	 * @param mapFunction
	 * @return
	 */
	public default <F, V extends SealPile<F>> 
	V _mapSetup(V v, Function<? super E, ? extends F> mapFunction){
		return _mapBuilder(v, mapFunction)
				.seal()
				.build();
	}
	/**
	 * Configure an un-sealed {@link SealPile} to represent the result of mapping this
	 * {@link ReadDependency} through a function
	 * @param <F>
	 * @param <V>
	 * @param v
	 * @param mapFunction
	 * @param Additional configuration to be applied to the builder
	 * @return
	 */
	public default <F, V extends SealPile<F>> 
	V _mapSetup(V v, Function<? super E, ? extends F> mapFunction, Consumer<? super SealPileBuilder<V, F>> config){
		return _mapBuilder(v, mapFunction)
				.seal()
				.configure(config)
				.build();
	}
	public default <V extends SealPile<F>, F> SealPileBuilder<V, F> _mapBuilder(V v,
			Function<? super E, ? extends F> mapFunction) {
		return new SealPileBuilder<>(v)
				.recompute(()->mapFunction.apply(get()))
				.dependOn(true, this);
	}
	/**
	 * Map this {@link ReadDependency} through a function
	 * @param <F>
	 * @param mapFunction
	 * @return
	 */
	public default <F> 
	SealPile<? extends F> map(Function<? super E, ? extends F> mapFunction) {
		return _mapSetup(new SealPile<>(), mapFunction);
	}
	/**
	 * Map this {@link ReadDependency} through a Boolean-valued function
	 * @param mapFunction
	 * @return
	 */
	public default  
	SealBool mapToBool(Function<? super E, ? extends Boolean> mapFunction) {
		return _mapSetup(new SealBool(), mapFunction);
	}
	/**
	 * Map this {@link ReadDependency} through a Boolean-valued function
	 * given as a Predicate
	 * @param mapFunction
	 * @return
	 */
	public default  
	SealBool mapPrimitive(Predicate<? super E> mapFunction) {
		return _mapSetup(new SealBool(), v->mapFunction.test(v));
	}
	/**
	 * Map this {@link ReadDependency} through a Integer-valued function
	 * @param mapFunction
	 * @return
	 */
	public default  
	SealInt mapToInt(Function<? super E, ? extends Integer> mapFunction) {
		return _mapSetup(new SealInt(), mapFunction);
	}
	/**
	 * Map this {@link ReadDependency} through a Double-valued function
	 * @param mapFunction
	 * @return
	 */
	public default  
	SealDouble mapToDouble(Function<? super E, ? extends Double> mapFunction) {
		return _mapSetup(new SealDouble(), mapFunction);
	}
	/**
	 * Map this {@link ReadDependency} through a String-valued function
	 * @param mapFunction
	 * @return
	 */
	public default  
	SealString mapToString(Function<? super E, ? extends String> mapFunction) {
		return _mapSetup(new SealString(), mapFunction);
	}
	/**
	 * Map this {@link ReadDependency} through a {@link Predicate} used as a boolean-valued function
	 * @param mapFunction
	 * @return
	 */
	public default  
	SealBool mapToBoolP(Predicate<? super E> mapFunction) {
		return _mapSetup(new SealBool(), mapFunction::test);
	}
	/**
	 * Map this {@link ReadDependency} through a int-valued function
	 * @param mapFunction
	 * @return
	 */
	public default  
	SealInt mapToIntP(ToIntFunction<? super E> mapFunction) {
		return _mapSetup(new SealInt(), mapFunction::applyAsInt);
	}
	/**
	 * Map this {@link ReadDependency} through a double-valued function
	 * @param mapFunction
	 * @return
	 */
	public default  
	SealDouble mapToDoubleP(ToDoubleFunction<? super E> mapFunction) {
		return _mapSetup(new SealDouble(), mapFunction::applyAsDouble);
	}
	
	
	/**
	 * Map this {@link ReadDependency} through a function
	 * @param <F>
	 * @param mapFunction
	 * @param Additional configuration to be applied to the builder
	 * @return
	 */
	public default <F> 
	SealPile<? extends F> map(Function<? super E, ? extends F> mapFunction, Consumer<? super SealPileBuilder<SealPile<F>, F>> config) {
		return _mapSetup(new SealPile<>(), mapFunction, config);
	}
	/**
	 * Map this {@link ReadDependency} through a Boolean-valued function
	 * @param mapFunction
	 * @param Additional configuration to be applied to the builder
	 * @return
	 */
	public default  
	SealBool mapToBool(Function<? super E, ? extends Boolean> mapFunction, Consumer<? super SealPileBuilder<SealBool, Boolean>> config) {
		return _mapSetup(new SealBool(), mapFunction, config);
	}
	/**
	 * Map this {@link ReadDependency} through a Boolean-valued function
	 * given as a Predicate
	 * @param mapFunction
	 * @param Additional configuration to be applied to the builder
	 * @return
	 */
	public default  
	SealBool mapPrimitive(Predicate<? super E> mapFunction, Consumer<? super SealPileBuilder<SealBool, Boolean>> config) {
		return _mapSetup(new SealBool(), v->mapFunction.test(v), config);
	}
	/**
	 * Map this {@link ReadDependency} through a Integer-valued function
	 * @param mapFunction
	 * @param Additional configuration to be applied to the builder
	 * @return
	 */
	public default  
	SealInt mapToInt(Function<? super E, ? extends Integer> mapFunction, Consumer<? super SealPileBuilder<SealInt, Integer>> config) {
		return _mapSetup(new SealInt(), mapFunction, config);
	}
	/**
	 * Map this {@link ReadDependency} through a Double-valued function
	 * @param mapFunction
	 * @param Additional configuration to be applied to the builder
	 * @return
	 */
	public default  
	SealDouble mapToDouble(Function<? super E, ? extends Double> mapFunction, Consumer<? super SealPileBuilder<SealDouble, Double>> config) {
		return _mapSetup(new SealDouble(), mapFunction, config);
	}
	/**
	 * Map this {@link ReadDependency} through a String-valued function
	 * @param mapFunction
	 * @param Additional configuration to be applied to the builder
	 * @return
	 */
	public default  
	SealString mapToString(Function<? super E, ? extends String> mapFunction, Consumer<? super SealPileBuilder<SealString, String>> config) {
		return _mapSetup(new SealString(), mapFunction, config);
	}
	/**
	 * Map this {@link ReadDependency} through a {@link Predicate} used as a boolean-valued function
	 * @param mapFunction
	 * @param Additional configuration to be applied to the builder
	 * @return
	 */
	public default  
	SealBool mapToBoolP(Predicate<? super E> mapFunction, Consumer<? super SealPileBuilder<SealBool, Boolean>> config) {
		return _mapSetup(new SealBool(), mapFunction::test, config);
	}
	/**
	 * Map this {@link ReadDependency} through a int-valued function
	 * @param mapFunction
	 * @param Additional configuration to be applied to the builder
	 * @return
	 */
	public default  
	SealInt mapToIntP(ToIntFunction<? super E> mapFunction, Consumer<? super SealPileBuilder<SealInt, Integer>> config) {
		return _mapSetup(new SealInt(), mapFunction::applyAsInt, config);
	}
	/**
	 * Map this {@link ReadDependency} through a double-valued function
	 * @param mapFunction
	 * @param Additional configuration to be applied to the builder
	 * @return
	 */
	public default  
	SealDouble mapToDoubleP(ToDoubleFunction<? super E> mapFunction, Consumer<? super SealPileBuilder<SealDouble, Double>> config) {
		return _mapSetup(new SealDouble(), mapFunction::applyAsDouble, config);
	}
	/**
	 * Query whether this {@link ReadDependency} is guaranteed to never change,
	 * for example because it is a {@link Constant} or because it is a {@link Sealable}
	 * sealed with the default interceptor and not having a recomputer
	 * @return
	 */
	public boolean willNeverChange();
	/**
	 * Destroy this instance. It cannot be used afterwards.
	 * Any {@link Depender}s of which it is an essential dependency are also destroyed.
	 */
	public void destroy();
	/**
	 * Call {@link #destroy()} iff the {@code this.}{@link HasAssociations#isMarkedDisposable() isMarkedDisposable} 
	 * exists and returns true.
	 * @return
	 */
	default boolean destroyIfMarkedDisposable() {
		if(this instanceof HasAssociations) {
			if(((HasAssociations) this).isMarkedDisposable()) {
				destroy();
				return true;
			}else {
				return false;
			}
		}else {
			return false;
		}
	}

	/**
	 * Make a boolean {@link Pile} that reflects the equality of the wrapped values of this
	 * {@link ReadDependency} and another. The objects are compared using one of their
	 * {@link Object#equals(Object) equals()}-methods
	 * @param op2
	 * @return
	 */
	public default SealBool isEqual(ReadDependency<? extends Object> op2) {
		return PileBool.equalityComparison(this, op2, Boolean.TRUE, Boolean.FALSE);
	}

	/**
	 * Make a boolean {@link Pile} that reflects the inequality of the wrapped values of this
	 * {@link ReadDependency} and another. The objects are compared using one of their
	 * {@link Object#equals(Object) equals()}-methods
	 * @param op2
	 * @return
	 */
	public default SealBool isUnequal(ReadDependency<? extends Object> op2) {
		return PileBool.equalityComparison(this, op2, Boolean.FALSE, Boolean.TRUE);
	}
	/**
	 * Make a boolean {@link Pile} that reflects the equality of the wrapped value of this
	 * {@link ReadDependency} and a constant. The objects are compared using one of their
	 * {@link Object#equals(Object) equals()}-methods
	 * @param op2
	 * @return
	 */
	public default SealBool isEqualConst(Object op2) {
		return PileBool.equalityComparison(this, op2, Boolean.TRUE, Boolean.FALSE);
	}
	/**
	 * Make a boolean {@link Pile} that reflects the inequality of the wrapped value of this
	 * {@link ReadDependency} and a constant. The objects are compared using one of their
	 * {@link Object#equals(Object) equals()}-methods
	 * @param op2
	 * @return
	 */
	public default SealBool isUnequalConst(Object op2) {
		return PileBool.equalityComparison(this, op2, Boolean.FALSE, Boolean.TRUE);
	}
	/**
	 * Make a boolean {@link Pile} that reflects the equality of the wrapped values of this
	 * {@link ReadDependency} and another.
	 * @param op2
	 * @param eq the equivalence relation to use
	 * @return
	 */
	public default SealBool isEqual(ReadDependency<? extends E> op2, BiPredicate<? super E, ? super E> eq) {
		return PileBool.equalityComparison(this, op2, Boolean.TRUE, Boolean.FALSE, eq);
	}
	/**
	 * Make a boolean {@link Pile} that reflects the inequality of the wrapped values of this
	 * {@link ReadDependency} and another. 
	 * @param op2
	 * @param eq the equivalence relation to use
	 * @return
	 */
	public default SealBool isUnequal(ReadDependency<? extends E> op2, BiPredicate<? super E, ? super E> eq) {
		return PileBool.equalityComparison(this, op2, Boolean.FALSE, Boolean.TRUE, eq);
	}
	/**
	 * Make a boolean {@link Pile} that reflects the equality of the wrapped value of this
	 * {@link ReadDependency} and a constant. 
	 * @param op2
	 * @param eq the equivalence relation to use
	 * @return
	 */
	public default SealBool isEqualConst(E op2, BiPredicate<? super E, ? super E> eq) {
		return PileBool.equalityComparison(this, op2, Boolean.TRUE, Boolean.FALSE, eq);
	}
	/**
	 * Make a boolean {@link Pile} that reflects the inequality of the wrapped value of this
	 * {@link ReadDependency} and a constant. 
	 * @param op2
	 * @param eq the equivalence relation to use
	 * @return
	 */
	public default SealBool isUnequalConst(E op2, BiPredicate<? super E, ? super E> eq) {
		return PileBool.equalityComparison(this, op2, Boolean.FALSE, Boolean.TRUE, eq);
	}

	/**
	 * Make an Integer {@link Pile} that reflects the ordering of the wrapped values of this
	 * {@link ReadDependency} and another. 
	 * @param op2
	 * @param order the ordering relation relation to use
	 * @return
	 */
	public default SealInt compareTo(ReadDependency<? extends E> op2, Comparator<? super E> order) {
		return PileInt.comparison(this, op2, order);
	}
	/**
	 * Make an Integer {@link Pile} that reflects the ordering of the wrapped value of this
	 * {@link ReadDependency} and a constant. 
	 * @param op2
	 * @param order the ordering relation relation to use
	 * @return
	 */
	public default SealInt compareToConst(E op2, Comparator<? super E> order) {
		return PileInt.comparison(this, op2, order);
	}
	/**
	 * Make a Boolean {@link Pile} that reflects the ordering of the wrapped values of this
	 * {@link ReadDependency} and another. 
	 * @param op2
	 * @param order the ordering relation relation to use
	 * @return
	 */
	public default SealBool greaterThan(ReadDependency<? extends E> op2, Comparator<? super E> order) {
		return PileBool.greaterThan(this, op2, order);
	}
	/**
	 * Make a Boolean {@link Pile} that reflects the ordering of the wrapped values of this
	 * {@link ReadDependency} and another. 
	 * @param op2
	 * @param order the ordering relation relation to use
	 * @return
	 */
	public default SealBool lessThan(ReadDependency<? extends E> op2, Comparator<? super E> order) {
		return PileBool.lessThan(this, op2, order);
	}
	/**
	 * Make a Boolean {@link Pile} that reflects the ordering of the wrapped values of this
	 * {@link ReadDependency} and another. 
	 * @param op2
	 * @param order the ordering relation relation to use
	 * @return
	 */
	public default SealBool lessThanOrEqual(ReadDependency<? extends E> op2, Comparator<? super E> order) {
		return PileBool.lessThanOrEqual(this, op2, order);
	}
	/**
	 * Make a Boolean {@link Pile} that reflects the ordering of the wrapped values of this
	 * {@link ReadDependency} and another. 
	 * @param op2
	 * @param order the ordering relation relation to use
	 * @return
	 */
	public default SealBool greaterThanOrEqual(ReadDependency<? extends E> op2, Comparator<? super E> order) {
		return PileBool.greaterThanOrEqual(this, op2, order);
	}
	/**
	 * Make a Boolean {@link Pile} that reflects the ordering of the wrapped value of this
	 * {@link ReadDependency} and a constant. 
	 * @param op2
	 * @param order the ordering relation relation to use
	 * @return
	 */
	public default SealBool greaterThanConst(E op2, Comparator<? super E> order) {
		return PileBool.greaterThan(this, op2, order);
	}
	/**
	 * Make a Boolean {@link Pile} that reflects the ordering of the wrapped value of this
	 * {@link ReadDependency} and a constant. 
	 * @param op2
	 * @param order the ordering relation relation to use
	 * @return
	 */
	public default SealBool lessThanConst(E op2, Comparator<? super E> order) {
		return PileBool.lessThan(this, op2, order);
	}
	/**
	 * Make a Boolean {@link Pile} that reflects the ordering of the wrapped value of this
	 * {@link ReadDependency} and a constant. 
	 * @param op2
	 * @param order the ordering relation relation to use
	 * @return
	 */
	public default SealBool lessThanOrEqualConst(E op2, Comparator<? super E> order) {
		return PileBool.lessThanOrEqual(this, op2, order);
	}
	/**
	 * Make a Boolean {@link Pile} that reflects the ordering of the wrapped value of this
	 * {@link ReadDependency} and a constant. 
	 * @param op2
	 * @param order the ordering relation relation to use
	 * @return
	 */
	public default SealBool greaterThanOrEqualConst(E op2, Comparator<? super E> order) {
		return PileBool.greaterThanOrEqual(this, op2, order);
	}

	/**
	 * Make a wrapper around this {@link ReadDependency} that reflects its current value but cannot
	 * be modified
	 * @return
	 */
	public default SealPile<E> readOnly(){
		return Piles.readOnlyWrapper(this);
	}
	/**
	 * Make a {@link Pile} that reflects the wrapped value of a {@link ReadListenDependency}-valued
	 * field of this {@link ReadDependency}'s wrapped value.
	 * @param <I>
	 * @param <E2>
	 * @param extract The function that extracts the field. Whenever {@code this} holds a <code>null</code>
	 * value, the function will not be called and the returned {@link SealPile} will be invalid.
	 * @return
	 */
	public default <I extends ReadDependency<? extends E2>, E2> SealPile<E2> field(Function<? super E, ? extends I> extract) {
		return fieldBuilder(false, extract).build();
	}
	/**
	 * Make a builder for a {@link Pile} that reflects the wrapped value of a {@link ReadListenDependency}-valued
	 * field of this {@link ReadDependency}'s wrapped value.
	 * Use this instead of {@link #field(Function)} if you need more detailed configuration
	 * of the resulting {@link Pile}. 
	 * @param <I>
	 * @param <E2>
	 * @param nullable Whether the {@code extract} function should be called even if its argument is <code>null</code>
	 * @param extract
	 * @see ISealPileBuilder#setupField(ReadDependency, boolean, Function)
	 * @return
	 */
	public default <I extends ReadDependency<? extends E2>, E2> 
	SealPileBuilder<SealPile<E2>, E2> fieldBuilder(	
			boolean nullable,
			Function<? super E, ? extends I> extract) {
		return Piles.<E2>sb().setupField(this, nullable, extract);
	}
	/**
	 * Specialization of {@link #field(Function)} for {@link Boolean}-valued fields.
	 * @param <I>
	 * @param <E2>
	 * @param extract The function that extracts the aspect
	 * @return
	 */
	public default <I extends ReadDependency<? extends Boolean>> SealBool fieldBool(Function<? super E, ? extends I> extract) {
		return fieldBuilderBool(false, extract).build();
	}
	/** 
	 * Specialization of {@link #fieldBuilderBool(boolean, Function)} for {@link Boolean}-valued fields.
	 * @param <I>
	 * @param nullable
	 * @param extract
	 * @return
	 */
	public default <I extends ReadDependency<? extends Boolean>> SealPileBuilder<SealBool, Boolean> fieldBuilderBool(
			boolean nullable,
			Function<? super E, ? extends I> extract) {
		return PileBool.sb().setupField(this, nullable, extract);
	}
	/**
	 * Specialization of {@link #field(Function)} for {@link Double}-valued fields.
	 * @param <I>
	 * @param <E2>
	 * @param extract The function that extracts the aspect
	 * @return
	 */
	public default <I extends ReadDependency<? extends Double>> SealDouble fieldDouble(Function<? super E, ? extends I> extract) {
		return fieldBuilderDouble(false, extract).build();
	}
	/** 
	 * Specialization of {@link #fieldBuilderBool(boolean, Function)} for {@link Double}-valued fields.
	 * @param <I>
	 * @param nullable
	 * @param extract
	 * @return
	 */
	public default <I extends ReadDependency<? extends Double>> SealPileBuilder<SealDouble, Double> fieldBuilderDouble(
			boolean nullable,
			Function<? super E, ? extends I> extract) {
		return PileDouble.sb().setupField(this, nullable, extract);
	}
	/**
	 * Specialization of {@link #field(Function)} for {@link Integer}-valued fields.
	 * @param <I>
	 * @param <E2>
	 * @param extract The function that extracts the aspect
	 * @return
	 */
	public default <I extends ReadDependency<? extends Integer>> SealInt fieldInt(Function<? super E, ? extends I> extract) {
		return fieldBuilderInt(false, extract).build();
	}
	/** 
	 * Specialization of {@link #fieldBuilderBool(boolean, Function)} for {@link Integer}-valued fields.
	 * @param <I>
	 * @param nullable
	 * @param extract
	 * @return
	 */
	public default <I extends ReadDependency<? extends Integer>> SealPileBuilder<SealInt, Integer> fieldBuilderInt(
			boolean nullable,
			Function<? super E, ? extends I> extract) {
		return PileInt.sb().setupField(this, nullable, extract);
	}
	/** 
	 * Specialization of {@link #fieldBuilderBool(boolean, Function)} for {@link String}-valued fields.
	 * @param <I>
	 * @param nullable
	 * @param extract
	 * @return
	 */
	public default <I extends ReadDependency<? extends String>> SealString fieldString(Function<? super E, ? extends I> extract) {
		return fieldBuilderString(false, extract).build();
	}
	/** 
	 * Specialization of {@link #fieldBuilderBool(boolean, Function)} for {@link String}-valued fields.
	 * @param <I>
	 * @param nullable
	 * @param extract
	 * @return
	 */
	public default <I extends ReadDependency<? extends String>> SealPileBuilder<SealString, String> fieldBuilderString(
			boolean nullable,
			Function<? super E, ? extends I> extract) {
		return PileString.sb().setupField(this, nullable, extract);
	}

	/**
	 * Make a {@link Pile} that reflects the wrapped 
	 * value of a {@link ReadListenDependency}-valued
	 * field of this {@link ReadDependency}'s wrapped value.
	 * Writing to the returned {@link SealPile} affects the field, if if currently exists.
	 * @param <I>
	 * @param <E2>
	 * @param extract The function that extracts the field. Whenever {@code this} holds a <code>null</code>
	 * value, the function will not be called and the returned {@link SealPile} will be invalid.
	 * @return
	 */
	public default <I extends ReadWriteDependency<E2>, E2> SealPile<E2> writableField(Function<? super E, ? extends I> extract) {
		return writableFieldBuilder(false, extract).build();
	}
	/**
	 * Make a builder for a {@link Pile} that reflects the wrapped 
	 * value of a {@link ReadListenDependency}-valued
	 * field of this {@link ReadDependency}'s wrapped value.
	 * Writing to the returned {@link SealPile} affects the field, if if currently exists.
	 * @param <I>
	 * @param <E2>
	 * @param nullable Whether the {@code extract} function should be called even if its argument is <code>null</code>
	 * @param extract The function that extracts the field. 
	 * @return
	 */	
	public default <I extends ReadWriteDependency<E2>, E2> SealPileBuilder<SealPile<E2>, E2> 
	writableFieldBuilder(
			boolean nullable,
			Function<? super E, ? extends I> extract) {
		return Piles.<E2>sb().setupWritableField(this, nullable, extract);
	}

	/**
	 * Specialization of {@link #writableField(Function)} for {@link Boolean}-valued fields.
	 */
	public default <I extends ReadWriteDependency<Boolean>> SealBool writableFieldBool(Function<? super E, ? extends I> extract) {
		return writableFieldBuilderBool(false, extract).build();
	}
	/**
	 *  Specialization of {@link #writableFieldBuilderBool(boolean, Function)} for {@link Boolean}-valued fields. 
	 */
	public default <I extends ReadWriteDependency<Boolean>> SealPileBuilder<SealBool, Boolean> writableFieldBuilderBool(
			boolean nullable,
			Function<? super E, ? extends I> extract) {
		return PileBool.sb().setupWritableField(this, nullable, extract);
	}
	/**
	 * Specialization of {@link #writableField(Function)} for {@link Double}-valued fields.
	 */
	public default <I extends ReadWriteDependency<Double>> SealDouble writableFieldDouble(Function<? super E, ? extends I> extract) {
		return writableFieldBuilderDouble(false, extract).build();
	}
	/**
	 *  Specialization of {@link #writableFieldBuilderBool(boolean, Function)} for {@link Double}-valued fields. 
	 */
	public default <I extends ReadWriteDependency<Double>> SealPileBuilder<SealDouble, Double> writableFieldBuilderDouble(
			boolean nullable,
			Function<? super E, ? extends I> extract) {
		return PileDouble.sb().setupWritableField(this, nullable, extract);
	}
	/**
	 * Specialization of {@link #writableField(Function)} for {@link Integer}-valued fields.
	 */
	public default <I extends ReadWriteDependency<Integer>> SealInt writableFieldInt(Function<? super E, ? extends I> extract) {
		return writableFieldBuilderInt(false, extract).build();
	}
	/**
	 *  Specialization of {@link #writableFieldBuilderBool(boolean, Function)} for {@link Integer}-valued fields. 
	 */
	public default <I extends ReadWriteDependency<Integer>> SealPileBuilder<SealInt, Integer> writableFieldBuilderInt(
			boolean nullable,
			Function<? super E, ? extends I> extract) {
		return PileInt.sb().setupWritableField(this, nullable, extract);
	}
	/**
	 * Specialization of {@link #writableField(Function)} for {@link String}-valued fields.
	 */
	public default <I extends ReadWriteDependency<String>> SealString writableFieldString(Function<? super E, ? extends I> extract) {
		return writableFieldBuilderString(false, extract).build();
	}
	/**
	 *  Specialization of {@link #writableFieldBuilderBool(boolean, Function)} for {@link String}-valued fields. 
	 */
	public default <I extends ReadWriteDependency<String>> SealPileBuilder<SealString, String> writableFieldBuilderString(
			boolean nullable,
			Function<? super E, ? extends I> extract) {
		return PileString.sb().setupWritableField(this, nullable, extract);
	}

	/**
	 * Make a value that follows this {@link ReadDependency}'s value, 
	 * but when it is {@linkplain WriteValue#set(Object) written to},
	 * it takes on a different value until this {@link ReadDependency} changes again or becomes invalid.
	 * @return
	 */

	public default PileImpl<E> overridable() {
		return Piles.compute(this::get).name(dependencyName()+"*").whenChanged(this);
	}
	/**
	 * Get the equivalence relation used by this {@link ReadDependency} 
	 * to decide whether a change in the wrapped value really counts as a change.
	 * @return
	 */
	public BiPredicate<? super E, ? super E> _getEquivalence();
	/**
	 * Return a value that becomes <code>true</code> is this {@link ReadListenDependency}
	 * takes on a value of <code>null</code> of becomes observably invalid.
	 */
	public ReadListenDependencyBool nullOrInvalid();


}
