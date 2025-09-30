package pile.aspect.listen;

import java.util.Set;
import java.util.WeakHashMap;

import pile.utils.WeakCleanup;

/**
 * A wrapper around another {@link ValueListener}. 
 * It forwards all {@link #valueChanged(ValueEvent)} calls to that listener, 
 * but keeps only a weak reference to it and removes itself from all {@link ListenValue}s
 * it has been registered with when the reference is cleared.
 * 
 * When using this class, you should not register the inner {@link ValueListener}
 * anywhere, but keep a reference to it as long as it should remain registered.
 * @author bb
 *
 */
public class WeakValueListener extends WeakCleanup<ValueListener> implements ValueListener{

	int priority;
	public WeakValueListener(ValueListener back) {
		super(back);
		this.priority=back.priority();
	}
	WeakHashMap<ListenValue, ?> addedTo = new WeakHashMap<>();
	/**
	 * This removes the listener from all ListenValues it has been registered with and that are still
	 * reachable.
	 */
	
	@Override
	public int priority() {
		return priority;
	}
	@Override
	public synchronized void run() {
		Set<ListenValue> removeFrom = addedTo.keySet();
		addedTo=null;
		removeFrom.forEach(this::removeFrom);	
	}
	private void removeFrom(ListenValue v) {
		v.removeValueListener(this);
	}
	@Override
	public synchronized void youWereAdded(ListenValue toThis) {
		if(addedTo!=null)
			addedTo.put(toThis, null);
	}
	@Override
	public synchronized void youWereRemoved(ListenValue fromThis) {
		if(addedTo!=null)
			addedTo.remove(fromThis);
	}
	@Override
	public void valueChanged(ValueEvent e) {
		ValueListener strong = get();
		if(strong!=null)
			strong.valueChanged(e);
	}
	@Override
	public WeakValueListener asWeakValueListener() {
		return this;
	}
	@Override
	public void runImmediately() {
		ValueListener g = get();
		if(g!=null)
			g.runImmediately();
	}
	@Override
	public void runImmediately(boolean inThisThread) {
		ValueListener g = get();
		if(g!=null)
			g.runImmediately(inThisThread);
	}

}
