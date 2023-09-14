package pile.interop.wait;

import java.util.Date;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;

import pile.aspect.suppress.MockBlock;

/**
 * A WaitService defines a way to {@link Object#wait() wait}, 
 * {@link Object#notify() notify},
 * {@link Thread#sleep(long) sleep} and 
 * {@link Thread#interrupt()}. This may be useful for example for debugging
 * or for assigning a different meaning to the interruption mechanism.
 * 
 * WaitServices also abstract the methods of {@link Condition} so that
 * the waiting behavior of the {@link Condition} becomes compatible with the
 * {@link WaitService}'s semantics. If the {@link Condition} implements 
 * {@link WaitServiceUsingCondition}, the {@link WaitService} should delegate to
 * its {@link WaitService}-aware methods.
 * @author bb
 *
 */
public interface WaitService {
	
	
	
	
	void sleep(long millis) throws InterruptedException;
	void wait(Object monitor) throws InterruptedException;
	void wait(Object monitor, long millis) throws InterruptedException;
	long waitNanos(Object o, long nanosTimeout) throws InterruptedException;
	void interrupt(Thread t);
	boolean interrupted();
	boolean isInterrupted();
	void notify(Object monitor);
	void notifyAll(Object monitor);
	
	/**
	 * While the returned {@link MockBlock} is active, the current thread should not be interrupted natively unless the interruption
	 * is one that would cause an {@link InterruptedException} the be thrown from this {@link WaitService}'s a?wait.* or sleep methods.
	 * Interruptions that happen to the {@link Thread} while in that uninterruptible state are to be applied when it ends.
	 * This is needed to wrap calls to some library methods that abort when interrupted, but the {@link WaitService} may have repurposed interruptions 
	 * for a more general signaling mechanism.
	 * @return
	 */
	MockBlock noNonstandardInterrupts();
	
	default void sleep(long duration, TimeUnit unit) throws InterruptedException {
		sleep(unit.toMillis(duration));
	}
	default void wait(Object monitor, long duration, TimeUnit unit) throws InterruptedException {
		wait(monitor, unit.toMillis(duration));
	}
	default void interrupt(Thread t, InterruptedException cause) {
		interrupt(t);
	}
	default void interruptSelf() {
		interrupt(Thread.currentThread());
	}
	default void interruptSelf(InterruptedException cause) {
		interrupt(Thread.currentThread(), cause);
	}
	default void checkInterrupt() throws InterruptedException{
		if(interrupted())
			throw new InterruptedException();
	}
	default void clearInterrupted() {
		interrupted();
	}

	default void waitUninterruptibly(Object monitor) {
		InterruptedException interrupted = null;
		try {
			while(true) {
				try {
					wait(monitor);
					return;
				} catch (InterruptedException e) {
					interrupted = e;
				}
			}
		}finally {
			if(interrupted!=null)
				interruptSelf(interrupted);
		}
	}
	default void waitUninterruptibly(Object monitor, long millis) {
		long start = System.currentTimeMillis();
		InterruptedException interrupted = null;
		try {
			while(true) {
				long now = System.currentTimeMillis();
				long remaining = millis - (now - start);
				if(remaining<=0)
					return;
				try {
					wait(monitor, remaining);
					return;
				} catch (InterruptedException e) {
					interrupted = e;
				}
			}
		}finally {
			if(interrupted!=null)
				interruptSelf(interrupted);
		}
	}
	default long waitNanosUninterruptibly(Object monitor, long nanos) {
		long start = System.nanoTime();
		InterruptedException interrupted = null;
		try {
			while(true) {
				long now = System.nanoTime();
				long remaining = nanos - (now - start);
				if(remaining<=0)
					return remaining;
				try {
					waitNanos(monitor, remaining);
					now = System.nanoTime();
					remaining = nanos - (now - start);
					return remaining;
				} catch (InterruptedException e) {
					interrupted = e;
				}
			}
		}finally {
			if(interrupted!=null)
				interruptSelf(interrupted);
		}	
	}

	default void waitUninterruptibly(Object monitor, long duration, TimeUnit unit) {
		waitUninterruptibly(monitor, unit.toMillis(duration));
	}
	default void sleepUninterruptibly(long millis) {
		long start = System.currentTimeMillis();
		InterruptedException interrupted = null;
		try {
			while(true) {
				long now = System.currentTimeMillis();
				long remaining = millis - (now - start);
				if(remaining<=0)
					return;
				try {
					sleep(remaining);
				} catch (InterruptedException e) {
					interrupted = e;
				}
			}
		}finally {
			if(interrupted!=null)
				interruptSelf(interrupted);
		}
	}
	default void sleepUninterruptibly(long duration, TimeUnit unit) {
		sleepUninterruptibly(unit.toMillis(duration));
	}
	
	
	
