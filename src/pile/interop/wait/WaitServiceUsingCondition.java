package pile.interop.wait;

import java.util.Date;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;

/**
 * A condition that uses a {@link WaitService} for all its methods.
 * For each method of {@link Condition}, there is a method that additionally 
 * has a {@link WaitService}
 * as its first parameter. The methods of {@link Condition} get default implementations
 * that delegate to these using the {@link WaitService#get() injected} {@link WaitService}.
 * @author bb
 *
 */
public interface WaitServiceUsingCondition extends Condition{

	@Override
	default public void await() throws InterruptedException {
		await(WaitService.get());
	}
	public void await(WaitService ws) throws InterruptedException;

	@Override
	default public void awaitUninterruptibly() {
		awaitUninterruptibly(WaitService.get());
	}
	public void awaitUninterruptibly(WaitService ws);

	@Override
	default public long awaitNanos(long nanosTimeout) throws InterruptedException {
		return awaitNanos(WaitService.get(), nanosTimeout);
	}
	public long awaitNanos(WaitService ws, long nanosTimeout) throws InterruptedException;

	@Override
	default public boolean await(long time, TimeUnit unit) throws InterruptedException {
		return await(WaitService.get(), time, unit);
	}
	public boolean await(WaitService ws, long time, TimeUnit unit) throws InterruptedException;

	@Override
	default public boolean awaitUntil(Date deadline) throws InterruptedException {
		return awaitUntil(WaitService.get(), deadline);
	}
	public boolean awaitUntil(WaitService ws, Date deadline) throws InterruptedException;

	@Override
	default public void signal() {
		signal(WaitService.get());
	}
	public void signal(WaitService ws);
	
	@Override
	default public void signalAll() {
		signalAll(WaitService.get());
	}
	public void signalAll(WaitService ws);
	public long awaitNanosUninterruptibly(WaitService ws, long nanos);
	public boolean awaitUninterruptiblyUntil(WaitService ws, Date deadline);
	default public boolean awaitUninterruptibly(WaitService ws, long time, TimeUnit unit) {
		return awaitNanosUninterruptibly(ws, unit.toNanos(time))>0;
	}

}
