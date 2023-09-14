package pile.utils;

import java.lang.ref.Reference;
import java.lang.ref.ReferenceQueue;
import java.util.logging.Level;
import java.util.logging.Logger;


/**
 * A {@link ReferenceQueue} of {@link Reference}s that are also {@link Runnable} together with a {@link Thread}
 * that polls the {@link ReferenceQueue} and runs the returned {@link Reference}s.
 * @author bb
 *
 */
public class DefaultReferenceManager implements AbstractReferenceManager{
	private final static Logger log=Logger.getLogger("DefaultReferenceManager");

	/**
	 * The reference queue
	 */
	ReferenceQueue<? super Object> rq=new ReferenceQueue<Object>();
	/**
	 * The worker thread. Is a daemon thread.
	 */
	volatile Thread worker;
	/**
	 * lock that synchronizes starting the worker
	 */
	Object lock=new Object();
	/**
	 * This is what the worker thread runs
	 */
	private Runnable workerRunnable=() -> {
		try{
			while(true){
				Runnable ref;
				try {
					ref = (Runnable) rq.remove();
				} catch (InterruptedException e) {
					log.warning("Who interrupted the ReferenceManager?");
					e.printStackTrace();
					continue;
				}
				safeRun(ref);
			}
		}finally{
			synchronized (lock) {
				if(worker==Thread.currentThread())
					worker=null;
			}
		}
	};
	/**
	 * Start the worker thread if it is not already running
	 */
	public void startWorker(){
		if(worker!=null)
			return;
		synchronized(lock) {
			if(worker!=null)
				return;
			worker=new Thread(workerRunnable);
			worker.setName("util.memory.ReferenceManager");
			worker.setDaemon(true);
			worker.start();
		}
	}
	void safeRun(Runnable r){
		try{
			r.run();
		}catch (Exception e) {
			log.log(Level.WARNING, "Exception in cleanup handler", e);
		}catch (Error e) {
			log.log(Level.SEVERE, "Error in cleanup handler", e);
			if(e instanceof UnknownError) throw e;
			if(e instanceof InternalError) throw e;
			if(e instanceof ThreadDeath) throw e;
			//recovery by restarting
			synchronized (lock) {
				worker=new Thread(workerRunnable);
				worker.setDaemon(true);
				worker.start();
			}
			throw e;
		}
	}


	@Override
	public ReferenceQueue<? super Object> getQueueAndStartWorker() {
		if(worker==null)
			startWorker();
		return rq;
	}
	@Override
	public void runIfWeak(Object o, Runnable r) {
		WeakCleanup.runIfWeak(o, r, this);
		
	}
	
	
}
