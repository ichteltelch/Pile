package pile.aspect.listen;

import pile.aspect.suppress.SafeCloseable;

/**
 * Interface for "unregisterers" that can add {@link ValueListener}s to {@link ListenValue}s
 * and remember to remove them later when their {@link #run()}-method is called.
 * @author bb
 *
 */
public interface ValueListenenerUnregisterer extends Runnable, SafeCloseable{
	/**
	 * Add a {@link ValueListener} to the {@link ListenValue} and remember to remove it later
	 * when the {@link #run()}-method is called.
	 * @param c
	 * @param v
	 */
	public void add(ValueListener c, ListenValue v);
	/**
	 * Add a {@link ValueListener} to the {@link ListenValue} and remember to remove it later
	 * when the {@link #run()}-method is called. The {@link ListenValue#addWeakValueListener(ValueListener)
	 * method} is used so that the {@link ValueListener} can be garbage collected if it referenced nowhere else.
	 * @param c
	 * @param v
	 * @return
	 */
	public ValueListener addWeak(ValueListener c, ListenValue v);
	@Override default void close() {run();}
}
