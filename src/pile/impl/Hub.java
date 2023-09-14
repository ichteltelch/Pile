package pile.impl;

import java.util.function.BiPredicate;

import javax.xml.bind.ValidationEvent;

import pile.aspect.Dependency;
import pile.aspect.listen.ListenValue;
import pile.aspect.listen.ValueListener;

/**
 * {@link Hub} are used to bundle several {@link Dependency Dependencies} into one.
 * If one of their dependencies changes, they will recompute themselves, and even though the new 
 * value is identical to the old, it will by default be considered a change 
 * (because of a deliberately inconsistent "equivalence relation").
 * Keep this in mind when you try to make a {@link #field(java.util.function.Function) field} 
 * that takes its
 * value from a {@link Hub}, because the utility methods for making fields use value equality as their
 * equivalence relation by default.
 * @author bb
 *
 */
public class Hub extends PileImpl<Object>{
	
	/**
	 * The <q>equivalence relation</q> used for {@link Hub}s
	 */
	public static BiPredicate<Object, Object> HUBS_ARE_ALWAYS_UNEQUAL=(a, b)->false;

	/**
	 * @throws UnsupportedOperationException If you really need to set the value
	 * to something different, use the {@link #setExplicitly(Object)} method.
	 */
	@Override
	public Hub set(Object val) {
		throw new UnsupportedOperationException();
	}
	@Override
	public Hub setName(String name) {
		super.setName(name);
		return this;
	}
	@Override
	public Hub setNull() {
		return set(null);
	}
	public Hub setExplicitly(Object val) {
		super.set(val);
		return this;
	}
	/**
	 * @see Hub#Hub(Object, boolean, Dependency...)
	 * @param deep
	 */
	public Hub(boolean deep) {
		this(deep, (Dependency[])null);
	}
	/**
	 * @see Hub#Hub(Object, boolean, Dependency...)
	 * @param value
	 * @param deep
	 */
	public Hub(Object value, boolean deep) {
		this(value, deep, (Dependency[])null);
	}
	/**
	 * @see Hub#Hub(Object, boolean, Dependency...)
	 * @param deep
	 * @param deps
	 */
	public Hub(boolean deep, Dependency... deps) {
		this("Hub", deep, deps);
	}
	/**
	 * Make a new {@link Hub}
	 * @param value The value that the {@link Hub} should always take when it is valid.
	 * Default value is {@code "Hub"}
	 * @param deep A "deep" {@link Hub} forwards {@link ValidationEvent}s from its
	 * {@link Dependency Dependencies} if they are also {@link ListenValue}s.
	 * @param deps Things the {@link Hub} should initially depend on.
	 */
	public Hub(Object value, boolean deep, Dependency... deps) {
		deepListener = deep?makeDeepListener():null;
		if(deps!=null)
			addDependency(false, deps);
		_setEquivalence(HUBS_ARE_ALWAYS_UNEQUAL);
		_setRecompute(r->{
			r.fulfill(value);
		});
		if(value instanceof String)
			super.setName((String)value);
//		setDebugCallback(new DebugCallback() {
//			@Override
//			public void dependencyEndsChanging(ReadListenDependency<?> source, Dependency d) {
//				DebugCallback.super.dependencyEndsChanging(source, d);
//			}
//		});
	}
	private ValueListener makeDeepListener() {
		return e -> {

			if(listeners==null) {
				return;
			}
			getListenerManager().fireValueChange(e);
		};
	}

	/**
	 * The listener that listens to the {@link Dependency Dependencies}, if this is a deep {@link Hub}.
	 */
	final ValueListener deepListener;

	@Override
	protected void dependencyAdded(Dependency d) {
		if(deepListener!=null && d instanceof ListenValue) {
			((ListenValue) d).addValueListener(deepListener);
		}
	}
	@Override
	protected void dependencyRemoved(Dependency d) {
		if(deepListener!=null && d instanceof ListenValue) {
			((ListenValue) d).removeValueListener(deepListener);
		}
	}
//	@Override
//	public void endTransaction(boolean b) {
//		super.endTransaction(b);
////		if(!valid && allDependenciesValid() && autoValidationSuppressors==0 && openTransactions()==0 && pendingRecompute==null) {
////			System.out.println();
////			super.beginTransaction();
////			super.endTransaction();
////		}
//	}
//	@Override
//	protected void releaseAutoValidationSuppressor() {
//		super.releaseAutoValidationSuppressor();
////		if(!valid && allDependenciesValid() && autoValidationSuppressors==0 && openTransactions()==0 && pendingRecompute==null)
////			System.out.println();
//	}


}
