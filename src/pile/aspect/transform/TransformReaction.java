package pile.aspect.transform;

import pile.aspect.transform.TransformHandler.UntypedReaction;

/**
 * Instances of this class represent a concrete reaction to a transformrequest on 
 * a {@link TransformableValue}.
 * @author bb
 *
 */
public abstract class TransformReaction implements Runnable{
	/**
	 * The different ways a transform request can be handled
	 * @author bb
	 *
	 */
	public static enum ReactionType{
		/**
		 * Don't react to the transform request. The value will remain unchanged
		 */
		IGNORE, 
		/**
		 * Propagate the transform request to all {@link Depender}s, but do not transform yourself
		 * and to not start a transaction
		 */
		JUST_PROPAGATE_NO_TRANSACTION,
		/**
		 * Propagate the transform request to all {@link Depender}s and enter a transaction for the duration of the transformation,
		 * but do not transform yourself
		 */
		JUST_PROPAGATE_WITH_TRANSACTION,
		/**
		 * Transform by mutating the value
		 */
		MUTATE,
		/**
		 * Transform by computing a new value
		 */
		REPLACE,
		/**
		 * Do not transform. Instead, invalidate the value and recompute it afterwards
		 */
		RECOMPUTE
	}
	TransformReaction(){}
	/**
	 * 
	 * @return the {@link ReactionType} of this reaction
	 */
	public abstract TransformReaction.ReactionType getType(); 
	/**
	 * Whether the transformation code runs fast and therefore should be run sequentially in the
	 * {@link Thread} that triggered the transformation.
	 * @return
	 */
	public boolean fast() {return true;}
	/**
	 * Run the transformation code
	 */
	public void run() {}

	/**
	 * The singleton wrapper for {@link ReactionType#IGNORE}
	 */
	public static final TransformReaction IGNORE=new UntypedReaction(ReactionType.IGNORE);
	/**
	 * The singleton wrapper for {@link ReactionType#RECOMPUTE}
	 */
	public static final TransformReaction RECOMPUTE=new UntypedReaction(ReactionType.RECOMPUTE);
	/**
	 * The singleton wrapper for {@link ReactionType#JUST_PROPAGATE_NO_TRANSACTION}
	 */
	public static final TransformReaction JUST_PROPAGATE_NO_TRANSACTION=new UntypedReaction(ReactionType.JUST_PROPAGATE_NO_TRANSACTION);
	/**
	 * The singleton wrapper for {@link ReactionType#JUST_PROPAGATE_WITH_TRANSACTION}
	 */
	public static final TransformReaction JUST_PROPAGATE_WITH_TRANSACTION=new UntypedReaction(ReactionType.JUST_PROPAGATE_WITH_TRANSACTION);
	public static <E> TransformReaction ignore(){return IGNORE;}
	public static <E> TransformReaction recompute(){return RECOMPUTE;}
	public static <E> TransformReaction justPropagate_noTransaction(){return JUST_PROPAGATE_NO_TRANSACTION;}
	public static <E> TransformReaction justPropagateWithTransaction(){return JUST_PROPAGATE_WITH_TRANSACTION;}
}