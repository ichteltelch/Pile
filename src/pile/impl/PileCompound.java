package pile.impl;


import pile.aspect.Depender;
import pile.aspect.bracket.ValueBracket;
import pile.aspect.combinations.Pile;
import pile.aspect.listen.ValueEvent;
import pile.aspect.listen.ValueListener;
import pile.aspect.recompute.Recomputation;
import pile.aspect.recompute.Recomputer;
import pile.specialized_bool.combinations.ReadListenDependencyBool;

/**
 * A {@link PileCompound} object comprises several {@link PileImpl}s. 
 * The special {@link #head} {@link PileImpl}
 * should be made to depend on all of them.
 * @author bb
 *
 */
public abstract class PileCompound {
	public abstract static class PublicHead extends PileCompound{
		@Override public Pile<Object> head() {return super.head();}
	}
	/**
	 * The {@link #head} of an {@link PileCompound} can be used to make it depend on the components,
	 * so each time a component changes the head generates a {@link ValueEvent} 
	 */
	private final PileImpl<Object> head=makeHead();
	/**
	 * Return the head, which can be used to make it depend on the components,
	 * so each time a component changes the head generates a {@link ValueEvent}.
	 * Override this method only to make it public, nothing else.
	 * @return
	 */
	protected Pile<Object> head(){return head;}
	/**
	 * The is called to make the {@link #head} value. Override to provide your own.
	 * The {@link PileImpl} returned by this method calls {@link #headInvalidated()} whenever the head is invalidated
	 * and {@link #headFiresChange(ValueEvent)} whenever a change event is fired.
	 * It also re-fires all {@link ValueEvent}s happening on its dependencies.
	 * It calls {@link #recomputeHead(PileImpl)} in order to recompute itself, typically by taking on some dummy value.
	 * @return
	 */
	protected PileImpl<Object> makeHead() {
		boolean deep=false;
		return makeHead(deep);
	}
	/**
	 * Make a default {@link Hub}-based head for this {@link PileCompound}
	 * @param deep passed to the constructor of {@link Hub}
	 * @return
	 */
	protected PileImpl<Object> makeHead(boolean deep) {
		Recomputer<Object> recompute = this::recomputeHead;
		ValueListener changed = this::headFiresChange;
		Object parent=this;
		PileImpl<Object> ret = new Hub(deep);
		ReadListenDependencyBool hv = ret.validity();
		ValueListener validityChanged = e->{if(!hv.isTrue())headInvalidated();};
		
		makeHead(ret, recompute, changed, validityChanged, parent);
		
		ret.avName=autoCompundName();
		return ret;
	}
	/**
	 * Make/configure a {@link #head()} value for use with a {@link PileCompound}  
	 * @param ret the value to use. If this is <code>null</code>, a {@link Hub} will be created
	 * @param recompute How to recompute the head.
	 *  If this is <code>null</code>, {@link PileCompound#defaultRecomputeHead} is used.
	 * @param changed An optional {@link PileList} to be added to the head
	 * @param validityChanged An optional {@link PileList} to be added to the head's {@link PileImpl#validity() validity}.
	 * @param parent What should go into the {@link PileImpl#owner} field of the head.
	 * @return
	 */
	public static PileImpl<Object> makeHead(PileImpl<Object> ret, Recomputer<Object> recompute,
			ValueListener changed, ValueListener validityChanged, Object parent) {
		if(ret==null)
			ret = new Hub(false);
		if(recompute==null)
			recompute=PileCompound.defaultRecomputeHead;
		ret._setRecompute(recompute);
		if(validityChanged!=null)
			ret.validity().addValueListener(validityChanged);
		if(changed!=null)
			ret.addValueListener(changed);
		ret.owner=parent;
		return ret;
	}
	/**
	 * Called whenever the {@link #head() head} was invalidated
	 */
	protected void headInvalidated() {}
	/**
	 * Destroy all {@link PileImpl}s that this compound comprises
	 */
	abstract public void destroy();
	/**
	 * Called when the default head fires a {@link ValueEvent}
	 * @param ee
	 */
	protected void headFiresChange(ValueEvent ee) {}
	/**
	 * @return A name for this {@link PileCompound}
	 */
	abstract public String autoCompundName();
	/**
	 * Called when the default head is recomputed
	 * @param head
	 */
	protected void recomputeHead(Recomputation<Object> head) {
		defaultRecomputeHead.accept(head);
	}
	private static final Recomputer<Object> defaultRecomputeHead=reco->reco.fulfill("dummy");

	
	/**
	 * Make a bracket that, when opened on a {@link PileCompound}, causes a {@link Depender}
	 * to depend on its {@link #head()}.
	 * @param <E>
	 * @param d
	 * @return
	 */
	public static <E extends PileCompound.PublicHead> ValueBracket<? super E, Object> headDependBracket(Depender d) {
		return ValueBracket.dependencyBracket(PileCompound::head, d);
	};	



}
