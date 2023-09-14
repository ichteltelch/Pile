package pile.aspect.transform;

import pile.aspect.listen.ValueEvent;
import pile.aspect.listen.ValueListener;

/**
 * A {@link ValueEvent} that has been fired because a {@link TransformableValue}'s value 
 * has been mutated by a transform
 * @author bb
 * @see ValueListener#ignoreTransformEvents
 *
 */
public class TransformValueEvent extends ValueEvent{

	/**
	 * 
	 */
	private static final long serialVersionUID = -3970036866507955446L;
	public TransformValueEvent(Object source) {
		super(source);
	}
	public boolean isTransformValueEvent() {
		return true;
	}
}
