package pile.builder;

import pile.impl.SealPile;
/**
 * Fixed point class for actually implementing the {@link IIndependentBuilder} interface 
 * with a concrete type
 * @author bb
 *
 * @param <V>
 * @param <E>
 */
public final class SealPileBuilder<V extends SealPile<E>, E> extends AbstractSealPileBuilder<SealPileBuilder<V, E>, V, E>{
	/**
	/**
	 * @param value The value that this builder should act on, which must not already be seale
	 */
	public SealPileBuilder(V v) {
		super(v);
	}
	@Override
	public SealPileBuilder<V, E> self() {
		return this;
	}


}