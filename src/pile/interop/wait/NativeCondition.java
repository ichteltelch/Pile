package pile.interop.wait;

import java.util.Date;
import java.util.concurrent.TimeUnit;

/**
 * This class wraps an Object's monitor. It uses a {@link WaitService}
 * for the actual waiting.
 * @author bb
 *
 */
public class NativeCondition implements WaitServiceUsingCondition{
	final Object o;
	public NativeCondition(Object o) {
		this.o = o;
	}
	@Override
	public void await(WaitService ws) throws InterruptedException {
		ws.wait(o);
	}
	@Override
	public void awaitUninterruptibly(WaitService ws) {
		ws.waitUninterruptibly(o);
	}
	@Override
	public long awaitNanos(WaitService ws, long nanosTimeout) throws InterruptedException {
		return ws.waitNanos(o, nanosTimeout);

	}
	@Override
	public boolean await(WaitService ws, long time, TimeUnit unit) throws InterruptedException {
		long start = System.nanoTime();
		ws.wait(o, time, unit);
		long end = System.nanoTime();
		return unit.toNanos(time) - (end - start) > 0;
	}
	@Override
	public boolean awaitUntil(WaitService ws, Date deadline) throws InterruptedException {
		Date start = new Date();
		if(start.after(deadline)) {
			ws.checkInterrupt();
			return true;
		}
		long waitTime = deadline.getTime() - start.getTime();
		ws.wait(o, waitTime);
		Date end = new Date();
		return !end.after(deadline);
	}
	@Override
	public void signal(WaitService ws) {
		ws.notify(o);
	}
	@Override
	public void signalAll(WaitService ws) {
		ws.notifyAll(o);
	}
	public ObservableCondition observable() {
		return new ObservableCondition(this);
	}
	@Override
	public long awaitNanosUninterruptibly(WaitService ws, long nanos) {
		return ws.waitNanosUninterruptibly(o, nanos);
	}
	@Override
	public boolean awaitUninterruptiblyUntil(WaitService ws, Date deadline) {
		Date start = new Date();
		if(start.after(deadline)) {
			return true;
		}
		long waitTime = deadline.getTime() - start.getTime();
		ws.waitUninterruptibly(o, waitTime);
		Date end = new Date();
		return !end.after(deadline);	
	}

}
