package pile.aspect.combinations;

import java.util.function.Consumer;

import pile.aspect.ReadValue;
import pile.aspect.WriteValue;
import pile.aspect.listen.ValueEvent;
import pile.aspect.listen.ValueListener;
import pile.specialized_bool.combinations.ReadListenDependencyBool;

/**
 * Combination of {@link ReadValue} and {@link WriteValue}
 * @author bb
 *
 * @param <E>
 */
public interface ReadWriteValue<E> extends ReadValue<E>, WriteValue<E>, Prosumer<E>{
	/**
	 * Call {@link #set(Object) set(null)} and return this.
	 * Subclasses and subinterfaces should override this method to return the same type as themselves.
	 */
	@Override public default ReadWriteValue<E> setNull() {
		set(null);
		return this;
	}

	/**
	 * Does something as soon as this value is valid
	 * @param what Some function to call when the value is valid. Will be called with wrapped value.
	 * @return <code>null</code> if the value was valid and the think could be done immediately.
	 * Otherwise, returns a {@link ValueListener} that you can remove to cancel the action.
	 */
	public default ValueListener doOnceWhenValid(Consumer<? super E> what) {

		if(isValid()) {
			try {
				E v = getValidOrThrow();
				what.accept(v);
				return null;
			}catch(InvalidValueException e) {
				// Value was invalid after all
			}
		}
		ReadListenDependencyBool valid = validity();
		ValueListener vl = new ValueListener() {
			@Override
			public void valueChanged(ValueEvent e) {
				if(isValid()) {
					try {
						E v = getValidOrThrow();
						valid.removeValueListener(this);
						what.accept(v);
						return;
					}catch(InvalidValueException x) {
						// Value was invalid after all;
						//Do not remove the listener, 
						//but keep it installed to get notified again when the value becomes valid.
					}
				}				
			}
		};
		valid.addValueListener_(vl);
		return vl;
	}
	
}
