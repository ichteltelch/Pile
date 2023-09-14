package pile.impl;

import pile.aspect.combinations.Prosumer;

/**
 * A mutable wrapper around a reference.
 * Use this class instead of {@link MutRef} during startup in order to avoid loading 
 * the {@link Piles} class and all that belongs to it
 * @author bb
 *
 * @param <T> The type of the reference
 */
public final class EarlyMutRef<T> extends ReleasableMutRef<T>
implements Prosumer<T>{
	/**
	 * Make a new {@link EarlyMutRef} instance whose intial value is the <code>null</code> reference
	 */
	public EarlyMutRef(){}
	/**
	 * Make a new {@link EarlyMutRef} initially containing the specified reference
	 * @param val
	 */
	public EarlyMutRef(T val){this.val=val;}
	/**
	 * Change the reference wrapped by this object to the specified value
	 * @param val
	 * @return this
	 */
	public EarlyMutRef<T> set(T val){
		this.val=val;
		return this;
	}
	/**
	 * Change the reference wrapped by this object to the one wrapped by the given {@link EarlyMutRef} instance
	 * @param o
	 * @return
	 */
	public EarlyMutRef<T> set(EarlyMutRef<T> o){
		this.val=o.val;
		return this;
	}
	/**
	 * @return the wrapped value
	 */
	public T get(){return val;}
	/**
	 * @return A {@link String} representation of the wrapped value, enclosed in anlge brackets
	 */
	public String toString() {
		return "<"+val+">";
	}
	/**
	 * Set the reference wrapped by this {@link EarlyMutRef} to <code>null</code>
	 * @return this
	 */
	public EarlyMutRef<T> setNull() {
		val=null;
		return this;
	}
	/**
	 * Set the reference wrapped by this {@link EarlyMutRef} to <code>null</code>
	 */
	@Override
	public void release() {
		val=null;
	}
	@Override
	public void accept(T t) {
		val=t;
	}
	
	
}
