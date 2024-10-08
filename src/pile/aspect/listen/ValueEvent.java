package pile.aspect.listen;

import java.util.EventObject;

import pile.aspect.transform.TransformValueEvent;


public class ValueEvent extends EventObject{

	/**
	 * 
	 */
	private static final long serialVersionUID = 6359852190229399840L;
	
	/**
	 * Constructs a new {@code ValueEvent} with the specified {@code source}
	 * @param source
	 */
	public ValueEvent(Object source) {
		super(source);
	}
	/**
	 * Returns true if {@code this} is a {@link TransformValueEvent}
	 * @return
	 */
	public boolean isTransformValueEvent() {
		return false;
	}
	@Override
	public String toString() {
		return "ValueEvent[source=" + getSource() + "]";
	}

}
