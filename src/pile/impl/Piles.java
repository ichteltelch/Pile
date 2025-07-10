package pile.impl;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.logging.Logger;

import pile.aspect.Dependency;
import pile.aspect.Depender;
import pile.aspect.HasInfluencers;
import pile.aspect.LastValueRememberer;
import pile.aspect.ReadValue;
import pile.aspect.ReadValue.InvalidValueException;
import pile.aspect.Sealable;
import pile.aspect.combinations.Pile;
import pile.aspect.combinations.ReadDependency;
import pile.aspect.combinations.ReadListenDependency;
import pile.aspect.combinations.ReadListenValue;
import pile.aspect.combinations.ReadWriteDependency;
import pile.aspect.combinations.ReadWriteListenDependency;
import pile.aspect.combinations.ReadWriteListenValue;
import pile.aspect.listen.ValueListener;
import pile.aspect.recompute.Recomputation;
import pile.aspect.suppress.MockBlock;
import pile.aspect.suppress.Suppressor;
import pile.aspect.transform.TransformReaction;
import pile.builder.AbstractPileBuilder;
import pile.builder.IIndependentBuilder;
import pile.builder.IPileBuilder;
import pile.builder.ISealPileBuilder;
import pile.builder.IndependentBuilder;
import pile.builder.PileBuilder;
import pile.builder.SealPileBuilder;
import pile.specialized_String.ConstantString;
import pile.specialized_String.IndependentString;
import pile.specialized_String.PileString;
import pile.specialized_String.PileStringImpl;
import pile.specialized_String.SealString;
import pile.specialized_String.combinations.LastValueRemembererString;
import pile.specialized_String.combinations.ReadListenDependencyString;
import pile.specialized_bool.ConstantBool;
import pile.specialized_bool.IndependentBool;
import pile.specialized_bool.PileBool;
import pile.specialized_bool.PileBool.BoolAggregator;
import pile.specialized_bool.PileBoolImpl;
import pile.specialized_bool.SealBool;
import pile.specialized_bool.combinations.LastValueRemembererBool;
import pile.specialized_bool.combinations.ReadDependencyBool;
import pile.specialized_bool.combinations.ReadListenDependencyBool;
import pile.specialized_double.ConstantDouble;
import pile.specialized_double.IndependentDouble;
import pile.specialized_double.PileDouble;
import pile.specialized_double.PileDoubleImpl;
import pile.specialized_double.SealDouble;
import pile.specialized_double.combinations.LastValueRemembererDouble;
import pile.specialized_int.ConstantInt;
import pile.specialized_int.IndependentInt;
import pile.specialized_int.PileInt;
import pile.specialized_int.PileIntImpl;
import pile.specialized_int.SealInt;
import pile.specialized_int.combinations.LastValueRemembererInt;
import pile.utils.Functional;

public class Piles {

	
	@SuppressWarnings("unused")
	private final static Logger log=Logger.getLogger("Values");

	private static ThreadLocal<Boolean> shouldFireDeepRevalidateOnSet=new ThreadLocal<>();
	private static ThreadLocal<Boolean> shouldDeepRevalidate=new ThreadLocal<>();

	
	public static final ConstantBool TRUE = constant(true);
	public static final ConstantBool FALSE = constant(false);
	public static final Constant<?> NULL = new Constant<>(null);
	public static final ConstantString EMPTY_STRING = constant("");


	public static final ConstantDouble NULL_D = Piles.constant((Double)null);
	public static final ConstantDouble ZERO_D = Piles.constant(0d);
	public static final ConstantDouble ONE_D = Piles.constant(1d);
	public static final ConstantDouble POSITIVE_INFINITY_D = Piles.constant(Double.POSITIVE_INFINITY);
	public static final ConstantDouble NEGATIVE_INFINITY_D = Piles.constant(Double.NEGATIVE_INFINITY);

	public static final ConstantBool NULL_B = Piles.constant((Boolean)null);


	public static final ConstantInt NULL_I = Piles.constant((Integer)null);
	public static final ConstantInt ZERO_I = Piles.constant(0);
	public static final ConstantInt ONE_I = Piles.constant(1);
	public static final ConstantInt MIN_VALUE_I = Piles.constant(Integer.MIN_VALUE);
	public static final ConstantInt MAX_VALUE_I = Piles.constant(Integer.MAX_VALUE);

	public static final ReadWriteListenDependency<?> CONST_INVALID = makeNewConstInvalid();
	@SuppressWarnings("unchecked")
	public static <V> ReadWriteListenDependency<V> constInvalid() {
		return (ReadWriteListenDependency<V>) CONST_INVALID;
	}

	private static <V> ReadWriteListenDependency<V> makeNewConstInvalid() {
		SealPile<V> ret = new SealPile<>();
		ret.avName = "CONST_INVALID";
		ret.seal(Functional.NOP, true);
		return ret;
	}

	//////////////////////////// Methods for making constants
	/**
	 * Return the {@link #NULL} constant, cast to the appropriate type. 
	 * @param <E>
	 * @return
	 */
	@SuppressWarnings("unchecked")
	public static <E> Constant<E> constNull(){
		return (Constant<E>) NULL;
	}

	/**
	 * Make a {@link Constant} with the given value
	 * @param <E>
	 * @param value
	 * @return
	 */
	public static <E> Constant<E> constant(E value){
		return new Constant<>(value);
	}
	/**
	 * Make a somewhat unmodifiable {@link SealPile} with the given value.
	 * Note: Starting a transactions on the {@link SealPile} will cause it to become 
	 * invalid for duration of the transaction
	 * @param <E>
	 * @param value
	 * @return
	 */
	public static <E> SealPile<E> sealedConstant(E value){
		SealPile<E> ret = new SealPile<E>();
		ret.set(value);
		ret.seal();
		return ret;
	}
	/**  @see #constant(Object) */
	public static <E> ConstantBool constant(Boolean value){
		return new ConstantBool(value);
	}
	public static <E> ConstantBool getConstant(boolean value){
		return value?TRUE:FALSE;
	}
	/**  @see #sealedConstant(Object) */
	public static SealBool sealedConstant(Boolean value){
		SealBool ret = new SealBool();
		ret.set(value);
		ret.seal();
		return ret;
	}
	/**  @see #constant(Object) */
	public static <E> ConstantDouble constant(Double value){
		return new ConstantDouble(value);
	}
	/**  @see #sealedConstant(Object) */
	public static SealDouble sealedConstant(Double value){
		SealDouble ret = new SealDouble();
		ret.set(value);
		ret.seal();
		return ret;
	}
	/**  @see #constant(Object) */
	public static <E> ConstantInt constant(Integer value){
		return new ConstantInt(value);
	}
	/**  @see #sealedConstant(Object) */
	public static SealInt sealedConstant(Integer value){
		SealInt ret = new SealInt();
		ret.set(value);
		ret.seal();
		return ret;
	}
	/**  @see #constant(Object) */
	public static <E> ConstantString constant(String value){
		return new ConstantString(value);
	}
	/**  @see #sealedConstant(Object) */
	public static SealString sealedConstant(String value){
		SealString ret = new SealString();
		ret.set(value);
		ret.seal();
		return ret;
	}

	/**
	 * Method for starting a ValueBuilder by specifying initial value
	 */
	public static <E> PileBuilder<PileImpl<E>, E> init(E init){
		return new PileBuilder<>(new PileImpl<E>()).init(init);
	}
	/**
	 * Method for starting a ValueBuilder by specifying initial value
	 */
	public static PileBuilder<PileBoolImpl, Boolean> init(Boolean init){
		return new PileBuilder<>(new PileBoolImpl()).init(init).ordering(Comparator.naturalOrder());
	}
	/**
	 * Method for starting a ValueBuilder by specifying initial value
	 */
	public static PileBuilder<PileIntImpl, Integer> init(Integer init){
		return new PileBuilder<>(new PileIntImpl()).init(init).ordering(Comparator.naturalOrder());
	}
	/**
	 * Method for starting a ValueBuilder by specifying initial value
	 */
	public static PileBuilder<PileDoubleImpl, Double> init(Double init){
		return new PileBuilder<>(new PileDoubleImpl()).init(init).ordering(Comparator.naturalOrder());
	}
	/**
	 * Method for starting a ValueBuilder by specifying initial value
	 */
	public static PileBuilder<PileStringImpl, String> init(String init){
		return new PileBuilder<>(new PileStringImpl()).init(init).ordering(Comparator.naturalOrder());
	}

	/**
	 * Make a generic ValueBuilder
	 * @param <E>
	 * @return
	 */
	public static <E> PileBuilder<PileImpl<E>, E> generic(){
		return new PileBuilder<>(new PileImpl<>());
	}
	/**
	 * Make a generic {@link SealPileBuilder}
	 * @param <E>
	 * @return
	 */
	public static <E> SealPileBuilder<SealPile<E>, E> genericSealable(){
		return new SealPileBuilder<>(new SealPile<>());
	}
	/**
	 * Make a generic {@link IndependentBuilder}
	 * @param <E>
	 * @return
	 */
	public static <E> IndependentBuilder<Independent<E>, E> genericIndependent(){
		return new IndependentBuilder<>(new Independent<>(null));
	}

	/**
	 * Method for starting a {@link PileBuilder} by specifying how the value should be recomputed.
	 * @see PileBuilder#recompute(Supplier)
	 */
	public static <E> PileBuilder<PileImpl<E>, E> compute(Supplier<? extends E> howToRecompute){
		return new PileBuilder<>(new PileImpl<E>()).recompute(howToRecompute);
	}
	/**
	 * Method for starting a {@link PileBuilder} by specifying how the value should be recomputed.
	 * The natural ordering relation is pre-configured in case you want to put bounds on the value. 
	 * @see PileBuilder#recompute(Supplier)
	 */
	public static PileBuilder<PileBoolImpl, Boolean> computeBool(Supplier<? extends Boolean> howToRecompute){
		return new PileBuilder<>(new PileBoolImpl()).ordering(Comparator.naturalOrder()).recompute(howToRecompute::get);
	}
	/**
	 * Method for starting a {@link PileBuilder} by specifying how the value should be recomputed.
	 * The natural ordering relation is pre-configured in case you want to put bounds on the value. 
	 * @see PileBuilder#recompute(Supplier)
	 */
	public static PileBuilder<PileBoolImpl, Boolean> decide(BooleanSupplier howToRecompute){
		return new PileBuilder<>(new PileBoolImpl()).ordering(Comparator.naturalOrder()).recompute(howToRecompute::getAsBoolean);
	}
	/**
	 * Method for starting a {@link PileBuilder} by specifying how the value should be recomputed.
	 * The natural ordering relation is pre-configured in case you want to put bounds on the value. 
	 * @see PileBuilder#recompute(Supplier)
	 */
	public static PileBuilder<PileIntImpl, Integer> computeInt(Supplier<? extends Integer> howToRecompute){
		return new PileBuilder<>(new PileIntImpl()).ordering(Comparator.naturalOrder()).recompute(howToRecompute::get);
	}
	/**
	 * Method for starting a {@link PileBuilder} by specifying how the value should be recomputed.
	 * The natural ordering relation is pre-configured in case you want to put bounds on the value. 
	 * @see PileBuilder#recompute(Supplier)
	 */
	public static PileBuilder<PileDoubleImpl, Double> computeDouble(Supplier<? extends Double> howToRecompute){
		return new PileBuilder<>(new PileDoubleImpl()).ordering(Comparator.naturalOrder()).recompute(howToRecompute::get);
	}
	/**
	 * Method for starting a {@link PileBuilder} by specifying how the value should be recomputed.
	 * The natural ordering relation is pre-configured in case you want to put bounds on the value. 
	 * @see PileBuilder#recompute(Supplier)
	 */
	public static PileBuilder<PileStringImpl, String> computeString(Supplier<? extends String> howToRecompute){
		return new PileBuilder<>(new PileStringImpl()).ordering(Comparator.naturalOrder()).recompute(howToRecompute);
	}

	/**
	 * Calls {@link #compute(Supplier)}. Use this method to resolve overloading issues.
	 */
	public static <E> PileBuilder<PileImpl<E>, E> computeS(Supplier<? extends E> howToRecompute){
		return compute(howToRecompute);
	}
	/**
	 * Calls {@link #computeSBool(Supplier)}. Use this method to resolve overloading issues.
	 */
	public static PileBuilder<PileBoolImpl, Boolean> computeSBool(Supplier<? extends Boolean> howToRecompute){
		return computeBool(howToRecompute);
	}
	/**
	 * Calls {@link #decide(BooleanSupplier)}. Use this method to resolve overloading issues.
	 */
	public static PileBuilder<PileBoolImpl, Boolean> decideS(BooleanSupplier howToRecompute){
		return decide(howToRecompute);
	}
	/**
	 * Calls {@link #computeInt(Supplier)}. Use this method to resolve overloading issues.
	 */
	public static PileBuilder<PileIntImpl, Integer> computeSInt(Supplier<? extends Integer> howToRecompute){
		return computeInt(howToRecompute);
	}
	/**
	 * Calls {@link #computeDouble(Supplier)}. Use this method to resolve overloading issues.
	 */
	public static PileBuilder<PileDoubleImpl, Double> computeSDouble(Supplier<? extends Double> howToRecompute){
		return computeDouble(howToRecompute);
	}
	/**
	 * Calls {@link #computeString(Supplier)}. Use this method to resolve overloading issues.
	 */
	public static PileBuilder<PileStringImpl, String> computeSString(Supplier<? extends String> howToRecompute){
		return computeString(howToRecompute);
	}


