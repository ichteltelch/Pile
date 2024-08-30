package pile.aspect.bracket;

import java.util.function.Function;

import pile.aspect.Dependency;
import pile.aspect.Depender;
import pile.aspect.HasInternalLock;
import pile.aspect.listen.ListenValue;
import pile.interop.debug.DebugEnabled;

/**
 * A bracket that lets a {@link Depender} extracted from the value depend on one or more 
 * {@link Dependencies}s while the bracket is open.
 * @author bb
 *
 * @param <V>
 */
public final class DependerBracket<V> implements ValueOnlyBracket<V> {
	private final Dependency[] ds;
	private final Function<? super V, ? extends Depender> extract;
	private final boolean inheritable;
	private final Dependency d0;
	boolean triggerChange;
	/**
	 * @param inheritable See {@link #isInheritable()}
	 * @param extract The Function that extracts the {@link Depender} that should be made to depend on the {@link Dependency Dependencies}
	 * It is important that this function is deterministic, otherwise the {@link Dependency Dependencies} cannot be removed again properly.
	 * @param triggerChange Whether the {@link Dependency Dependencies} should be added and removed in a way that triggers recomputation of the {@link Depender}. 
	 * @param d0 One of the {@link Dependency Dependencies}. May be <code>null</code>.
	 * @param ds Possibly more {@link Dependency Dependencies}
	 */
	DependerBracket(boolean inheritable, Function<? super V, ? extends Depender> extract, boolean triggerChange,
			Dependency d0, Dependency[] ds) {
		this.ds = ds==null?null:ds.clone();
		this.extract = extract;
		this.inheritable = inheritable;
		this.d0 = d0;
		this.triggerChange=triggerChange;
	}

	public static <V> ValueOnlyBracket<V> create(boolean inheritable, Function<? super V, ? extends Depender> extract, boolean triggerChange,
			Dependency d0, Dependency[] ds) {
		ValueOnlyBracket<V> ret = new DependerBracket<>(inheritable, extract, triggerChange, d0, ds);
		if(DebugEnabled.DETECT_STUCK_BRACKETS)
			ret=ret.detectStuck();
		return ret;
	}

	@Override
	public boolean open(V value, Object owner) {
		if(value==null)
			return true;
		if(owner instanceof HasInternalLock && ((HasInternalLock) owner).holdsLock()) {
			//QueuedValueBracket.getDefaultDependencyQueue().enqueue(()->doOpen(value, owner));
			ListenValue.DEFER.run(()->doOpen(value, owner));
		}else {
			doOpen(value, owner);
		}
		return true;
	}
	public void doOpen(V value, Object owner) {
		Depender fd = extract.apply(value);
		if(d0!=null)
			fd.addDependency(d0, triggerChange);
		if(ds!=null)
			for(Dependency d: ds)
				fd.addDependency(d, triggerChange);
	}

	@Override
	public boolean close(V value, Object owner) {
		if(value==null)
			return false;
		if(owner instanceof HasInternalLock && ((HasInternalLock) owner).holdsLock()) {
			//			QueuedValueBracket.getDefaultDependencyQueue().enqueue(()->doClose(value, owner));
			ListenValue.DEFER.run(()->doClose(value, owner));
		}else {
			doClose(value, owner);
		}
		return false;
	}
	public void doClose(V value, Object owner) {
		Depender fd = extract.apply(value);
		if(d0!=null)
			fd.removeDependency(d0, triggerChange);
		if(ds!=null)
			for(Dependency d: ds)
				fd.removeDependency(d, triggerChange);

	}

	@Override
	public boolean isInheritable() {
		return inheritable;
	}
	@Override public boolean openIsNop() {return false;}
	@Override public boolean closeIsNop() {return false;}

	@Override public boolean canBecomeObsolete() {return false;}


}