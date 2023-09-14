package pile.specialized_Comparable;

import java.util.function.BiFunction;

import pile.aspect.Depender;
import pile.aspect.combinations.Pile;
import pile.aspect.combinations.ReadDependency;
import pile.impl.Piles;
import pile.specialized_Comparable.combinations.ReadWriteListenDependencyComparable;
import pile.specialized_int.SealInt;

public interface PileComparable<E extends Comparable<? super E>> 
extends Depender, ReadWriteListenDependencyComparable<E>, Pile<E>{

	@Override public default PileComparable<E> setNull() {
		set(null);
		return this;
	}


	/**
	 * Make a reactive integer that takes on the value of the comparison result of two
	 * reactive values according to their natural ordering. 
	 * @param <S>
	 * @param op1
	 * @param op2
	  * @param nullIsLess Whether a <code>null</code> reference should be 
	  * treated as less than all other values of type {@code S}.
	  * If this Parameter is <code>null</code>, the reactive integer will take the value <code>null</code>
	 * @return
	 */
	public static <S extends Comparable<? super S>> SealInt
	compareTo(ReadDependency<? extends S> op1, ReadDependency<? extends S> op2, Boolean nullIsLess){
		BiFunction<? super S, ? super S, ? extends Integer> op;
		if(nullIsLess==null)
			op=PileComparable::compareNullIsNull;
		else if(nullIsLess)
			op=PileComparable::compareNullIsLess;
		else
			op=PileComparable::compareNullIsGreater; 
		return Piles.makeBinOp(op1, op2, new SealInt(), op);
	}
	

	/**
	 * Compare two values according to their natural ordering. 
	 * If one or both are <code>null</code>, <code>null</code> is returned.
	 * @param <S>
	 * @param a
	 * @param b
	 * @return
	 */
	public static
	<S extends Comparable<? super S>>
	Integer compareNullIsNull(S a, S b) {
		return a==null?null:b==null?null:a.compareTo(b);
	}
	/**
	 * Compare two values according to their natural ordering. 
	 * <code>null</code> is considered to be less than all other values of type {@code S}.
	 * @param <S>
	 * @param a
	 * @param b
	 * @return
	 */
	public static
	<S extends Comparable<? super S>>
	int compareNullIsLess(S a, S b) {
		return a==null?b==null?0:-1:b==null?1:a.compareTo(b);
	}
	/**
	 * Compare two values according to their natural ordering. 
	 * <code>null</code> is considered to be greater than all other values of type {@code S}.
	 * @param <S>
	 * @param a
	 * @param b
	 * @return
	 */
	public static
	<S extends Comparable<? super S>>
	int compareNullIsGreater(S a, S b) {
		return a==null?b==null?0:1:b==null?-1:a.compareTo(b);
	}

}
