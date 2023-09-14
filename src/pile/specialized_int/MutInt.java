package pile.specialized_int;

import java.util.function.IntSupplier;

import pile.specialized_int.MutInt;
import pile.specialized_int.combinations.JustReadValueInt;

/**
 * A mutable wrapper around an int value
 * @author bb
 *
 */
public final class MutInt implements JustReadValueInt, IntSupplier{
	/**
	 * The value
	 */
	public int val;
	/**
	 * Make a new {@link MutInt} instance with an initial {@link #val}ue of {@code 0}
	 */
	public MutInt(){}
	/**
	 * Make a new {@link MutInt} instance initially holding the specified number 
	 * @param val
	 */
	public MutInt(int val){this.val=val;}
	/**
	 * Set the wrapped value to the specified number
	 * @param val
	 * @return {@code this}
	 */
	public MutInt set(int val){
		this.val=val;
		return this;
	}
	/**
	 * Set the wrapped value to the {@link #val}ue of the other {@link MutInt}
	 * @param o
	 * @return {@code this}
	 */
	public MutInt set(MutInt o){
		this.val=o.val;
		return this;
	}
	/**
	 * @return {@link #val}
	 */
	public Integer get(){return val;}
	/**
	 * @return {@link #val}
	 */
	@Override public int getAsInt() {return val;}
	/**
	 * @return A {@link String} representation of the wrapped value, enclosed in angle brackets
	 */
	public String toString() {
		return "<"+val+">";
	}
}