	/**
	 * Method for starting a {@link PileBuilder} by specifying how the value should be recomputed.
	 * @see PileBuilder#recompute(Consumer)
	 */
	public static <E> PileBuilder<PileImpl<E>, E> compute(Consumer<? super Recomputation<E>> howToRecompute){
		return new PileBuilder<>(new PileImpl<E>()).recompute(howToRecompute);
	}
	/**
	 * Method for starting a {@link PileBuilder} by specifying how the value should be recomputed.
	 * The natural ordering relation is pre-configured in case you want to put bounds on the value. 
	 * @see PileBuilder#recompute(Consumer)
	 */
	public static PileBuilder<PileBoolImpl, Boolean> computeBool(Consumer<? super Recomputation<Boolean>> howToRecompute){
		return new PileBuilder<>(new PileBoolImpl()).ordering(Comparator.naturalOrder()).recompute(howToRecompute);
	}
	/**
	 * Method for starting a {@link PileBuilder} by specifying how the value should be recomputed.
	 * The natural ordering relation is pre-configured in case you want to put bounds on the value. 
	 * @see PileBuilder#recompute(Consumer)
	 */
	public static PileBuilder<PileBoolImpl, Boolean> decide(Consumer<? super Recomputation<Boolean>> howToRecompute){
		return new PileBuilder<>(new PileBoolImpl()).ordering(Comparator.naturalOrder()).recompute(howToRecompute);
	}
	/**
	 * Method for starting a {@link PileBuilder} by specifying how the value should be recomputed.
	 * The natural ordering relation is pre-configured in case you want to put bounds on the value. 
	 * @see PileBuilder#recompute(Consumer)
	 */
	public static PileBuilder<PileIntImpl, Integer> computeInt(Consumer<? super Recomputation<Integer>> howToRecompute){
		return new PileBuilder<>(new PileIntImpl()).ordering(Comparator.naturalOrder()).recompute(howToRecompute);
	}
	/**
	 * Method for starting a {@link PileBuilder} by specifying how the value should be recomputed.
	 * The natural ordering relation is pre-configured in case you want to put bounds on the value. 
	 * @see PileBuilder#recompute(Consumer)
	 */
	public static PileBuilder<PileDoubleImpl, Double> computeDouble(Consumer<? super Recomputation<Double>> howToRecompute){
		return new PileBuilder<>(new PileDoubleImpl()).ordering(Comparator.naturalOrder()).recompute(howToRecompute);
	}
	/**
	 * Method for starting a {@link PileBuilder} by specifying how the value should be recomputed.
	 * The natural ordering relation is pre-configured in case you want to put bounds on the value. 
	 * @see PileBuilder#recompute(Consumer)
	 */
	public static PileBuilder<PileStringImpl, String> computeString(Consumer<? super Recomputation<String>> howToRecompute){
		return new PileBuilder<>(new PileStringImpl()).ordering(Comparator.naturalOrder()).recompute(howToRecompute);
	}

	/**
	 * Calls {@link #compute(Consumer)}. Use this method to resolve overloading issues.
	 */	
	public static <E> PileBuilder<PileImpl<E>, E> computeX(Consumer<? super Recomputation<E>> howToRecompute){
		return compute(howToRecompute);
	}
	/**
	 * Calls {@link #computeBool(Consumer)}. Use this method to resolve overloading issues.
	 */	
	public static PileBuilder<PileBoolImpl, Boolean> computeXBool(Consumer<? super Recomputation<Boolean>> howToRecompute){
		return computeBool(howToRecompute);
	}
	/**
	 * Calls {@link #decide(Consumer)}. Use this method to resolve overloading issues.
	 */	
	public static PileBuilder<PileBoolImpl, Boolean> decideX(Consumer<? super Recomputation<Boolean>> howToRecompute){
		return decide(howToRecompute);
	}
	/**
	 * Calls {@link #computeInt(Consumer)}. Use this method to resolve overloading issues.
	 */	
	public static PileBuilder<PileIntImpl, Integer> computeXInt(Consumer<? super Recomputation<Integer>> howToRecompute){
		return computeInt(howToRecompute);
	}
	/**
	 * Calls {@link #computeDouble(Consumer)}. Use this method to resolve overloading issues.
	 */	
	public static PileBuilder<PileDoubleImpl, Double> computeXDouble(Consumer<? super Recomputation<Double>> howToRecompute){
		return computeDouble(howToRecompute);
	}
	/**
	 * Calls {@link #computeString(Consumer)}. Use this method to resolve overloading issues.
	 */	
	public static PileBuilder<PileStringImpl, String> computeXString(Consumer<? super Recomputation<String>> howToRecompute){
		return computeString(howToRecompute);
	}

	/**
	 * Method for starting a {@link PileBuilder} by specifying how the value should be recomputed.
	 * @see PileBuilder#recomputeStaged(Function)
	 */
	public static <E> PileBuilder<PileImpl<E>, E> computeStaged(Function<? super Recomputation<E>, ? extends Runnable> howToRecompute){
		return new PileBuilder<>(new PileImpl<E>()).recomputeStaged(howToRecompute);
	}
	/**
	 * Method for starting a {@link PileBuilder} by specifying how the value should be recomputed.
	 * The natural ordering relation is pre-configured in case you want to put bounds on the value. 
	 * @see PileBuilder#recomputeStaged(Function)
	 */
	public static PileBuilder<PileBoolImpl, Boolean> computeStagedBool(Function<? super Recomputation<Boolean>, ? extends Runnable> howToRecompute){
		return new PileBuilder<>(new PileBoolImpl()).ordering(Comparator.naturalOrder()).recomputeStaged(howToRecompute);
	}
	/**
	 * Method for starting a {@link PileBuilder} by specifying how the value should be recomputed.
	 * The natural ordering relation is pre-configured in case you want to put bounds on the value. 
	 * @see PileBuilder#recomputeStaged(Function)
	 */
	public static PileBuilder<PileBoolImpl, Boolean> decideStaged(Function<? super Recomputation<Boolean>, ? extends Runnable> howToRecompute){
		return new PileBuilder<>(new PileBoolImpl()).ordering(Comparator.naturalOrder()).recomputeStaged(howToRecompute);
	}
	/**
	 * Method for starting a {@link PileBuilder} by specifying how the value should be recomputed.
	 * The natural ordering relation is pre-configured in case you want to put bounds on the value. 
	 * @see PileBuilder#recomputeStaged(Function)
	 */
	public static PileBuilder<PileIntImpl, Integer> computeStagedInt(Function<? super Recomputation<Integer>, ? extends Runnable> howToRecompute){
		return new PileBuilder<>(new PileIntImpl()).ordering(Comparator.naturalOrder()).recomputeStaged(howToRecompute);
	}
	/**
	 * Method for starting a {@link PileBuilder} by specifying how the value should be recomputed.
	 * The natural ordering relation is pre-configured in case you want to put bounds on the value. 
	 * @see PileBuilder#recomputeStaged(Function)
	 */
	public static PileBuilder<PileDoubleImpl, Double> computeStagedDouble(Function<? super Recomputation<Double>, ? extends Runnable> howToRecompute){
		return new PileBuilder<>(new PileDoubleImpl()).ordering(Comparator.naturalOrder()).recomputeStaged(howToRecompute);
	}
	/**
	 * Method for starting a {@link PileBuilder} by specifying how the value should be recomputed.
	 * The natural ordering relation is pre-configured in case you want to put bounds on the value. 
	 * @see PileBuilder#recomputeStaged(Function)
	 */
	public static PileBuilder<PileStringImpl, String> computeStagedString(Function<? super Recomputation<String>, ? extends Runnable> howToRecompute){
		return new PileBuilder<>(new PileStringImpl()).ordering(Comparator.naturalOrder()).recomputeStaged(howToRecompute);
	}


	/**
	 * Method for starting an IndependentBuilder by specifying how the value should be backed
	 * by some {@link LastValueRememberer}
	 */
	public static <E> IndependentBuilder<Independent<E>, E> 
	remembered(LastValueRememberer<E> rem) {
		return new IndependentBuilder<>(new Independent<E>(null))
				.fromStore(rem, true);
	}
	/**
	 * Method for starting an IndependentBuilder by specifying how the value should be backed
	 * The natural ordering relation is pre-configured in case you want to put bounds on the value. 
	 * by some {@link LastValueRemembererBool}
	 */
	public static IndependentBuilder<IndependentBool, Boolean> 
	remembered(LastValueRemembererBool rem) {
		return new IndependentBuilder<>(new IndependentBool(null))
				.ordering(Comparator.naturalOrder())
				.fromStore(rem, true);
	}
	/**
	 * Method for starting an IndependentBuilder by specifying how the value should be backed
	 * The natural ordering relation is pre-configured in case you want to put bounds on the value. 
	 * by some {@link LastValueRememberer}
	 */
	public static IndependentBuilder<IndependentBool, Boolean> 
	rememberedBool(LastValueRememberer<Boolean> rem) {
		return new IndependentBuilder<>(new IndependentBool(null))
				.ordering(Comparator.naturalOrder())
				.fromStore(rem, true);
	}
	/**
	 * Method for starting an IndependentBuilder by specifying how the value should be backed
	 * The natural ordering relation is pre-configured in case you want to put bounds on the value. 
	 * by some {@link LastValueRememberer}
	 */
	public static IndependentBuilder<IndependentInt, Integer> 
	rememberedInt(LastValueRememberer<Integer> rem) {
		return new IndependentBuilder<>(new IndependentInt(null))
				.ordering(Comparator.naturalOrder())
				.fromStore(rem, true);
	}
	/**
	 * Method for starting an IndependentBuilder by specifying how the value should be backed
	 * The natural ordering relation is pre-configured in case you want to put bounds on the value. 
	 * by some {@link LastValueRemembererInt}
	 */
	public static IndependentBuilder<IndependentInt, Integer> 
	remembered(LastValueRemembererInt rem) {
		return new IndependentBuilder<>(new IndependentInt(null))
				.ordering(Comparator.naturalOrder())
				.fromStore(rem, true);
	}
	/**
	 * Method for starting an IndependentBuilder by specifying how the value should be backed
	 * The natural ordering relation is pre-configured in case you want to put bounds on the value. 
	 * by some {@link LastValueRemembererDouble}
	 */
	public static IndependentBuilder<IndependentDouble, Double> 
	remembered(LastValueRemembererDouble rem) {
		return new IndependentBuilder<>(new IndependentDouble(null))
				.ordering(Comparator.naturalOrder())
				.fromStore(rem, true);
	}
	/**
	 * Method for starting an IndependentBuilder by specifying how the value should be backed
	 * The natural ordering relation is pre-configured in case you want to put bounds on the value. 
	 * by some {@link LastValueRememberer}
	 */
	public static IndependentBuilder<IndependentDouble, Double> 
	rememberedDouble(LastValueRememberer<Double> rem) {
		return new IndependentBuilder<>(new IndependentDouble(null))
				.ordering(Comparator.naturalOrder())
				.fromStore(rem, true);
	}
	/**
	 * Method for starting an IndependentBuilder by specifying how the value should be backed
	 * The natural ordering relation is pre-configured in case you want to put bounds on the value. 
	 * by some {@link LastValueRemembererString}
	 */
	public static IndependentBuilder<IndependentString, String> 
	remembered(LastValueRemembererString rem) {
		return new IndependentBuilder<>(new IndependentString(null))
				.ordering(Comparator.naturalOrder())
				.fromStore(rem, true);
	}
	/**
	 * Method for starting an IndependentBuilder by specifying how the value should be backed
	 * The natural ordering relation is pre-configured in case you want to put bounds on the value. 
	 * by some {@link LastValueRememberer}
	 */
	public static IndependentBuilder<IndependentString, String> 
	rememberedString(LastValueRememberer<String> rem) {
		return new IndependentBuilder<>(new IndependentString(null))
				.ordering(Comparator.naturalOrder())
				.fromStore(rem, true);
	}
	/**
	 * Method for starting an {@link IndependentBuilder} by giving an initial value
	 */
	public static <E> IndependentBuilder<Independent<E>, E> 
	independent(E init) {
		return new IndependentBuilder<>(new Independent<E>(null)).init(init);
	}
	/**
	 * Method for starting an {@link IndependentBuilder} for an {@link Independent}
	 * that initially holds a <code>null</code> value
	 */
	public static <E> IndependentBuilder<Independent<E>, E> 
	independent() {
		return new IndependentBuilder<>(new Independent<E>(null));
	}
	/**
	 * Method for starting an {@link IndependentBuilder} by giving an initial value
	 * The natural ordering relation is pre-configured in case you want to put bounds on the value. 
	 */
	public static IndependentBuilder<IndependentBool, Boolean> 
	independent(Boolean init) {
		return new IndependentBuilder<>(new IndependentBool(null))
				.init(init)
				.ordering(Comparator.naturalOrder());
	}
	/**
	 * Method for starting an {@link IndependentBuilder} by giving an initial value
	 * The natural ordering relation is pre-configured in case you want to put bounds on the value. 
	 */
	public static IndependentBuilder<IndependentInt, Integer> 
	independent(Integer init) {
		return new IndependentBuilder<>(new IndependentInt(null))
				.init(init)
				.ordering(Comparator.naturalOrder());
	}
	/**
	 * Method for starting an {@link IndependentBuilder} by giving an initial value
	 * The natural ordering relation is pre-configured in case you want to put bounds on the value. 
	 */
	public static IndependentBuilder<IndependentDouble, Double> 
	independent(Double init) {
		return new IndependentBuilder<>(new IndependentDouble(null))
				.init(init)
				.ordering(Comparator.naturalOrder());
	}
	/**
	 * Method for starting an {@link IndependentBuilder} by giving an initial value
	 * The natural ordering relation is pre-configured in case you want to put bounds on the value. 
	 */
	public static IndependentBuilder<IndependentString, String> 
	independent(String init) {
		return new IndependentBuilder<>(new IndependentString(null))
				.init(init)
				.ordering(Comparator.naturalOrder());
	}
	/**
	 * Method for starting a {@link SealPileBuilder} by giving an initial value
	 */
	public static <E> SealPileBuilder<SealPile<E>, E> sealed(E init){
		return new SealPileBuilder<>(new SealPile<E>()).init(init)
				.seal();
	}
	/**
	 * Method for starting a {@link SealPileBuilder} by giving an initial value
	 */
	public static SealPileBuilder<SealBool, Boolean> sealed(Boolean init){
		return new SealPileBuilder<>(new SealBool()).init(init)
				.ordering(Comparator.naturalOrder())
				.seal();
	}
	/**
	 * Method for starting a {@link SealPileBuilder} by giving an initial value
	 */
	public static SealPileBuilder<SealDouble, Double> sealed(Double init){
		return new SealPileBuilder<>(new SealDouble()).init(init)
				.ordering(Comparator.naturalOrder())
				.seal();
	}
	/**
	 * Method for starting a {@link SealPileBuilder} by giving an initial value
	 */
	public static SealPileBuilder<SealInt, Integer> sealed(Integer init){
		return new SealPileBuilder<>(new SealInt()).init(init)
				.ordering(Comparator.naturalOrder())
				.seal();
	}
	/**
	 * Method for starting a {@link SealPileBuilder} by giving an initial value
	 */
	public static SealPileBuilder<SealString, String> sealed(String init){
		return new SealPileBuilder<>(new SealString()).init(init)
				.ordering(Comparator.naturalOrder())
				.seal();
	}
	/**
	 * Method for starting a {@link SealPileBuilder} without giving an initial value
	 * @param <E>
	 * @param phantom Give intended value class here to aid type inference
	 * @return
	 */
	public static <E> SealPileBuilder<SealPile<E>, E> sealedNoInit(Class<E> phantom){
		return sealedNoInit();
	}
	/**
	 * Method for starting a {@link SealPileBuilder} without giving an initial value
	 * @param <E>
	 * @return
	 */
	public static <E> SealPileBuilder<SealPile<E>, E> sealedNoInit(){
		return new SealPileBuilder<>(new SealPile<E>())
				.seal();
	}
	/**
	 * Method for starting a {@link SealPileBuilder} without giving an initial value
	 * The natural ordering relation is pre-configured in case you want to put bounds on the value. 
	 * @return
	 */
	public static SealPileBuilder<SealBool, Boolean> sealedNoInitBool(){
		return new SealPileBuilder<>(new SealBool())
				.ordering(Comparator.naturalOrder())
				.seal();
	}
	/**
	 * Method for starting a {@link SealPileBuilder} without giving an initial value
	 * The natural ordering relation is pre-configured in case you want to put bounds on the value. 
	 * @return
	 */
	public static SealPileBuilder<SealInt, Integer> sealedNoInitInt(){
		return new SealPileBuilder<>(new SealInt())
				.ordering(Comparator.naturalOrder())
				.seal();
	}
	/**
	 * Method for starting a {@link SealPileBuilder} without giving an initial value
	 * The natural ordering relation is pre-configured in case you want to put bounds on the value. 
	 * @return
	 */
	public static SealPileBuilder<SealDouble, Double> sealedNoInitDouble(){
		return new SealPileBuilder<>(new SealDouble())
				.ordering(Comparator.naturalOrder())
				.seal();
	}
	/**
	 * Method for starting a {@link SealPileBuilder} without giving an initial value
	 * The natural ordering relation is pre-configured in case you want to put bounds on the value. 
	 * @return
	 */
	public static SealPileBuilder<SealString, String> sealedNoInitString(){
		return new SealPileBuilder<>(new SealString())
				.ordering(Comparator.naturalOrder())
				.seal();
	}


