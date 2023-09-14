package pile.aspect.transform;

/**
 * This enum lists the possible behaviors when certain operations are attempted while
 * a value is transforming.
 * @author bb
 *
 */
public enum BehaviorDuringTransform {
	/**
	 * Behavior: Proceed as usual
	 */
	NOP,
	/**
	 * Behavior: Throw a {@link TransformingException}
	 */
	THROW_TRANSFORMINGEXCEPTION,
	/**
	 * Behavior: Wait until the transformation is finished
	 */
	BLOCK
}
