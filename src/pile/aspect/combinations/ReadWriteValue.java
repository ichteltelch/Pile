package pile.aspect.combinations;

import pile.aspect.ReadValue;
import pile.aspect.WriteValue;

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
}