	/**
	 * Call {@link #makeReadOnlyWrapper(ReadDependency, SealPile)} on the given
	 * {@link ReadDependency}, unless it is a {@link Constant} or a sealed
	 * {@link SealPile}.
	 * @param <E>
	 * @param in
	 * @return
	 */

	public static <E> ReadListenDependency<E> readOnlyWrapperIdempotent(ReadDependency<? extends E> in){
		if(in instanceof SealPile<?>) {
			@SuppressWarnings("unchecked")
			SealPile<E> cast = (SealPile<E>) in;
			if(cast.isDefaultSealed())
				return cast;
		}else if(in instanceof Constant<?>) {
			@SuppressWarnings("unchecked")
			Constant<E> cast = (Constant<E>) in;
			return cast;	
		}
		return makeReadOnlyWrapper(in, new SealPile<>());
	}
	/**
	 * Make a wrapper around the value that does not allow to modify it.
	 * @param <E>
	 * @param in
	 * @return
	 */
	public static <E> SealPile<E> readOnlyWrapper(ReadDependency<? extends E> in){
		return makeReadOnlyWrapper(in, new SealPile<>());
	}
	/**
	 * Configure the template to be a wrapper around the {@code v} that does not allow to modify it.
	 * @param <V>
	 * @param <E>
	 * @param in
	 * @param template An un-sealed {@link SealPile}, which will be sealed.
	 * @return
	 */
	public static <V extends SealPile<E>, E> 
	V makeReadOnlyWrapper(
			ReadDependency<? extends E> in, 
			V template
			) {
		return new SealPileBuilder<>(template)
				.recompute(in)
				.parent(in).name(in.dependencyName()+" readOnly")
				.seal()
				.whenChanged(in);
	}	



	/**
	 * Make a sealed {@link SealPile Value} that takes on the minimum of two other values.
	 * A <code>null</code> value will treated as less than all other values.
	 * @param <E>
	 * @param op1
	 * @param op2
	 * @return
	 */
	public static <E extends Comparable<? super E>> 
	SealPile<E> minOpNullIsLess(ReadDependency<? extends E> op1, ReadDependency<? extends E> op2){
		return min(op1, op2, true);
	}
	/**
	 * Make a sealed {@link SealPile Value} that takes on the minimum of two other values.
	 * A <code>null</code> value will treated as greater than all other values.
	 * @param <E>
	 * @param op1
	 * @param op2
	 * @return
	 */
	public static <E extends Comparable<? super E>> 
	SealPile<E> minOpNullIsGreater(ReadDependency<? extends E> op1, ReadDependency<? extends E> op2){
		return min(op1, op2, false);
	}

	/**
	 * Make a {@link BiFunction} that makes a sealed {@link SealPile Value} that takes on the minimum of two other values.
	 * @param <E>
	 * @param nullIsLess Whether <code>null</code> values should be treaded as less than or greater than all other values.
	 * If this parameter is <code>null</code>, <code>null</code> values will cause {@link NullPointerException}s.
	 * @return
	 */
	public static <E extends Comparable<? super E>> 
	BiFunction<? super ReadDependency<? extends E>, ? super ReadDependency<? extends E>, SealPile<E>>
	minOp(Boolean nullIsLess){
		return (op1, op2)->min(op1, op2, nullIsLess);
	}
	/**
	 * Make a {@link BiFunction} that makes a sealed {@link SealPile Value} that takes on the minimum of two other values.
	 * @param <E>
	 * @param ordering The ordering relation to use
	 * @return
	 */
	public static <E> 
	BiFunction<? super ReadDependency<? extends E>, ? super ReadDependency<? extends E>, SealPile<E>>
	minOp(Comparator<? super E> ordering){
		return (op1, op2)->min(op1, op2, ordering);
	}
	/**
	 * Make a {@link BiFunction} that makes a sealed {@link SealPile Value} that takes on the minimum of two other values.
	 * @param <E>
	 * @param nullIsLess Whether <code>null</code> values should be treaded as less than or greater than all other values.
	 * If this parameter is <code>null</code>, <code>null</code> values will cause {@link NullPointerException}s.
	 * @param <V>
	 * @param <E>
	 * @param makeTemplate Factory for the {@link SealPile}s that will then be configured to compute the minimum
	 * @return
	 */
	public static <V extends SealPile<E>, E extends Comparable<? super E>> 
	BiFunction<? super ReadDependency<? extends E>, ? super ReadDependency<? extends E>, V>
	minOp(Supplier<? extends V> makeTemplate, Boolean nullIsLess){
		return (op1, op2)->makeMin(op1, op2, makeTemplate.get(), nullIsLess);
	}
	/**
	 * Make a {@link BiFunction} that makes a sealed {@link SealPile Value} that takes on the minimum of two other values.
	 * @param <V>
	 * @param <E>
	 * @param makeTemplate Factory for the {@link SealPile}s that will then be configured to compute the minimum
	 * @param ordering The ordering relation to use
	 * @return
	 */
	public static <V extends SealPile<E>, E> 
	BiFunction<? super ReadDependency<? extends E>, ? super ReadDependency<? extends E>, V>
	minOp(Supplier<? extends V> makeTemplate, Comparator<? super E> ordering){
		return (op1, op2)->makeMin(op1, op2, makeTemplate.get(), ordering);
	}


	/**
	 * Make a sealed {@link SealPile Value} that takes on the maximum of two other values.
	 * A <code>null</code> value will treated as less than all other values.
	 * @param <E>
	 * @param op1
	 * @param op2
	 * @return
	 */
	public static <E extends Comparable<? super E>> 
	SealPile<E> maxOpNullIsLess(ReadDependency<? extends E> op1, ReadDependency<? extends E> op2){
		return max(op1, op2, true);
	}
	/**
	 * Make a sealed {@link SealPile Value} that takes on the maximum of two other values.
	 * A <code>null</code> value will treated as greater than all other values.
	 * @param <E>
	 * @param op1
	 * @param op2
	 * @return
	 */
	public static <E extends Comparable<? super E>> 
	SealPile<E> maxOpNullIsGreater(ReadDependency<? extends E> op1, ReadDependency<? extends E> op2){
		return max(op1, op2, false);
	}
	/**
	 * Make a {@link BiFunction} that makes a sealed {@link SealPile Value} that takes on the maximum of two other values.
	 * @param <E>
	 * @param nullIsLess Whether <code>null</code> values should be treaded as less than or greater than all other values.
	 * If this parameter is <code>null</code>, <code>null</code> values will cause {@link NullPointerException}s.
	 * @return
	 */
	public static <E extends Comparable<? super E>> 
	BiFunction<? super ReadDependency<? extends E>, ? super ReadDependency<? extends E>, SealPile<E>>
	maxOp(Boolean nullIsLess){
		return (op1, op2)->max(op1, op2, nullIsLess);
	}
	/**
	 * Make a {@link BiFunction} that makes a sealed {@link SealPile Value} that takes on the maximum of two other values.
	 * @param <E>
	 * @param ordering The ordering relation to use
	 * @return
	 */
	public static <E> 
	BiFunction<? super ReadDependency<? extends E>, ? super ReadDependency<? extends E>, SealPile<E>>
	maxOp(Comparator<? super E> ordering){
		return (op1, op2)->max(op1, op2, ordering);
	}
	/**
	 * Make a {@link BiFunction} that makes a sealed {@link SealPile Value} that takes on the maximum of two other values.
	 * @param <E>
	 * @param nullIsLess Whether <code>null</code> values should be treaded as less than or greater than all other values.
	 * If this parameter is <code>null</code>, <code>null</code> values will cause {@link NullPointerException}s.
	 * @param <V>
	 * @param <E>
	 * @param makeTemplate Factory for the {@link SealPile}s that will then be configured to compute the maximum
	 * @return
	 */
	public static <V extends SealPile<E>, E extends Comparable<? super E>> 
	BiFunction<? super ReadDependency<? extends E>, ? super ReadDependency<? extends E>, V>
	maxOp(Supplier<? extends V> makeTemplate, Boolean nullIsLess){
		return (op1, op2)->makeMax(op1, op2, makeTemplate.get(), nullIsLess);
	}
	/**
	 * Make a {@link BiFunction} that makes a sealed {@link SealPile Value} that takes on the maximum of two other values.
	 * @param <V>
	 * @param <E>
	 * @param makeTemplate Factory for the {@link SealPile}s that will then be configured to compute the maximum
	 * @param ordering The ordering relation to use
	 * @return
	 */
	public static <V extends SealPile<E>, E> 
	BiFunction<? super ReadDependency<? extends E>, ? super ReadDependency<? extends E>, V>
	maxOp(Supplier<? extends V> makeTemplate, Comparator<? super E> ordering){
		return (op1, op2)->makeMax(op1, op2, makeTemplate.get(), ordering);
	}



