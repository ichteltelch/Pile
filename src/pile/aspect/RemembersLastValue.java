package pile.aspect;

import pile.aspect.suppress.Suppressor;

/**
 * Interface for value-holders that can remember a single previously set value and restore the remembered value
 * @see LastValueRememberer
 * @author bb
 *
 */
public interface RemembersLastValue extends LastValueRememberSuppressible.Single {
	/**
	 * Query whether changes currently trigger saving of the new value
	 * @return
	 */
	boolean remembersLastValue();
	/**
	 * Manually trigger storage of the current value as the last remembered value
	 */
	void storeLastValueNow();
	/**
	 * Manually restore the last remembered value
	 */
	public void resetToLastValue();
	/**
	 * Delegates to {@link #suppressRememberLastValue()}
	 */
	default Suppressor suppressRememberLastValues() {
		return suppressRememberLastValue();
	}
	/**
	 * Make a {@link Suppressor} which suppresses last value remembering behavior of this object 
	 * until it is released
	 * @return
	 */
	Suppressor suppressRememberLastValue();


	
}
