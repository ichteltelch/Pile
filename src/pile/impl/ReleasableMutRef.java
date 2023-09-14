package pile.impl;

import java.util.function.Supplier;

/**
 * A wrapper around a reference that can take a special action 
 * when its value is set to <code>null</code> 
 * via its {@link #release()} method
 * @author bb
 *
 * @param <T>
 */
public abstract class ReleasableMutRef<T> implements Supplier<T> {
	/**
	 * The value that this reference holds
	 */
	public T val;
	/**
	 * @return the value that this reference holds
	 */
	public T get() {
		return val;
	}
	public ReleasableMutRef(){}
	public ReleasableMutRef(T val){this.val=val;}
	/**
	 * This should set {@link #val} to <code>null</code> and may also do other things, 
	 * for example destroying the value or decrementing a reference counter
	 */
	public abstract void release();
	
	
}