	/**
	 * Make a {@link SealPile Value} that takes on the maximum of two other values.
	 * @param <E>
	 * @param op1
	 * @param op2
	 * @param ordering The ordering relation to use
	 * @return
	 */
	public static <E> SealPile<E> min(ReadDependency<? extends E> op1, ReadDependency<? extends E> op2, Comparator<? super E> ordering){
		return makeMin(op1, op2, new SealPile<>(), ordering);		
	}
	/**
	 * Make a {@link SealPile Value} that takes on the minimum of two other values.
	 * @param <E>
	 * @param nullIsLess Whether <code>null</code> values should be treaded as less than or greater than all other values.
	 * If this parameter is <code>null</code>, <code>null</code> values will cause {@link NullPointerException}s.
	 * @param op1
	 * @param op2
	 * @return
	 */
	public static <E extends Comparable<? super E>> SealPile<E> min(ReadDependency<? extends E> op1, ReadDependency<? extends E> op2, Boolean nullIsLess){
		return makeMin(op1, op2, new SealPile<>(), nullIsLess);		
	}
	/**
	 * Make a {@link SealPile Value} that takes on the maximum of two other values.
	 * @param <E>
	 * @param op1
	 * @param op2
	 * @param ordering The ordering relation to use
	 * @return
	 */
	public static <E> SealPile<E> max(ReadDependency<? extends E> op1, ReadDependency<? extends E> op2, Comparator<? super E> ordering){
		return makeMax(op1, op2, new SealPile<>(), ordering);		
	}
	/**
	 * Make a {@link SealPile Value} that takes on the maximum of two other values.
	 * @param <E>
	 * @param nullIsLess Whether <code>null</code> values should be treaded as less than or greater than all other values.
	 * If this parameter is <code>null</code>, <code>null</code> values will cause {@link NullPointerException}s.
	 * @param op1
	 * @param op2
	 * @return
	 */
	public static <E extends Comparable<? super E>> SealPile<E> max(ReadDependency<? extends E> op1, ReadDependency<? extends E> op2, Boolean nullIsLess){
		return makeMax(op1, op2, new SealPile<>(), nullIsLess);		
	}
	/**
	 * Configure an unsealed {@link SealPile} to take on the minimum of two other values.
	 * @param <V>
	 * @param <E>
	 * @param op1
	 * @param op2
	 * @param template The {@link SealPile} to configure. It will be {@link SealPile#seal() seal}ed
	 * @param ordering The ordering relation to use
	 * @return
	 */
	public static <V extends SealPile<E>, E> 
	V makeMin(
			ReadDependency<? extends E> op1, 
			ReadDependency<? extends E> op2, 
			V template, 
			Comparator<? super E> ordering) {
		template.setName("min {"+op1.dependencyName()+", "+op2.dependencyName()+"}");
		return makeBinOp(op1, op2, template, (o1, o2)->{
			int comp = ordering.compare(o1, o2);
			if(comp<=0)
				return o1;
			else
				return o2;
		});

	}
	/**
	 * Configure an unsealed {@link SealPile} to take on the minimum of two other values.
	 * @param <V>
	 * @param <E>
	 * @param op1
	 * @param op2
	 * @param template The {@link SealPile} to configure. It will be {@link SealPile#seal() seal}ed
	 * @param nullIsLess Whether <code>null</code> values should be treaded as less than or greater than all other values.
	 * If this parameter is <code>null</code>, <code>null</code> values will cause {@link NullPointerException}s.
	 * @return
	 */
	public static <V extends SealPile<E>, E extends Comparable<? super E>> 
	V makeMin(
			ReadDependency<? extends E> op1, 
			ReadDependency<? extends E> op2, 
			V template,
			Boolean nullIsLess) {
		template.setName("min {"+op1.dependencyName()+", "+op2.dependencyName()+"}");
		return makeBinOp(op1, op2, template, (o1, o2)->{
			if(o1==null)
				return nullIsLess?null:o2;
			if(o2==null)
				return nullIsLess?null:o1;

			int comp = o1.compareTo(o2);
			if(comp<=0)
				return o1;
			else
				return o2;
		});

	}
	/**
	 * Configure an unsealed {@link SealPile} to take on the maximum of two other values.
	 * @param <V>
	 * @param <E>
	 * @param op1
	 * @param op2
	 * @param template The {@link SealPile} to configure. It will be {@link SealPile#seal() seal}ed
	 * @param ordering The ordering relation to use
	 * @return
	 */
	public static <V extends SealPile<E>, E> V makeMax(
			ReadDependency<? extends E> op1, 
			ReadDependency<? extends E> op2, 
			V template, 
			Comparator<? super E> ordering) {
		template.setName("max {"+op1.dependencyName()+", "+op2.dependencyName()+"}");
		return makeBinOp(op1, op2, template, (o1, o2)->{
			int comp = ordering.compare(o1, o2);
			if(comp>=0)
				return o1;
			else
				return o2;
		});	
	}
	/**
	 * Configure an unsealed {@link SealPile} to take on the maximum of two other values.
	 * @param <V>
	 * @param <E>
	 * @param op1
	 * @param op2
	 * @param template The {@link SealPile} to configure. It will be {@link SealPile#seal() seal}ed
	 * @param nullIsLess Whether <code>null</code> values should be treaded as less than or greater than all other values.
	 * If this parameter is <code>null</code>, <code>null</code> values will cause {@link NullPointerException}s.
	 * @return
	 */
	public static <V extends SealPile<E>, E extends Comparable<? super E>> V makeMax(
			ReadDependency<? extends E> op1, 
			ReadDependency<? extends E> op2, 
			V template,
			Boolean nullIsLess) {
		template.setName("max {"+op1.dependencyName()+", "+op2.dependencyName()+"}");
		return makeBinOp(op1, op2, template, (o1, o2)->{
			if(o1==null)
				return nullIsLess?o2:null;
			if(o2==null)
				return nullIsLess?o1:null;

			int comp = o1.compareTo(o2);
			if(comp>=0)
				return o1;
			else
				return o2;
		});
	}
	/**
	 * Make an {@link AggregationMonoid} for computing the minimum of several values
	 * @param <V>
	 * @param <E>
	 * @param makeTemplate Factory for the {@link SealPile}s that will then be configured to compute the minimum of two values
	 * @param max The maximum possible value. This will be used as the result of zero values are aggregated.
	 * @param ordering The ordering relation to use
	 * @return
	 */
	public static <V extends SealPile<E>, E>
	OpAggregation<E, V>
	minAggregation(Supplier<? extends V> makeTemplate, V max, Comparator<? super E> ordering){
		return new OpAggregation<E, V>(
				max, 
				in->makeReadOnlyWrapper(in, makeTemplate.get()), 
				minOp(makeTemplate, ordering));
	}
	/**
	 * Make an {@link AggregationMonoid} for computing the minimum of several values
	 * @param <V>
	 * @param <E>
	 * @param makeTemplate Factory for the {@link SealPile}s that will then be configured to compute the minimum of two values
	 * @param max The maximum possible value. This will be used as the result of zero values are aggregated.
	 * @param nullIsLess Whether <code>null</code> values should be treaded as less than or greater than all other values.
	 * If this parameter is <code>null</code>, <code>null</code> values will cause {@link NullPointerException}s.
	 * @return
	 */
	public static <V extends SealPile<E>, E extends Comparable<? super E>>
	OpAggregation<E, V>
	minAggregation(Supplier<? extends V> makeTemplate, V max, Boolean nullIsLess){
		return new OpAggregation<E, V>(
				max, 
				in->makeReadOnlyWrapper(in, makeTemplate.get()), 
				minOp(makeTemplate, nullIsLess));
	}
	/**
	 * Make an {@link AggregationMonoid} for computing the minimum of several values
	 * @param <E>
	 * @param max The maximum possible value. This will be used as the result of zero values are aggregated.
	 * @param ordering The ordering relation to use
	 * @return
	 */
	public static <E>
	OpAggregation<E, ReadListenDependency<E>>
	minAggregation(ReadListenDependency<E> max, Comparator<? super E> ordering){
		return new OpAggregation<>(
				max, 
				Piles::readOnlyWrapperIdempotent, 
				minOp(ordering));
	}
	/**
	 * Make an {@link AggregationMonoid} for computing the minimum of several values
	 * @param <E>
	 * @param max The maximum possible value. This will be used as the result of zero values are aggregated.
	 * @param nullIsLess Whether <code>null</code> values should be treaded as less than or greater than all other values.
	 * If this parameter is <code>null</code>, <code>null</code> values will cause {@link NullPointerException}s.
	 * @return
	 */
	public static <E extends Comparable<? super E>>
	OpAggregation<E, ReadListenDependency<E>>
	minAggregation(ReadListenDependency<E> max, Boolean nullIsLess){
		return new OpAggregation<>(
				max, 
				Piles::readOnlyWrapperIdempotent, 
				nullIsLess?Piles::minOpNullIsLess:Piles::minOpNullIsGreater);
	}
	/**
	 * Make an {@link AggregationMonoid} for computing the minimum of several values
	 * @param <V>
	 * @param <E>
	 * @param makeTemplate Factory for the {@link SealPile}s that will then be configured to compute the minimum of two values
	 * @param max The maximum possible value. This will be used as the result of zero values are aggregated.
	 * @param ordering The ordering relation to use
	 * @return
	 */
	public static <V extends SealPile<E>, E>
	OpAggregation<E, V>
	minAggregationC(Supplier<? extends V> makeTemplate, E max, Comparator<? super E> ordering){
		V vMax = makeReadOnlyWrapper(new Constant<E>(max), makeTemplate.get());
		return minAggregation(makeTemplate, vMax, ordering);
	}
	/**
	 * Make an {@link AggregationMonoid} for computing the minimum of several values
	 * @param <V>
	 * @param <E>
	 * @param makeTemplate Factory for the {@link SealPile}s that will then be configured to compute the minimum of two values
	 * @param max The maximum possible value. This will be used as the result of zero values are aggregated.
	 * @param nullIsLess Whether <code>null</code> values should be treaded as less than or greater than all other values.
	 * If this parameter is <code>null</code>, <code>null</code> values will cause {@link NullPointerException}s.
	 * @return
	 */
	public static <V extends SealPile<E>, E extends Comparable<? super E>>
	OpAggregation<E, V>
	minAggregationC(Supplier<? extends V> makeTemplate, E max, Boolean nullIsLess){
		V vMax = makeReadOnlyWrapper(new Constant<E>(max), makeTemplate.get());
		return minAggregation(makeTemplate, vMax, nullIsLess);
	}
	/**
	 * Make an {@link AggregationMonoid} for computing the minimum of several values
	 * @param <E>
	 * @param max The maximum possible value. This will be used as the result of zero values are aggregated.
	 * @param ordering The ordering relation to use
	 * @return
	 */
	public static <E>
	OpAggregation<E, ReadListenDependency<E>>
	minAggregationC(E max, Comparator<? super E> ordering){
		SealPile<E> vMax = new SealPileBuilder<>(new SealPile<E>()).init(max).seal().build();
		return new OpAggregation<>(
				vMax, 
				Piles::readOnlyWrapperIdempotent, 
				minOp(ordering));
	}
	/**
	 * Make an {@link AggregationMonoid} for computing the minimum of several values
	 * @param <E>
	 * @param max The maximum possible value. This will be used as the result of zero values are aggregated.
	 * @param nullIsLess Whether <code>null</code> values should be treaded as less than or greater than all other values.
	 * If this parameter is <code>null</code>, <code>null</code> values will cause {@link NullPointerException}s.
	 * @return
	 */
	public static <E extends Comparable<? super E>>
	OpAggregation<E, ReadListenDependency<E>>
	minAggregationC(E max, Boolean nullIsLess){
		SealPile<E> vMax = new SealPileBuilder<>(new SealPile<E>()).init(max).seal().build();
		return new OpAggregation<>(
				vMax, 
				Piles::readOnlyWrapperIdempotent, 
				nullIsLess?Piles::minOpNullIsLess:Piles::minOpNullIsGreater);
	}

	/**
	 * Make an {@link AggregationMonoid} for computing the maximum of several values
	 * @param <V>
	 * @param <E>
	 * @param makeTemplate Factory for the {@link SealPile}s that will then be configured to compute the maximum of two values
	 * @param max The minimum possible value. This will be used as the result of zero values are aggregated.
	 * @param ordering The ordering relation to use
	 * @return
	 */
	public static <V extends SealPile<E>, E>
	OpAggregation<E, V>
	maxAggregation(Supplier<? extends V> makeTemplate, V min, Comparator<? super E> ordering){
		return new OpAggregation<E, V>(
				min, 
				in->makeReadOnlyWrapper(in, makeTemplate.get()), 
				maxOp(makeTemplate, ordering));
	}
	/**
	 * Make an {@link AggregationMonoid} for computing the maximum of several values
	 * @param <V>
	 * @param <E>
	 * @param makeTemplate Factory for the {@link SealPile}s that will then be configured to compute the maximum of two values
	 * @param max The minimum possible value. This will be used as the result of zero values are aggregated.
	 * @param nullIsLess Whether <code>null</code> values should be treaded as less than or greater than all other values.
	 * If this parameter is <code>null</code>, <code>null</code> values will cause {@link NullPointerException}s.
	 * @return
	 */
	public static <V extends SealPile<E>, E extends Comparable<? super E>>
	OpAggregation<E, V>
	maxAggregation(Supplier<? extends V> makeTemplate, V min, Boolean nullIsLess){
		return new OpAggregation<E, V>(
				min, 
				in->makeReadOnlyWrapper(in, makeTemplate.get()), 
				maxOp(makeTemplate, nullIsLess));
	}
	/**
	 * Make an {@link AggregationMonoid} for computing the maximum of several values
	 * @param <V>
	 * @param <E>
	 * @param max The minimum possible value. This will be used as the result of zero values are aggregated.
	 * @param ordering The ordering relation to use
	 * @return
	 */
	public static <E>
	OpAggregation<E, ReadListenDependency<E>>
	maxAggregation(ReadListenDependency<E> min, Comparator<? super E> ordering){
		return new OpAggregation<>(
				min, 
				Piles::readOnlyWrapperIdempotent, 
				maxOp(ordering));
	}
	/**
	 * Make an {@link AggregationMonoid} for computing the maximum of several values
	 * @param <V>
	 * @param <E>
	 * @param max The minimum possible value. This will be used as the result of zero values are aggregated.
	 * @param nullIsLess Whether <code>null</code> values should be treaded as less than or greater than all other values.
	 * If this parameter is <code>null</code>, <code>null</code> values will cause {@link NullPointerException}s.
	 * @return
	 */
	public static <E extends Comparable<? super E>>
	OpAggregation<E, ReadListenDependency<E>>
	maxAggregation(ReadListenDependency<E> min, Boolean nullIsLess){
		return new OpAggregation<>(
				min, 
				Piles::readOnlyWrapperIdempotent, 
				nullIsLess?Piles::maxOpNullIsLess:Piles::maxOpNullIsGreater);
	}
	/**
	 * Make an {@link AggregationMonoid} for computing the maximum of several values
	 * @param <V>
	 * @param <E>
	 * @param makeTemplate Factory for the {@link SealPile}s that will then be configured to compute the maximum of two values
	 * @param max The minimum possible value. This will be used as the result of zero values are aggregated.
	 * @param ordering The ordering relation to use
	 * @return
	 */	
	public static <V extends SealPile<E>, E>
	OpAggregation<E, V>
	maxAggregationC(Supplier<? extends V> makeTemplate, E min, Comparator<? super E> ordering){
		V vMin = makeReadOnlyWrapper(new Constant<E>(min), makeTemplate.get());
		return maxAggregation(makeTemplate, vMin, ordering);
	}
	/**
	 * Make an {@link AggregationMonoid} for computing the maximum of several values
	 * @param <V>
	 * @param <E>
	 * @param makeTemplate Factory for the {@link SealPile}s that will then be configured to compute the maximum of two values
	 * @param max The minimum possible value. This will be used as the result of zero values are aggregated.
	 * @param nullIsLess Whether <code>null</code> values should be treaded as less than or greater than all other values.
	 * If this parameter is <code>null</code>, <code>null</code> values will cause {@link NullPointerException}s.
	 * @return
	 */
	public static <V extends SealPile<E>, E extends Comparable<? super E>>
	OpAggregation<E, V>
	maxAggregationC(Supplier<? extends V> makeTemplate, E min, Boolean nullIsLess){
		V vMin = makeReadOnlyWrapper(new Constant<E>(min), makeTemplate.get());
		return maxAggregation(makeTemplate, vMin, nullIsLess);
	}
	/**
	 * Make an {@link AggregationMonoid} for computing the maximum of several values
	 * @param <V>
	 * @param <E>
	 * @param max The minimum possible value. This will be used as the result of zero values are aggregated.
	 * @param ordering The ordering relation to use
	 * @return
	 */
	public static <E>
	OpAggregation<E, ReadListenDependency<E>>
	maxAggregationC(E min, Comparator<? super E> ordering){
		SealPile<E> vMin = new SealPileBuilder<>(new SealPile<E>()).init(min).seal().build();
		return new OpAggregation<>(
				vMin, 
				Piles::readOnlyWrapperIdempotent, 
				maxOp(ordering));
	}
	/**
	 * Make an {@link AggregationMonoid} for computing the maximum of several values
	 * @param <V>
	 * @param <E>
	 * @param max The minimum possible value. This will be used as the result of zero values are aggregated.
	 * @param nullIsLess Whether <code>null</code> values should be treaded as less than or greater than all other values.
	 * If this parameter is <code>null</code>, <code>null</code> values will cause {@link NullPointerException}s.
	 * @return
	 */
	public static <E extends Comparable<? super E>>
	OpAggregation<E, ReadListenDependency<E>>
	maxAggregationC(E min, Boolean nullIsLess){
		SealPile<E> vMin = new SealPileBuilder<>(new SealPile<E>()).init(min).seal().build();
		return new OpAggregation<>(
				vMin, 
				Piles::readOnlyWrapperIdempotent, 
				nullIsLess?Piles::maxOpNullIsLess:Piles::maxOpNullIsGreater);
	}
	/**
	 * A <q>monoid</q> for aggregating values in an associative way.
	 * It is not strictly a monoid because the type of the results for the monoid operations is a 
	 * subtype of the type of the inputs.
	 * @author bb
	 *
	 * @param <E> This input type for the monoid operation is {@link ReadListenDependency ReadListenDependency&lt;? extends E&gt;}
	 * @param <V> The result type of the monoid operations
	 */
	public static interface AggregationMonoid<E, V extends ReadListenDependency<? extends E>>{
		/**
		 * Get the neutral element of the monoid
		 * @return
		 */
		V constantNeutral();
		/**
		 * Apply the binary operation of the monoid
		 * @param op1
		 * @param op2
		 * @return
		 */
		V apply(ReadListenDependency<? extends E> op1, ReadListenDependency<? extends E> op2);
		/**
		 * If necessary, wrap a value to have the correct type 
		 * @param o
		 * @return
		 */
		V inject(ReadListenDependency<? extends E> o);
	}
	/**
	 * An {@link AggregationMonoid} that tries to pick one of its operands
	 * based on whether it fulfills a {@link Predicate}. 
	 * 
	 * Common superclass of {@link LeftmostFulfilling} and {@link RightmostFulfilling}.
	 * 
	 * @author bb
	 *
	 * @param <E>
	 * @param <V>
	 */
	public static abstract class SidemostFulfilling<E, V extends SealPile<E>> implements AggregationMonoid<E, V>{
		public final E ifNone;
		public final Predicate<? super E> mustFulfill;
		public final V constantNeutral;
		@Override
		public V constantNeutral() {
			return constantNeutral;
		}
		public SidemostFulfilling(Predicate<? super E> mustFulfill, E ifNone) {
			if(mustFulfill.test(ifNone))
				throw new IllegalArgumentException("The ifNone element must not fulfill the predicate");
			this.ifNone = ifNone;
			this.mustFulfill = mustFulfill;
			constantNeutral = makeConstant(ifNone);
		}
		protected abstract V makeConstant(E v);
		protected abstract ISealPileBuilder<?, V, E> makeBuilder();
		public V applyPreferring(
				ReadListenDependency<? extends E> preferred,
				ReadListenDependency<? extends E> notPreferred) {
			return makeBuilder().recompute(opPreferring(preferred, notPreferred)).dependOn(true, preferred).seal().dd();
		}
		public Supplier<? extends E> opPreferring(
				ReadListenDependency<? extends E> preferred, 
				ReadListenDependency<? extends E> notPreferred) {
			return ()->{
				E v = preferred.get();
				if(mustFulfill.test(v))
					return v;
				v = notPreferred.get();
				if(mustFulfill.test(v))
					return v;
				return ifNone;
			};
		}
		@Override
		public V inject(ReadListenDependency<? extends E> o) {
			return makeBuilder().recompute(o).whenChanged(o);
		}
	}
	/**
	 * An {@link AggregationMonoid} that tries to pick one of its operands
	 * based on whether it fulfills a {@link Predicate}, preferring the left operand. 
	 * If the left operand is picked, the right operand will be ignored and may be invalid.

	 * @author bb
	 *
	 * @param <E>
	 */
	public static class LeftmostFulfilling<E> extends SidemostFulfilling<E, SealPile<E>>{
		public static LeftmostFulfilling<?> NOT_NULL = new LeftmostFulfilling<>(Functional.IS_NOT_NULL, null);
		@SuppressWarnings("unchecked")
		public static <E> LeftmostFulfilling<E> notNull(){
			return (LeftmostFulfilling<E>) NOT_NULL;
		}
		public LeftmostFulfilling(Predicate<? super E> mustFulfill, E ifNone) {
			super(mustFulfill, ifNone);
		}