	void await(Condition c) throws InterruptedException;
	void awaitUninterruptibly(Condition c) ;
	long awaitNanos(Condition c, long nanosTimeout) throws InterruptedException;
	default long awaitNanosUninterruptibly(Condition c, long nanosTimeout) {
		InterruptedException interrupted = null;
		long startNanos = System.nanoTime();
		try {
			while(true) {
				long now = System.nanoTime();
				long remaining = nanosTimeout - (now - startNanos);
				try {
					if(remaining>0)
						return awaitNanos(c, remaining);
				} catch (InterruptedException e) {
					interrupted = e;
				}
			}
		}finally {
			if(interrupted!=null)
				interruptSelf(interrupted);
		}
	}
	boolean await(Condition c, long timeout, TimeUnit unit) throws InterruptedException;
	default boolean awaitUninterruptibly(Condition c, long timeout, TimeUnit unit) {
		return awaitNanosUninterruptibly(c, unit.toNanos(timeout))>0;
	}
	boolean awaitUntil(Condition c, Date deadline) throws InterruptedException;
	default boolean awaitUninterruptiblyUntil(Condition c, Date deadline) {
		InterruptedException interrupted = null;
		try {
			while(true) {
				long remaining = deadline.getTime() - System.currentTimeMillis();
				try {
					return remaining>0 && await(c, remaining, TimeUnit.MILLISECONDS);
				} catch (InterruptedException e) {
					interrupted = e;
				}
			}
		}finally {
			if(interrupted!=null)
				interruptSelf(interrupted);
		}
	}
	void signal(Condition c) ;
	void signalAll(Condition c) ;

	
	
	
	
	default public void await(WaitServiceUsingCondition c) throws InterruptedException 
	{ c.await(this); }
	default public void awaitUninterruptibly(WaitServiceUsingCondition c) 
	{ c.awaitUninterruptibly(this); }
	default public long awaitNanos(WaitServiceUsingCondition c, long nanosTimeout) throws InterruptedException
	{ return c.awaitNanos(this, nanosTimeout); }
	default public long awaitNanosUninterruptibly(WaitServiceUsingCondition c, long nanosTimeout) 
	{ return c.awaitNanosUninterruptibly(this, nanosTimeout); }
	default public boolean await(WaitServiceUsingCondition c, long timeout, TimeUnit unit) throws InterruptedException
	{ return c.await(this, timeout, unit); }
	default public boolean awaitUninterruptibly(WaitServiceUsingCondition c, long timeout, TimeUnit unit) 
	{ return c.awaitUninterruptibly(this, timeout, unit); }
	default public boolean awaitUntil(WaitServiceUsingCondition c, Date deadline) throws InterruptedException
	{ return c.awaitUntil(this, deadline); }
	default public boolean awaitUninterruptiblyUntil(WaitServiceUsingCondition c, Date deadline) 
	{ return c.awaitUninterruptiblyUntil(this, deadline); }
	default public void signal(WaitServiceUsingCondition c)
	{ c.signal(this); }
	default	public void signalAll(WaitServiceUsingCondition c) 
	{ c.signalAll(this); }
	
	
	
	
	/**
	 * This {@link WaitService} implementation just delegates to the default implementation.
	 */
	public static final WaitService NATIVE = new WaitService() {
		@Override public void sleep(long millis) throws InterruptedException 
		{ Thread.sleep(millis);	}

		@Override public void wait(Object monitor) throws InterruptedException 
		{ monitor.wait(); }

		@Override public void wait(Object monitor, long millis) throws InterruptedException 
		{ if(millis>0) monitor.wait(millis); else checkInterrupt();}
		
		@Override
		public long waitNanos(Object o, long nanosTimeout) throws InterruptedException 
		{
			long start = System.nanoTime();
			if(nanosTimeout>0) 
				TimeUnit.NANOSECONDS.timedWait(o, nanosTimeout);
			else
				checkInterrupt();
			long end = System.nanoTime();
			return nanosTimeout - (end - start);
		}

		@Override public void interrupt(Thread t) 
		{ t.interrupt(); }

		@Override public boolean interrupted() 
		{ return Thread.interrupted(); }

		@Override public boolean isInterrupted() {
			if(Thread.interrupted()) {
				interruptSelf();
				return true;
			}
			return false;
		}

		@Override public void notify(Object monitor)  
		{ monitor.notify(); }

		@Override public void notifyAll(Object monitor)  
		{ monitor.notifyAll(); }

		@Override public void await(Condition c) throws InterruptedException 
		{ c.await(); }

		@Override
		public void awaitUninterruptibly(Condition c)  
		{ c.awaitUninterruptibly(); }

		@Override public long awaitNanos(Condition c, long nanosTimeout) throws InterruptedException 
		{ return c.awaitNanos(nanosTimeout); }

		@Override public boolean await(Condition c, long timeout, TimeUnit unit) throws InterruptedException 
		{ return c.await(timeout, unit); }

		@Override public boolean awaitUntil(Condition c, Date d) throws InterruptedException 
		{ return c.awaitUntil(d); }

		@Override public void signal(Condition c)  
		{ c.signal(); }

		@Override public void signalAll(Condition c)  
		{ c.signalAll(); }
		
		@Override public MockBlock noNonstandardInterrupts() 
		{ return MockBlock.NOP; }
	};
	/**
	 * This class provides the special handling for {@link WaitServiceUsingCondition}s. 
	 * If a {@link Condition} is not a {@link WaitServiceUsingCondition}, the corresponding
	 * method whose name starts with "__" is called.
	 * @author bb
	 *
	 */
	public interface Dispatch extends WaitService{

		
		
