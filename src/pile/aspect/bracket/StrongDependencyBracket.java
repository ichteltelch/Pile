package pile.aspect.bracket;

import java.util.function.Function;

import pile.aspect.Dependency;
import pile.aspect.Depender;
import pile.aspect.HasInternalLock;
import pile.aspect.listen.ListenValue;
import pile.interop.debug.DebugEnabled;

/**
 * A bracket that lets one or more {@link Depender}s depend on a {@link Dependency}
 * extracted from the value while the bracket is open.
 * @author bb
 *
 * @param <V>
 */
public final class StrongDependencyBracket<V> implements ValueOnlyBracket<V> {
	private final Depender[] ds;
	private final Function<? super V, ? extends Dependency> extract;
	private final boolean inheritable;
	private final Depender d0;
	boolean recompute;


	/**
	 * 
	 * @param inheritable See {@link #isInheritable()}
	 * @param extract The Function that extracts the {@link Dependency} that should be added. 
	 * It is important that this function is deterministic, otherwise the {@link Dependency} cannot be removed again properly. 
	 * @param d0 One of the {@link Depender}s. May be <code>null</code>.
	 * @param ds Possibly more {@link Depender}s
	 */
	StrongDependencyBracket(boolean inheritable, Function<? super V, ? extends Dependency> extract,
			boolean recompute, Depender d0, Depender[] ds) {
		this.ds = ds==null?null:ds.clone();
		this.extract = extract;
		this.inheritable = inheritable;
		this.d0 = d0;
		this.recompute=recompute;
	}
	public static <V> ValueOnlyBracket<V> create(
			boolean inheritable, Function<? super V, ? extends Dependency> extract,
			boolean recompute, Depender d0, Depender[] ds){
		ValueOnlyBracket<V> ret = new StrongDependencyBracket<>(inheritable, extract, recompute, d0, ds);
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
	private void doOpen(V value, Object owner) {
		Dependency fd = extract.apply(value);
		if(d0!=null)
			d0.addDependency(fd, recompute);
		if(ds!=null)
			for(Depender d: ds)
				d.addDependency(fd, recompute);
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
		Dependency fd = extract.apply(value);
		if(d0!=null)
			d0.removeDependency(fd, recompute);
		if(ds!=null)
			for(Depender d: ds)
				d.removeDependency(fd, recompute);
	}
	@Override
	public boolean isInheritable() {
		return inheritable;
	}
	@Override public boolean openIsNop() {return false;}
	@Override public boolean closeIsNop() {return false;}
	@Override public boolean canBecomeObsolete() {return false;}

}