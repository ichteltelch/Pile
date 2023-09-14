package pile.aspect.transform;

import pile.aspect.ReadValue.InvalidValueException;

/**
 * Specifies how a {@link TransformableValue} reacts to a transformation request
 * @author bb
 * @see TransformReaction 
 *
 * @param <E>
 */
@FunctionalInterface
public interface TransformHandler<E> {
	/**
	 * Give a reaction to a transformation request 
	 * @param owner Holds the value that is being transformed  
	 * @param transform The object describing the transformation
	 * @return
	 */
	public TransformReaction react(TransformableValue<E> owner, Object transform);


	TransformHandler<?> IGNORE = (v, t)->TransformReaction.IGNORE;
	TransformHandler<?> RECOMPUTE = (v, t)->TransformReaction.RECOMPUTE;
	TransformHandler<?> JUST_PROPAGATE_NO_TRANSACTION = (v, t)->TransformReaction.JUST_PROPAGATE_NO_TRANSACTION;
	TransformHandler<?> JUST_PROPAGATE_WITH_TRANSACTION = (v, t)->TransformReaction.JUST_PROPAGATE_WITH_TRANSACTION;
	@SuppressWarnings("unchecked")
	public static <E> TransformHandler<E> ignore(){return (TransformHandler<E>) IGNORE;}
	@SuppressWarnings("unchecked")
	public static <E> TransformHandler<E> recompute(){return (TransformHandler<E>) RECOMPUTE;}
	@SuppressWarnings("unchecked")
	public static <E> TransformHandler<E> justPropagate_noTransaction(){return (TransformHandler<E>) JUST_PROPAGATE_NO_TRANSACTION;}
	@SuppressWarnings("unchecked")
	public static <E> TransformHandler<E> justPropagateWithTransaction(){return (TransformHandler<E>) JUST_PROPAGATE_WITH_TRANSACTION;}

//	public default boolean allowMultipleTransforms() {return false;}
//	public interface AllowingMultipleTransforms<E> extends TransformHandler<E>{
//		public default boolean allowMultipleTransforms() {return true;}
//	}

	
	/**
	 * One of the four standard reactions, which don't need a generic type annotation
	 * @author bb
	 *
	 */
	public static final class UntypedReaction extends TransformReaction{
		public final ReactionType type;
		UntypedReaction(ReactionType t){type=t;}
		@Override public ReactionType getType() {return type;}
	}
	/**
	 * Either a {@link MutateReaction} or a {@link ReplaceReaction}
	 * @author bb
	 *
	 * @param <E>
	 */
	public abstract static class TypedReaction<E> extends TransformReaction{
		public E apply(E in) throws InvalidValueException {return in;}
		private final Runnable cancelCode;
		TypedReaction(Runnable cancelCode){
			this.cancelCode=cancelCode;
		}
		/**
		 * Called when the transformation code was not actually invoked because
		 * the value had become invalid.
		 */
		public void cancel() {
			if(cancelCode!=null)
				cancelCode.run();
		}
	}
}