		@Override
		default public void await(Condition c) throws InterruptedException {
			if(c instanceof WaitServiceUsingCondition) {
				await((WaitServiceUsingCondition)c);
				return;
			}
			__await(c);
		}

		@Override
		default public void awaitUninterruptibly(Condition c) {
			if(c instanceof WaitServiceUsingCondition) {
				awaitUninterruptibly((WaitServiceUsingCondition)c);
				return;
			}
			__awaitUninterruptibly(c);
		}

		@Override
		default public long awaitNanos(Condition c, long nanosTimeout) throws InterruptedException{
			if(c instanceof WaitServiceUsingCondition) {
				return awaitNanos((WaitServiceUsingCondition)c, nanosTimeout);
			}
			return __awaitNanos(c, nanosTimeout);
		}

		@Override
		default public long awaitNanosUninterruptibly(Condition c, long nanosTimeout) {
			if(c instanceof WaitServiceUsingCondition) {
				return awaitNanosUninterruptibly((WaitServiceUsingCondition)c, nanosTimeout);
			}
			return __awaitNanosUninterruptibly(c, nanosTimeout);
		}

		@Override
		default public boolean await(Condition c, long timeout, TimeUnit unit) throws InterruptedException{
			if(c instanceof WaitServiceUsingCondition) {
				return await((WaitServiceUsingCondition)c, timeout, unit);
			}
			return __await(c, timeout, unit);
		}

		@Override
		default public boolean awaitUninterruptibly(Condition c, long timeout, TimeUnit unit) {
			if(c instanceof WaitServiceUsingCondition) {
				return awaitUninterruptibly((WaitServiceUsingCondition)c, timeout, unit);
			}
			return __awaitUninterruptibly(c, timeout, unit);
		}

		@Override
		default public boolean awaitUntil(Condition c, Date deadline) throws InterruptedException{
			if(c instanceof WaitServiceUsingCondition) {
				return awaitUntil((WaitServiceUsingCondition)c, deadline);
			}
			return __awaitUntil(c, deadline);
		}

		@Override
		default public boolean awaitUninterruptiblyUntil(Condition c, Date deadline) {
			if(c instanceof WaitServiceUsingCondition) {
				return awaitUninterruptiblyUntil((WaitServiceUsingCondition)c, deadline);
			}
			return __awaitUninterruptiblyUntil(c, deadline);
		}

		@Override
		default public void signal(Condition c) {
			if(c instanceof WaitServiceUsingCondition) {
				signal((WaitServiceUsingCondition)c);
				return;
			}
			__signal(c);
		}

		@Override
		default public void signalAll(Condition c) {
			if(c instanceof WaitServiceUsingCondition) {
				signalAll((WaitServiceUsingCondition)c);
				return;
			}
			__signalAll(c);
		}

		
		
