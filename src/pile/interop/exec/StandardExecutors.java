package pile.interop.exec;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

import pile.impl.Piles;
import pile.interop.wait.WaitService;

/**
 * This class is used to configure the {@link ExecutorService}s used by default by this
 * framework. If you want to change the defaults, you should set your own {@link ExecutorService}s
 * at startup.
 * For maximum performance, you should do that before loading the {@link Piles} class.
 * <p>
 * This class also contains some utility methods.
 * @author bb
 *
 */
public class StandardExecutors {
	private final static Logger log=Logger.getLogger("StandardExecutors");
	private static volatile ExecutorService unlimited;
	private static volatile ExecutorService limited;
	private static volatile ScheduledExecutorService delayed;
	/**
	 * Get the {@link ExecutorService} to be used for unlimited parallelity.
	 * If none has been set, a default is created.
	 * @return 
	 */
	public static ExecutorService unlimited() {
		ExecutorService local = unlimited;
		if(local==null) {
			synchronized (StandardExecutors.class) {
				local = unlimited;
				if(local==null) {
					unlimited = local = createDefaultUnlimited();
				}
			}
		}
		return local;
	}
	/**
	 * Get the {@link ExecutorService} to be used for limited parallelity.
	 * If none has been set, {@link ForkJoinPool#commonPool()} is used.
	 * @return
	 */
	public static ExecutorService limited() {
		ExecutorService local = limited;
		if(local==null) {
			synchronized (StandardExecutors.class) {
				local = limited;
				if(local==null) {
					limited = local = createDefaultLimited();
				}
			}
		}
		return local;
	}
	/**
	 * Get the {@link ScheduledExecutorService} to be used for delayed execution 
	 * with unlimited parallelity.
	 * If none has been set, a default is created.
	 * @return
	 */
	public static ScheduledExecutorService delayed() {
		ScheduledExecutorService local = delayed;
		if(local==null) {
			synchronized (StandardExecutors.class) {
				local = delayed;
				if(local==null) {
					delayed = local = createDefaultDelayed();
				}
			}
		}
		return local;
	}
	/**
	 * Set the {@link ExecutorService} to be used for unlimited parallelity.
	 * @param e
	 */
	public static void setUnlimited(ExecutorService e) {
		unlimited = e;
	}
	/**
	 * Set the {@link ExecutorService} to be used for limited parallelity.
	 * @param e
	 */
	public static void setLimited(ExecutorService e) {
		limited = e;
	}
	/**
	 * Set the {@link ScheduledExecutorService} to be used for delayed execution with unlimited parallelity.
	 * @param e
	 */
	public static void setDelayed(ScheduledExecutorService e) {
		delayed = e;
	}
	/**
	 * Use the same {@link ExecutorService} for both unlimited parallelity and delayed execution.
	 * @param e
	 */
	public static void setDelayedAndUnlimited(ScheduledExecutorService e) {
		unlimited = e;
		delayed = e;
	}
	private static ExecutorService createDefaultUnlimited() {
		long keepAlive=1000;
		ThreadPoolExecutor ret = new ThreadPoolExecutor(0, Integer.MAX_VALUE,
				keepAlive, TimeUnit.MILLISECONDS,
				new SynchronousQueue<Runnable>()) {
			@Override
			protected void afterExecute(Runnable r, Throwable t) {
				if(t!=null) {
					log.log(Level.WARNING, "Uncaught Throwable thrown from submitted task", t);
				}
			}
		};
		ret.setThreadFactory(DEFAULT_THREAD_FACTORY);
		return ret;
	}
	private static ScheduledExecutorService createDefaultDelayed() {
		ScheduledThreadPoolExecutor ret = new ScheduledThreadPoolExecutor(0);
		ret.setKeepAliveTime(1000, TimeUnit.MILLISECONDS);
		ret.setMaximumPoolSize(64);
		ret.setThreadFactory(DEFAULT_THREAD_FACTORY);
		return ret;
	}
	private static ExecutorService createDefaultLimited() {
		return ForkJoinPool.commonPool();
	}
	/**
	 * The {@link ThreadGroup} for {@link Thread}s created by the {@link #DEFAULT_THREAD_FACTORY}.
	 * This field is initialized to the {@link ThreadGroup} of the {@link Thread} that is loading this class.
	 */
	private static ThreadGroup defaultThreadGroup = Thread.currentThread().getThreadGroup();
	/**
	 * Set the {@link ThreadGroup} for {@link Thread}s created by the {@link #DEFAULT_THREAD_FACTORY}.
	 * @param tg
	 */
	public static void setDefaultThreadGroup(ThreadGroup tg) {
		Objects.requireNonNull(tg);
		defaultThreadGroup = tg;
	}
	/**
	 * Get the {@link ThreadGroup} for {@link Thread}s created by the {@link #DEFAULT_THREAD_FACTORY}.
	 * If none has been set using {@link #setDefaultThreadGroup(ThreadGroup)}, the
	 * {@link ThreadGroup} of the {@link Thread} that loaded this class is returned.
	 * @return
	 */
	public ThreadGroup getDefaultThreadGroup() {
		return defaultThreadGroup;
	}
	/**
	 * Used for numbering the {@link Thread}s created by the {@link #DEFAULT_THREAD_FACTORY}.
	 */
	private static final AtomicInteger threadCounter=new AtomicInteger(); 
	/**
	 * A simple {@link ThreadFactory}
	 */
	public static final ThreadFactory DEFAULT_THREAD_FACTORY = r -> {
		Thread t=new Thread(defaultThreadGroup, r);
		t.setUncaughtExceptionHandler((thread, e)->{
			log.log(Level.WARNING, "Uncaught Throwable thrown from submitted task", e);
		});
		t.setName("DefaultFactoryThread-"+threadCounter.incrementAndGet());
		
		return t;
	};
	/**
	 * Catches and logs any {@link Throwable}s thrown from the {@link Runnable#run() run} method of the {@link Runnable}
	 * @param r
	 * @return
	 */
	public static Throwable safe(Runnable r) {
		if(r==null)
			return null;
		try {
			r.run();
		}catch(Throwable t) {
			log.log(Level.INFO, "Isolated an error", t);
			return t;
		}
		return null;
	}
	/**
	 * Catches and logs any {@link Throwable}s thrown from the {@link Supplier#get() get} method of the {@link Supplier}
	 * @param r
	 * @return The result returned from {@link Supplier#get()} if it returned normally; <code>null</code> otherwise
	 */
	public static <T> T safe(Supplier<? extends T> r) {
		if(r==null)
			return null;
		try {
			return r.get();
		}catch(Throwable t) {
			log.log(Level.INFO, "Isolated an error", t);
			return null;
		}
	}
	/**
	 * 
	 * @param yield whether {@link Thread#yield()} should be called
	 * @throws InterruptedException if {@link Thread#interrupt()} returns <code>true</code>
	 */
	public static void checkInterrupt(boolean yield) throws InterruptedException {
		if(yield)Thread.yield();
		WaitService.get().checkInterrupt();
	}
	/**
	 * 
	 * @throws InterruptedException if {@link Thread#interrupt()} returns <code>true</code>
	 */
	public static void checkInterrupt() throws InterruptedException {
		WaitService.get().checkInterrupt();
	}
	/**
	 * Interrupt the current thread
	 * @see WaitService#interruptSelf()
	 * @see Thread#currentThread()
	 * @see Thread#interrupt()
	 */
	public static void interruptSelf() {
		WaitService ws = WaitService.get();
		ws.interruptSelf();
	}
	/**
	 * Test the interrupted status of the current thread without clearing it
	 * @see WaitService#isInterrupted()
	 * @see Thread#interrupted()
	 * @see Thread#currentThread()
	 * @see Thread#interrupt()
	 * @return
	 */
	public static boolean interrupted() {
		WaitService ws = WaitService.get();
		return ws.isInterrupted();
	}
	/**
	 * Run several things in parallel and wait until all are done.
	 * If one of them terminates abnormally, the others are cancelled 
	 * ({@link Future#cancel(boolean) Future.cancel(true)}) and the 
	 * offending {@link Exception} is thrown from this method.
	 * @param rs
	 * @throws InterruptedException
	 */
	public static void parallel(Runnable... rs) throws InterruptedException {
		ArrayList<Future<?>> fs=new ArrayList<>(rs.length);
		ExecutorService exec = unlimited();
		for(Runnable run: rs) {
			if(run!=null)
				fs.add(exec.submit(run));
		}
		joinAll(fs);
	}
	/**
	 * @see #parallel(Collection)
	 * @param toDo
	 * @throws InterruptedException
	 */
	public static void parallel(Collection<? extends Runnable> toDo) throws InterruptedException {
		ArrayList<Future<?>> fs=new ArrayList<>(toDo.size());
		ExecutorService exec = unlimited();
		for(Runnable run: toDo) {
			if(run!=null)
				fs.add(exec.submit(run));
		}
		joinAll(fs);
	}
	/**
	 * @see #parallel(Collection)
	 * @param toDo
	 * @param sync Run this job synchronously in this thread
	 * @throws InterruptedException
	 */
	public static void parallel(Collection<? extends Runnable> toDo, Runnable sync) throws InterruptedException {
		ArrayList<Future<?>> fs=new ArrayList<>(toDo.size());
		ExecutorService exec = unlimited();
		for(Runnable run: toDo) {
			if(run!=null)
				fs.add(exec.submit(run));
		}
		safe(sync);
		joinAll(fs);
	}
	/**
	 * Wait until all {@link Future}s are done.
	 * If an {@link Exception} happens, all {@link Future}s will be cancelled.
	 * @param chunks
	 * @throws InterruptedException
	 */
	public static void joinAll(Collection<? extends Future<?>> chunks) throws InterruptedException {
		try {
			for(Future<?> f: chunks)
				f.get();
		} catch (ExecutionException e) {
			throwCause(e);
		}finally {
			for(Future<?> f: chunks)
				if(f!=null)
					f.cancel(true);	
		}
	}
	/**
	 * Wait until all {@link Future}s are done.
	 * If an {@link Exception} happens, all {@link Future}s will be cancelled.
	 * @param chunks
	 * @throws InterruptedException
	 */
	public static void joinAll(Future<?>... chunks) throws InterruptedException {
		try {
			for(Future<?> f: chunks)
				if(f!=null)
					f.get();
		} catch (ExecutionException e) {
			throwCause(e);
		}finally {
			for(Future<?> f: chunks)
				if(f!=null)
					f.cancel(true);	
		}
	}
	/**
	 * This throws the exceptions cause if it is either a {@link RuntimeException} or an {@link Error}
	 * @param <T>
	 * @param e
	 * @return
	 * @throws IllegalArgumentException if the cause is <code>null</code> or a checked {@link Exception}
	 */
	public static <T> T throwCause(Exception e) {
		Throwable cause = e.getCause();
		if(cause instanceof RuntimeException)
			throw (RuntimeException)cause;
		if(cause instanceof Error)
			throw (Error)cause;
		else if(cause==null) 
			throw new IllegalArgumentException("throwCause(): Argument has no cause", e);
		else 
			throw new IllegalArgumentException("throwCause(): Cause of the argument is checked", e);
	}
}
