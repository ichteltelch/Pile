package pile.specialized_double;

import java.util.Comparator;
import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.ToDoubleBiFunction;

import pile.aspect.AutoValidationSuppressible;
import pile.aspect.Dependency;
import pile.aspect.Depender;
import pile.aspect.combinations.Pile;
import pile.aspect.combinations.ReadDependency;
import pile.aspect.combinations.ReadListenDependency;
import pile.aspect.combinations.ReadWriteDependency;
import pile.aspect.suppress.Suppressor;
import pile.builder.FulfillInvalid;
import pile.builder.IBuilder;
import pile.builder.IndependentBuilder;
import pile.builder.PileBuilder;
import pile.builder.SealPileBuilder;
import pile.impl.Independent;
import pile.impl.MutRef;
import pile.impl.PileImpl;
import pile.impl.Piles;
import pile.impl.Piles.AggregationMonoid;
import pile.specialized_Comparable.PileComparable;
import pile.specialized_double.combinations.ReadListenDependencyDouble;
import pile.specialized_double.combinations.ReadValueDouble;
import pile.specialized_double.combinations.ReadWriteListenDependencyDouble;
import pile.specialized_double.combinations.WriteValueDouble;
import pile.specialized_int.SealInt;
import pile.utils.Bijection;

public interface PileDouble 
extends Depender, ReadWriteListenDependencyDouble, PileComparable<Double>{

	/** Delegates to {@link PileDouble#negativeRW(ReadDependency)} */
	public static SealDouble negative(ReadWriteDependency<Double> input) {
		return negativeRW(input);
	}	
	/** Delegates to {@link PileDouble#inverseRW(ReadDependency)} */
	public static SealDouble inverse(ReadWriteDependency<Double> input) {
		return inverseRW(input);
	}
	/** Delegates to {@link PileDouble#negativeRO(ReadDependency)} */
	public static SealDouble negative(ReadDependency<? extends Double> input) {
		return negativeRO(input);
	}	
	/** Delegates to {@link PileDouble#inverseRO(ReadDependency)} */
	public static SealDouble inverse(ReadDependency<? extends Double> input) {
		return inverseRO(input);
	}

	/** Make a reactive double precision number that is the negative of the 
	 * {@code input} value. Writing to it will attempt to change the 
	 * {@code input} accordingly
	 * 
	 * @param input
	 * @return
	 */
	public static SealDouble negativeRW(ReadWriteDependency<Double> input) {
		String inputName = input.dependencyName();
		return new SealPileBuilder<SealDouble, Double>(new SealDouble())
				.recompute(reco->{
					Double v = input.get();
					if(v==null)
						reco.fulfill(null);
					else
						reco.fulfill(-v);

				})
				.seal(v->{
					if(v==null)
						input.set(null);
					else
						input.set(-v);
				})
				.name(inputName==null?"! ?":("- ("+inputName+")"))
				.whenChanged(input);
	}
	/**
	 * Make a reactive double precision number that is the inverse of the 
	 * {@code input} value. Writing to it will attempt to change the 
	 * {@code input} accordingly.
	 * @param input
	 * @return
	 */
	public static SealDouble inverseRW(ReadWriteDependency<Double> input) {
		String inputName = input.dependencyName();
		return new SealPileBuilder<SealDouble, Double>(new SealDouble())
				.recompute(reco->{
					Double v = input.get();
					if(v==null)
						reco.fulfill(null);
					else
						reco.fulfill(-v);

				})
				.seal(v->{
					if(v==null)
						input.set(null);
					else
						input.set(1/v);
				})
				.name(inputName==null?"! ?":("! ("+inputName+")^-1"))
				.whenChanged(input);
	}


	/**
	 * Make a reactive double precision number that is the negative of the 
	 * {@code input} value. Writing to it will fail. 
	 * @param input
	 * @return
	 */
	public static SealDouble negativeRO(ReadDependency<? extends Double> input) {
		String inputName = input.dependencyName();
		return new SealPileBuilder<SealDouble, Double>(new SealDouble())
				.recompute(reco->{
					Double v = input.get();
					if(v==null)
						reco.fulfill(null);
					else
						reco.fulfill(-v);

				})
				.seal()
				.name(inputName==null?"! ?":("- ("+inputName+")"))
				.whenChanged(input);
	}
	/**
	 * Make a reactive double precision number that is the inverse of the 
	 * {@code input} value. Writing to it will fail. 
	 * @param input
	 * @return
	 */
	public static SealDouble inverseRO(ReadDependency<? extends Double> input) {
		String inputName = input.dependencyName();
		return new SealPileBuilder<SealDouble, Double>(new SealDouble())
				.recompute(reco->{
					Double v = input.get();
					if(v==null)
						reco.fulfill(null);
					else
						reco.fulfill(-v);

				})
				.seal()
				.name(inputName==null?"! ?":("! ("+inputName+")^-1"))
				.whenChanged(input);
	}


	@Override public default PileDouble setNull() {
		set(null);
		return this;
	}

	/** 
	 * Make a reactive double precision number that computes itself
	 * from its operands using the given {@link ToDoubleBiFunction}
	 * @param <O1>
	 * @param <O2>
	 * @param op1
	 * @param op2
	 * @param op
	 * @return
	 */
	public static <O1, O2> SealDouble binOp(
			ReadDependency<? extends O1> op1, 
			ReadDependency<? extends O2> op2, 
			ToDoubleBiFunction<? super O1, ? super O2> op) {
		return Piles.makeBinOp(op1, op2, new SealDouble(), op::applyAsDouble);
	}
	/**
	 * Make a reactive double precision number that computes itself
	 * from its operands using the given {@link Double}-valued {@link BiFunction}
	 * @param <O1>
	 * @param <O2>
	 * @param op1
	 * @param op2
	 * @param op
	 * @return
	 */
	public static <O1, O2> SealDouble binOp(
			ReadDependency<? extends O1> op1, 
			ReadDependency<? extends O2> op2, 
			BiFunction<? super O1, ? super O2, ? extends Double> op) {
		return Piles.makeBinOp(op1, op2, new SealDouble(), op);
	}
	/**
	 * Make a reactive double precision number that computes itself as the sum of its operands.
	 * @param op1
	 * @param op2
	 * @return
	 */
	public static SealDouble add(ReadDependency<? extends Number> op1, ReadDependency<? extends Number> op2) {
		return binOp(op1, op2, (Number a, Number b)->{
			if(a==null || b==null)
				return null;
			return a.doubleValue()+b.doubleValue();		
		});
	}
	/**
	 * Make a reactive double precision number that computes itself as the difference of its operands.
	 * @param op1
	 * @param op2
	 * @return
	 */
	public static SealDouble subtract(ReadDependency<? extends Number> op1, ReadDependency<? extends Number> op2) {
		return binOp(op1, op2, (Number a, Number b)->{
			if(a==null || b==null)
				return null;
			return a.doubleValue()-b.doubleValue();		
		});
	}
	/**
	 * Make a reactive double precision number that computes itself as the product of its operands.
	 * @param op1
	 * @param op2
	 * @return
	 */
	public static SealDouble multiply(ReadDependency<? extends Number> op1, ReadDependency<? extends Number> op2) {
		return binOp(op1, op2, (Number a, Number b)->{
			if(a==null || b==null)
				return null;
			return a.doubleValue()*b.doubleValue();		
		});
	}
	/**
	 * Make a reactive double precision number that computes itself as the quotient of its operands.
	 * @param op1
	 * @param op2
	 * @return
	 */
	public static SealDouble divide(ReadDependency<? extends Number> op1, ReadDependency<? extends Number> op2) {
		return binOp(op1, op2, (Number a, Number b)->{
			if(a==null || b==null)
				return null;
			return a.doubleValue()/b.doubleValue();		
		});
	}

	/** Delegates to {@link #addRO(ReadDependency, double)} */
	public static SealDouble add(ReadDependency<? extends Number> op, double value) {
		return addRO(op, value);
	}
	/** Delegates to {@link #addRW(ReadWriteDependency, double)} */
	public static SealDouble add(ReadWriteDependency<Double> op, double value) {
		return addRW(op, value);
	}
	/**
	 * Make a reactive double value that computes itself as the sum of 
	 * a reactive double precision number and a constant.
	 * @param op
	 * @param value
	 * @return
	 */
	public static SealDouble addRO(ReadDependency<? extends Number> op, double value) {
		return op.mapToDouble(o->o==null?null:o.doubleValue()+value);
	}
	/**
	 * Make a reactive double precision number that computes itself as the sum of 
	 * a reactive double precision number and a constant. Writing to it will attempt to 
	 * change the first operand accordingly.
	 * @param op
	 * @param value
	 * @return
	 */
	public static SealDouble addRW(ReadWriteDependency<Double> op, double value) {
		return op.bijectToDouble(
				Bijection.define(o->o==null?null:o.doubleValue()+value, o->o==null?null:o.doubleValue()-value));
	}

	/** Delegates to {@link #subtractRO(ReadDependency, double)} */
	public static SealDouble subtract(ReadDependency<? extends Number> op, double value) {
		return subtractRO(op, value);
	}
	/** Delegates to {@link #subtractRW(ReadWriteDependency, double)} */
	public static SealDouble subtract(ReadWriteDependency<Double> op, double value) {
		return subtractRW(op, value);
	}
	/** 
	 * Make a reactive double precision number that computes itself as the difference of 
	 * a reactive double precision number and a constant.
	 * @param op
	 * @param value
	 * @return
	 */
	public static SealDouble subtractRO(ReadDependency<? extends Number> op, double value) {
		return addRO(op, -value);
	}
	/**
	 * Make a reactive double precision number that computes itself as the difference of 
	 * a reactive double precision number and a constant. Writing to it will attempt to 
	 * change the first operand accordingly.
	 * @param op
	 * @param value
	 * @return
	 */
	public static SealDouble subtractRW(ReadWriteDependency<Double> op, double value) {
		return addRW(op, -value);
	}
	/** Delegates to {@link #subtractRO(double, ReadDependency)} */
	public static SealDouble subtract(double value, ReadDependency<? extends Number> op) {
		return subtractRO(value, op);
	}
	/** Delegates to {@link #subtractRW(double, ReadWriteDependency)} */
	public static SealDouble subtract(double value, ReadWriteDependency<Double> op) {
		return subtractRW(value, op);
	}
	/**
	 * Make a reactive double precision number that computes itself as the difference of 
	 * a constant and a reactive double precision number.
	 * @param value
	 * @param op
	 * @return
	 */
	public static SealDouble subtractRO(double value, ReadDependency<? extends Number> op) {
		return op.mapToDouble(o->o==null?null:value-o.doubleValue());
	}
	/**
	 * Make a reactive double precision number that computes itself as the difference of 
	 * a constant and a reactive double precision number. Writing to it will attempt to 
	 * change the second operand accordingly.
	 * @param value
	 * @param op
	 * @return
	 */
	public static SealDouble subtractRW(double value, ReadWriteDependency<Double> op) {
		return op.bijectToDouble(Bijection.involution(o->o==null?null:value-o.doubleValue()));
	}

	/** Delegates to {@link #multiplyRO(ReadDependency, double)} */
	public static SealDouble multiply(ReadDependency<? extends Number> op, double value) {
		return multiplyRO(op, value);
	}
	/** Delegates to {@link #multiplyRW(ReadWriteDependency, double)} */
	public static SealDouble multiply(ReadWriteDependency<Double> op, double value) {
		return multiplyRW(op, value);
	}
	/**
	 * Make a reactive double precision number that computes itself as the product of 
	 * a reactive double precision number and a constant.
	 * @param op
	 * @param value
	 * @return
	 */
	public static SealDouble multiplyRO(ReadDependency<? extends Number> op, double value) {
		return op.mapToDouble(o->o==null?null:o.doubleValue()*value);
	}
	/**
	 * Make a reactive double precision number that computes itself as the product of 
	 * a reactive double precision number and a constant. Writing to it will attempt to 
	 * change the first operand accordingly.
	 * @param op
	 * @param value
	 * @return
	 */
	public static SealDouble multiplyRW(ReadWriteDependency<Double> op, double value) {
		return op.bijectToDouble(
				Bijection.define(o->o==null?null:o.doubleValue()*value, o->o==null?null:o.doubleValue()/value));
	}
	/** Delegates to {@link #divideRO(ReadDependency, double)} */
	public static SealDouble divide(ReadDependency<? extends Number> op, double value) {
		return divideRO(op, value);
	}
	/** Delegates to {@link #divideRW(ReadWriteDependency, double)} */
	public static SealDouble divide(ReadWriteDependency<Double> op, double value) {
		return divideRW(op, value);
	}
	/**
	 * Make a reactive double precision number that computes itself as the quotient of 
	 * a reactive double precision number and a constant.
	 * @param op
	 * @param value
	 * @return
	 */
	public static SealDouble divideRO(ReadDependency<? extends Number> op, double value) {
		return multiplyRO(op, 1/value);
	}
	/**
	 * Make a reactive double precision number that computes itself as the quotient of 
	 * a reactive double precision number and a constant. Writing to it will attempt to 
	 * change the first operand accordingly.  
	 * @param op
	 * @param value
	 * @return
	 */
	public static SealDouble divideRW(ReadWriteDependency<Double> op, double value) {
		return multiplyRO(op, 1/value);
	}

	/** Delegates to {@link #divideRO(double, ReadDependency)} */
	public static SealDouble divide(double value, ReadDependency<? extends Number> op) {
		return divideRO(value, op);
	}
	/** Delegates to {@link #divideRW(double, ReadWriteDependency)} */
	public static SealDouble divide(double value, ReadWriteDependency<Double> op) {
		return divideRW(value, op);
	}
	/**
	 * Make a reactive double precision number that computes itself as the quotient of 
	 * a constant and a reactive double precision number.
	 * @param value
	 * @param op
	 * @return
	 */
	public static SealDouble divideRO(double value, ReadDependency<? extends Number> op) {
		return op.mapToDouble(o->o==null?null:value/o.doubleValue());
	}
	/**
	 * Make a reactive double precision number that computes itself as the quotient of 
	 * a constant and a reactive double precision number. Writting to it will attempt to 
	 * change the second operand accordingly.
	 * @param value
	 * @param op
	 * @return
	 */
	public static SealDouble divideRW(double value, ReadWriteDependency<Double> op) {
		return op.bijectToDouble(Bijection.involution(o->o==null?null:value/o.doubleValue()));
	}

	/**
	 * Make a reactive double precision number that computes itself as the maximum of 
	 * its operands.
	 * @param op1
	 * @param op2
	 * @return
	 */
	public static SealDouble max(ReadDependency<? extends Number> op1, ReadDependency<? extends Number> op2) {
		return binOp(op1, op2, (Number a, Number b)->{
			if(a==null || b==null)
				return null;
			return Math.max(a.doubleValue(),b.doubleValue());		
		});
	}
	/**
	 * Make a reactive double precision number that computes itself as the minimum of 
	 * its operands.
	 * @param op1
	 * @param op2
	 * @return
	 */
	public static SealDouble min(ReadDependency<? extends Number> op1, ReadDependency<? extends Number> op2) {
		return binOp(op1, op2, (Number a, Number b)->{
			if(a==null || b==null)
				return null;
			return Math.min(a.doubleValue(),b.doubleValue());		
		});
	}
	/**
	 * Make a reactive double precision number that computes itself as the maximum of
	 * its operands.
	 * @param op1
	 * @param value
	 * @return
	 */
	public static SealDouble max(ReadDependency<? extends Number> op1, double value) {
		return op1.mapToDouble(v->v==null?null:Math.max(v.doubleValue(), value));
	}
	/**
	 * Make a reactive double precision number that computes itself as the minimum of 
	 * its operands.
	 * @param op1
	 * @param value
	 * @return
	 */
	public static SealDouble min(ReadDependency<? extends Number> op1, double value) {
		return op1.mapToDouble(v->v==null?null:Math.min(v.doubleValue(), value));
	}

	/** Specialization of {@link Piles#readOnlyWrapperIdempotent(ReadDependency)} */
	public static ReadListenDependencyDouble readOnlyWrapperIdempotent(ReadDependency<? extends Number> in){
		if(in instanceof SealDouble) {
			SealDouble cast = (SealDouble)in;
			if(cast.isDefaultSealed())
				return cast;
		}else if(in instanceof ConstantDouble) {
			ConstantDouble cast = (ConstantDouble) in;
			return cast;	
		}
		return readOnlyWrapper(in);
	}
	/**
	 * Specialization of {@link Piles#readOnlyWrapper(ReadDependency)}
	 * @param in
	 * @return
	 */
	public static SealDouble readOnlyWrapper(ReadDependency<? extends Number> in){
		return new SealPileBuilder<>(new SealDouble())
				.recompute(()->{
					Number v = in.get();
					return v==null?null:(v instanceof Double)?(Double)v:v.doubleValue();
				})
				.seal()
				.whenChanged(in);

	}
	/**
	 * Partly specialized {@link AggregationMonoid} for double precision operations
	 * @author bb
	 *
	 */
	public static class DoubleAggregator implements Piles.AggregationMonoid<Number, ReadListenDependencyDouble>{

		final ReadListenDependencyDouble neutral;
		final BiFunction<? super ReadListenDependency<? extends Number>, ? super ReadListenDependency<? extends Number>, ? extends ReadListenDependencyDouble> op;
		public DoubleAggregator(Double neutral, 
				BiFunction<? super ReadListenDependency<? extends Number>, ? super ReadListenDependency<? extends Number>, ? extends ReadListenDependencyDouble> op) {
			if(neutral==null)
				this.neutral=Piles.NULL_D;
			else if(neutral==0)
				this.neutral=Piles.ZERO_D;
			else if(neutral==1)
				this.neutral=Piles.ONE_D;
			else if(neutral==Double.POSITIVE_INFINITY)
				this.neutral=Piles.POSITIVE_INFINITY_D;
			else if(neutral==Double.NEGATIVE_INFINITY)
				this.neutral=Piles.NEGATIVE_INFINITY_D;
			else
				this.neutral=Piles.sealedConstant(neutral);
			this.op=op;
		}
		@Override
		public ReadListenDependencyDouble constantNeutral() {
			return neutral;
		}
		@Override
		public ReadListenDependencyDouble apply(ReadListenDependency<? extends Number> op1, ReadListenDependency<? extends Number> op2) {
			return op.apply(op1, op2);
		}

		@Override
		public ReadListenDependencyDouble inject(ReadListenDependency<? extends Number> o) {
			return readOnlyWrapperIdempotent(o);
		}

	}
	/**
	 * Aggregator for computing sums
	 */
	public static final DoubleAggregator sumAggregator = new DoubleAggregator(0d, PileDouble::add);
	/**
	 * Aggregator for computing products
	 */
	public static final DoubleAggregator productAggregator = new DoubleAggregator(1d, PileDouble::multiply);
	/**
	 * Aggregator for computing minima
	 */
	public static final DoubleAggregator minAggregator = new DoubleAggregator(Double.POSITIVE_INFINITY, PileDouble::min);
	/**
	 * Aggregator for computing maxima
	 */
	public static final DoubleAggregator maxAggregator = new DoubleAggregator(Double.NEGATIVE_INFINITY, PileDouble::max);

	public static ReadListenDependencyDouble sum(Iterable<? extends ReadListenDependency<? extends Number>> items) {
		return Piles.aggregate(sumAggregator, items);
	}
	@SafeVarargs
	public static ReadListenDependencyDouble sum(ReadListenDependency<? extends Number>... items) {
		return Piles.aggregate(sumAggregator, items);
	}
	public static ReadListenDependencyDouble product(Iterable<? extends ReadListenDependency<? extends Number>> items) {
		return Piles.aggregate(productAggregator, items);
	}
	@SafeVarargs
	public static ReadListenDependencyDouble product(ReadListenDependency<? extends Number>... items) {
		return Piles.aggregate(productAggregator, items);
	}
	public static ReadListenDependencyDouble min(Iterable<? extends ReadListenDependency<? extends Number>> items) {
		return Piles.aggregate(minAggregator, items);
	}
	@SafeVarargs
	public static ReadListenDependencyDouble min(ReadListenDependency<? extends Number>... items) {
		return Piles.aggregate(minAggregator, items);
	}
	public static ReadListenDependencyDouble max(Iterable<? extends ReadListenDependency<? extends Number>> items) {
		return Piles.aggregate(maxAggregator, items);
	}
	@SafeVarargs
	public static ReadListenDependencyDouble max(ReadListenDependency<? extends Number>... items) {
		return Piles.aggregate(maxAggregator, items);
	}
	/**
	 * Make a reactive integer that computes itself as the sign of the given value
	 * @param value
	 * @return
	 */
	public static SealInt signum(ReadDependency<? extends Number> value) {
		return value.mapToInt(v->v==null?null:(int)Math.signum(v.doubleValue()));
	}

	/**
	 * Define various monoids for dynamic aggregation over the dependencies.
	 * @author bb
	 *
	 */
	public static interface DoubleMonoidOp {
		public static final Double SUM_NEUTRAL = 0d;
		public static final Double PRODUCT_NEUTRAL = 1d;
		public static final Double FLIP_PRODUCT_NEUTRAL = 0d;
		public static final Double MAX_NEUTRAL = Double.NEGATIVE_INFINITY;
		public static final Double MIN_NEUTRAL = Double.POSITIVE_INFINITY;
		public static final DoubleMonoidOp SUM = (a, b)->a+b;
		public static final DoubleMonoidOp PRODUCT = (a, b)->a*b;
		/**
		 * The <q>flip-product</q> of {@code a} and {@code b} is defined as
		 * {@code 1 - (1-a) * (1-b)} 
		 */
		public static final DoubleMonoidOp FLIP_PRODUCT = (a, b)->1-(1-a)*(1-b);
		public static final DoubleMonoidOp MAX = Math::max;
		public static final DoubleMonoidOp MIN = Math::min;
		public static final Consumer<? super PileBuilder<? extends PileImpl<Double>,Double>> SUM_CONFIG = configurator(SUM_NEUTRAL, SUM);
		public static final Consumer<? super PileBuilder<? extends PileImpl<Double>,Double>> PRODUCT_CONFIG = configurator(PRODUCT_NEUTRAL, PRODUCT);
		public static final Consumer<? super PileBuilder<? extends PileImpl<Double>,Double>> FLIP_PRODUCT_CONFIG = configurator(FLIP_PRODUCT_NEUTRAL, FLIP_PRODUCT);
		public static final Consumer<? super PileBuilder<? extends PileImpl<Double>,Double>> MAX_CONFIG = configurator(MAX_NEUTRAL, MAX);
		public static final Consumer<? super PileBuilder<? extends PileImpl<Double>,Double>> MIN_CONFIG = configurator(MIN_NEUTRAL, MIN);

		public double apply(double a, double b);

		/**
		 * Make a {@link IBuilder#configure(Consumer) configurator} 
		 * that sets {@link Pile} so that it computes itself
		 * as the result of aggregating all its {@link Dependency Dependencies}
		 * that are instances of {@link ReadValueDouble} using the given monoid. 
		 * @param <V>
		 * @param ifEmpty Value to take if there are no operands; Neutral element of the monoid
		 * @param op The monoid operation
		 * @return
		 */
		public static 
		<V extends PileImpl<Double>> 
		Consumer<? super PileBuilder<? extends V,Double>> 
		configurator(Double ifEmpty, DoubleMonoidOp op){
			return vb->{vb.recompute(()->{
				V val = vb.valueBeingBuilt();
				MutRef<Double> result = new MutRef<>();
				val.giveDependencies(d->{
					if(d instanceof ReadValueDouble) {
						ReadValueDouble dd = (ReadValueDouble)d;
						Double dv = dd.get();
						if(dv==null)
							throw new FulfillInvalid("One of the operands is null");
						Double rv = result.val;
						result.val = rv==null?dv:op.apply(rv, dv);
					}
				});
				return result.val==null?ifEmpty:result.val;
			})
				.nameIfUnnamed("Dynamic double aggregator");
			};
		}

	}


	/**
	 * Make a reactive double precision number that computes itself 
	 * as the product of its {@link Dependency Dependencies} that are
	 * also {@link ReadValueDouble} instances.
	 * @param deps initial {@link Dependency Dependencies}
	 * @return
	 */
	public static PileDoubleImpl dynamicProduct(Dependency... deps) {
		return buildDynamicProduct(new PileDoubleImpl()).whenChanged(deps);
	}
	/**
	 * Make a reactive double precision number that computes itself 
	 * as the <q>flip-product</q> of its {@link Dependency Dependencies} that are
	 * also {@link ReadValueDouble} instances.
	 * @see DoubleMonoidOp#FLIP_PRODUCT
	 * @param deps initial {@link Dependency Dependencies}
	 * @return
	 */
	public static PileDoubleImpl dynamicFlipProduct(Dependency... deps) {
		return buildDynamicFlipProduct(new PileDoubleImpl()).whenChanged(deps);
	}
	/**
	 * Make a reactive double precision number that computes itself 
	 * as the minimum of its {@link Dependency Dependencies} that are
	 * also {@link ReadValueDouble} instances.
	 * @param deps initial {@link Dependency Dependencies}
	 * @return
	 */
	public static PileDoubleImpl dynamicMin(Dependency... deps) {
		return buildDynamicMin(new PileDoubleImpl()).whenChanged(deps);
	}
	/**
	 * Make a reactive double precision number that computes itself 
	 * as the maximum of its {@link Dependency Dependencies} that are
	 * also {@link ReadValueDouble} instances.
	 * @param deps initial {@link Dependency Dependencies}
	 * @return
	 */
	public static PileDoubleImpl dynamicMax(Dependency... deps) {
		return buildDynamicMax(new PileDoubleImpl()).whenChanged(deps);
	}
	/**
	 * Make a reactive double precision number that computes itself 
	 * as the sum of its {@link Dependency Dependencies} that are
	 * also {@link ReadValueDouble} instances.
	 * @return
	 */
	public static PileDoubleImpl dynamicSum() {
		return buildDynamicSum(new PileDoubleImpl()).build();
	}
	/**
	 * Make a reactive double precision number that computes itself 
	 * as the product of its {@link Dependency Dependencies} that are
	 * also {@link ReadValueDouble} instances.
	 * @return
	 */
	public static PileDoubleImpl dynamicProduct() {
		return buildDynamicProduct(new PileDoubleImpl()).build();
	}
	/**
	 * Make a reactive double precision number that computes itself 
	 * as the <q>flip-product</q> of its {@link Dependency Dependencies} that are
	 * also {@link ReadValueDouble} instances.
	 * @see DoubleMonoidOp#FLIP_PRODUCT
	 * @return
	 */
	public static PileDoubleImpl dynamicFlipProduct() {
		return buildDynamicFlipProduct(new PileDoubleImpl()).build();
	}
	/**
	 * Make a reactive double precision number that computes itself 
	 * as the minimum of its {@link Dependency Dependencies} that are
	 * also {@link ReadValueDouble} instances.
	 * @return
	 */
	public static PileDoubleImpl dynamicMin() {
		return buildDynamicMin(new PileDoubleImpl()).build();
	}
	/**
	 * Make a reactive double precision number that computes itself 
	 * as the maximum of its {@link Dependency Dependencies} that are
	 * also {@link ReadValueDouble} instances.
	 * @return
	 */
	public static PileDoubleImpl dynamicMax() {
		return buildDynamicMax(new PileDoubleImpl()).build();
	}
	/**
	 * Make a {@link PileBuilder} configured to make the given value compute itself
	 * as the sum of its {@link Dependency Dependencies} that are also
	 * {@link ReadValueDouble} instances. 
	 * @param <V>
	 * @param val
	 * @return
	 */
	public static <V extends PileImpl<Double>> PileBuilder<V, Double> buildDynamicSum(V val){
		return new PileBuilder<>(val).configure(DoubleMonoidOp.SUM_CONFIG);
	}
	/**
	 * Make a reactive double precision number that computes itself 
	 * as the sum of its {@link Dependency Dependencies} that are
	 * also {@link ReadValueDouble} instances.
	 * @param deps initial {@link Dependency Dependencies}
	 * @return
	 */
	public static PileDoubleImpl dynamicSum(Dependency... deps) {
		return buildDynamicSum(new PileDoubleImpl()).whenChanged(deps);
	}
	/**
	 * Make a {@link PileBuilder} configured to make the given value compute itself 
	 * as the product of its {@link Dependency Dependencies} that are also
	 * {@link ReadValueDouble} instances. 
	 */
	public static <V extends PileImpl<Double>> PileBuilder<V, Double> buildDynamicProduct(V val){
		return new PileBuilder<>(val).configure(DoubleMonoidOp.PRODUCT_CONFIG);
	}
	/**
	 * Make a {@link PileBuilder} configured to make the given value compute itself 
	 * as the <q>flip-product</q> of its {@link Dependency Dependencies} that are
	 * also {@link ReadValueDouble} instances. 
	 * @see DoubleMonoidOp#FLIP_PRODUCT
	 * @param <V>
	 * @param val
	 * @return
	 */
	public static <V extends PileImpl<Double>> PileBuilder<V, Double> buildDynamicFlipProduct(V val){
		return new PileBuilder<>(val).configure(DoubleMonoidOp.FLIP_PRODUCT_CONFIG);
	}
	/**
	 * Make a {@link PileBuilder} configured to make the given value compute itself 
	 * as the minimum of its {@link Dependency Dependencies} that are 
	 * also {@link ReadValueDouble} instances. 
	 * @param <V>
	 * @param val
	 * @return
	 */
	public static <V extends PileImpl<Double>> PileBuilder<V, Double> buildDynamicMin(V val){
		return new PileBuilder<>(val).configure(DoubleMonoidOp.MIN_CONFIG);
	}
	/**
	 * Make a {@link PileBuilder} configured to make the given value compute itself 
	 * as the maximum of its {@link Dependency Dependencies} that are
	 * also {@link ReadValueDouble} instances. 
	 * @param <V>
	 * @param val
	 * @return
	 */
	public static <V extends PileImpl<Double>> PileBuilder<V, Double> buildDynamicMax(V val){
		return new PileBuilder<>(val).configure(DoubleMonoidOp.MAX_CONFIG);
	}

	/**
	 * Make a {@link PileBuilder} configured to make the given value compute itself 
	 * as the result of aggregating with the the given {@link DoubleMonoidOp DoubleMonoidOp}
	 * all its {@link Dependency Dependencies} that are also {@link ReadValueDouble} instances. 
	 * @param <V>
	 * @param val
	 * @param ifEmpty Value to take if there are no operands; Neutral element of the monoid
	 * @param op The monoid operation
	 * @return
	 */
	public static <V extends PileImpl<Double>> PileBuilder<V, Double> buildDynamicMonoid(V val, Double ifEmpty, DoubleMonoidOp op){
		return new PileBuilder<>(val).configure(DoubleMonoidOp.configurator(ifEmpty, op));
	}
	/**
	 * Create a {@link PileBuilder} for reactive double precision values
	 * @return
	 */
	public static PileBuilder<PileDoubleImpl, Double> rb(){return new PileBuilder<>(new PileDoubleImpl()).ordering(Comparator.naturalOrder());}
	/**
	 * Create a {@link SealPileBuilder} for sealable reactive double precision values
	 * @return
	 */
	public static SealPileBuilder<SealDouble, Double> sb(){return new SealPileBuilder<>(new SealDouble()).ordering(Comparator.naturalOrder());}
	/**
	 * Create an {@link IndependentBuilder} for independent reactive double precision values with a <code>null</code> initial value.
	 * @return
	 */
	public static IndependentBuilder<IndependentDouble, Double> ib(){return new IndependentBuilder<>(new IndependentDouble(null)).ordering(Comparator.naturalOrder());}
	/**
	 * Create an {@link IndependentBuilder} for independent reactive double precision values
	 * @param init initial value of the {@link Independent}
	 * @return
	 */
	public static IndependentBuilder<IndependentDouble, Double> ib(Double init){return new IndependentBuilder<>(new IndependentDouble(init)).ordering(Comparator.naturalOrder());}
	
	/**
	 * A value that reflects the average of the given values.
	 * If written to, it will set the value of all the given values to the new average.
	 * Note: if the input values do for some reason no change to the given value,
	 * the average value will assume the value of their actual average after the write attempt.
	 * 
	 * @param items
	 * @return
	 */
	
	public static ReadWriteListenDependencyDouble writableAverage(ReadWriteListenDependencyDouble... items) {
		Objects.requireNonNull(items);
		for(Object o: items)
			Objects.requireNonNull(o);
		ReadListenDependencyDouble sum = sum(items);
		return Piles.sealed(sum.get())
				.recompute(()->{
					Double s = sum.get();
					if(s==null)
						return s;
					return s/items.length;
				})
				.seal(v->{
					AutoValidationSuppressible asum;
					if(sum instanceof AutoValidationSuppressible) {
						asum = (AutoValidationSuppressible)sum;
						try (Suppressor _s = asum.suppressAutoValidation()) {
							for(WriteValueDouble i: items)
								i.set(v);
						}
					} else {
						for(WriteValueDouble i: items)
							i.set(v);
					}
				})
				.whenChanged(sum);
	}

}