		@Override
		public SealPile<E> apply(ReadListenDependency<? extends E> op1,	ReadListenDependency<? extends E> op2) {
			return applyPreferring(op1, op2);
		}
		@Override
		protected ISealPileBuilder<?, SealPile<E>, E> makeBuilder() {
			return sb();
		}
		protected SealPile<E> makeConstant(E e){
			return sealedConstant(e);
		}
	}
	/**
	 * An {@link AggregationMonoid} that tries to pick one of its operands
	 * based on whether it fulfills a {@link Predicate}, preferring the right operand.
	 * If the right operand is picked, the left operand will be ignored and may be invalid.
	 * @author bb
	 *
	 * @param <E>
	 */
	public static class RightmostFulfilling<E> extends SidemostFulfilling<E, SealPile<E>>{
		public static RightmostFulfilling<?> NOT_NULL = new RightmostFulfilling<>(Functional.IS_NOT_NULL, null);
		@SuppressWarnings("unchecked")
		public static <E> RightmostFulfilling<E> notNull(){
			return (RightmostFulfilling<E>) NOT_NULL;
		}
		public RightmostFulfilling(Predicate<? super E> mustFulfill, E ifNone) {
			super(mustFulfill, ifNone);
		}

		@Override
		public SealPile<E> apply(ReadListenDependency<? extends E> op1,	ReadListenDependency<? extends E> op2) {
			return applyPreferring(op1, op2);
		}
		@Override
		protected ISealPileBuilder<?, SealPile<E>, E> makeBuilder() {
			return sb();
		}
		protected SealPile<E> makeConstant(E e){
			return sealedConstant(e);
		}
	}
	/**
	 * A generic implementation of {@link AggregationMonoid} that can be customized using
	 * {@link BiFunction (Bi)}{@link Function} objects
	 * @author bb
	 *
	 * @param <E>
	 * @param <V>
	 */
	public static class OpAggregation<E, V extends ReadListenDependency<E>> 
	implements AggregationMonoid<E, V>{
		final V neutral;
		final Function<? super ReadListenDependency<? extends E>, ? extends V> inject;
		final BiFunction<? super ReadListenDependency<? extends E>, ? super ReadListenDependency<? extends E>, ? extends V> op;
		public OpAggregation(
				V neutral, 
				Function<? super ReadDependency<? extends E>, ? extends V> inject,
				BiFunction<? super ReadDependency<? extends E>, ? super ReadDependency<? extends E>, ? extends V> op) {
			this.neutral = neutral;
			this.inject=inject;
			this.op=op;
		}
		@Override
		public V constantNeutral() {
			if(neutral==null)
				throw new UnsupportedOperationException("Must provide at least one operand");
			return neutral;
		}

		@Override
		public V apply(ReadListenDependency<? extends E> op1, ReadListenDependency<? extends E> op2) {
			return op.apply(op1, op2);
		}
		@Override
		public V inject(ReadListenDependency<? extends E> o) {
			return inject.apply(o);
		}
	}

	/**
	 * Aggregate a sequence of values using the given operation.
	 * This will construct a static binary tree of values that has logarithmic depth in the number
	 * of values.
	 * @param <E>
	 * @param <V>
	 * @param operation
	 * @param items
	 * @return
	 */
	public static <E, V extends ReadListenDependency<? extends E>> V aggregate(
			AggregationMonoid<E, ? extends V> operation,
			Iterable<? extends ReadListenDependency<? extends E>> items
		){
		return aggregate(null, operation, items);
	}
	public static <E, V extends ReadListenDependency<? extends E>> V aggregate(
			Predicate<? super ReadListenDependency<? extends E>> isNeutral,
			AggregationMonoid<E, ? extends V> operation,
			Iterable<? extends ReadListenDependency<? extends E>> items
			){
		ArrayList<ReadListenDependency<? extends E>> stack=new ArrayList<>();
		int index = 0;
		V lastResult=null;
		for(ReadListenDependency<? extends E> item: items) {
			if(item==null)
				continue;
			if(isNeutral!=null && isNeutral.test(item))
				continue;
			stack.add(item);
			// 000 -> 0
			// 001 -> 1
			// 010 -> 0
			// 011 -> 2
			//
			//
			for(int bits = index; (bits&1)!=0; bits>>>=1) {
				ReadListenDependency<? extends E> op2 = stack.remove(stack.size()-1);
				ReadListenDependency<? extends E> op1 = stack.remove(stack.size()-1);
				lastResult = operation.apply(op1, op2);
				stack.add(lastResult);
			}
			++index;
		}
		if(index==0)
			return operation.constantNeutral();
		if(index==1) {
			assert stack.size()==1;
			return operation.inject(stack.get(0));
		}
		if(stack.size()>1) {
			ReadListenDependency<? extends E> op2 = stack.remove(stack.size()-1);
			while(!stack.isEmpty()) {
				ReadListenDependency<? extends E> op1 = stack.remove(stack.size()-1);
				op2 = lastResult = operation.apply(op1, op2);
			}
		}
		return lastResult;

	}
	/**
	 * Aggregate a sequence of values using the given operation.
	 * This will construct a static binary tree of values that has logarithmic depth in the number
	 * of values
	 * @param <E>
	 * @param <V>
	 * @param operation
	 * @param items
	 * @return
	 */
	@SafeVarargs
	public static <E, V extends ReadListenDependency<? extends E>> V aggregate(
			AggregationMonoid<E, ? extends V> operation,
			ReadListenDependency<? extends E>... items
			){
		return aggregate(null, operation, items);
	}	
	@SafeVarargs
	public static <E, V extends ReadListenDependency<? extends E>> V aggregate(
			Predicate<? super ReadListenDependency<? extends E>> isNeutral,
			AggregationMonoid<E, ? extends V> operation,
			ReadListenDependency<? extends E>... items
			){

		ArrayList<ReadListenDependency<? extends E>> stack=new ArrayList<>();
		int index = 0;
		V lastResult=null;
		for(ReadListenDependency<? extends E> item: items) {
			if(item==null)
				continue;
			if(isNeutral!=null && isNeutral.test(item))
				continue;
			stack.add(item);
			for(int bits = index; (bits&1)!=0; bits>>>=1) {
				ReadListenDependency<? extends E> op2 = stack.remove(stack.size()-1);
				ReadListenDependency<? extends E> op1 = stack.remove(stack.size()-1);
				lastResult = operation.apply(op1, op2);
				stack.add(lastResult);
			}
			++index;
		}
		if(index==0)
			return operation.constantNeutral();
		if(index==1) {
			assert stack.size()==1;
			return operation.inject(stack.get(0));
		}
		if(stack.size()>1) {
			ReadListenDependency<? extends E> op2 = stack.remove(stack.size()-1);
			while(!stack.isEmpty()) {
				ReadListenDependency<? extends E> op1 = stack.remove(stack.size()-1);
				op2 = lastResult = operation.apply(op1, op2);
			}
		}
		return lastResult;

	}
	/**
	 * Make a not yet {@link SealPile#seal seal}ed {@link SealPile} that takes on the result of a binary 
	 * function, applied to two other values.
	 * @param <E>
	 * @param <O1>
	 * @param <O2>
	 * @param op1
	 * @param op2
	 * @param op
	 * @return
	 */
	public static <E, O1, O2> SealPile<E> makeBinOp(
			ReadDependency<? extends O1> op1,
			ReadDependency<? extends O2> op2,
			BiFunction<? super O1, ? super O2, ? extends E> op
			) {
		return makeBinOp(op1, op2, new SealPile<>(), op);

	}
	/**
	 * Configure a not yet {@link SealPile#seal seal}ed {@link SealPile} to take on the result of a binary 
	 * function, applied to two other values.
	 * @param <E>
	 * @param <V>
	 * @param <O1>
	 * @param <O2>
	 * @param op1
	 * @param op2
	 * @param template The value that will be configured (and {@link SealPile#seal() seal}ed)
	 * @param op
	 * @return
	 */
	public static <E, V extends SealPile<E>, O1, O2> V makeBinOp(
			ReadDependency<? extends O1> op1,
			ReadDependency<? extends O2> op2,
			V template,
			BiFunction<? super O1, ? super O2, ? extends E> op
			) {
		return new SealPileBuilder<>(template)
				.recompute(()->{
					O1 o1 = op1.get();
					O2 o2 = op2.get();
					return op.apply(o1, o2);
				})
				.seal()
				.whenChanged(op1, op2);

	}
	/**
	 * Make a {@link Sealable#seal() seal}ed {@link SealString} that takes on the value
	 * of the string concatenation of the {@link String#valueOf(boolean) string representations}
	 * of two other values.
	 * @param op1
	 * @param op2
	 * @return
	 */
	public static SealString concatStrings(ReadDependency<?> op1, ReadDependency<?> op2){
		return makeBinOp(op1, op2, new SealString(), 
				(o1, o2)->String.valueOf(o1)+String.valueOf(o2))
				.setName("("+op1.dependencyName()+" ++ "+op2.dependencyName()+")");	
	}
	/**
	 * Make a reactive value that concatenates the string representations of several
	 * (possibly reactive) values that are either constants or instances of {@link ReadDependency}.
	 * @param preserveNull Specifies that, if there is just one value, and it is <code>null</code>,
	 * then the result will be <code>null</code> instead of <code>"null"</code>.
	 * @param args
	 * @return
	 */
	public static ReadListenDependencyString concatAny(boolean preserveNull, Object[] args) {
		if(args==null)
			return null;
		if(args.length==0)
			return EMPTY_STRING;
		if(args.length==1) {
			Object a0 = args[0];
			return toString(preserveNull, a0);
		}
		ReadListenDependencyString[] parts = new ReadListenDependencyString[args.length];
		for(int i=0; i<parts.length; ++i) {
			parts[i]=toString(false, args[i]);
		}
		return aggregate(PileString.concatAggregation, parts);
	}

