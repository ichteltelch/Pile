package pile.utils;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

import pile.interop.exec.StandardExecutors;
import pile.interop.wait.GuardedCondition;
import pile.interop.wait.NativeCondition;
import pile.interop.wait.WaitService;

/**
 * A facility where {@link Runnable}s can be enqueued to run sequentially.
 * @author bb
 *
 */
public class SequentialQueue extends AbstractExecutorService{
	private final static Logger log=Logger.getLogger("SequentialQueue");

	Future<?> queueWorkerFuture;
	Thread queueWorkerThread;
	ArrayDeque<Runnable> q;
	private String name;
	Runnable afterJob;
	ExecutorService exa;
	final WaitService ws;
	/**
	 * Make a new {@link SequentialQueue}
	 * @param name Name of the queue.
	 */
	public SequentialQueue(String name) {
		this(name, null, null, null);
	}
	/**
	 * Make a new {@link SequentialQueue}.
	 * @param name Name of the queue.
	 * @param exa The {@link ExecutorService} to use. If this is omitted, the 
	 * {@link ExecutorService} will be obtained from {@link StandardExecutors#unlimited()} 
	 * each time it is needed.
	 */
	public SequentialQueue(String name, ExecutorService exa) {
		this(name, null, exa, null);
	}
	/**
	 * Make a new {@link SequentialQueue}.
	 * @param name Name of the queue.
	 * @param afterJob will run this after each completed job
	 */
	public SequentialQueue(String name, Runnable afterJob) {
		this(name, afterJob, null, null);
	}
	/**
	 * Make a new {@link SequentialQueue}.
	 * @param name Name of the queue.
	 * @param afterJob will run this after each completed job
	 * @param exa The {@link ExecutorService} to use. If this is omitted, the 
	 * {@link ExecutorService} will be obtained from {@link StandardExecutors#unlimited()} 
	 * each time it is needed. 
	 */
	public SequentialQueue(String name, Runnable afterJob, ExecutorService exa) {
		this(name, afterJob, exa, null);
	}
	/**
	 * Make a new {@link SequentialQueue}.
	 * @param name Name of the queue.
	 * @param ws The {@link WaitService} to use. 
	 * If this is omitted, the {@link WaitService} will be obtained from {@link WaitService#get()}
	 * in the constructor. 
	 */
	public SequentialQueue(String name, WaitService ws) {
		this(name, null, null, ws);
	}
	/**
	 * Make a new {@link SequentialQueue}.
	 * @param name Name of the queue.
	 * @param exa The {@link ExecutorService} to use. If this is omitted, the 
	 * {@link ExecutorService} will be obtained from {@link StandardExecutors#unlimited()} 
	 * each time it is needed.
	 * @param ws The {@link WaitService} to use. If this is omitted, the 
	 * {@link WaitService} will be obtained from {@link WaitService#get()} 
	 * in the constructor. 
	 */
	public SequentialQueue(String name, ExecutorService exa, WaitService ws) {
		this(name, null, exa, ws);
	}
	/**
	 * Make a new {@link SequentialQueue}.
	 * @param name Name of the queue.
	 * @param afterJob will run this after each completed job
	 * @param ws The {@link WaitService} to use. If this is omitted, the {@link WaitService}
	 * will be obtained from {@link WaitService#get()} in the constructor. 
	 */
	public SequentialQueue(String name, Runnable afterJob, WaitService ws) {
		this(name, afterJob, null, ws);
	}
	/**
	 * Make a new {@link SequentialQueue}.
	 * @param name Name of the queue.
	 * @param afterJob will run this after each completed job
	 * @param exa The {@link ExecutorService} to use. If this is omitted, the {@link ExecutorService}
	 * will be obtained from {@link StandardExecutors#unlimited()} each time it is needed.
	 * @param ws The {@link WaitService} to use. If this is omitted, the {@link WaitService}
	 * will be obtained from {@link WaitService#get()} in the constructor. 
	 */
	public SequentialQueue(String name, Runnable afterJob, ExecutorService exa, WaitService ws) {
		this.name = name;
		this.afterJob = afterJob;
		this.exa = exa;
		this.ws = ws==null?WaitService.get():ws;
	}
	/**
	 * Submit a job.
	 * @param task
	 */
	synchronized public void enqueue(Runnable task) {
		if(closed)
			return;
		if(q==null)
			q=new ArrayDeque<>();
		q.add(task);
		WaitService.get().notifyAll(this);
		if(queueWorkerFuture==null) {
			try {
				if(exa==null)
					exa = StandardExecutors.unlimited();
				queueWorkerFuture = exa.submit(() -> {
					ArrayDeque<Runnable> queue = q;
					Thread ct = Thread.currentThread();
					String oldName = ct.getName();
					Future<?> self;
					synchronized (this) {
						self = queueWorkerFuture;
						if(self==null)
							return;
						queueWorkerThread = Thread.currentThread();
					}
					try {
						ct.setName("SequentialQueue worker: "+name);
						while(true) {
							Runnable r=null;
							try {
								int nopTimes=0;
								synchronized(this) {
									while(queue.isEmpty()) {
										if(nopTimes>2) {
											assert queueWorkerFuture==self;
											if(queueWorkerFuture==self) {
												queueWorkerFuture=null;
												queueWorkerThread=null;
											}
											return;
										}
										WaitService.get().wait(this, 1000);
										nopTimes++;
									}
									r = queue.poll();
								}
							} catch (InterruptedException e) {
								//							e.printStackTrace();
							}
							
							ws.clearInterrupted();
							if(r!=null) {
								//							System.out.println("queue length: "+queue.size());
								StandardExecutors.safe(r);
								StandardExecutors.safe(afterJob);
							}
						}
					}catch(RuntimeException|Error x){
						log.log(Level.WARNING, "", x);
					}finally {
						synchronized(this) {
							if(queueWorkerFuture==self) {
								queueWorkerFuture=null;
								queueWorkerThread=null;
							}
						}
						ct.setName(oldName);

					}
				});
			}finally {

			}
		}


	}
	/**
	 * Wait until all currently queued jobs have been executed
	 * @throws InterruptedException
	 */
	public void sync() throws InterruptedException {
		syncEnqueue(Functional.NOP);
	}
	public void syncOrWaitUntilShorter(int limit) throws InterruptedException {
		syncEnqueue(Functional.NOP, limit);
	}

