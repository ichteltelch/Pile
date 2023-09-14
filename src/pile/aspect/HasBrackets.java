package pile.aspect;

/**
 * Common superinterface for objects that can hold a current and an old value and offer the ability
 * to perform certain actions whenever these values change, using {@link ValueBracket}s. 
 * @author bb
 *
 * @param <Self> Relevant implementing subtype
 * @param <E> Type of the value held
 */
public interface HasBrackets<Self extends HasBrackets<? extends Self, ? extends E>, E> {
	/**
	 * Add a {@link ValueBracket} that opens whenever a new "current" value becomes active,
	 * and closes when it becomes inactive because it becomes invalid or is exchanged for another object.
	 * The bracket must only close on values that it has been opened on.
	 * @param openNow whether the bracket is to be opened if it is added while a value is already active.
	 * 
	 * @param b
	 */
	public void _addValueBracket(boolean openNow, ValueBracket<? super E, ? super Self> b);
	/**
	 * Add a {@link ValueBracket} that opens whenever a new "old" value becomes active,
	 * and closes when it becomes inactive because it becomes invalid or is exchanged for another object.
	 * The bracket must only close on values that it has been opened on.
	 * @param openNow whether the bracket is to be opened if it is added while a value is already active.
	 * @param b
	 */
	public void _addOldValueBracket(boolean openNow, ValueBracket<? super E, ? super Self> b);
	/**
	 * Add a {@link ValueBracket} that opens whenever a new "current" or "old" value becomes active when
	 * the same object was not already a "current" or "old" value,
	 * and closes when both the "old" and the "current" value no longer point to that object 
	 * because they became invalid or were exchanged for another object.
	 * The bracket must only close on values that it has been opened on.
	 * @param openNow whether the bracket is to be opened if it is added while a value is already active.
	 * @param b
	 */
	public void _addAnyValueBracket(boolean openNow, ValueBracket<? super E, ? super Self> b);
	/**
	 * Copy all inheritable brackets from this object to the given {@link HasBrackets} instance
	 * @param v
	 */
	public void bequeathBrackets(boolean openNow, HasBrackets<Self, ? extends E> v);
}
