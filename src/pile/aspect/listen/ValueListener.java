package pile.aspect.listen;

import java.util.Comparator;
import java.util.concurrent.ExecutorService;
import java.util.function.Consumer;

import pile.aspect.listen.RateLimitedValueListener.MultiEvent;
import pile.aspect.transform.TransformValueEvent;
import pile.interop.exec.StandardExecutors;
import pile.utils.IdentityComparator;

/**
 * An object that runs event handler code when a {@link ValueEvent} is received.
 * @author bb
 *
 */
@FunctionalInterface
public interface ValueListener {

	/**
	 * Compare objects according to the value their {@link #priority()} method returns.
	 */
	public static final Comparator<? super ValueListener> COMPARE_BY_PRIORITY=(a, b)->{
		int ap = a.priority();
		int bp = b.priority();
		return Integer.compare(ap, bp);
	};
	public static final Comparator<? super ValueListener> COMPARE_BY_PRIORITY_AND_IDENTITY =
			COMPARE_BY_PRIORITY.thenComparing(IdentityComparator.INST);
	
	/**
	 * Event handler code goes here
	 * @param e
	 */
	public void valueChanged(ValueEvent e);
	/**
	 * Make a {@link ValueListener} with the same handler code, but different {@link #priority() priority}
	 * @param prio
	 * @return
	 */
	default public ValueListener withPrio(int prio) {
		return new PrioWrappedValueListener(this, prio);
	}
	/**
	 * Make a {@link ValueListener} with the same handler code as {@code vl}, b
	 * ut different {@link #priority() priority}
	 * @param prio
	 * @return
	 */
	public static ValueListener withPrio(int prio, ValueListener vl) {
		return vl.withPrio(prio);
	}
	/**
	 * Called by a {@link ListenValue} when this object has been added to it as a {@link ValueListener}
	 * @param toThis
	 */
	default public void youWereAdded(ListenValue toThis) {}
	/**
	 * Called by a {@link ListenValue} when this object has been removed from its set of 
	 * {@link ValueListener}s
	 * @param toThis
	 */
	default public void youWereRemoved(ListenValue fromThis) {};
	
	/**
	 * Make a {@link ValueListener} that runs its event handler code at a limited rate, accumulating
	 * the sources of events from which {@link ValueEvent}s were received in between runs.
	 * @see RateLimitedValueListener
	 * @param coldStartTime
	 * @param coolDownTime
	 * @param startCoolingBefore
	 * @param run
	 * @return
	 */
	public static RateLimitedValueListener rateLimited(
			long coldStartTime, 
			long coolDownTime, 
			boolean startCoolingBefore,
			Consumer<? super MultiEvent> run) {
		return RateLimitedValueListener.wrap(coldStartTime, coolDownTime, startCoolingBefore, false, run);
	}
	/**
	 * Make a {@link ValueListener} that runs its event handler code at a limited rate, accumulating
	 * the sources of events from which {@link ValueEvent}s were received in between runs.
	 * @see RateLimitedValueListener
	 * @param coldStartTime
	 * @param coolDownTime
	 * @param startCoolingBefore
	 * @param run
	 * @return
	 */
	public static RateLimitedValueListener rateLimited(
			long coldStartTime, 
			long coolDownTime, 
			Consumer<? super MultiEvent> run) {
		return RateLimitedValueListener.wrap(coldStartTime, coolDownTime, false, false, run);
	}
	/**
	 * Make a {@link ValueListener} that runs its event handler code at a limited rate, ignoring
	 * the sources of events.
	 * @see RateLimitedValueListener
	 * @param coldStartTime
	 * @param coolDownTime
	 * @param startCoolingBefore
	 * @param run
	 * @return
	 */
	public static RateLimitedValueListener rateLimited(
			long coldStartTime, 
			long coolDownTime, 
			boolean startCoolingBefore,
			Runnable run) {
		return RateLimitedValueListener.wrap(coldStartTime, coolDownTime, startCoolingBefore, false, run);
	}
	/**
	 * Make a {@link ValueListener} that runs its event handler code at a limited rate, ignoring
	 * the sources of events.
	 * @see RateLimitedValueListener
	 * @param coldStartTime
	 * @param coolDownTime
	 * @param startCoolingBefore
	 * @param run
	 * @return
	 */
	public static RateLimitedValueListener rateLimited(
			long coldStartTime, 
			long coolDownTime, 
			Runnable run) {
		return RateLimitedValueListener.wrap(coldStartTime, coolDownTime, false, false, run);
	}
	/**
	 * Wrap a given ValueListener so that it runs its event handler code asynchronously
	 * using the given {@link ExecutorService}
	 * @param exec
	 * @param l
	 * @return
	 */
	public static ValueListener async(ExecutorService exec, ValueListener l) {
		return e->exec.execute(()->l.valueChanged(e));
	}
	/**
	 * Wrap a given ValueListener so that it runs its event handler code asynchronously
	 * using the default {@link ExecutorService} {@link StandardExecutors#unlimited()}
	 * @param l
	 * @return
	 */
	public static ValueListener async(ValueListener l) {
		return async(StandardExecutors.unlimited(), l);
	}
	/**
	 * Defines how early this Listener should run. Smaller priorities run sooner.
	 * The default implementation gives a priority value of 0 .
	 * The priority must not change!
	 * @return
	 */
	default public int priority() {
		return 0;
	}
	/**
	 * @return A {@link ValueListener} like this one, but it ignores {@link TransformValueEvent}s
	 */
	public default ValueListener ignoreTransformEvents() {
		return ignoreTransformEvents(this);
	}
	/**
	 * @return A {@link ValueListener} like the argument, but it ignores TransformValueEvents
	 */
	public static ValueListener ignoreTransformEvents(ValueListener self) {
		return new TransformValueEventIgnoringValueListener(self);
	}
	/**
	 * Cast this {@link ValueListener} to {@link WeakValueListener}, or return <code>null</code>
	 * if it isn't one.
	 * @return
	 */
	public default WeakValueListener asWeakValueListener() {
		return null;
	}
	/**
	 * Run this {@link ValueListener} immediately with a <code>null</code> event, and in another thread
	 */
	default void runImmediately() {
		runImmediately(false);
	}
	/**
	 * Run this {@link ValueListener} immediately with a <code>null</code> event.
	 */
	default void runImmediately(boolean inThisThread) {
		if(inThisThread) {
			valueChanged(null);
		} else {
			StandardExecutors.unlimited().execute(()->valueChanged(null) );
		}
	}
}
