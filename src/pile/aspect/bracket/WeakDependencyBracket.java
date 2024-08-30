package pile.aspect.bracket;

import java.lang.ref.WeakReference;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

import pile.aspect.Dependency;
import pile.aspect.Depender;
import pile.aspect.HasInternalLock;
import pile.aspect.listen.ListenValue;
import pile.interop.debug.DebugEnabled;

/**
 * A bracket that lets one or more {@link Depender}s depend on the value while the bracket is open.
 * This Bracket keeps only {@link WeakReference}s to the Dependers. When all the
 * references have been cleared, the bracket requests that it be removed from the value holder,
 * as it will from now on have no effect.
 * @author bb
 *
 * @param <V>
 */

public final class WeakDependencyBracket<V> implements ValueOnlyBracket<V> {
	private final WeakReference<Depender>[] ds;
	private final Function<? super V, ? extends Dependency> extract;
	private final boolean inheritable;
	private final WeakReference<Depender> d0;

	/**
	 * 
	 * @param inheritable See {@link #isInheritable()}
	 * @param extract The Function that extracts the {@link Dependency} that should be added. 
	 * It is important that this function is deterministic, otherwise the {@link Dependency} cannot be removed again properly. 
	 * @param d0 One of the {@link Depender}s. May be <code>null</code>.
	 * @param ds Possibly more {@link Depender}s
	 */
	@SuppressWarnings("unchecked")
	WeakDependencyBracket(boolean inheritable, Function<? super V, ? extends Dependency> extract,
			Depender d0, Depender[] ds) {
		if(ds==null)
			this.ds=null;
		else {
			this.ds=new WeakReference[ds.length];
			for(int i=0; i<ds.length; ++i) {
				this.ds[i]=new WeakReference<>(ds[i]);
			}
		}			
		this.extract = extract;
		this.inheritable = inheritable;
		this.d0 = d0==null?null:new WeakReference<Depender>(d0);
	}
	public static <V> ValueOnlyBracket<V> create(boolean inheritable, Function<? super V, ? extends Dependency> extract,
			Depender d0, Depender[] ds) {
		ValueOnlyBracket<V> ret = new WeakDependencyBracket<>(inheritable, extract, d0, ds);
		if(DebugEnabled.DETECT_STUCK_BRACKETS)
			ret=ret.detectStuck();
		return ret;
	}

	final ConcurrentHashMap<Object, Object> obsoleteOn = new ConcurrentHashMap<Object, Object>();
	public boolean open(V value, Object owner) {
		if(value==null)
			return true;
		if(obsoleteOn.remove(owner)!=null)
			return false;
		if(owner instanceof HasInternalLock && ((HasInternalLock) owner).holdsLock()) {
			//QueuedValueBracket.getDefaultDependencyQueue().enqueue(()->doOpen(value, owner));
			ListenValue.DEFER.run(()->doOpen(value, owner));
		}else {
			doOpen(value, owner);
		}
		return true;
	}

	public void doOpen(V value, Object owner) {

		Dependency fd = extract.apply(value);
		boolean doneSth=false;
		if(d0!=null){
			Depender d = d0.get();
			if(d!=null) {
				d.addDependency(fd);
				doneSth=true;
			}

		}
		if(ds!=null)
			for(WeakReference<Depender> ref: ds) {
				Depender d = ref.get();
				if(d!=null) {
					d.addDependency(fd);
					doneSth=true;
				}
			}
		if(!doneSth)
			obsoleteOn.put(owner, this);
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
		if(d0!=null){
			Depender d = d0.get();
			if(d!=null)
				d.removeDependency(fd);

		}
		if(ds!=null)
			for(WeakReference<Depender> ref: ds) {
				Depender d = ref.get();
				if(d!=null)
					d.removeDependency(fd);
			}

	}

	@Override
	public boolean isInheritable() {
		return inheritable;
	}
	@Override public boolean openIsNop() {return false;}
	@Override public boolean closeIsNop() {return false;}
	@Override public boolean canBecomeObsolete() {return true;}

}