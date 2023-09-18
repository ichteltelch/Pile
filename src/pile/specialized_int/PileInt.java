package pile.specialized_int;

import java.util.Comparator;
import java.util.function.BiFunction;
import java.util.function.ToIntBiFunction;

import pile.aspect.Depender;
import pile.aspect.WriteValue;
import pile.aspect.combinations.ReadDependency;
import pile.aspect.combinations.ReadListenDependency;
import pile.aspect.combinations.ReadWriteDependency;
import pile.builder.IndependentBuilder;
import pile.builder.PileBuilder;
import pile.builder.SealPileBuilder;
import pile.impl.Independent;
import pile.impl.Piles;
import pile.impl.Piles.AggregationMonoid;
import pile.impl.SealPile;
import pile.specialized_Comparable.PileComparable;
import pile.specialized_bool.SealBool;
import pile.specialized_int.combinations.ReadListenDependencyInt;
import pile.specialized_int.combinations.ReadWriteListenDependencyInt;
import pile.utils.Bijection;

public interface PileInt 
extends Depender, ReadWriteListenDependencyInt, PileComparable<Integer>{

	/** Delegates to {@link PileInt#negativeRW(ReadDependency)} */
	public static SealInt negative(ReadWriteDependency<Integer> input) {
		return negativeRW(input);
	}
	/** Delegates to {@link PileInt#negativeRO(ReadDependency)} */
	public static SealInt negative(ReadDependency<? extends Integer> input) {
		return negativeRO(input);
	}

	/** Make a reactive integer value that is the negative of the 
	 * {@code input} value. Writing to it will attempt to change the 
	 * {@code input} accordingly
	 * 
	 * @param input
	 * @return
	 */
	public static SealInt negativeRW(ReadWriteDependency<Integer> input) {
		String inputName = input.dependencyName();
		return new SealPileBuilder<SealInt, Integer>(new SealInt())
				.recompute(reco->{
					Integer v = input.get();
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
	 * Make a reactive integer value that is the negative of the 
	 * {@code input} value. 
	 * @param input
	 * @return
	 */
	public static SealInt negativeRO(ReadDependency<? extends Integer> input) {
		String inputName = input.dependencyName();
		return new SealPileBuilder<SealInt, Integer>(new SealInt())
				.recompute(reco->{
					Integer v = input.get();
					if(v==null)
						reco.fulfill(null);
					else
						reco.fulfill(-v);

				})
				.seal()
				.name(inputName==null?"! ?":("- ("+inputName+")"))
				.whenChanged(input);
	}
	@Override public default PileInt setNull() {
		set(null);
		return this;
	}



	/**
	 * Make a reactive integer that computes itself to the result of comparing
	 * two reactive values according to their natural ordering
	 * @param <E>
	 * @param op1
	 * @param op2
	 * @param nullIsLess Whether a <code>null</code> reference should be 
	 * treated as less than all other values of type {@code E}.
	 * If this Parameter is <code>null</code>, the reactive integer will 
	 * take the value <code>null</code>
	 * @return
	 */
	public static <E extends Comparable<? super E>> 
	SealInt comparison(
			ReadDependency<? extends E> op1, 
			ReadDependency<? extends E> op2,
			Boolean nullIsLess
			){
		return Piles.makeBinOp(op1, op2, new SealInt(), (o1, o2)->{
			int comp;
			if(o1==o2) {
				if(o1==null & nullIsLess==null)
					return null;			
				comp=0;
			}else if(o1==null) {
				if(nullIsLess==null)
					return null;
				comp=nullIsLess?-1:1;
			}else if(o2==null) {
				if(nullIsLess==null)
					return null;
				comp=nullIsLess?1:-1;
			}else
				comp = o1.compareTo(o2);
			return comp;
		});
	}
	/**
	 * Make a reactive integer that computes itself as the result of comparing
	 * a constant to a reactive value according to their natural ordering
	 * @param <E>
	 * @param o1
	 * @param op2
	 * @param nullIsLess Whether a <code>null</code> reference should be 
	 * treated as less than all other values of type {@code E}.
	 * If this Parameter is <code>null</code>, the reactive integer will 
	 * take the value <code>null</code>
	 * @return
	 */
	public static <E extends Comparable<? super E>> 
	SealInt comparison(
			E o1,
			ReadDependency<? extends E> op2, 
			Boolean nullIsLess
			){
		return op2.mapToInt(o2->{
			int comp;
			if(o1==o2) {
				if(o1==null & nullIsLess==null)
					return null;			
				comp=0;
			}else if(o1==null) {
				if(nullIsLess==null)
					return null;
				comp=nullIsLess?-1:1;
			}else if(o2==null) {
				if(nullIsLess==null)
					return null;
				comp=nullIsLess?1:-1;
			}else
				comp = o1.compareTo(o2);
			return comp;
		});
	}
	/**
	 * Make a reactive integer that computes itself as the result of comparing
	 * a reactive value to a constant according to their natural ordering
	 * @param <E>
	 * @param oü1
	 * @param o2
	 * @param nullIsLess Whether a <code>null</code> reference should be 
	 * treated as less than all other values of type {@code E}.
	 * If this Parameter is <code>null</code>, the reactive integer will 
	 * take the value <code>null</code>
	 * @return
	 */
	public static <E extends Comparable<? super E>> 
	SealInt comparison(
			ReadDependency<? extends E> op1, 
			E o2,
			Boolean nullIsLess
			){
		return op1.mapToInt(o1->{
			int comp;
			if(o1==o2) {
				if(o1==null & nullIsLess==null)
					return null;			
				comp=0;
			}else if(o1==null) {
				if(nullIsLess==null)
					return null;
				comp=nullIsLess?-1:1;
			}else if(o2==null) {
				if(nullIsLess==null)
					return null;
				comp=nullIsLess?1:-1;
			}else
				comp = o1.compareTo(o2);
			return comp;
		});
	}

	/**
	 * Make a reactive integer that computes itself as the result of comparing
	 * two reactive values according to the given total ordering
	 * @param <E>
	 * @param op1
	 * @param op2
	 * @param ordering
	 * @return
	 */
	public static <E> 
	SealInt comparison(
			ReadDependency<? extends E> op1, 
			ReadDependency<? extends E> op2,
			Comparator<? super E> ordering
			){
		return Piles.makeBinOp(op1, op2, new SealInt(), ordering::compare);
	}
	/**
	 * Make a reactive integer that computes itself to the result of comparing
	 * a reactive value to a constant according to the given total ordering
	 * @param <E>
	 * @param op1
	 * @param o2
	 * @param ordering
	 * @return
	 */
	public static <E> 
	SealInt comparison(
			ReadDependency<? extends E> op1, 
			E o2,
			Comparator<? super E> ordering
			){
		return op1.mapToInt(o1->{
			int comp = ordering.compare(o1, o2);
			return comp;
		});
	}
	/**
	 * Make a reactive integer that computes itself as the result of comparing
	 * a constant to a reactive value according to the given total ordering
	 * @param <E>
	 * @param o1
	 * @param op2
	 * @param ordering
	 * @return
	 */
	public static <E> 
	SealInt comparison(
			E o1,
			ReadDependency<? extends E> op2, 
			Comparator<? super E> ordering
			){
		return op2.mapToInt(o2->{
			int comp = ordering.compare(o1, o2);
			return comp;
		});	
	}
	/**
	 * Make a reactive boolean that computes itself to be 
	 * <code>true</code> iff the given reactive integer is positive
	 * and <code>null</code> if it is <code>null</code>
	 * @param op
	 * @return
	 */
	public static SealBool isPositive(ReadDependency<? extends Integer> op) {
		return op.mapToBool(v->v==null?null:v>0);
	}
	/**
	 * Make a reactive boolean that computes itself to be 
	 * <code>true</code> iff the given reactive intege is negative
	 * and <code>null</code> if it is <code>null</code>
	 * @param op
	 * @return
	 */
	public static SealBool isNegative(ReadDependency<? extends Integer> op) {
		return op.mapToBool(v->v==null?null:v<0);
	}
	/**
	 * Make a reactive boolean that computes itself to be 
	 * <code>true</code> iff the given reactive intege is non-positive
	 * but <code>null</code> if it is <code>null</code>
	 * @param op
	 * @return
	 */
	public static SealBool isNonPositive(ReadDependency<? extends Integer> op) {
		return op.mapToBool(v->v==null?null:v<=0);
	}
	/**
	 * Make a reactive boolean that computes itself to be 
	 * <code>true</code> iff the given reactive intege is non-negative
	 * but <code>null</code> if it is <code>null</code>
	 * @param op
	 * @return
	 */
	public static SealBool isNonNegative(ReadDependency<? extends Integer> op) {
		return op.mapToBool(v->v==null?null:v>=0);
	}
	/**
	 * Make a reactive boolean that computes itself to be 
	 * <code>true</code> iff the given reactive intege is non-zero
	 * but <code>null</code> if it is <code>null</code>
	 * @param op
	 * @return
	 */
	public static SealBool isNonZero(ReadDependency<? extends Integer> op) {
		return op.mapToBool(v->v==null?null:v!=0);
	}
	/**
	 * Make a reactive boolean that computes itself to be 
	 * <code>true</code> iff the given reactive integer is zero
	 * and <code>null</code> if it is <code>null</code>
	 * @param op
	 * @return
	 */
	public static SealBool isZero(ReadDependency<? extends Integer> op) {
		return op.mapToBool(v->v==null?null:v==0);
	}





	/**
	 * Configure the {@code template} to take on the value of one of the
	 * four branches, depending on the sign and nullity of the the value held by {@code chooser} 
	 * @param <E>
	 * @param <V>
	 * @param chooser
	 * @param ifNeg
	 * @param ifZero
	 * @param ifPos
	 * @param ifNull
	 * @param template
	 * @return
	 */
	public static <E, V extends SealPile<E>> V _choose(
			ReadDependency<? extends Integer> chooser, 
			ReadDependency<? extends E> ifNeg, 
			ReadDependency<? extends E> ifZero, 
			ReadDependency<? extends E> ifPos, 
			ReadDependency<? extends E> ifNull,
			V template) {
		if(ifNull==null) {
			return _choose(chooser, ifNeg, ifZero, ifPos, Piles.constNull(), template);
		}
		String chooserName = chooser.dependencyName();
		String ifPosName = ifPos.dependencyName();
		String ifZeroName = ifZero.dependencyName();
		String ifNegName = ifNeg.dependencyName();
		String ifNullName = ifNull.dependencyName();
		String name = "(choice on "+(chooserName == null?"?":chooserName)+": ["+
				(ifNegName==null?"?":ifNegName)+","+
				(ifZeroName==null?"?":ifZeroName)+","+
				(ifPosName==null?"?":ifPosName)+","+
				(ifNullName==null?"?":ifNullName)+"])";

		return new SealPileBuilder<>(template)
				.essential(chooser, ifNeg, ifZero, ifPos, ifNull)
				.mayNotRemoveDynamicDependency(chooser)
				.recompute(reco->{
					if(!chooser.isValid()) {
						reco.fulfillRetry();
					}
					Integer choice = chooser.get();
					if(choice==null) {
						reco.fulfill(ifNull.get());
					}else if(choice==0) {
						reco.fulfill(ifZero.get());
					}else if(choice>0) {
						reco.fulfill(ifPos.get());
					}else {
						reco.fulfill(ifNeg.get());
					}

				})
				.dynamicDependencies()
				.name(name)
				.seal()
				.build();
	}

	/**
	 * Configure the {@code template} to take on the value of one of the
	 * four branches, depending on the sign and nullity of the the value held by {@code chooser} 
	 * <p>
	 * Writes to the {@code template} will be forwarded to the active branch.

	 * @param <E>
	 * @param <V>
	 * @param chooser
	 * @param ifNeg
	 * @param ifZero
	 * @param ifPos
	 * @param ifNull
	 * @param template
	 * @return
	 */
	public static <E, V extends SealPile<E>> V _chooseWritable(
			ReadDependency<? extends Integer> chooser, 
			ReadWriteDependency<E> ifNeg, 
			ReadWriteDependency<E> ifZero, 
			ReadWriteDependency<E> ifPos, 
			ReadWriteDependency<E> ifNull,
			V template) {
		if(ifNull==null) {
			return _chooseWritable(chooser, ifNeg, ifZero, ifPos, Piles.constNull(), template);
		}
		String chooserName = chooser.dependencyName();
		String ifPosName = ifPos.dependencyName();
		String ifZeroName = ifZero.dependencyName();
		String ifNegName = ifNeg.dependencyName();
		String ifNullName = ifNull.dependencyName();
		String name = "(choice on "+(chooserName == null?"?":chooserName)+": ["+
				(ifNegName==null?"?":ifNegName)+","+
				(ifZeroName==null?"?":ifZeroName)+","+
				(ifPosName==null?"?":ifPosName)+","+
				(ifNullName==null?"?":ifNullName)+"])";

		return new SealPileBuilder<>(template)
				.essential(chooser, ifNeg, ifZero, ifPos, ifNull)
				.mayNotRemoveDynamicDependency(chooser)
				.recompute(reco->{
					if(!chooser.isValid()) {
						reco.fulfillRetry();
					}
					Integer choice = chooser.get();
					if(choice==null) {
						reco.fulfill(ifNull.get());
					}else if(choice==0) {
						reco.fulfill(ifZero.get());
					}else if(choice>0) {
						reco.fulfill(ifPos.get());
					}else {
						reco.fulfill(ifNeg.get());
					}

				})
				.dynamicDependencies()
				.name(name)
				.seal(v->{
					if(!chooser.isValid()) {
						return;
					}
					Integer choice = chooser.get();
					WriteValue<? super E> chosen;
					if(choice==null) {
						chosen=ifNull;
					}else if(choice<0){
						chosen=ifNeg;
					}else if(choice==0){
						chosen=ifZero;
					}else {
						chosen=ifPos;
					}

					chosen.set(v);

				})
				.build();


	}
	/**
	 * Configure the {@code template} to take on the value that is one of the
	 * three branches, depending on the sign and nullity of the value held by {@code chooser}
	 * @param <E>
	 * @param <V>
	 * @param chooser
	 * @param ifNeg
	 * @param ifZero
	 * @param ifPos
	 * @param ifNull
	 * @param template
	 * @return
	 */
	public static <E, V extends SealPile<E>> V _chooseConst(
			ReadDependency<? extends Integer> chooser, 
			E ifNeg, 
			E ifZero, 
			E ifPos, 
			E ifNull,
			V template) {
		String chooserName = chooser.dependencyName();
		String name = "(choice on "+(chooserName == null?"?":chooserName)+")";

		return new SealPileBuilder<>(template)
				.dependOn(chooser)
				.recompute(()->{
					Integer b = chooser.get();
					if(b==null)
						return ifNull;
					if(b<0)
						return ifNeg;
					if(b==0)
						return ifZero;
					return ifPos;
				})
				.name(name)
				.seal()
				.build();
	}













	/**
	 * Make a reactive integer that computes itself
	 * from its operands using the given {@link ToIntBiFunction}
	 * @param <O1>
	 * @param <O2>
	 * @param op1
	 * @param op2
	 * @param op
	 * @return
	 */
	public static <O1, O2> SealInt binOp(
			ReadDependency<? extends O1> op1, 
			ReadDependency<? extends O2> op2, 
			ToIntBiFunction<? super O1, ? super O2> op) {
		return Piles.makeBinOp(op1, op2, new SealInt(), op::applyAsInt);
	}
	/**
	 * Make a reactive integer that computes itself
	 * from its operands using the given {@link Integer}-valued {@link BiFunction}
	 * @param <O1>
	 * @param <O2>
	 * @param op1
	 * @param op2
	 * @param op
	 * @return
	 */
	public static <O1, O2> SealInt binOp(
			ReadDependency<? extends O1> op1, 
			ReadDependency<? extends O2> op2, 
			BiFunction<? super O1, ? super O2, ? extends Integer> op) {
		return Piles.makeBinOp(op1, op2, new SealInt(), op);
	}

	/**
	 * Make a reactive integer that computes itself as the sum of its operands.
	 * @param op1
	 * @param op2
	 * @return
	 */
	public static SealInt add(ReadDependency<? extends Integer> op1, ReadDependency<? extends Integer> op2) {
		return binOp(op1, op2, (Integer a, Integer b)->{
			if(a==null || b==null)
				return null;
			return a+b;		
		});
	}
	/**
	 * Make a reactive integer that computes itself as the difference of its operands.
	 * @param op1
	 * @param op2
	 * @return
	 */
	public static SealInt subtract(ReadDependency<? extends Integer> op1, ReadDependency<? extends Integer> op2) {
		return binOp(op1, op2, (Integer a, Integer b)->{
			if(a==null || b==null)
				return null;
			return a-b;		
		});
	}
	/**
	 * Make a reactive integer that computes itself as the product of its operands.
	 * @param op1
	 * @param op2
	 * @return
	 */
	public static SealInt multiply(ReadDependency<? extends Integer> op1, ReadDependency<? extends Integer> op2) {
		return binOp(op1, op2, (Integer a, Integer b)->{
			if(a==null || b==null)
				return null;
			return a*b;		
		});
	}
	/**
	 * Make a reactive integer that computes itself as the integer quotient of its operands.
	 * @param op1
	 * @param op2
	 * @return
	 */
	public static SealInt integerDivide(ReadDependency<? extends Integer> op1, ReadDependency<? extends Integer> op2) {
		return binOp(op1, op2, (Integer a, Integer b)->{
			if(a==null || b==null)
				return null;
			return a/b;		
		});
	}
	/**
	 * Make a reactive integer that computes itself as the remainder of its operands.
	 * @param op1
	 * @param op2
	 * @return
	 */
	public static SealInt remainder(ReadDependency<? extends Integer> op1, ReadDependency<? extends Integer> op2) {
		return binOp(op1, op2, (Integer a, Integer b)->{
			if(a==null || b==null)
				return null;
			return a%b;		
		});
	}
	/**
	 * Make a reactive integer that computes itself as the smallest non-negative integer that is 
	 * congruent to its first operand modulo its second operand.
	 * @param op1
	 * @param op2
	 * @return
	 */
	public static SealInt modulo(ReadDependency<? extends Integer> op1, ReadDependency<? extends Integer> op2) {
		return binOp(op1, op2, (Integer a, Integer b)->{
			if(a==null || b==null)
				return null;
			int c = a%b;
			return c<0?c+b:c;		
		});
	}
	/** Delegates to {@link #addRO(ReadDependency, int)} */
	public static SealInt add(ReadDependency<? extends Integer> op, int value) {
		return addRO(op, value);
	}
	/** Delegates to {@link #addRW(ReadWriteDependency, int)} */
	public static SealInt add(ReadWriteDependency<Integer> op, int value) {
		return addRW(op, value);
	}
	/**
	 * Make a reactive integer that computes itself as the sum of 
	 * a reactive integer and a constant.
	 * @param op
	 * @param value
	 * @return
	 */
	public static SealInt addRO(ReadDependency<? extends Integer> op, int value) {
		return op.mapToInt(o->o==null?null:+value);
	}
	/**
	 * Make a reactive integer that computes itself as the sum of 
	 * a reactive integer and a constant.
	 * Writing to it will attempt to change the first operand accordingly.
	 * @param op
	 * @param value
	 * @return
	 */
	public static SealInt addRW(ReadWriteDependency<Integer> op, int value) {
		return op.bijectToInt(
				Bijection.define(o->o==null?null:o+value, o->o==null?null:o-value));
	}

	/** Delegates to {@link #subtractRO(ReadDependency, int)} */
	public static SealInt subtract(ReadDependency<? extends Integer> op, int value) {
		return subtractRO(op, value);
	}
	/** Delegates to {@link #subtractRW(ReadWriteDependency, int)} */
	public static SealInt subtract(ReadWriteDependency<Integer> op, int value) {
		return subtractRW(op, value);
	}
	/**
	 * Make a reactive integer that computes itself as the difference of 
	 * a reactive integer and a constant.
	 * @param op
	 * @param value
	 * @return
	 */
	public static SealInt subtractRO(ReadDependency<? extends Integer> op, int value) {
		return addRO(op, -value);
	}
	/** 
	 * Make a reactive integer that computes itself as the difference of 
	 * a reactive integer and a constant.
	 * Writing to it will attempt to change the first operand accordingly.
	 * @param op
	 * @param value
	 * @return
	 */
	public static SealInt subtractRW(ReadWriteDependency<Integer> op, int value) {
		return addRW(op, -value);
	}

	/** Delegates to {@link #subtractRO(int, ReadDependency)} */
	public static SealInt subtract(int value, ReadDependency<? extends Integer> op) {
		return subtractRO(value, op);
	}
	/** Delegates to {@link #subtractRW(int, ReadWriteDependency)} */
	public static SealInt subtract(int value, ReadWriteDependency<Integer> op) {
		return subtractRW(value, op);
	}
	/**
	 * Make a reactive integer that computes itself as the difference of 
	 * a constant and a reactive integer.
	 * @param value
	 * @param op
	 * @return
	 */
	public static SealInt subtractRO(int value, ReadDependency<? extends Integer> op) {
		return op.mapToInt(o->o==null?null:value-o);
	}
	/** 
	 * Make a reactive integer that computes itself as the difference of 
	 * a constant and a reactive integer.
	 * Writing to it will attempt to change the second operand accordingly.
	 * @param value
	 * @param op
	 * @return
	 */
	public static SealInt subtractRW(int value, ReadWriteDependency<Integer> op) {
		return op.bijectToInt(Bijection.involution(o->o==null?null:value-o));
	}

	/**
	 * Make a reactive integer that computes itself as the product of 
	 * a reactive integer and a constant.
	 * @param op
	 * @param value
	 * @return
	 */
	public static SealInt multiply(ReadDependency<? extends Integer> op, int value) {
		return op.mapToInt(o->o==null?null:o*value);
	}
	/**
	 * Make a reactive integer that computes itself as the integer quotient of
	 * a reactive integer and a constant.
	 * @param op1
	 * @param o2
	 * @return
	 */
	public static SealInt integerDivide(ReadDependency<? extends Integer> op1, int o2) {
		return op1.mapToInt(o1->{
			if(o1==null)
				return null;
			return o1/o2;		
		});
	}
	/**
	 * Make a reactive integer that computes itself as the integer remainder of
	 * a reactive integer and a constant.
	 * @param op1
	 * @param o2
	 * @return
	 */
	public static SealInt remainder(ReadDependency<? extends Integer> op1, int o2) {
		return op1.mapToInt(o1->{
			if(o1==null)
				return null;
			return o1%o2;		
		});
	}
	/**
	 * Make a reactive integer that computes itself as the smallest non-negative integer
	 * that is congruent with a reactive integer modulo a constant
	 * @param op1
	 * @param o2
	 * @return
	 */
	public static SealInt modulo(ReadDependency<? extends Integer> op1, int o2) {
		return op1.mapToInt(o1->{
			if(o1==null)
				return null;
			int c = o1%o2;
			return c<0?c+o2:c;		
		});
	}

	/**
	 * Make a reactive integer that computes itself as the integer quotient of
	 * a constant and a reactive integer.
	 * @param o1
	 * @param op2
	 * @return
	 */
	public static SealInt integerDivide(int o1, ReadDependency<? extends Integer> op2) {
		return op2.mapToInt(o2->{
			if(o2==null)
				return null;
			return o1/o2;		
		});
	}
	/**
	 * Make a reactive integer that computes itself as the integer remainder of
	 * a constant and a reactive integer.
	 * @param o1
	 * @param op2
	 * @return
	 */
	public static SealInt remainder(int o1, ReadDependency<? extends Integer> op2) {
		return op2.mapToInt(o2->{
			if(o2==null)
				return null;
			return o1%o2;		
		});
	}
	/**
	 * Make a reactive integer that computes itself as the smallest non-negative integer
	 * that is congruent with a constant modulo a reactive integer.
	 * @param o1
	 * @param op2
	 * @return
	 */
	public static SealInt modulo(int o1, ReadDependency<? extends Integer> op2) {
		return op2.mapToInt(o2->{
			if(o2==null)
				return null;
			int c = o1%o2;
			return c<0?c+o2:c;		
		});
	}



	/**
	 * Make a reactive integer that computes itself as the maximum of
	 * two reactive integers.
	 * @param op1
	 * @param op2
	 * @return
	 */

	public static SealInt max(ReadDependency<? extends Integer> op1, ReadDependency<? extends Integer> op2) {
		return binOp(op1, op2, (Integer a, Integer b)->{
			if(a==null || b==null)
				return null;
			return Math.max(a, b);		
		});
	}
	/**
	 * Make a reactive integer that computes itself as the minimum of
	 * two reactive integers.
	 * @param op1
	 * @param op2
	 * @return
	 */
	public static SealInt min(ReadDependency<? extends Integer> op1, ReadDependency<? extends Integer> op2) {
		return binOp(op1, op2, (Integer a, Integer b)->{
			if(a==null || b==null)
				return null;
			return Math.min(a, b);		
		});
	}
	/**
	 * Make a reactive integer that computes itself as the maximum of
	 * a reactive integer and a constant.
	 */
	public static SealInt max(ReadDependency<? extends Integer> op1, int value) {
		return op1.mapToInt(v->v==null?null:Math.max(v, value));
	}
	/**
	 * Make a reactive integer that computes itself as the minimum of
	 * a reactive integer and a constant.
	 * @param op1
	 * @param value
	 * @return
	 */
	public static SealInt min(ReadDependency<? extends Integer> op1, int value) {
		return op1.mapToInt(v->v==null?null:Math.min(v, value));
	}

	/** Specialization of {@link Piles#readOnlyWrapperIdempotent(ReadDependency)} */
	public static ReadListenDependencyInt readOnlyWrapperIdempotent(ReadDependency<? extends Integer> in){
		if(in instanceof SealInt) {
			SealInt cast = (SealInt)in;
			if(cast.isDefaultSealed())
				return cast;
		}else if(in instanceof ConstantInt) {
			ConstantInt cast = (ConstantInt) in;
			return cast;	
		}
		return readOnlyWrapper(in);
	}
	/** Specialization of {@link Piles#readOnlyWrapper(ReadDependency)} */
	public static SealInt readOnlyWrapper(ReadDependency<? extends Integer> in){
		return new SealPileBuilder<>(new SealInt())
				.recompute(in::get)
				.seal()
				.whenChanged(in);

	}
	/**
	 * Partly specialized {@link AggregationMonoid} for integer operations
	 * @author bb
	 *
	 */
	public static class IntAggregator implements Piles.AggregationMonoid<Integer, ReadListenDependencyInt>{

		final ReadListenDependencyInt neutral;
		final BiFunction<? super ReadListenDependency<? extends Integer>, ? super ReadListenDependency<? extends Integer>, ? extends ReadListenDependencyInt> op;
		public IntAggregator(Integer neutral, 
				BiFunction<? super ReadListenDependency<? extends Integer>, ? super ReadListenDependency<? extends Integer>, ? extends ReadListenDependencyInt> op) {
			if(neutral==null)
				this.neutral=Piles.NULL_I;
			else if(neutral==0)
				this.neutral=Piles.ZERO_I;
			else if(neutral==1)
				this.neutral=Piles.ONE_I;
			else if(neutral==Integer.MAX_VALUE)
				this.neutral=Piles.MAX_VALUE_I;
			else if(neutral==Integer.MIN_VALUE)
				this.neutral=Piles.MIN_VALUE_I;
			else
				this.neutral=Piles.sealedConstant(neutral);
			this.op=op;
		}
		@Override
		public ReadListenDependencyInt constantNeutral() {
			return neutral;
		}
		@Override
		public ReadListenDependencyInt apply(ReadListenDependency<? extends Integer> op1, ReadListenDependency<? extends Integer> op2) {
			return op.apply(op1, op2);
		}

		@Override
		public ReadListenDependencyInt inject(ReadListenDependency<? extends Integer> o) {
			return readOnlyWrapperIdempotent(o);
		}

	}
	/**
	 * Aggregator for computing sums
	 */
	public static final IntAggregator sumAggregator = new IntAggregator(0, PileInt::add);
	/**
	 * Aggregator for computing products
	 */
	public static final IntAggregator productAggregator = new IntAggregator(1, PileInt::multiply);
	/**
	 * Aggregator for computing minima
	 */
	public static final IntAggregator minAggregator = new IntAggregator(Integer.MAX_VALUE, PileInt::min);
	/**
	 * Aggregator for computing maxima
	 */
	public static final IntAggregator maxAggregator = new IntAggregator(Integer.MIN_VALUE, PileInt::max);


	public static ReadListenDependencyInt sum(Iterable<? extends ReadListenDependency<? extends Integer>> items) {
		return Piles.aggregate(sumAggregator, items);
	}
	@SafeVarargs
	public static ReadListenDependencyInt sum(ReadListenDependency<? extends Integer>... items) {
		return Piles.aggregate(sumAggregator, items);
	}
	public static ReadListenDependencyInt product(Iterable<? extends ReadListenDependency<? extends Integer>> items) {
		return Piles.aggregate(productAggregator, items);
	}
	@SafeVarargs
	public static ReadListenDependencyInt product(ReadListenDependency<? extends Integer>... items) {
		return Piles.aggregate(productAggregator, items);
	}
	public static ReadListenDependencyInt min(Iterable<? extends ReadListenDependency<? extends Integer>> items) {
		return Piles.aggregate(minAggregator, items);
	}
	@SafeVarargs
	public static ReadListenDependencyInt min(ReadListenDependency<? extends Integer>... items) {
		return Piles.aggregate(minAggregator, items);
	}
	public static ReadListenDependencyInt max(Iterable<? extends ReadListenDependency<? extends Integer>> items) {
		return Piles.aggregate(maxAggregator, items);
	}
	@SafeVarargs
	public static ReadListenDependencyInt max(ReadListenDependency<? extends Integer>... items) {
		return Piles.aggregate(maxAggregator, items);
	}
	/**
	 * Make a reactive integer that computes itself as the sign of the given value
	 * @param value
	 * @return
	 */
	public static ReadListenDependencyInt signum(ReadListenDependency<? extends Integer> value) {
		return value.mapToInt(v->v==null?null:(int)Math.signum(v.doubleValue()));
	}


	/**
	 * Create a {@link PileBuilder} for reactive integers
	 * @return
	 */
	public static PileBuilder<PileIntImpl, Integer> rb(){return new PileBuilder<>(new PileIntImpl()).ordering(Comparator.naturalOrder());}
	/**
	 * Create a {@link SealPileBuilder} for sealable reactive integers
	 * @return
	 */
	public static SealPileBuilder<SealInt, Integer> sb(){return new SealPileBuilder<>(new SealInt()).ordering(Comparator.naturalOrder());}
	/**
	 * Create an {@link IndependentBuilder} for independent reactive integers with a <code>null</code> initial value.
	 * @return
	 */
	public static IndependentBuilder<IndependentInt, Integer> ib(){return new IndependentBuilder<>(new IndependentInt(null)).ordering(Comparator.naturalOrder());}
	/**
	 * Create an {@link IndependentBuilder} for independent reactive integers
	 * @param init initial value of the {@link Independent}
	 * @return
	 */
	public static IndependentBuilder<IndependentInt, Integer> ib(Integer init){return new IndependentBuilder<>(new IndependentInt(init)).ordering(Comparator.naturalOrder());}


}

