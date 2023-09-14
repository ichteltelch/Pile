package pile.aspect;

import java.util.function.Consumer;

/**
 * Interface for objects holding some value of type {@code E} that can be manipulated, 
 * but at some point that manipulation should be restricted.
 * @author bb
 *
 * @param <E>
 */
public interface Sealable<E> {
	/**
	 * Restrict further manipulation if the value.
	 * This should call {@link #seal(Consumer)} with a default interceptor 
	 * whose effect is to throw an
	 * {@link IllegalStateException}
	 * It can only be changed using a {@link WriteValue} object obtained
	 * from {@link #makeSetter()}
	 * @throws IllegalStateException If the object has already been sealed 
	 * (with a different interceptor)
	 * 
	 */
	public void seal();
	/**
	 * Restrict further manipulation if the value.
	 * When the value is manipulated through some means other than a {@link WriteValue}
	 * previously obtained from {@link #makeSetter()}, the given interceptor
	 *  is called instead.
	 * @param interceptor If this is <code>null</code>, the same default interceptor is used
	 * @param allowInvalidation Whether will be allowed to call {@link #permaInvalidate()}.
	 * as if {@link #seal()} had been called 
	 */
	public void seal(Consumer<? super E> interceptor, boolean allowInvalidation);
	/**
	 * Test if the value has been sealed
	 * @return
	 */
	public boolean isSealed();
	/**
	 * Make a {@link Consumer} that can be called to set the value even if
	 * the object has been sealed. 
	 * @return
	 * @throws IllegalStateException If the object has already been sealed
	 */
	public WriteValue<E> makeSetter();
	
	/**
	 * @return <code>true</code> iff the object was sealed with the default interceptor 
	 */
	public boolean isDefaultSealed();
	
	/**
	 * 
	 * @return Whether {@link #makeSetter()} has ever returned something
	 */
	public boolean setterExists();
	
}
