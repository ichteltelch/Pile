package pile.aspect;

import java.util.Objects;
import java.util.function.Supplier;

import pile.aspect.combinations.ReadListenDependency;
import pile.aspect.recompute.DependencyRecorder;
import pile.aspect.recompute.Recomputation;
import pile.interop.wait.WaitService;
import pile.specialized_bool.combinations.JustReadValueBool;
import pile.specialized_bool.combinations.ReadListenDependencyBool;
import pile.specialized_bool.combinations.ReadValueBool;

/**
 * A value's aspect of being readable.
 * @author bb
 *
 * @param <E>
 */
public interface ReadValue<E> extends Supplier<E>, DoesTransactions{
	
	/**
	 * Get the current value.
	 * If it is invalid, <code>null</code> is returned and the invalidity becomes observed.
	 * The access may be recorded by a {@link DependencyRecorder}.
	 * If it is lazy-validating, a {@link Recomputation} may be triggered and if
	 * it is fulfilled synchronously, the {@link ReadValue} will become valid before result will be returned. 
	 * @return
	 */
	public E get();
	
	/**
	 * Just get the current value, without any locking or fancy stuff.
	 * If it is invalid, <code>null</code> is returned, but invalidity is not observed.
	 * The access will not be recorded by a {@link DependencyRecorder}.
	 * @return
	 */
	public E getAsync();



	/**
	 * Get the current value. If necessary, wait until it becomes valid.
	 * @return
	 * @throws InterruptedException
	 */
	default public E getValid() throws InterruptedException{
		return getValid(WaitService.get());
	}
	/**
	 * Get the current value. If necessary, wait until it becomes valid.
	 * @param timeout If it does not become valid before this many milliseconds have elapsed, 
	 * null is returned and the invalidity becomes observable.
	 * @return
	 * @throws InterruptedException
	 */
	default public E getValid(long timeout) throws InterruptedException{
		return getValid(WaitService.get(), timeout);
	}
	
	/**
	 * Get the current value. If necessary, wait until it becomes valid.
	 * @param ws Use this {@link WaitService}
	 * @return
	 * @throws InterruptedException
	 */
	public E getValid(WaitService ws) throws InterruptedException;
	/**
	 * Get the current value. If necessary, wait until it becomes valid.
	 * @param ws Use this {@link WaitService}
	 * @param timeout If it does not become valid before this many milliseconds have elapsed, 
	 * null is returned and the invalidity becomes observable.
	 * @return
	 * @throws InterruptedException
	 */
	public E getValid(WaitService ws, long timeout) throws InterruptedException;
	
	
	/**
	 * Get the value if it is valid; throw an {@link InvalidValueException} if it is not.
	 * To avoid throwing many exceptions, you should use this method like this:
	 * <br>
	 * <pre>
	 * <code>
	 * 	if(v.isValid()){
	 * 		try {
	 * 			E e = v.getValidOrThrow();
	 * 			// Do something with the value
	 * 		}catch(InvalidValueException x){
	 * 			//react to invalid value
	 * 		}
	 * 	}else{
	 * 		//react to invalid value
	 * 	}
	 * </code>
	 * </pre>
	 * Feel free to do something about that code duplication.
	 * @return
	 * @throws InvalidValueException
	 */
	public E getValidOrThrow() throws InvalidValueException;
	/**
	 * Get the value it it is valid; If it is not, the invalidity becomes observable and the "old" value 
	 * is returned, or, if that too is invalid or there is no concept of "old" value, <code>null</code>
	 * is returned.
	 * @return
	 */
	public E getOldIfInvalid();