	/**
	 * Wait several times until all jobs queued at the start of each wait have been executed.
	 * 
	 * @param count number of repetitions
	 * @throws InterruptedException
	 */
	public void sync(int count) throws InterruptedException {
		for(int i=0; i<count; ++i)
			syncEnqueue(Functional.NOP);
	}

	/**
	 * Enqueue a job and wait until it is done
	 * @param runThis
	 * @throws InterruptedException
	 */
	public void syncEnqueue(Runnable runThis) throws InterruptedException {
		class Wrapper implements Runnable{
			boolean ran = false;
			@Override
			public void run() {
				StandardExecutors.safe(runThis);
				synchronized (this) {
					ran = true;
					WaitService.get().notifyAll(this);
				}

			}
		}
		Wrapper w = new Wrapper();
		enqueue(w);
		synchronized (w) {
			while(!w.ran)
				WaitService.get().wait(w, 1000);
		}

	}
	/**
	 * Enqueue a job and wait until it is done or the queue is shorter than the given limit
	 * @param runThis
	 * @throws InterruptedException
	 */
	public void syncEnqueue(Runnable runThis, int limit) throws InterruptedException {
		class Wrapper implements Runnable{
			boolean ran = false;
			@Override
			public void run() {
				StandardExecutors.safe(runThis);
				synchronized (SequentialQueue.this) {
					ran = true;
					WaitService.get().notifyAll(SequentialQueue.this);
				}

			}
		}
		Wrapper w = new Wrapper();
		enqueue(w);
		synchronized (this) {
			while(!w.ran && q.size()>limit)
				WaitService.get().wait(this);
		}

	}
	/**
	 * Test whether the current thread is currently the worker {@link Thread} of this {@link SequentialQueue}
	 * @return
	 */
	public synchronized boolean isQueueWorkerThread() {
		return Thread.currentThread()==queueWorkerFuture;
	}
	/**
	 * Discard all currently queued jobs
	 */
	public synchronized void clearQueue() {
		if(q!=null)
			q.clear();
	}
	/**
	 * Interrupt the worker {@link Thread} of this {@link SequentialQueue}
	 */
	public synchronized void interrupt() {
		if(queueWorkerThread!=null)
			ws.interrupt(queueWorkerThread);
	}
	/**
	 * 
	 * @return The future that represents the worker {@link Thread} of this {@link SequentialQueue}
	 */
	public synchronized Future<?> getQueueWorker() {
		return queueWorkerFuture;
	}
	boolean closed;
	@Override
	public synchronized void shutdown() {
		closed = true;
		if(queueWorkerFuture!=null)
			queueWorkerFuture.cancel(true);
		notifyAll();

	}
	@Override
	public synchronized List<Runnable> shutdownNow() {
		closed = true;
		if(queueWorkerFuture!=null)
			queueWorkerFuture.cancel(true);
		ArrayList<Runnable> ret = new ArrayList<>(q);
		q.clear();
		notifyAll();
		return ret;
	}
	@Override
	public boolean isShutdown() {
		return closed;
	}
	@Override
	public boolean isTerminated() {
		return closed && queueWorkerFuture==null && (q==null || q.isEmpty()); 
	}
	@Override
	public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
		ws.await(
				new GuardedCondition(new NativeCondition(this), this::isTerminated),
				timeout, unit
				);
		return isTerminated();
	}
	@Override
	public void execute(Runnable command) {
		enqueue(command);
	}

}