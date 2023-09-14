package pile.interop.wait;

import java.util.Date;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;

/**
 * A {@link WrappedCondition} wraps a {@link #back}ing {@link Condition}.
 * You can extend this class to add more functionality, of call
 * {@link #of(Condition) of}-to wrap an arbitrary {@link Condition} so it becomes
 * usable as a {@link WaitServiceUsingCondition}.  
 * @author bb
 *
 */
public abstract class WrappedCondition implements WaitServiceUsingCondition{

	public static WrappedCondition of(Condition back) {
		return new WrappedCondition(back) {	
			@Override public void signalAll(WaitService ws) { ws.signalAll(back); }
			@Override public void signal(WaitService ws) { ws.signal(back); }
		};
	}
	
	protected final Condition back;

	public WrappedCondition(Condition back) {
		this.back=back;
	}

	@Override
	public void await(WaitService ws) throws InterruptedException {
		ws.await(back);
	}

	@Override
	public void awaitUninterruptibly(WaitService ws) {
		ws.awaitUninterruptibly(back);
	}

	@Override
	public long awaitNanos(WaitService ws, long nanosTimeout) throws InterruptedException {
		return ws.awaitNanos(back, nanosTimeout);
	}

	@Override
	public boolean await(WaitService ws, long time, TimeUnit unit) throws InterruptedException {
		return ws.await(back, time, unit);
	}

	@Override
	public boolean awaitUntil(WaitService ws, Date deadline) throws InterruptedException {
		return ws.awaitUntil(back, deadline);
	}
	@Override
	public long awaitNanosUninterruptibly(WaitService ws, long nanos) {
		return ws.awaitNanosUninterruptibly(back, nanos);
	}
	
	@Override
	public boolean awaitUninterruptiblyUntil(WaitService ws, Date deadline) {
		return ws.awaitUninterruptiblyUntil(back, deadline);
	}

}