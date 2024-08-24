package pile.aspect.listen;

import java.util.HashMap;

/**
 * A {@link ListenValue} that can listen to multiple other {@link ListenValue}s
 * and forward the events to all its listeners. 
 */
public class ConcreteMultiListenValue implements MultiListenValue, ListenValue.Managed{

	

	final HashMap<ListenValue, ValueListener> listenTo = new HashMap<>();

	final ListenerManager manager = new ListenerManager();
	final ValueListener listener;
	
	/**
	 * If you call this constructor, 
	 * The events will be forwarded individually and specify the original source.
	 */
	public ConcreteMultiListenValue() {
		listener = e->{
			manager.fireValueChange(e);
		};
	}
	/**
	 * If you call this constructor, the events will be collected in a
	 * {@link RateLimitedValueListener} before being forwarded at a limited rate.
	 * @param coldStartTime 
	 * @param coolDownTime
	 * @param startCoolingBefore
	 * @param careAboutSources if <code>true</code>, the source will be the set of sources collected
	 * by the {@link RateLimitedValueListener}. If <code>false</code>, the source
	 * will be this {@link ConcreteMultiListenValue}
	 */
	public ConcreteMultiListenValue(
			long coldStartTime, 
			long coolDownTime, 
			boolean startCoolingBefore,
			boolean careAboutSources) {
		if(careAboutSources) {
			listener = ValueListener.rateLimited(coldStartTime, coolDownTime, startCoolingBefore, e->{
	            manager.fireValueChange(new ValueEvent(manager.getValueEventSource()));
	        });
		}else {
			listener = ValueListener.rateLimited(coldStartTime, coolDownTime, startCoolingBefore, ()->{
	            manager.fireValueChange();
	        });		
		}
	}
	
	/**
	 * Start collecting events from a {@link ListenValue}.
	 * @param v the value. will hold no strong references to this {@link ConcreteMultiListenValue}.
	 * @return <code>true</code> iff the value was not already collected from
	 */
	public synchronized boolean add(ListenValue v) {
		if(listenTo.containsKey(v))
			return false;
		listenTo.put(v, v.addWeakValueListener(listener));
		return true;
	}
	/**
	 * Stop collecting Events from a {@link ListenValue}
	 * @param v 
	 * @return <code>true</code> iff the value was previously collected from
	 */
	public synchronized boolean remove(ListenValue v) {
		ValueListener rem = listenTo.remove(v);
		if(rem==null) 
			return false;
		v.removeValueListener(rem);
		return true;
	}
	/**
	 * @param v
	 * @return Whether {@link ValueEvent}s from the given {@link ListenValue} 
	 * are currently collected
	 */
	public synchronized boolean collectsFrom(ListenValue v) {
		return listenTo.containsKey(v);
	}
	
	
	@Override
	public ListenerManager _getListenerManager() {
		return manager;
	}

}
