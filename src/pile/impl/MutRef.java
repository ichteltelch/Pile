package pile.impl;

import java.util.function.BiPredicate;

import pile.aspect.JustReadValue;
import pile.aspect.combinations.ReadWriteValue;
import pile.aspect.suppress.Suppressor;

/**
 * A mutable wrapper around a reference
 * @author bb
 *
 * @param <T> The type of the reference
 */
public final class MutRef<T> extends ReleasableMutRef<T>
implements ReadWriteValue<T>, JustReadValue<T>{
	/**
	 * Make a new {@link MutRef} instance whose intial value is the <code>null</code> reference
	 */
	public MutRef(){}
	/**
	 * Make a new {@link MutRef} initially containing the specified reference
	 * @param val
	 */
	public MutRef(T val){this.val=val;}
	/**
	 * Change the reference wrapped by this object to the specified value
	 * @param val
	 * @return this
	 */
	public T set(T val){
		this.val=val;
		return val;
	}
	/**
	 * Change the reference wrapped by this object to the one wrapped by the given {@link MutRef} instance
	 * @param o
	 * @return
	 */
	public MutRef<T> take(MutRef<T> o){
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
	 * Set the reference wrapped by this {@link MutRef} to <code>null</code>
	 * @return this
	 */
	public MutRef<T> setNull() {
		val=null;
		return this;
	}
	/**
	 * Set the reference wrapped by this {@link MutRef} to <code>null</code>
	 */
	@Override
	public void release() {
		val=null;
	}
	
	@Override public void __beginTransaction() {}
	@Override public void __endTransaction(boolean b) {}
	@Override public void permaInvalidate() {}
	@Override public void _setEquivalence(BiPredicate<? super T, ? super T> equivalence) {}
	@Override public void valueMutated() {}
	public static final BiPredicate<Object, Object> DEFAULT_EQUIVALENCE = (a, b)->a==b;
	@Override
	public BiPredicate<? super T, ? super T> _getEquivalence() {
		return DEFAULT_EQUIVALENCE;
	}
	@Override
	public void revalidate() {		
	}
	@Override
	public void __endTransaction() {JustReadValue.super.__endTransaction();}
	@Override public T applyCorrection(T v) {return v;}
	@Override public boolean remembersLastValue() {return false;}
	@Override public void resetToLastValue() {}
	@Override public void storeLastValueNow() {}
	@Override public Suppressor suppressRememberLastValue() {return Suppressor.NOP;}
}
