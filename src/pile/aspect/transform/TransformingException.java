package pile.aspect.transform;

import pile.aspect.recompute.Recomputation;

/**
 * Thrown when a transform is in progress and something is attempted that shouldn't 
 * be attempted while the transform is in progress, such as starting another transform
 * or fulfilling a {@link Recomputation}.
 */
public class TransformingException extends RuntimeException{

	/**
	 * 
	 */
	private static final long serialVersionUID = 4138772545326487879L;

}
