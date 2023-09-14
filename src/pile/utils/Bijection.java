package pile.utils;

import java.util.Objects;
import java.util.function.Function;

/**
 * A {@link Function} that has an inverse function.
 * @author bb
 *
 * @param <From>
 * @param <To>
 */
public interface Bijection<From, To> extends Function<From, To>{
	/**
	 * Apply the inverse function.
	 * @param y
	 * @return
	 */
	public From applyInverse(To y);
	/**
	 * Get the inverse function, which is also a Bijection.
	 * Bijections that are involutions should override this method
	 * to return {@code this}
	 * @return
	 */
	public default Bijection<To, From> inverse(){
		return new InverseBijection<>(this);
	}
	/**
	 * The standard implementation of an inverse of another {@link Bijection}
	 * @author bb
	 *
	 * @param <From>
	 * @param <To>
	 */
	public static class InverseBijection<From, To> implements Bijection<From, To>{
		final Bijection<To, From> inverse;
		protected InverseBijection(Bijection <To, From> invert) {
			Objects.requireNonNull(invert);
			inverse=invert;
		}
		@Override
		public To apply(From t) {
			return inverse.applyInverse(t);
		}
		@Override
		public From applyInverse(To y) {
			return inverse.apply(y);
		}
		@Override
		public Bijection<To, From> inverse() {
			return inverse;
		}
	}
	/**
	 * Define a Bijection from two functions, which must be inverses of one another.
	 * @param <From>
	 * @param <To>
	 * @param to
	 * @param fro
	 * @return
	 */
	public static <From, To> Bijection<From, To> define(
			Function<? super From, ? extends To> to, 
			Function<? super To, ? extends From> fro){
		return new Bijection<From, To>() {
			@Override
			public To apply(From t) {
				return to.apply(t);
			}
			@Override
			public From applyInverse(To y) {
				return fro.apply(y);
			}
		};
	}
	/**
	 * Define an involution from a function which must be its own inverse.
	 * @param <T>
	 * @param f
	 * @return
	 */
	public static <T> Bijection<T, T> involution(Function<T, T> f){
		return new Bijection<T, T>() {
			@Override
			public T apply(T t) {
				return f.apply(t);
			}
			@Override
			public T applyInverse(T y) {
				return f.apply(y);
			}
			@Override
			public Bijection<T, T> inverse() {
				return this;
			}
		};
	}

}