	/**
	 * Test if this value is currently valid.
	 * If it is not, the invalidity becomes observable.
	 * This may record a dependency on {@link #validity()} in a {@link DependencyRecorder}
	 * @return
	 */
	public boolean isValid();
	/**
	 * Return a lazily initialized boolean value that changes with the
	 * observed validity status of this value.
	 * The observed validity of becomes <code>true</code> as soon as the 
	 * actual validity becomes <code>true</code>,
	 * but becomes <code>false</code> only some time later (or not at all, 
	 * if the actual validity changes back to <code>true</code> on time). 
	 * This will be at least the case if the invalidity is observed by {@link #isValid()} 
	 * or one of the get* methods (see their documentation for the rules).
	 * @return
	 */
	public ReadListenDependencyBool validity();
	

	/**
	 * Check atomically whether this value holds a <code>null</code> reference but is valid.
	 * @return
	 */
	boolean isValidNull();
	/**
	 * Return a boolean {@link ReadValue} that reflects whether this value
	 * holds a <code>null</code> reference but is valid.
	 * <br>
	 * Subclasses of {@link ReadValue} should override this method to return a more specific
	 * type that for example allows observing changes.
	 * 
	 * @return
	 */
	public default ReadValueBool validNull() {
		return (JustReadValueBool)()->isValidNull();
	}

	/**
	 * Test whether this value currently has at least one active transaction
	 * @return
	 */
	public boolean isInTransaction();
	/**
	 * Return a boolean {@link ReadListenDependency} that reflects whether this
	 * currently has at least one active transaction
	 * @return
	 */
	public ReadListenDependencyBool inTransactionValue();

	/**
	 * Shorthand for {@code get()==null}
	 * @see #nullOrInvalid()
	 * @see #isNonNull()
	 * @return
	 */
	default boolean isNull() {return get()==null;}
	/**
	 * Return a boolean {@link ReadValue} that reflects whether this value
	 * holds a <code>null</code> reference or is invalid.
	 * <br>
	 * Subclasses of {@link ReadValue} should override this method to return a more specific
	 * type that for example allows observing changes.
	 * @see #isNull()
	 * @see #isNonNull()
	 * @return
	 */
	public ReadValueBool nullOrInvalid();
	/**
	 * Shorthand for {@code get()!=null}
	 * @return
	 */
	default boolean isNonNull() {return get()!=null;}
	
//	public Maybe<E> getWithValidity();
	

	/**
	 * An exception thrown by {@link ReadValue#getValidOrThrow()} if the value is not valid
	 * @author bb
	 *
	 */
	public static class InvalidValueException extends Exception{
		/**
		 * 
		 */
		private static final long serialVersionUID = 4411899497460548672L;
		public InvalidValueException() {
		}
		public InvalidValueException(String msg) {
			super(msg);
		}		
	}
	/**
	 * 
	 * @return Whether this object has been destroyed and should no longer be used
	 */
	public boolean isDestroyed();
	
	/**
	 * Convert the given {@link Supplier} to a {@link JustReadValue}
	 * @param <E>
	 * @param compute
	 * @return
	 */
	public static <E> JustReadValue<E> wrap(Supplier<E> compute){
		return compute::get;
	}
	/**
	 * Test if the wrapped value is valid and equal to the given value.
	 * The default implementation uses {@link Objects#equals(Object)} to compare,
	 * but implementors may use different equivalence relations.
	 * @param v
	 * @return
	 */
	public default boolean is(E v) {
		if(v==null)
			return isValidNull();
		else
			return Objects.equals(get(), v);
	}
	/**
	 * Copy the contents to another value
	 * @param v the target value
	 * @param alsoInvalidate Whether to {@link WriteValue#revalidate()} the target value if this
	 * {@link ReadValue} is invalid
	 */
	default void transferTo(WriteValue<? super E> v, boolean alsoInvalidate) {
		if(isValid()) {
			try {
				v.set(getValidOrThrow());
				return;
			} catch (InvalidValueException e) {
			}
		}
		if(alsoInvalidate)
			v.revalidate();
	}

	/**
	 * Simply query the validity status of this object, without any locking or triggered actions such
	 * as making invalidity observed or lazy-validation.
	 * @return
	 */
	public boolean isValidAsync();
	

}
