package pile.specialized_double;

import java.util.function.DoubleSupplier;

import pile.specialized_double.combinations.JustReadValueDouble;

/**
 * A mutable wrapper around a {@code double} precision floating point value
 * @author bb
 *
 */
public final class MutDouble implements JustReadValueDouble, DoubleSupplier{
	/**
	 * The value
	 */
	public double val;
	/**
	 * Make a new {@link MutDouble} instance with an initial {@link #val}ue of {@code 0}
	 */
	public MutDouble(){}
	/**
	 * Make a new {@link MutDouble} instance initially holding the specified number 
	 * @param val
	 */
	public MutDouble(double val){this.val=val;}
	/**
	 * Set the wrapped value to the specified number
	 * @param val
	 * @return {@code this}
	 */
	public MutDouble set(double val){
		this.val=val;
		return this;
	}
	/**
	 * Set the wrapped value to the {@link #val}ue of the other {@link MutDouble}
	 * @param o
	 * @return {@code this}
	 */
	public MutDouble set(MutDouble o){
		this.val=o.val;
		return this;
	}
	/**
	 * @return {@link #val}
	 */
	public Double get(){return val;}
	/**
	 * @return {@link #val}
	 */
	@Override
	public double getAsDouble() {return val;}
	/**
	 * @return A {@link String} representation of the wrapped value, enclosed in angle brackets
	 */
	public String toString() {
		return "<"+val+">";
	}
	/**
	 * Get the {@link Math#sqrt(double) square root} of the wrapped value
	 * @return
	 */
	public double sqrt() {
		return Math.sqrt(val);
	}
}