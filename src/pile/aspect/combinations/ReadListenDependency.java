package pile.aspect.combinations;

import pile.aspect.bracket.HasBrackets;
import pile.impl.Piles;
import pile.impl.SealPile;
import pile.specialized_bool.combinations.ReadDependencyBool;
import pile.specialized_bool.combinations.ReadListenDependencyBool;

public interface ReadListenDependency<E> extends ReadListenValue<E>, ReadDependency<E>, HasBrackets<ReadListenDependency<? extends E>, E>{


	/**
	 * Make a {@link Pile} that reflects whether this value is valid but holds a <code>null</code>
	 * reference 
	 */
	default ReadListenDependencyBool validNull() {
		ReadDependencyBool val = validity();
		ReadDependency<?> vb = readOnlyValidBuffer_memo();
		return Piles.sealedNoInitBool()
				.recompute(()->val.isTrue() && vb.isNull())
				.whenChanged(val, vb);
	}
	/**
	 * Make a {@link Pile} that reflects whether this value is invalid or <code>null</code>
	 * @return
	 */

	default ReadListenDependencyBool nullOrInvalid() {
		ReadDependencyBool val = validity();
		ReadDependency<?> vb = readOnlyValidBuffer_memo();
		return Piles.sealedNoInitBool()
				.recompute(()->{
					return val.isFalse() || vb.isNull();
				})
				.name(dependencyName() + " == null or invalid")
				.whenChanged(val, vb);
	}
	/**
	 * Shorthand for <code>{@link #nullOrInvalid()}.{@link ReadDependencyBool#not() not()}</code>
	 * @return
	 */
	public default ReadListenDependencyBool validNonNull() {
		return nullOrInvalid().not();
	}
	/**
	 * Make a {@link Pile} that takes on the given constant value whenever 
	 * this {@link ReadListenDependency} is invalid.
	 * @param v
	 * @return
	 */
	public default SealPile<E> fallback(E v){
		return Piles.fallback(this, v);
	}
	
	/**
	 * Set a name for debugging purposes.
	 * Subclasses and subinterfaces should override this method to return the same type as themselves.
	 * @param s
	 * @return {@code this}
	 */
	public ReadListenDependency<E> setName(String s);

}
