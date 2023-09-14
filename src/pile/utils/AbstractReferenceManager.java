package pile.utils;

import java.lang.ref.Reference;
import java.lang.ref.ReferenceQueue;
import java.util.function.Supplier;


/**
 * A {@link ReferenceQueue} of {@link Reference}s that are also {@link Runnable} 
 * together with a {@link Thread}
 * that polls the {@link ReferenceQueue} and runs the returned {@link Reference}s.
 * @author bb
 *
 */
public interface AbstractReferenceManager extends Supplier<ReferenceQueue<? super Object>>{
	static class __Privates{
		/**
		 * The global default {@link ReferenceQueue}
		 */
		private static volatile Supplier<? extends ReferenceQueue<? super Object>> STD;
		
	}
	/**
	 * Set the Supplier yields the standard {@link ReferenceQueue} instance
	 * @param r
	 */
	public static void setStd(Supplier<? extends ReferenceQueue<? super Object>> r) {
		__Privates.STD=r;
	}

	
	/**
	 * 
	 * @return a Supplier that yields the standard {@link ReferenceQueue} instance,
	 * lazily initializing it to be a
	 * {@link DefaultReferenceManager} if it was not {@link #setStd(Supplier) set} before.
	 */
	public static Supplier<? extends ReferenceQueue<? super Object>> Std() {
		Supplier<? extends ReferenceQueue<? super Object>> local = __Privates.STD;
		if(local==null) {
			synchronized (__Privates.class) {
				local = __Privates.STD;
				if(local==null) {
					__Privates.STD = local = new DefaultReferenceManager();
				}				
			}
		}
		return local;
	}
//	private final static Logger log=Logger.getLogger("ReferenceManager");



	/**
	 * Run the given {@link Runnable} if the given {@link Object} becomes weakly reachable
	 * @param o
	 * @param r
	 */
	public void runIfWeak(Object o, Runnable r);

	/**
	 * Get the {@link ReferenceQueue} and ensure that 
	 * the worker thread that polls the queue is running.
	 * @return
	 */
	public ReferenceQueue<? super Object> getQueueAndStartWorker();
	/**
	 * The default implementation calls the {@link #getQueueAndStartWorker()}
	 */
	@Override
	default ReferenceQueue<? super Object> get() {
		return getQueueAndStartWorker();
	}
	
}
