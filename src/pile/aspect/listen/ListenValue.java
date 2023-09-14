package pile.aspect.listen;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

import pile.aspect.listen.RateLimitedValueListener.MultiEvent;


/**
 * The aspect of a value concerning its observability with {@linkplain ValueListener listeners}
 * @author bb
 *
 */
public interface ListenValue {

	/**
	 * Add a {@link ValueListener} that is to be run immediately when the value changes
	 * @param cl
	 */
	public void addValueListener(ValueListener cl);
	/**
	 * Add a {@link ValueListener} that is to be run immediately when the value changes
	 * and after it was added (with a <code>null</code> {@link ValueEvent})
	 * @param cl
	 */
	public default void addValueListener_(ValueListener cl) {
		addValueListener(cl);
		cl.valueChanged(null);
	}
	/**
	 * Remove a {@link ValueListener} that was to be run immediately
	 * @param cl
	 */
	public void removeValueListener(ValueListener cl);
	/**
	 * Test if a {@link ValueListener} is registered as one that is to be run immediately
	 * @param cl
	 * @return
	 */
	public boolean hasValueListener(ValueListener cl);
	
	/**
	 * Fire a change event
	 */
	public void fireValueChange();
	
	/**
	 * @return The object that is to be considered the source of the event.
	 * The default implementation returns {@code this}.
	 */
	public default Object getValueEventSource() {
		return this;
	}
	
	
	

	
	/**
	 * Add a {@link ValueListener} that is removed by the given {@link ValueListenenerUnregisterer}
	 * @param u
	 * @param l
	 */
	public default void addValueListener(ValueListenenerUnregisterer u, ValueListener l){
		Objects.requireNonNull(l);
		u.add(l, this);
	}
	/**
	 * Add a {@link ValueListener} that is removed by the given {@link ValueListenenerUnregisterer}
	 * of when it becomes weakly reachable or when the returned {@link ValueListener}
	 * is manually removed from this {@link ListenValue}
	 * @param u
	 * @param l
	 * @return 
	 */
	public default ValueListener addWeakValueListener(ValueListenenerUnregisterer u, ValueListener l){
		Objects.requireNonNull(l);
		return u.addWeak(l, this);
	}
	/**
	 * Register a {@link ValueListener} in a way that keeps no strong reference to it.
	 * You can remove it again manually by calling 
	 * {@link #removeWeakValueListener(ValueListener) removeWeakValueListener(listener)} or
	 * more efficiently by calling {@link #removeValueListener(ValueListener)} with the argument being
	 * the {@link ValueListener} returned by this method.
	 * @param listener The listener. You should keep a reference to it for as long as 
	 * it should remain registered.
	 * @return The ValueListener that was actually added, which may be {@code listener}
	 * or a different object. This is the reference you need to pass to
	 * {@link #removeValueListener(ValueListener)} in order to manually remove the listener.
	 * @see WeakValueListener
	 */
	public default ValueListener addWeakValueListener(ValueListener listener){
		WeakValueListener wrapper = new WeakValueListener(listener);
		addValueListener(wrapper);
		return wrapper;
	}
	/**
	 * A value class may implement this in order to manage its listeners using a
	 * {@link ListenerManager}.
	 * The abstract methods defined by ListenValue are forwarded to the {@link ListenerManager}
	 * returned by the {@link #getListenerManager()} method 
	 * @author bb
	 *
	 */
	static interface Managed extends ListenValue{
		/**
		 * Get the ListenerManager that the other method calls should be forwarded to
		 * @return
		 */
		public ListenerManager getListenerManager();
		@Override default
		void addValueListener(ValueListener cl) {
			getListenerManager().addValueListener(cl);
		}
		@Override default
		void removeValueListener(ValueListener cl) {
			getListenerManager().removeValueListener(cl);
		}
		@Override default
		boolean hasValueListener(ValueListener cl) {
			return getListenerManager().hasValueListener(cl);
		}
		@Override
		default void fireValueChange() {
			getListenerManager().fireValueChange();
		}
		@Override
		default void removeWeakValueListener(ValueListener wrapped) {
			getListenerManager().removeWeakValueListener(wrapped);
		}
	}
	/**
	 * Default implementation for managing all the listeners associated with a value.
	 * @author bb
	 *
	 */
	static class ListenerManager implements ListenValue{
		private final static Logger log=Logger.getLogger("ListenValue.ListenerManager");