		public void __await(Condition c) throws InterruptedException;
		public void __awaitUninterruptibly(Condition c);
		public long __awaitNanos(Condition c, long nanosTimeout) throws InterruptedException;
		public long __awaitNanosUninterruptibly(Condition c, long nanosTimeout);
		public boolean __await(Condition c, long timeout, TimeUnit unit) throws InterruptedException;
		public boolean __awaitUninterruptibly(Condition c, long timeout, TimeUnit unit);
		public boolean __awaitUntil(Condition c, Date deadline) throws InterruptedException;
		public boolean __awaitUninterruptiblyUntil(Condition c, Date deadline);
		public void __signal(Condition c);
		public void __signalAll(Condition c);
		
		
		
		

	}
	
	/**
	 * The standard {@link DebuggableWaitService} wrapper around the {@link #NATIVE} {@link WaitService}.
	 */
	public static final DebuggableWaitService DEBUGGABLE_NATIVE = new DebuggableWaitService(NATIVE);
	
	/**
	 * A {@link DebuggableWaitService} wraps another {@link WaitService} so as to 
	 * limit the time a waiting/sleeping {@link Thread}
	 * spends unscheduled. This makes debugging easier because debuggers 
	 * may not be able to inspect a permanently suspended {@link Thread} properly.
	 * @author bb
	 *
	 */
	public static final class DebuggableWaitService implements Dispatch {
		public final WaitService raw;
		private long periodicWakeupTime;
		public DebuggableWaitService(WaitService raw, long wakeUpMillis) {
			Objects.requireNonNull(raw);
			if(wakeUpMillis<1)
				throw new IllegalArgumentException("Maxiumum sleep time must at least 1 ms");
			this.raw = raw;
			periodicWakeupTime = wakeUpMillis;
		}
		public DebuggableWaitService(WaitService raw) {
			this(raw, 1000);
		}
		public DebuggableWaitService(WaitService raw, long wakeUpEvery, TimeUnit unit) {
			this(raw, TimeUnit.MILLISECONDS.convert(wakeUpEvery, unit));
		}
		public void setWakeUpTime(long millis) {
			if(millis<1)
				throw new IllegalArgumentException("Maxiumum sleep time must at least 1 ms");
			periodicWakeupTime = millis;
		}
		@Override public void sleep(long millis) throws InterruptedException { 
			long startTime = System.currentTimeMillis();
			while(true) {
				long now = System.currentTimeMillis();
				long remaining = millis - (now - startTime);
				if(remaining<=0)
					return;
				raw.sleep(Math.min(periodicWakeupTime, remaining));
			}
		}

		@Override public void wait(Object monitor) throws InterruptedException { 
			raw.wait(periodicWakeupTime);
		}

		@Override public void wait(Object monitor, long millis) throws InterruptedException 
		{ raw.wait(monitor, Math.min(millis, periodicWakeupTime)); }

		@Override public long waitNanos(Object monitor, long nanos) throws InterruptedException 
		{ return raw.waitNanos(monitor, Math.min(nanos, TimeUnit.MILLISECONDS.toNanos(periodicWakeupTime))); }

		@Override public void interrupt(Thread t) 
		{ raw.interrupt(t); }
		
		@Override
		public void interrupt(Thread t, InterruptedException cause)
		{ raw.interrupt(t, cause); }
		
		@Override
		public boolean interrupted() 
		{ return raw.interrupted(); }
		
		@Override
		public boolean isInterrupted() 
		{ return raw.isInterrupted(); }
		
		@Override public void interruptSelf() 
		{ raw.interruptSelf(); }

		@Override public void interruptSelf(InterruptedException cause) 
		{ raw.interruptSelf(cause); }
		
		@Override public void clearInterrupted() 
		{ raw.clearInterrupted(); }
		
		@Override public void notify(Object monitor)  
		{ raw.notify(monitor); }

		@Override public void notifyAll(Object monitor)  
		{ raw.notifyAll(monitor); }
		
		@Override
		public void waitUninterruptibly(Object monitor, long millis) {
			raw.waitUninterruptibly(monitor, Math.min(millis, periodicWakeupTime));
		}
		@Override public void sleepUninterruptibly(long millis) { 
			long startTime = System.currentTimeMillis();
			while(true) {
				long now = System.currentTimeMillis();
				long remaining = millis - (now - startTime);
				if(remaining<=0)
					return;
				raw.sleepUninterruptibly(Math.min(periodicWakeupTime, remaining));
			}
		}
		
		
		
		
		
		
		
		
		@Override
		public void await(Condition c) throws InterruptedException {
			if(c instanceof WaitServiceUsingCondition) {
				await((WaitServiceUsingCondition)c);
				return;
			}
			__await(c);
		}