	/**
	 * Convert any value to a reactive string.
	 * @param preserveNull
	 * @param a0 If this parameter is a {@link ReadDependency}, then it will be dereferenced reactively, otherwise it will be used as is.
	 * The value (possibly after dereferencing) will be converted to a {@link String}.
	 * If this parameter is <code>null</code>, the result will be
	 * <code>null</code> or <code>"null"</code>, depending on the value of {@code preserveNull}.
	 * 
	 * @return
	 */
	private static ReadListenDependencyString toString(boolean preserveNull, Object a0) {
		if(a0==null) {
			if(preserveNull)
				return PileStringImpl.NULL;
			else
				return PileString.CONST_QUOTED_NULL;
		}

		if(a0 instanceof ReadDependency<?>) {
			if(a0 instanceof ReadListenDependencyString && preserveNull)
				return (ReadListenDependencyString)a0;
			return ((ReadDependency<?>)a0).mapToString(
					preserveNull?(o->o==null?null:o.toString()):String::valueOf
					);
		}
		return Piles.constant(String.valueOf(a0));
	}

	/**
	 * Make a {@link Sealable#seal() seal}ed {@link SealPile} that takes on the value of the 
	 * first {@link ReadValue#isValid() valid} value in a sequence of values.
	 * @param <E>
	 * @param
	 * @param writableFirst Optionally the first value in the sequence, if you want writes to be redirected to it
	 * @param values The (remaining) sequence
	 * @return
	 */
	@SafeVarargs
	public static <E> SealPile<E> firstValid(
			ReadWriteListenDependency<E> writableFirst,
			ReadListenDependency<? extends E>... values){
		return makeFirstValid(new SealPile<>(), writableFirst, values);
	}
	/**
	 * Make a {@link Sealable#seal() seal}ed {@link SealBool} that takes on the value of the 
	 * first {@link ReadValue#isValid() valid} value in a sequence of boolean values.
	 * @param <E>
	 * @param writableFirst Optionally the first value in the sequence, if you want writes to be redirected to it
	 * @param values The (remaining) sequence
	 * @return
	 */
	@SafeVarargs
	public static SealBool firstValidBool(
			ReadWriteListenDependency<Boolean> writableFirst,
			ReadListenDependency<? extends Boolean>... values){
		return makeFirstValid(new SealBool(), writableFirst, values);
	}
	/**
	 * Make a {@link Sealable#seal() seal}ed {@link SealDouble} that takes on the value of the 
	 * first {@link ReadValue#isValid() valid} value in a sequence of double precision values.
	 * @param <E>
	 * @param writableFirst Optionally the first value in the sequence, if you want writes to be redirected to it
	 * @param values The (remaining) sequence
	 * @return
	 */
	@SafeVarargs
	public static SealDouble firstValidDouble(
			ReadWriteListenDependency<Double> writableFirst,			
			ReadListenDependency<? extends Double>... values){
		return makeFirstValid(new SealDouble(), writableFirst, values);
	}
	/**
	 * Make a {@link Sealable#seal() seal}ed {@link SealString} that takes on the value of the 
	 * first {@link ReadValue#isValid() valid} value in a sequence of {@link String} values.
	 * @param <E>
	 * @param writableFirst Optionally the first value in the sequence, if you want writes to be redirected to it
	 * @param values The (remaining) sequence
	 * @return
	 */
	@SafeVarargs
	public static SealString firstValidString(	
			ReadWriteListenDependency<String> writableFirst,			
			ReadListenDependency<? extends String>... values){
		return makeFirstValid(new SealString(), writableFirst, values);
	}
	/**
	 * Make a {@link Sealable#seal() seal}ed {@link SealInt} that takes on the value of the 
	 * first {@link ReadValue#isValid() valid} value in a sequence of integer values.
	 * @param <E>
	 * @param writableFirst Optionally the first value in the sequence, if you want writes to be redirected to it
	 * @param values The (remaining) sequence
	 * @return
	 */
	@SafeVarargs
	public static SealInt firstValidInt(
			ReadWriteListenDependency<Integer> writableFirst,			
			ReadListenDependency<? extends Integer>...values){
		return makeFirstValid(new SealInt(), writableFirst, values);
	}
	/**
	 * Configure an un{@link Sealable#seal() seal}ed {@link SealPile} to take on the value of the 
	 * first {@link ReadValue#isValid() valid} value in a sequence of reactive values.
	 * @param <V>
	 * @param <E>
	 * @param out The {@link SealPile}, which will get {@link Sealable#seal() seal}ed
	 * @param writableFirst Optionally the first value in the sequence, if you want writes to be redirected to it
	 * @param values0 The (remaining) sequence
	 * @return
	 */
	@SafeVarargs
	public static <V extends SealPile<E>, E> V makeFirstValid(
			V out, 
			ReadWriteListenDependency<E> writableFirst,
			ReadListenDependency<? extends E>... _values0)
	{
		ReadListenDependency<? extends E>[] values;
		if(writableFirst==null) {
			values = _values0.clone();
		}else {
			values = Arrays.copyOf(_values0, _values0.length+1);
            values[0] = writableFirst;
            System.arraycopy(_values0, 0, values, 1, _values0.length);
		}
		HashSet<ReadDependency<? extends E>> distinct=new HashSet<>();
		int shift = 0;
		for(int i=0; i<values.length; ++i) {
			if(distinct.add(values[i])) {
				if(shift>0)
					values[i-shift]=values[i];
			}else {
				++shift;
			}
		}
		ReadListenDependencyBool[] validities=new ReadListenDependencyBool[distinct.size()];
		for(int i=0; i<validities.length; ++i) {
			validities[i]=values[i].validity();
			values[i]=values[i].validBuffer_memo();
		}

		Depender pd = out.getPrivilegedDepender();
		SealPileBuilder<V, E> builder = new SealPileBuilder<V, E>(out)
				.recompute(new Consumer<Recomputation<E>>(){
					ReadDependency<? extends E> current=null;
					ReadListenDependencyBool currentValidity=null;
					ValueListener dependOnCurrentDependingOnItsValidity = e->{
						if(currentValidity==null)
							return;
						if(currentValidity.isTrue())
							;
						//							pd.addDependency(current); 
						else
							pd.removeDependency(current);
					};
					@Override
					public void accept(Recomputation<E> reco) {
						Collection<? extends Dependency> changed = reco.queryChangedDependencies(true);
						//shortcut for special case: 
						//the only changed Dependency is the old selected Dependency  
						ReadDependency<? extends E> current;
						ReadDependencyBool currentValidity;

						synchronized (this) {
							current = this.current;
							currentValidity = this.currentValidity;
						}
						if(current!=null && changed.size()==1 && changed.contains(current)) {
							if(currentValidity.isTrue()) {
								try {
									//									System.out.println("shortcut 1");
									reco.fulfill(current.getValidOrThrow(), ()->{
										pd.addDependency(current);
									});
									return;
								} catch (InvalidValueException e) {
								}
							}
						}		

						//select first valid dependency
						E firstValidValue=null;
						int validIndex;
						for(validIndex=0; validIndex<validities.length; ++validIndex){
							boolean valid = validities[validIndex].isTrue();
							if(valid) {
								try {
									firstValidValue=values[validIndex].getValidOrThrow();
									break;
								} catch (InvalidValueException e) {
								}
							}
						}
						//						System.out.println("valid index is "+validIndex);

						boolean validFound = validIndex<validities.length;

						//shortcut for special case: 
						//the same Dependency has been selected again 
						if(validFound && values[validIndex]==current) {
							//							System.out.println("shortcut 2");
							reco.fulfill(firstValidValue, ()->{
								pd.addDependency(current);
							});
							return;
						}


						if(!validFound) {
							//							System.out.println("no valid found");

							reco.fulfillInvalid(()->{
								synchronized (this) {
									for(int i=0; i<validities.length; ++i) {
										reco.addDependency(validities[i]);
										synchronized (this) {
											if(values[i]==current) {
												this.current=null;
												if(this.currentValidity!=null)
													this.currentValidity.removeValueListener(dependOnCurrentDependingOnItsValidity);
												this.currentValidity=null;
											}
											reco.removeDependency(values[i]);
										}
									}
								}						
							});
							return;
						}


						int fvi=validIndex;
						reco.fulfill(firstValidValue, ()->{
							//depend on the validity of those that can before the first valid
							//dependency, because when they become valid, it is no longer the first 
							synchronized (this) {
								for(int i=0; i<fvi; ++i) {
									reco.addDependency(validities[i]);
									synchronized (this) {
										if(values[i]==current)
											this.current=null;
										reco.removeDependency(values[i]);
									}
								}
								reco.addDependency(values[fvi]);
								this.current=values[fvi];
								if(this.currentValidity!=null)
									this.currentValidity.removeValueListener(dependOnCurrentDependingOnItsValidity);
								this.currentValidity=validities[fvi];
								this.currentValidity.addValueListener(dependOnCurrentDependingOnItsValidity);
								reco.addDependency(validities[fvi]);
								for(int i=fvi+1; i<validities.length; ++i) {
									reco.removeDependency(validities[i]);
									reco.removeDependency(values[i]);
								}
							}					
						});
					}
				})
//				.dontRetry()
				;
		if(writableFirst!=null)
			builder.seal(v->{
				writableFirst.set(v);
			});
		else
			builder.seal();
		return builder
				.build();
	}
	/**
	 * Make a {@link Sealable#seal() seal}ed {@link SealPile}
	 * that takes on as its value the first {@link ReadValue#isValid() valid} entry 
	 * in a sequence of reactive values.
	 * @param <E>
	 * @param values
	 * @return
	 */
	@SafeVarargs
	public static <I extends ReadDependency<? extends E>, E>
	SealPile<I>
	firstValidV(I... values){
		return makeFirstValidV(new SealPile<>(), values);
	}
	/**
	 * Configure an un{@link Sealable#seal() seal}ed {@link SealPile} to take
	 * on as its value the first {@link ReadValue#isValid() valid} entry 
	 * in a sequence of reactive values.
	 * @param <V>
	 * @param <I>
	 * @param <E>
	 * @param out The {@link SealPile}, which will get {@link Sealable#seal() seal}ed
	 * @param values0
	 * @see #makeFirstValid(SealPile, ReadListenDependency...) another way to do it, which is less elegant but better tested.
	 * I'll decide later which is better.
	 * @return
	 */
	@SafeVarargs
	public static <V extends SealPile<I>, I extends ReadDependency<? extends E>, E> 
	V makeFirstValidV(
			V out, I... values0
			)
	{
		I[] values = values0.clone();
		HashSet<ReadDependency<? extends E>> distinct=new HashSet<>();
		int shift = 0;
		for(int i=0; i<values.length; ++i) {
			if(distinct.add(values[i])) {
				if(shift>0)
					values[i-shift]=values[i];
			}else {
				++shift;
			}
		}
		ReadDependencyBool[] validities=new ReadDependencyBool[distinct.size()];
		for(int i=0; i<validities.length; ++i)
			validities[i]=values[i].validity();		
		return new SealPileBuilder<V, I>(out)
				.recompute(new Consumer<Recomputation<I>>(){
					//					I current=null;
					@Override
					public void accept(Recomputation<I> reco) {
						//						Collection<? extends Dependency> changed = reco.queryChangedDependencies(true);
						//						I current;
						//						synchronized (this) {
						//							current = this.current;
						//						}
						//						if(current!=null) {
						//							boolean currentChanged=changed.contains(current);
						//							boolean currentValidityChanged=changed.contains(current.validity());
						//							if(current.isValid()) {
						//								boolean ok1 = (currentChanged | currentValidityChanged) && changed.size()==1;
						//								boolean ok2 = (currentChanged & currentValidityChanged) && changed.size()==2;
						//								if(ok1 | ok2) {
						//									System.out.println("choice fulfill sc1: "+current);
						//									reco.fulfill(current);
						//									return;
						//								}
						//							}
						//						}

						int validIndex;
						for(validIndex=0; validIndex<validities.length; ++validIndex) {
							if(validities[validIndex].isTrue())
								break;
						}
						boolean validFound = validIndex<validities.length;

						//						//shortcut for special case: 
						//						//the same Dependency has been selected again 
						//						if(validFound && values[validIndex]==current) {
						//							System.out.println("choice fulfill sc2: "+current);
						//							reco.fulfill(current);
						//							return;
						//						}


						if(!validFound) {
							reco.fulfillInvalid(()->{
								for(int i=0; i<validities.length; ++i) {
									reco.addDependency(validities[i]);
								}		
							});
							return;
						}

						//						for(int i=validIndex+1; i<validities.length; ++i) {
						//							reco.removeDependency(validities[i]);
						////							reco.removeDependency(values[i]);
						//						}
						int fvi=validIndex;
						//						System.out.println("choice fulfill: "+values[validIndex]);
						reco.fulfill(values[validIndex], ()->{
							for(int i=0; i<=fvi; ++i) {
								reco.addDependency(validities[i]);
								//								reco.removeDependency(values[i]);
							}	
							//							synchronized (this) {
							////								reco.addDependency(values[validIndex]);
							//								this.current=values[fvi];
							//							}
							for(int i=fvi+1; i<validities.length; ++i) {
								reco.removeDependency(validities[i]);
								//								reco.removeDependency(values[i]);
							}
						});


					}
				})
				//				.canRecomputeWithInvalidDependencies()
				//				.dontRetry()
				.seal()
				.build();
	}
	//	public static <E> ReadListenDependency<E> firstValid(ReadDependency<E>... vals){
	//		ReadDependencyBool[] validities = new ReadDependencyBool[vals.length]; 
	//		return SealableValue<E>
	//		
	//	}

