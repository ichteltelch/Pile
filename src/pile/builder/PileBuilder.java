package pile.builder;

import pile.aspect.HasAssociations;
import pile.aspect.combinations.ReadListenDependency;
import pile.impl.PileImpl;

/**
 * Fixed point class for {@link AbstractPileBuilder}
 * @author bb
 *
 * @param <V>
 * @param <E>
 */
public final class PileBuilder<V extends PileImpl<E>, E> extends AbstractPileBuilder<PileBuilder<V, E>, V, E>{
	/**
	 * @param value The value this builder should act on
	 */
	public PileBuilder(V v) {
		super(v);
	}


	/**
	 * Query the "upper bound" association of something.
	 * This method is an alias for {@link ICorrigibleBuilder#getUpperBound(HasAssociations)}
	 * @param <E>
	 * @param v
	 * @return
	 */
	public static <E> ReadListenDependency<? extends E> getUpperBound(HasAssociations v){
		return ICorrigibleBuilder.getUpperBound(v);
	}
	/**
	 * Query the "lower bound" association of something.
	 * This method is an alias for {@link ICorrigibleBuilder#getLowerBound(HasAssociations)}
	 * @param <E>
	 * @param v
	 * @return
	 */
	public static <E> ReadListenDependency<? extends E> getLowerBound(HasAssociations v){
		return ICorrigibleBuilder.getLowerBound(v);
	}
	@Override
	public PileBuilder<V, E> self() {
		return this;
	}








}