		@Override
		public void awaitUninterruptibly(Condition c) {
			if(c instanceof WaitServiceUsingCondition) {
				awaitUninterruptibly((WaitServiceUsingCondition)c);
				return;
			}
			__awaitUninterruptibly(c);
		}

		@Override
		public long awaitNanos(Condition c, long nanosTimeout) throws InterruptedException{
			if(c instanceof WaitServiceUsingCondition) {
				return awaitNanos((WaitServiceUsingCondition)c, nanosTimeout);
			}
			return __awaitNanos(c, nanosTimeout);
		}

		@Override
		public long awaitNanosUninterruptibly(Condition c, long nanosTimeout) {
			if(c instanceof WaitServiceUsingCondition) {
				return awaitNanosUninterruptibly((WaitServiceUsingCondition)c, nanosTimeout);
			}
			return __awaitNanosUninterruptibly(c, nanosTimeout);
		}

		@Override
		public boolean await(Condition c, long timeout, TimeUnit unit) throws InterruptedException{
			if(c instanceof WaitServiceUsingCondition) {
				return await((WaitServiceUsingCondition)c, timeout, unit);
			}
			return __await(c, timeout, unit);
		}

		@Override
		public boolean awaitUninterruptibly(Condition c, long timeout, TimeUnit unit) {
			if(c instanceof WaitServiceUsingCondition) {
				return awaitUninterruptibly((WaitServiceUsingCondition)c, timeout, unit);
			}
			return __awaitUninterruptibly(c, timeout, unit);
		}

		@Override
		public boolean awaitUntil(Condition c, Date deadline) throws InterruptedException{
			if(c instanceof WaitServiceUsingCondition) {
				return awaitUntil((WaitServiceUsingCondition)c, deadline);
			}
			return __awaitUntil(c, deadline);
		}

		@Override
		public boolean awaitUninterruptiblyUntil(Condition c, Date deadline) {
			if(c instanceof WaitServiceUsingCondition) {
				return awaitUninterruptiblyUntil((WaitServiceUsingCondition)c, deadline);
			}
			return __awaitUninterruptiblyUntil(c, deadline);
		}

		@Override
		public void signal(Condition c) {
			if(c instanceof WaitServiceUsingCondition) {
				signal((WaitServiceUsingCondition)c);
				return;
			}
			__signal(c);
		}

		@Override
		public void signalAll(Condition c) {
			if(c instanceof WaitServiceUsingCondition) {
				signalAll((WaitServiceUsingCondition)c);
				return;
			}
			__signalAll(c);
		}

		
		