	/**
	 * Make a {@link Sealable#seal() seal}ed {@link SealPile}
	 * that takes on the same value as another if that value is {@link ReadValue#isValid() valid}
	 * and a default value if it is not. 
	 * @param <E>
	 * @param v
	 * @param def
	 * @return
	 */
	public static <E> SealPile<E> fallback(ReadListenDependency<? extends E> v, E def){
		return makeFirstValid(new SealPile<>(), null, v, constant(def));
	}
	/**
	 * Make a {@link Sealable#seal() seal}ed {@link SealPile}
	 * that takes on the same value as another if that value is {@link ReadValue#isValid() valid}
	 * and a default value if it is not. 
	 * Writes to the returned reactive value will be redirected to the given {@link ReadWriteListenDependency}.
	 * @param <E>
	 * @param v
	 * @param def
	 * @return
	 */
	public static <E> SealPile<E> fallback(ReadWriteListenDependency<E> v, E def){
		return makeFirstValid(new SealPile<>(), v, constant(def));
	}
	/**
	/**
	 * Make a {@link Sealable#seal() seal}ed {@link SealBool}
	 * that takes on the same value as another if that value is {@link ReadValue#isValid() valid}
	 * and a default value if it is not. 
	 * @param v
	 * @param def
	 * @return
	 */
	public static SealBool fallback(ReadListenDependency<? extends Boolean> v, Boolean def){
		return makeFirstValid(new SealBool(), null, v, constant(def));
	}
	/**
	 * Make a {@link Sealable#seal() seal}ed {@link SealBool}
	 * that takes on the same value as another if that value is {@link ReadValue#isValid() valid}
	 * and a default value if it is not. 
	 * Writes to the returned reactive value will be redirected to the given {@link ReadWriteListenDependency}.
	 * @param v
	 * @param def
	 * @return
	 */
	public static SealBool fallback(ReadWriteListenDependency<Boolean> v, Boolean def){
		return makeFirstValid(new SealBool(), v, constant(def));
	}
	/**
	 * Make a {@link Sealable#seal() seal}ed {@link SealInt}
	 * that takes on the same value as another if that value is {@link ReadValue#isValid() valid}
	 * and a default value if it is not. 
	 * @param v
	 * @param def
	 * @return
	 */
	public static SealInt fallback(ReadListenDependency<? extends Integer> v, Integer def){
		return makeFirstValid(new SealInt(), null, v, constant(def));
	}
	/**
	 * Make a {@link Sealable#seal() seal}ed {@link SealInt}
	 * that takes on the same value as another if that value is {@link ReadValue#isValid() valid}
	 * and a default value if it is not. 
	 * Writes to the returned reactive value will be redirected to the given {@link ReadWriteListenDependency}.
	 * @param v
	 * @param def
	 * @return
	 */
	public static SealInt fallback(ReadWriteListenDependency<Integer> v, Integer def){
		return makeFirstValid(new SealInt(), v, constant(def));
	}
	/**
	 * Make a {@link Sealable#seal() seal}ed {@link SealDouble}
	 * that takes on the same value as another if that value is {@link ReadValue#isValid() valid}
	 * and a default value if it is not. 
	 * @param v
	 * @param def
	 * @return
	 */
	public static SealDouble fallback(ReadListenDependency<? extends Double> v, Double def){
		return makeFirstValid(new SealDouble(), null, v, constant(def));
	}
	/**
	 * Make a {@link Sealable#seal() seal}ed {@link SealDouble}
	 * that takes on the same value as another if that value is {@link ReadValue#isValid() valid}
	 * and a default value if it is not. 
	 * Writes to the returned reactive value will be redirected to the given {@link ReadWriteListenDependency}.
	 * @param v
	 * @param def
	 * @return
	 */
	public static SealDouble fallback(ReadWriteListenDependency<Double> v, Double def){
		return makeFirstValid(new SealDouble(), v, constant(def));
	}

	/**
	 * Make a {@link Sealable#seal() seal}ed {@link SealString}
	 * that takes on the same value as another if that value is valid
	 * and a default value if it is not. 
	 * @param v
	 * @param def
	 * @return
	 */
	public static SealString fallback(ReadListenDependency<? extends String> v, String def){
		return makeFirstValid(new SealString(), null, v, constant(def));
	}
	/**
	 * Make a {@link Sealable#seal() seal}ed {@link SealString}
	 * that takes on the same value as another if that value is valid
	 * and a default value if it is not. 
	 * Writes to the returned reactive value will be redirected to the given {@link ReadWriteListenDependency}.
	 * @param v
	 * @param def
	 * @return
	 */
	public static SealString fallback(ReadWriteListenDependency<String> v, String def){
		return makeFirstValid(new SealString(), v, constant(def));
	}










	/** @see ISealPileBuilder#setupRateLimited(ReadListenValue, long, long) */
	public static <E> SealPile<E> rateLimited(ReadListenValue<E> leader, long coldStartTime, long coolDownTime) {
		return Piles.<E>sb().setupRateLimited(leader, coldStartTime, coolDownTime).build();
	}
	/** @see ISealPileBuilder#setupRateLimited(ReadListenValue, long, long) */
	public static SealBool rateLimitedBool(ReadListenValue<Boolean> leader, long coldStartTime, long coolDownTime) {
		return PileBool.sb().setupRateLimited(leader, coldStartTime, coolDownTime).build();
	}
	/** @see ISealPileBuilder#setupRateLimited(ReadListenValue, long, long) */
	public static SealInt rateLimitedInt(ReadListenValue<Integer> leader, long coldStartTime, long coolDownTime) {
		return PileInt.sb().setupRateLimited(leader, coldStartTime, coolDownTime).build();
	}
	/** @see ISealPileBuilder#setupRateLimited(ReadListenValue, long, long) */
	public static SealDouble rateLimitedDouble(ReadListenValue<Double> leader, long coldStartTime, long coolDownTime) {
		return PileDouble.sb().setupRateLimited(leader, coldStartTime, coolDownTime).build();
	}
	/** @see ISealPileBuilder#setupRateLimited(ReadListenValue, long, long) */
	public static SealString rateLimitedString(ReadListenValue<String> leader, long coldStartTime, long coolDownTime) {
		return PileString.sb().setupRateLimited(leader, coldStartTime, coolDownTime).build();
	}

	/** @see ISealPileBuilder#setupWritableRateLimited(ReadWriteListenValue, long, long) */
	public static <E> SealPile<E> writableRateLimited(ReadWriteListenValue<E> leader, long coldStartTime, long coolDownTime) {
		return Piles.<E>sb().setupWritableRateLimited(leader, coldStartTime, coolDownTime).build();
	}
	/** @see ISealPileBuilder#setupWritableRateLimited(ReadWriteListenValue, long, long) */
	public static SealBool writableRateLimitedBool(ReadWriteListenValue<Boolean> leader, long coldStartTime, long coolDownTime) {
		return PileBool.sb().setupWritableRateLimited(leader, coldStartTime, coolDownTime).build();
	}
	/** @see ISealPileBuilder#setupWritableRateLimited(ReadWriteListenValue, long, long) */
	public static SealInt writableRateLimitedInt(ReadWriteListenValue<Integer> leader, long coldStartTime, long coolDownTime) {
		return PileInt.sb().setupWritableRateLimited(leader, coldStartTime, coolDownTime).build();
	}
	/** @see ISealPileBuilder#setupWritableRateLimited(ReadWriteListenValue, long, long) */
	public static SealDouble writableRateLimitedDouble(ReadWriteListenValue<Double> leader, long coldStartTime, long coolDownTime) {
		return PileDouble.sb().setupWritableRateLimited(leader, coldStartTime, coolDownTime).build();
	}
	/** @see ISealPileBuilder#setupWritableRateLimited(ReadWriteListenValue, long, long) */
	public static SealString writableRateLimitedString(ReadWriteListenValue<String> leader, long coldStartTime, long coolDownTime) {
		return PileString.sb().setupWritableRateLimited(leader, coldStartTime, coolDownTime).build();
	}







	/**
	 * The {@link AggregationMonoid} for the {@link PileBool#and(ReadDependency, ReadDependency) and} operation.
	 */
	public static final BoolAggregator andAggregator = new BoolAggregator(true, PileBool::and);
	/**
	 * The {@link AggregationMonoid} for the {@link PileBool#and2(ReadDependency, ReadDependency) an2d} operation.
	 */
	public static final BoolAggregator and2Aggregator = new BoolAggregator(true, PileBool::and2);
	/**
	 * The {@link AggregationMonoid} for the {@link PileBool#and3(ReadDependency, ReadDependency) and3} operation.
	 */
	public static final BoolAggregator and3Aggregator = new BoolAggregator(true, PileBool::and3);
	/**
	 * The {@link AggregationMonoid} for the {@link PileBool#and(ReadDependency, ReadDependency) andNn} operation.
	 */
	public static final BoolAggregator andNnAggregator = new BoolAggregator(null, PileBool::andNn);
	/**
	 * The {@link AggregationMonoid} for the {@link PileBool#or(ReadDependency, ReadDependency) or} operation.
	 */
	public static final BoolAggregator orAggregator = new BoolAggregator(false, PileBool::or);
	/**
	 * The {@link AggregationMonoid} for the {@link PileBool#or2(ReadDependency, ReadDependency) or2} operation.
	 */
	public static final BoolAggregator or2Aggregator = new BoolAggregator(false, PileBool::or2);
	/**
	 * The {@link AggregationMonoid} for the {@link PileBool#or3(ReadDependency, ReadDependency) or3} operation.
	 */
	public static final BoolAggregator or3Aggregator = new BoolAggregator(false, PileBool::or3);
	/**
	 * The {@link AggregationMonoid} for the {@link PileBool#or(ReadDependency, ReadDependency) orNn} operation.
	 */
	public static final BoolAggregator orNnAggregator = new BoolAggregator(null, PileBool::orNn);


	/**
	 * Make a <q>transform dummy</q>: A {@link PileImpl} whose purpose is to forward
	 * {@link TransformableValue#transform(Object, Runnable transform}ations to
	 * its {@link Depender}s
	 * <br>
	 * NOTE: Since the value of a transform dummy is of no interest, I could have made a specialized class
	 * for this instead of using {@link PileImpl}. But besides the code bloat, the memory and runtime 
	 * saved by this will in most cases be outdone by the memory and startup time required for the
	 * extra {@link Class}es. 
	 * @param accept A predicate that is evaluated on the object describing the transformation
	 * to decide whether to forward the transformation request
	 * @param deps
	 * @return
	 */
	public static PileImpl<?> transformDummy(Predicate<? super Object> accept, Dependency... deps){
		PileImpl<?> ret = compute(Functional.NULL_SUPPLIER)
				.transformHandler((v, t)->{
					if(accept.test(t))
						return TransformReaction.justPropagateWithTransaction();
					else
						return null;
				})
				.whenChanged(deps);
		return ret;
	}
	/**
	 * Make a <q>transform dummy</q>: A {@link PileImpl} whose purpose is to forward
	 * {@link TransformableValue#transform(Object, Runnable transform}ations to
	 * its {@link Depender}s
	 * @param deps
	 * @return
	 */
	public static PileImpl<?> transformDummy(Dependency... deps) {
		return transformDummy(Functional.CONST_TRUE, deps);
	}
	/**
	 * A special value used by {@link AbstractPileBuilder}: 
	 * Return it from {@link IPileBuilder#recomputeStaged(Function)} to fulfill the recomputation
	 * with {@link Recomputation#fulfillInvalid()}
	 */
	public static final Runnable FULFILL_INVALID=()->{};
	/**
	 * A special value used by {@link AbstractPileBuilder}: 
	 * Return it from {@link IPileBuilder#recomputeStaged(Function)} to fulfill the recomputation
	 * with a <code>null</code> value.
	 */
	public static final Runnable FULFILL_NULL = ()->{};
	/**
	 * A {@link SealedValue} that always holds a <code>null</code> value 
	 */
	public static final SealPile<?> SEALED_NULL = sealedConstant((Object)null);
	
	@SuppressWarnings("unchecked")
	public static final <T> SealPile<T> sealedNull(){
		return (SealPile<T>)SEALED_NULL;
	}
	/**
	 * Call this nop-method to load the {@link Piles} class.
	 * If you experience problems with the order of class loading, you should call this
	 * method before using any other part of this framework.
	 */
	public static void loadClass() {}
	/**
	 * A way to <q>recompute</q> a {@link PileImpl} by restoring its old value
	 * This is just a reification of the {@link Recomputation#restoreOldValue()} method 
	 */
	public static Consumer<Recomputation<?>> RESTORE_OLD_VALUE=Recomputation::restoreOldValue;






	/**
	 * {@link Pile#revalidate() Revalidate} all piles that the given {@link Depender} 
	 * depends on transitively, via {@link Dependency} relationships or
	 * {@link Pile#giveInfluencers(Consumer) influencer} relationships.
	 * Which of these relationships are actually used is determined by two {@link Predicate}s.
	 * @param d
	 * @param followDependency 
	 * @param followInfluencer
	 */
	public static void superDeepRevalidate(Depender d, Predicate<? super Dependency> followDependency, Predicate<? super Object> followInfluencer) {
		List<Pile<?>> found = new ArrayList<>();
		collectDependenciesAndInfluencers(d, followDependency, followInfluencer, new HashSet<>(), o->{
			if(o instanceof Pile<?>) {
				Pile<?> v = (Pile<?>) o;
				if(v._isRecomputerDefined())
					found.add(v);
			}
		});
		try(Suppressor s = Suppressor.many(PileImpl.SUPPRESS_AUTO_VALIDATION, found)){
			found.forEach(Pile::revalidate);
		}		
	}
	
	/**
	 * Gather all objects all piles that the given object 
	 * depends on transitively, via {@link Dependency} relationships or
	 * {@link Pile#giveInfluencers(Consumer) influencer} relationships.
	 * Which of these relationships are actually used is determined by two {@link Predicate}s.

	 * @param <T>
	 * @param o
	 * @param followDependency Whether to consider a {@link Dependency} of {@code o},
	 * if {@code o} is a {@link Dependency}
	 * @param followInfluencer Whether to consider an influnecer of {@code o},
	 * if {@code o} {@link HasInfluencers}
	 * @param dedup Everything that was found will be added here. The objects already in this set
	 * will not be considered again.
	 * @param found Newly found objects will be given to this {@link Consumer}
	 * @return
	 */
	public static <T extends Set<Object>> T collectDependenciesAndInfluencers(
			Object o, 
			Predicate<? super Dependency> followDependency, 
			Predicate<? super Object> followInfluencer,
			T dedup,
			Consumer<? super Object> found) {
		if(dedup.add(o)) {
			found.accept(o);
			if(o instanceof Depender) {
				Depender d = (Depender)o;
				d.giveDependencies(dep->{
					if(followDependency.test(dep))
						collectDependenciesAndInfluencers(dep, followDependency, followInfluencer, dedup, found);
				});
			}
			if(o instanceof HasInfluencers) {
				HasInfluencers hi = (HasInfluencers) o;
				hi.giveInfluencers(i->{
					if(followInfluencer.test(i))
						collectDependenciesAndInfluencers(i, followDependency, followInfluencer, dedup, found);
				});
			}
		}

		return dedup;
	}


	
	
	
	
	
	
	
	
	
	
	
	
	
	/**
	 * Make a {@link Sealable#seal() seal}ed {@link SealBool} that takes on the boolean value 
	 * of the value held by another {@link ReadListenDependency value}
	 * @param <E>
	 * @param derefThis
	 * @return
	 */
	public static SealBool derefBool(ReadDependency<? extends ReadDependency<? extends Boolean>> derefThis){
		return makeDeref(derefThis, new SealBool());
	}
	/**
	 * Make a {@link Sealable#seal() seal}ed {@link SealInt} that takes on the integer value 
	 * of the value held by another {@link ReadListenDependency value}
	 * @param <E>
	 * @param derefThis
	 * @return
	 */
	public static SealInt derefInt(ReadDependency<? extends ReadDependency<? extends Integer>> derefThis){
		return makeDeref(derefThis, new SealInt());
	}
	/**
	 * Make a {@link Sealable#seal() seal}ed {@link SealDouble} that takes on the double precision value 
	 * of the value held by another {@link ReadListenDependency value}
	 * @param <E>
	 * @param derefThis
	 * @return
	 */
	public static SealDouble derefDouble(ReadDependency<? extends ReadDependency<? extends Double>> derefThis){
		return makeDeref(derefThis, new SealDouble());
	}
	/**
	 * Make a {@link Sealable#seal() seal}ed {@link SealString} that takes on the {@link String} value 
	 * of the value held by another {@link ReadListenDependency value}
	 * @param <E>
	 * @param derefThis
	 * @return
	 */
	public static SealString derefString(ReadDependency<? extends ReadDependency<? extends String>> derefThis){
		return makeDeref(derefThis, new SealString());
	}
	
