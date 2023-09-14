package pile.builder;

import pile.impl.Independent;


/**
 * Fixed point class for {@link AbstractIndependentBuilder}
 * @author bb
 *
 * @param <V>
 * @param <E>
 */
public final class IndependentBuilder<V extends Independent<E>, E> 
extends AbstractIndependentBuilder<IndependentBuilder<V, E>, V, E>{
	/**
	 * @param value The value that this builder should act on, which must not already be sealed
	 */

	public IndependentBuilder(V value) {
		super(value);
	}

	@Override
	public IndependentBuilder<V, E> self() {
		return this;
	}


}