		@Override
		public void __await(Condition c) throws InterruptedException {
			raw.awaitNanos(c, TimeUnit.MILLISECONDS.toNanos(periodicWakeupTime));
		}
		@Override
		public void __awaitUninterruptibly(Condition c) {
			raw.awaitNanosUninterruptibly(c, TimeUnit.MILLISECONDS.toNanos(periodicWakeupTime));
		}
		@Override
		public long __awaitNanos(Condition c, long nanosTimeout) throws InterruptedException {
			long startNanos = System.nanoTime();
			raw.awaitNanos(c, Math.min(nanosTimeout, TimeUnit.MILLISECONDS.toNanos(periodicWakeupTime)));
			long endNanos = System.nanoTime();
			long elapsedNanos = endNanos - startNanos;
			long remainingNanos = nanosTimeout - elapsedNanos;
			return remainingNanos;
		}
		@Override
		public long __awaitNanosUninterruptibly(Condition c, long nanosTimeout) {
			long startNanos = System.nanoTime();
			raw.awaitNanosUninterruptibly(c, Math.min(nanosTimeout, TimeUnit.MILLISECONDS.toNanos(periodicWakeupTime)));
			long endNanos = System.nanoTime();
			long elapsedNanos = endNanos - startNanos;
			long remainingNanos = nanosTimeout - elapsedNanos;
			return remainingNanos;
		}
		@Override
		public boolean __await(Condition c, long timeout, TimeUnit unit) throws InterruptedException {
			long startNanos = System.nanoTime();
			long nanosTimeout = unit.toNanos(timeout);
			raw.awaitNanos(c, Math.min(nanosTimeout, TimeUnit.MILLISECONDS.toNanos(periodicWakeupTime)));
			long endNanos = System.nanoTime();
			long elapsedNanos = endNanos - startNanos;
			long remainingNanos = nanosTimeout - elapsedNanos;
			return remainingNanos>0;
		}
		@Override
		public boolean __awaitUninterruptibly(Condition c, long timeout, TimeUnit unit) {
			long startNanos = System.nanoTime();
			long nanosTimeout = unit.toNanos(timeout);
			raw.awaitNanosUninterruptibly(c, Math.min(unit.toNanos(timeout), TimeUnit.MILLISECONDS.toNanos(periodicWakeupTime)));
			long endNanos = System.nanoTime();
			long elapsedNanos = endNanos - startNanos;
			long remainingNanos = nanosTimeout - elapsedNanos;
			return remainingNanos>0;
		}	
		@Override
		public boolean __awaitUntil(Condition c, Date deadline) throws InterruptedException {
			long maxDeadline = System.currentTimeMillis() + periodicWakeupTime;
			if(deadline.getTime() > maxDeadline) {
				raw.awaitUntil(c, new Date(maxDeadline));
				return deadline.getTime() > System.currentTimeMillis();
			}else {
				return raw.awaitUntil(c, deadline);
			}
		}
		@Override
		public boolean __awaitUninterruptiblyUntil(Condition c, Date deadline) {
			long maxDeadline = System.currentTimeMillis() + periodicWakeupTime;
			if(deadline.getTime() > maxDeadline) {
				raw.awaitUninterruptiblyUntil(c, new Date(maxDeadline));
				return deadline.getTime() > System.currentTimeMillis();
			}else {
				return raw.awaitUninterruptiblyUntil(c, deadline);
			}
		}
		@Override
		public void __signal(Condition c) {
			raw.signal(c);
		}
		@Override
		public void __signalAll(Condition c) {
			raw.signalAll(c);
		}
		
		@Override public MockBlock noNonstandardInterrupts() 
		{ return raw.noNonstandardInterrupts(); }


	}
	/**
	 * Get the {@link WaitService} that should be used by the current thread.
	 * @return
	 */
	public static WaitService get() {
		ThreadLocal<WaitService> current = WaitServiceConfig.current;
		if(current==null)
			return WaitServiceConfig.globalDefault;
		WaitService ret = current.get();
		if(ret==null)
			return WaitServiceConfig.globalDefault;
		return ret;
	}
	/**
	 * Set the global default {@link WaitService}
	 * @param ws
	 */
	public static void setGlobalDefault(WaitService ws) {
		Objects.requireNonNull(ws);
		WaitServiceConfig.globalDefault = ws;
	}
	/**
	 * Set the {@link WaitService} that should be used by this {@link Thread} from now on
	 * until another is set or an enclosing {@link MockBlock} opened by
	 * {@link #withThreadLocalDefault(WaitService)} is closed.
	 * @param ws
	 */
	public static void setThreadLocalDefault(WaitService ws) {
		WaitServiceConfig.current().set(ws);
	}
	/**
	 * Set the {@link WaitService} that should be used by this {@link Thread} from now on.
	 * When the MockBlock is closed, the previously set {@link WaitService} will be used.
	 */
	public static MockBlock withThreadLocalDefault(WaitService ws) {
		ThreadLocal<WaitService> current = WaitServiceConfig.current();
		return new MockBlock() {
			WaitService prev;
			@Override
			protected void open() {
				prev = current.get();
				current.set(ws);				
			}		
			@Override
			protected void close_impl() {
				current.set(prev);				
			}
		};
	}

//	public static void setThreadLocalDefault_ignorable(WaitService ws) {
//		ThreadLocal<WaitService> current = WaitServiceConfig.current(ws);
//		if(current!=null)
//			current.set(ws);
//	}
//	public static MockBlock withThreadLocalDefault_ignorable(WaitService ws) {
//		return new MockBlock() {
//			WaitService prev;
//			@Override
//			protected void open() {
//				ThreadLocal<WaitService> current = WaitServiceConfig.current(ws);
//				if(current == null) {
//					prev = WaitServiceConfig.globalDefault;
//				} else {
//					prev = current.get();
//					WaitServiceConfig.current.set(ws);				
//				}
//			}		
//			@Override
//			protected void close_impl() {
//				ThreadLocal<WaitService> current = WaitServiceConfig.current(prev);
//				if(current != null)
//					WaitServiceConfig.current.set(prev);				
//			}
//		};
//	}

	
	
	
	
}