	/**
	 * Make a {@link Sealable#seal() seal}ed value that takes on the value 
	 * of the value held by another {@link ReadListenDependency value}
	 * @param <E>
	 * @param derefThis
	 * @return
	 */
	public static <T> SealPile<T> deref(ReadDependency<? extends ReadDependency<? extends T>> derefThis){
		return makeDeref(derefThis, new SealPile<>());
	}
	/**
	 * Configure an un{@link Sealable#seal() seal}ed {@link SealPile} to take on the value 
	 * of the value held by another {@link ReadListenDependency value}
	 * @param <V>
	 * @param <E>
	 * @param derefThis
	 * @param putHere The {@link SealPile}, which will get {@link Sealable#seal() seal}ed
	 * @return
	 */
	public static <V extends SealPile<E>, E> V makeDeref(ReadDependency<? extends ReadDependency<? extends E>> derefThis, V putHere) {
		return new SealPileBuilder<V, E>(putHere).setupDeref(derefThis).build();
	}




	/**
	 * Make a {@link Sealable#seal() seal}ed {@link SealPile} that takes on the value 
	 * of the value held by another {@link ReadListenDependency value}, and forwards writes to it.
	 * @param <E>
	 * @param derefThis
	 * @return
	 */
	public static <E> SealPile<E> writableDeref(ReadDependency<? extends ReadWriteDependency<E>> derefThis){
		return makeWritableDeref(derefThis, new SealPile<>());
	}
	/**
	 * Make a {@link Sealable#seal() seal}ed {@link SealBool} that takes on the boolean value 
	 * of the value held by another {@link ReadListenDependency value}, and forwards writes to it.
	 * @param <E>
	 * @param derefThis
	 * @return
	 */
	public static SealBool writableDerefBool(ReadDependency<? extends ReadWriteDependency<Boolean>> derefThis){
		return makeWritableDeref(derefThis, new SealBool());
	}
	/**
	 * Make a {@link Sealable#seal() seal}ed {@link SealInt} that takes on the integer value 
	 * of the value held by another {@link ReadListenDependency value}, and forwards writes to it.
	 * @param <E>
	 * @param derefThis
	 * @return
	 */
	public static SealInt writableDerefInt(ReadDependency<? extends ReadWriteDependency<Integer>> derefThis){
		return makeWritableDeref(derefThis, new SealInt());
	}
	/**
	 * Make a {@link Sealable#seal() seal}ed {@link SealDouble} that takes on the double precision value 
	 * of the value held by another {@link ReadListenDependency value}, and forwards writes to it.
	 * @param <E>
	 * @param derefThis
	 * @return
	 */
	public static SealDouble writableDerefDouble(ReadDependency<? extends ReadWriteDependency<Double>> derefThis){
		return makeWritableDeref(derefThis, new SealDouble());
	}
	/**
	 * Make a {@link Sealable#seal() seal}ed {@link SealString} that takes on the {@link String} value 
	 * of the value held by another {@link ReadListenDependency value}, and forwards writes to it.
	 * @param <E>
	 * @param derefThis
	 * @return
	 */
	public static SealString writableDerefString(ReadDependency<? extends ReadWriteDependency<String>> derefThis){
		return makeWritableDeref(derefThis, new SealString());
	}
	/**
	 * Configure an un{@link Sealable#seal() seal}ed {@link SealPile} to take on the value 
	 * of the value held by another {@link ReadListenDependency value}, and forward writes to it.
	 * @param <V>
	 * @param <E>
	 * @param derefThis
	 * @param putHere The {@link SealPile}, which will get {@link Sealable#seal() seal}ed
	 * @return
	 */
	public static <V extends SealPile<E>, E> V makeWritableDeref(ReadDependency<? extends ReadWriteDependency<E>> derefThis, V putHere) {
		return new SealPileBuilder<V, E>(putHere).setupWritableDeref(derefThis).build();
	}


	

	/** @see ISealPileBuilder#setupBuffer(ReadListenValue) */

	public static <E> SealPile<E> buffer(ReadListenValue<E> leader) {
		return Piles.<E>sb().setupBuffer(leader).build();
	}
	/** @see ISealPileBuilder#setupBuffer(ReadListenValue) */
	public static SealBool bufferBool(ReadListenValue<Boolean> leader) {
		return PileBool.sb().setupBuffer(leader).build();
	}
	/** @see ISealPileBuilder#setupBuffer(ReadListenValue) */
	public static SealInt bufferInt(ReadListenValue<Integer> leader) {
		return PileInt.sb().setupBuffer(leader).build();
	}
	/** @see ISealPileBuilder#setupBuffer(ReadListenValue) */
	public static SealDouble bufferDouble(ReadListenValue<Double> leader) {
		return PileDouble.sb().setupBuffer(leader).build();
	}
	/** @see ISealPileBuilder#setupBuffer(ReadListenValue) */
	public static SealString bufferString(ReadListenValue<String> leader) {
		return PileString.sb().setupBuffer(leader).build();
	}






	/** @see ISealPileBuilder#setupWritableBuffer(ReadListenValue) */

	public static <E> SealPile<E> writableBuffer(ReadWriteListenValue<E> leader) {
		return Piles.<E>sb().setupWritableBuffer(leader).build();
	}
	/** @see ISealPileBuilder#setupWritableBuffer(ReadListenValue) */
	public static SealBool writableBufferBool(ReadWriteListenValue<Boolean> leader) {
		return PileBool.sb().setupWritableBuffer(leader).build();
	}
	/** @see ISealPileBuilder#setupWritableBuffer(ReadListenValue) */
	public static SealInt writableBufferInt(ReadWriteListenValue<Integer> leader) {
		return PileInt.sb().setupWritableBuffer(leader).build();
	}
	/** @see ISealPileBuilder#setupWritableBuffer(ReadListenValue) */
	public static SealDouble writableBufferDouble(ReadWriteListenValue<Double> leader) {
		return PileDouble.sb().setupWritableBuffer(leader).build();
	}
	/** @see ISealPileBuilder#setupWritableBuffer(ReadListenValue) */
	public static SealString writableBufferDtSealableString(ReadWriteListenValue<String> leader) {
		return PileString.sb().setupWritableBuffer(leader).build();
	}
	
	
	
	
	/** @see IIndependentBuilder#setupValidBuffer(ReadListenValue) */
	public static <E> Independent<E> validBuffer(ReadListenValue<E> leader){
		return Piles.<E>ib().setupValidBuffer(leader).build();
	}
	/** @see IIndependentBuilder#setupValidBuffer(ReadListenValue) */
	public static IndependentBool validBufferBool(ReadListenValue<Boolean> leader){
		return PileBool.ib().setupValidBuffer(leader).build();
	}
	/** @see IIndependentBuilder#setupValidBuffer(ReadListenValue) */
	public static IndependentInt validBufferInt(ReadListenValue<Integer> leader){
		return PileInt.ib().setupValidBuffer(leader).build();
	}
	/** @see IIndependentBuilder#setupValidBuffer(ReadListenValue) */
	public static IndependentDouble validBufferDouble(ReadListenValue<Double> leader){
		return PileDouble.ib().setupValidBuffer(leader).build();
	}
	/** @see IIndependentBuilder#setupValidBuffer(ReadListenValue) */
	public static IndependentString validBufferString(ReadListenValue<String> leader){
		return PileString.ib().setupValidBuffer(leader).build();
	}

	/** @see IIndependentBuilder#setupWritableValidBuffer(ReadListenValue) */
	public static <E> Independent<E> writableValidBuffer(ReadWriteListenValue<E> leader){
		return Piles.<E>ib().setupWritableValidBuffer(leader, null).build();
	}
	/** @see IIndependentBuilder#setupWritableValidBuffer(ReadListenValue) */
	public static IndependentBool writableValidBufferBool(ReadWriteListenValue<Boolean> leader){
		return PileBool.ib().setupWritableValidBuffer(leader, null).build();
	}
	/** @see IIndependentBuilder#setupWritableValidBuffer(ReadListenValue) */
	public static IndependentInt writableValidBufferInt(ReadWriteListenValue<Integer> leader){
		return PileInt.ib().setupWritableValidBuffer(leader, null).build();
	}
	/** @see IIndependentBuilder#setupWritableValidBuffer(ReadListenValue) */
	public static IndependentDouble writableValidBufferDouble(ReadWriteListenValue<Double> leader){
		return PileDouble.ib().setupWritableValidBuffer(leader, null).build();
	}
	/** @see IIndependentBuilder#setupWritableValidBuffer(ReadListenValue) */
	public static IndependentString writableValidBufferString(ReadWriteListenValue<String> leader){
		return PileString.ib().setupWritableValidBuffer(leader, null).build();
	}
	
	
	/** @param defer 
	 * @see IIndependentBuilder#setupWritableValidBuffer(ReadListenValue) */
	public static <E> Independent<E> writableValidBuffer(ReadWriteListenValue<E> leader, Function<Consumer<? super E>, Consumer<? super E>> defer){
		return Piles.<E>ib().setupWritableValidBuffer(leader, defer).build();
	}
	/** @see IIndependentBuilder#setupWritableValidBuffer(ReadListenValue) */
	public static IndependentBool writableValidBufferBool(ReadWriteListenValue<Boolean> leader, Function<Consumer<? super Boolean>, Consumer<? super Boolean>> defer){
		return PileBool.ib().setupWritableValidBuffer(leader, defer).build();
	}
	/** @see IIndependentBuilder#setupWritableValidBuffer(ReadListenValue) */
	public static IndependentInt writableValidBufferInt(ReadWriteListenValue<Integer> leader, Function<Consumer<? super Integer>, Consumer<? super Integer>> defer){
		return PileInt.ib().setupWritableValidBuffer(leader, defer).build();
	}
	/** @see IIndependentBuilder#setupWritableValidBuffer(ReadListenValue) */
	public static IndependentDouble writableValidBufferDouble(ReadWriteListenValue<Double> leader, Function<Consumer<? super Double>, Consumer<? super Double>> defer){
		return PileDouble.ib().setupWritableValidBuffer(leader, defer).build();
	}
	/** @see IIndependentBuilder#setupWritableValidBuffer(ReadListenValue) */
	public static IndependentString writableValidBufferString(ReadWriteListenValue<String> leader, Function<Consumer<? super String>, Consumer<? super String>> defer){
		return PileString.ib().setupWritableValidBuffer(leader, defer).build();
	}
	
	
	
	
	
	
	/**
	 * Create a generic {@link PileBuilder}
	 * @param <E>
	 * @return
	 */
	public static <E> PileBuilder<PileImpl<E>, E> rb(){return new PileBuilder<>(new PileImpl<>());}
	/**
	 * Create a generic {@link SealPileBuilder}
	 * @param <E>
	 * @return
	 */
	public static <E> SealPileBuilder<SealPile<E>, E> sb(){return new SealPileBuilder<>(new SealPile<>());}
	/**
	 * Create a generic {@link IndependentBuilder} with a <code>null</code> initial value
	 * @param <E>
	 * @return
	 */
	public static <E> IndependentBuilder<Independent<E>, E> ib(){return new IndependentBuilder<>(new Independent<>(null));}
	/**
	 * Create a generic {@link IndependentBuilder} 
	 * @param <E>
	 * @param init initial value of the {@link Independent}
	 * @return
	 */
	public static <E> IndependentBuilder<Independent<E>, E> ib(E init){return new IndependentBuilder<>(new Independent<>(init));}

	
	public static boolean shouldFireDeepRevalidateOnSet() {
		return !Boolean.FALSE.equals(shouldFireDeepRevalidateOnSet.get());
	}
	public static MockBlock withShouldFireDeepRevalidateOnSet(Boolean should) {
		Boolean old = shouldFireDeepRevalidateOnSet.get();
		MockBlock ret = MockBlock.closeOnly(()->shouldFireDeepRevalidateOnSet.set(old));
		shouldFireDeepRevalidateOnSet.set(should);
		return ret;
	}
	public static boolean shouldDeepRevalidate() {
		return !Boolean.FALSE.equals(shouldDeepRevalidate.get());
	}
	public static MockBlock dontDeepRevalidate() {
		return withShouldDeepRevalidate(false);
	}
	public static MockBlock withShouldDeepRevalidate(Boolean should) {
		Boolean old = shouldDeepRevalidate.get();
		MockBlock ret = MockBlock.closeOnly(()->shouldDeepRevalidate.set(old));
		shouldDeepRevalidate.set(should);
		return ret;
	}

	@SafeVarargs
	public static <T> ReadListenDependency<T> firstNonNull(
			ReadDependency<? extends T>... possibilities){
		ReadDependency<? extends T>[] fp = possibilities.clone();
			
		return Piles.<T>compute(reco->{
			for(ReadDependency<? extends T> p: fp) {
				if(p==null)
					continue;
				if(!p.isValid()) {
					reco.fulfillInvalid();
					return;
				}
				try {
					T got = p.getValidOrThrow();
					if(got!=null) {
						reco.fulfill(got);
						return;
					}
				}catch(InvalidValueException e) {
					reco.fulfillInvalid();
					return;
				}
			}
		}).dd();
	}
}
