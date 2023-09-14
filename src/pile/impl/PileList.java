package pile.impl;

/**
 * Concrete implementation of {@link PileList}.
 * @author bb
 *
 * @param <E>
 */
public class PileList<E> extends AbstractValueList<PileList<E>, E>{

	public PileList(String name) {
		super(name);
	}

	

	@Override
	public PileList<E> self() {
		return this;
	}

}
