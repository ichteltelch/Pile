package pile.aspect.listen;

import pile.aspect.transform.TransformValueEvent;

/**
 * A {@link ValueListener} like another one, but it ignores {@link TransformValueEvent}s
 * @author bb
 *
 */
final class TransformValueEventIgnoringValueListener implements ValueListener {
	private final ValueListener self;
	public TransformValueEventIgnoringValueListener(ValueListener self) {
		this.self = self;
	}
	@Override
	public void valueChanged(ValueEvent e) {
		if(!(e instanceof TransformValueEvent))
			self.valueChanged(e);
	}
	@Override
	public int priority() {
		return self.priority();
	}
}