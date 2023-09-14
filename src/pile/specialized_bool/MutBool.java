package pile.specialized_bool;

import java.util.function.BooleanSupplier;

import pile.aspect.combinations.Prosumer;
import pile.specialized_bool.combinations.JustReadValueBool;

/**
 * A mutable wrapper around a primitive boolean value
 * @author bb
 *
 */
public final class MutBool implements JustReadValueBool, BooleanSupplier, Prosumer<Boolean>{
	/**
	 * The value
	 */
	public boolean val;
	/**
	 * Make a new {@link MutBool} instance with an initial {@link #val}ue of {@code 0}
	 */
	public MutBool(){}
	/**
	 * Make a new {@link MutBool} instance initially holding the specified number 
	 * @param val
	 */
	public MutBool(boolean val){this.val=val;}
	/**
	 * Set the wrapped value to the specified number
	 * @param val
	 * @return {@code this}
	 */
	public MutBool set(boolean val){
		this.val=val;
		return this;
	}
	/**
	 * Set the wrapped value to the {@link #val}ue of the other {@link MutBool}
	 * @param o
	 * @return {@code this}
	 */
	public MutBool set(MutBool o){
		this.val=o.val;
		return this;
	}
	/**
	 * @return {@link #val}
	 */
	public Boolean get(){return val;}
	/**
	 * @return {@link #val}
	 */
	@Override public boolean getAsBoolean() {return val;}
	/**
	 * @return A {@link String} representation of the wrapped value, enclosed in angle brackets
	 */
	public String toString() {
		return "<"+val+">";
	}
	public void setFalse() {
		this.val=false;
	}
	public void setTrue() {
		this.val=true;
	}
	/**
	 * Set the value.
	 * @throws NullPointerException if {@code val} is {@code null}
	 */
	@Override
	public void accept(Boolean t) {
		val = t;
	}
}