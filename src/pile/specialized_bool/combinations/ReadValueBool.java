package pile.specialized_bool.combinations;

import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

import pile.aspect.ReadValue;

public interface ReadValueBool extends ReadValue<Boolean>, BooleanSupplier{
	/**
	 * Compare the held value to {@link Boolean#TRUE}
	 * @return
	 */
	public default boolean isTrue() {
		return Boolean.TRUE.equals(get());
	}
	/**
	 * Compare the held value to {@link Boolean#FALSE}
	 * @return
	 */
	public default boolean isFalse() {
		return Boolean.FALSE.equals(get());
	}
	/**
	 * Same as {@link #isTrue()}
	 */
	public default boolean getAsBoolean() {
		return Boolean.TRUE.equals(get());
	}
	/**
	 * Compare the held value to {@link Boolean#TRUE}
	 * @param v
	 * @return
	 */
	public static boolean isTrue(Supplier<? extends Boolean> v) {
		return Boolean.TRUE.equals(v.get());
	}
	/**
	 * Compare the held value to {@link Boolean#FALSE}
	 * @param v
	 * @return
	 */
	public static boolean isFalse(Supplier<? extends Boolean> v) {
		return Boolean.FALSE.equals(v.get());
	}
	/**
	 * Select one of the given values, depending on whether this {@link ReadValueBool} currently holds a <code>null</code>,
	 * <code>true</code> of <code>false</code> value.
	 * @see ReadDependencyBool#chooseConst(Object, Object, Object) for a reactive version of this
	 * @param ifTrue
	 * @param ifFalse
	 * @param ifNull
	 * @param <E>
	 * @return
	 */
	public default <E> E threeWay(E ifTrue, E ifFalse, E ifNull) {
		Boolean v=get();
		return v==null?ifNull:v?ifTrue:ifFalse;
	}
}
