package pile.utils;

import java.lang.ref.WeakReference;

/**
 * A {@link WeakReference} that will run a given {@link Runnable} in a daemon 
 * thread when the reference becomes enqueued, 
 * usually for cleanup and detection of loose ends.
 * @author bb
 * @param <E>
 */
public class WeakCleanupWithRunnable<E> extends WeakCleanup<E> {

	Runnable handle;

	public WeakCleanupWithRunnable(E referent, Runnable handle, AbstractReferenceManager rm) {
		super(referent, rm);
		this.handle=handle;
	}
	public WeakCleanupWithRunnable(E referent, Runnable handle) {
		super(referent);
		this.handle=handle;
	}
	@Override
	public void run() {
		handle.run();
	}
	/**
	 * Change the {@link Runnable} that will be run 
	 * when the reference becomes weakly reachable
	 * @param newHandle
	 */
	public void setCleanupAction(Runnable newHandle) {
		handle=newHandle;
	}
	

}