		/**
		 * The object that will be the source of the generated events 
		 */
		private Object source;
		
		/**
		 * 
		 * @param source The object that will be the source of the generated events
		 */
		public ListenerManager(Object source) {
			this.source=source;
		}
		/**
		 * The {@link ListenerManager} itself will be the source of the generated events
		 */
		public ListenerManager() {
			this.source=this;
		}

		/**
		 * The {@link ValueListener}s that should run immediately in the Thread that triggered the event.
		 * This field is <code>null</code> until the first listener is added.
		 * @see #addValueListener(ValueListener)
		 * @see #removeValueListener(ValueListener)
		 * @see #hasValueListener(ValueListener)
		 * @see #fireValue(ValueEvent, boolean)
		 */
		HashSet<ValueListener> listeners;

		@Override
		public void addValueListener(ValueListener l){
			Objects.requireNonNull(l);
			//		if(l instanceof EagerValueListener)
			//			System.err.println("Warning: ValueListener wants to be eager");
			boolean success;
			synchronized (this) {
				if(listeners==null)
					listeners=new HashSet<>();
				success = listeners.add(l);
			}
			if(success)
				l.youWereAdded(this);
		}

		/**
		 * Remove a {@link ValueListener} that would run immediately in the thread that triggered the event.
		 * @param l
		 */
		@Override
		public void removeValueListener(ValueListener l){
			Objects.requireNonNull(l);
			boolean success;
			synchronized (this) {
				if(listeners==null)
					return;
				success = listeners.remove(l);
			}
			if(success)
				l.youWereRemoved(this);
		}


		@Override
		public synchronized boolean hasValueListener(ValueListener listener) {
			return listeners!=null && listeners.contains(listener);
		}
		/**
		 * Fire a newly generated {@link ValueEvent}
		 */
		@Override
		public final void fireValueChange() {
			fireValueChange(null);
		}
		/**
		 * Fire a {@link ValueEvent}. 
		 * @param ee The event, or <code>null</code> to generate a new one.
		 * @throws IllegalMonitorStateException
		 */
		public void fireValueChange(ValueEvent ee) {
			if(Thread.holdsLock(this))
				throw new IllegalMonitorStateException();
			ValueListener[] cla ;
			synchronized (this) {
				if(listeners == null || listeners.isEmpty())
					return;
				HashSet<ValueListener> ls = listeners;
				if(ls==null || ls.isEmpty())
					return;
				cla = (ValueListener[]) ls.toArray(new ValueListener[ls.size()]);
			}
			ValueEvent e=ee==null?new ValueEvent(source):ee;

			Arrays.sort(cla, ValueListener.COMPARE_BY_PRIORITY);
			for(ValueListener c: cla){
				try{
					c.valueChanged(e);
				}catch(Exception x){
					log.log(Level.WARNING, "Exception in ValueListener", x);
				}
			}		
		}
		@Override
		public Object getValueEventSource() {
			return source;
		}
		@Override
		public boolean isSource(MultiEvent e) {
			return e.isSource(getValueEventSource());
		}
		@Override
		public void removeWeakValueListener(ValueListener wrapped) {
			ValueListener removed=null;
			ArrayList<ValueListener> removedList=null;
			synchronized (this) {
				if(listeners==null)
					return;
				for(Iterator<ValueListener> i = listeners.iterator(); i.hasNext(); ) {
					ValueListener l = i.next();
					WeakValueListener wl = l.asWeakValueListener();
					if(wl==null) continue;
					if(wl.get()==null || wl.get()==wrapped) {
						i.remove();
						if(removed==null)
							removed = l;
						else {
							if(removedList==null)
								removedList=new ArrayList<>();
							removedList.add(l);
						}
					}
				}
			}
			if(removed!=null)
				removed.youWereRemoved(this);
			if(removedList!=null)
				for(ValueListener l: removedList)
					l.youWereRemoved(this);

		}
	}
	/**
	 * Test if this {@link ListenValue} is a source of the given {@link MultiEvent}.
	 * @see MultiEvent#isSource(Object) (Simply calls this method with {@code this} as the argument)
	 * @param e
	 * @return
	 */
	public default boolean isSource(MultiEvent e) {
		return e.isSource(this);
	}
	/**
	 * Remove a {@link WeakValueListener} based on the ordinary {@link ValueListener} that it wraps.
	 * @param wrapped
	 */
	public void removeWeakValueListener(ValueListener wrapped);
	
//	public void syncWithInternalMutex();
}
