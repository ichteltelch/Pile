package pile.aspect.bracket;

import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

import pile.interop.exec.StandardExecutors;
import pile.utils.StackTraceWrapper;

/**
 * A value bracket that wraps another value bracket and reports if its operations take too long.
 * @param <E>
 * @param <O>
 */
public class DeadlockDetectingBracket<E, O> implements ValueBracket<E, O> {
	/**
	 * This {@link Logger} is used by the default action to report stack traces.
	 */
	public static final Logger log = Logger.getLogger("DeadlockDetectingBracket");
	/**
	 * The {@link ValueBracket} to wrap.
	 */
	final ValueBracket<E, O> back;
	/**
     * The timeout in milliseconds.
     */
	long timeoutMillis;
	/**
	 * The {@link ScheduledExecutorService} to use for scheduling the reporting action.
	 */
	ScheduledExecutorService ses;
	/**
	 * The action to take when the timeout occurs.
	 */
	Consumer<? super Thread> action;
	/**
	 * Whether opening the bracket is a no-op.
	 */
	final boolean nopOpen;
	/**
     * Whether closing the bracket is a no-op.
     */
	final boolean nopClose;
	/**
	 *
	 * @param back The {@link ValueBracket} to wrap.
	 * @param timeout The timeout in milliseconds.
	 * @param ses The {@link ScheduledExecutorService} to use for scheduling the reporting action. Default is {@link StandardExecutors#delayed()}.
	 * @param action The action to take when the timeout occurs. Default is to log a stack trace by calling {@link #logStackTrace(Thread)}.
	 */
	public DeadlockDetectingBracket(ValueBracket<E, O> back, long timeout, ScheduledExecutorService ses, Consumer<? super Thread> action) {
        this.back = back;
        if(timeout<=0)
        	timeout = 3000;  // default timeout is 3 seconds
        this.timeoutMillis = timeout;
        if(ses==null)
        	ses=StandardExecutors.delayed();
        this.ses = ses;
        if(action==null)
        	action = this::logStackTrace;
        this.action = action;
        this.nopOpen = back.openIsNop();
        this.nopClose = back.closeIsNop();
    }
	@Override
	public boolean open(E value, O owner) {
		if(nopOpen)
			return true;
		Thread myThread = Thread.currentThread();
		Future<?> runAction = ses.schedule(() -> action.accept(myThread), timeoutMillis, TimeUnit.MILLISECONDS);
		try {
			return back.open(value, owner);
		}finally {
			runAction.cancel(true);
		}
	}
	/**
	 * Logs a warning with the stack trace of the given thread, using {@link #log}.
	 * @param monitorThis
	 */
	public void logStackTrace(Thread monitorThis) {
		log.log(Level.WARNING, "Stuck ValueBracket in thread "+monitorThis.getName()+"@"+monitorThis.getId(), new StackTraceWrapper(monitorThis.getStackTrace()));
	}
	@Override
	public boolean close(E value, O owner) {
		if(nopClose)
			return false;
		Thread myThread = Thread.currentThread();
		Future<?> runAction = ses.schedule(() -> action.accept(myThread), timeoutMillis, TimeUnit.MILLISECONDS);
		try {
			return back.close(value, owner);
		}finally {
			runAction.cancel(false);
		}
		
	}

	@Override
	public boolean isInheritable() {
		return back.isInheritable();
	}

	@Override
	public boolean openIsNop() {
		return nopOpen;
	}

	@Override
	public boolean closeIsNop() {
		return nopClose;
	}
	
	@Override
	public ValueBracket<E, O> detectStuck() {
		return this;
	}
	@Override
	public ValueBracket<E, O> dontDetectStuck() {
		return back;
	}
	public static class ValueOnly<V> extends DeadlockDetectingBracket<V, Object> 
	implements ValueOnlyBracket<V>{

		public ValueOnly(ValueOnlyBracket<V> back, long timeout, ScheduledExecutorService ses,
				Consumer<? super Thread> action) {
			super(back, timeout, ses, action);
		}
		@Override
		public ValueOnlyBracket<V> detectStuck() {
			return this;
		}
		@Override
		public ValueOnlyBracket<V> dontDetectStuck() {
			return (ValueOnlyBracket<V>)back;
		}
		
	}
	@Override
	public boolean canBecomeObsolete() {
		return back.canBecomeObsolete();
	}

}
