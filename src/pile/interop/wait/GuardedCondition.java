package pile.interop.wait;

import java.util.Date;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.function.BooleanSupplier;

/**
 * A condition that wraps another {@link Condition} and is aware of
 * the predicate that defines the semantics of the {@link Condition}.
 * This enables it to stop waiting only if the event waited for has happened
 * (or the timeout has elapsed or the thread has been interrupted).
 * Signaling can also be made conditional on the predicate being fulfilled.
 * @author bb
 *
 */
public class GuardedCondition extends WrappedCondition{
	final BooleanSupplier checker;
	final boolean alwaysSignal;
	public GuardedCondition(Condition back, BooleanSupplier check) {
		this(back, check, false);
	}
	public GuardedCondition(Condition back, BooleanSupplier check, boolean alwaysSignal) {
		super(back);
		this.checker = check;
		this.alwaysSignal = alwaysSignal;
	}
	public GuardedCondition(Lock lock, BooleanSupplier check) {
		this(lock.newCondition(), check);
	}
	public GuardedCondition(Lock lock, BooleanSupplier check, boolean alwaysSignal) {
		this(lock.newCondition(), check, alwaysSignal);

	}
	@Override
	public void await(WaitService ws) throws InterruptedException {
		while(!checker.getAsBoolean())
			ws.await(back);
	}
	@Override
	public void awaitUninterruptibly(WaitService ws) {
		InterruptedException interrupted = null;
		try {
			while(!checker.getAsBoolean()) {
				try {
					ws.await(back);
				}catch(InterruptedException x) {
					interrupted = x;
				}
			}
		}finally {
			if(interrupted!=null)
				ws.interruptSelf(interrupted);
		}
	}
	@Override
	public long awaitNanos(WaitService ws, long nanosTimeout) throws InterruptedException {
		long start = System.nanoTime();
		while (!checker.getAsBoolean()) {
			long now = System.nanoTime();
			long timeSpentWaiting = now - start;
			long timeLeft = nanosTimeout - timeSpentWaiting;
			if(timeLeft<=0)
				return timeLeft;
			long waitTime = timeLeft;
			ws.awaitNanos(back, waitTime);
		}
		long now = System.nanoTime();
		long timeSpentWaiting = now - start;
		long timeLeft = nanosTimeout - timeSpentWaiting;
		return timeLeft;
	}
	@Override
	public long awaitNanosUninterruptibly(WaitService ws, long nanosTimeout) {
		long start = System.nanoTime();
		InterruptedException interrupted = null;
		try {
			while (!checker.getAsBoolean()) {
				long now = System.nanoTime();
				long timeSpentWaiting = now - start;
				long timeLeft = nanosTimeout - timeSpentWaiting;
				if(timeLeft<=0)
					return timeLeft;
				long waitTime = timeLeft;
				try {
					ws.awaitNanos(back, waitTime);
				} catch (InterruptedException e) {
					interrupted = e;
				}
			}
		}finally {
			if(interrupted!=null)
				ws.interruptSelf(interrupted);
		}
		long now = System.nanoTime();
		long timeSpentWaiting = now - start;
		long timeLeft = nanosTimeout - timeSpentWaiting;
		return timeLeft;
		
	}
	@Override
	public boolean await(WaitService ws, long time, TimeUnit unit) throws InterruptedException {
		return awaitNanos(ws, TimeUnit.NANOSECONDS.convert(time, unit))>0;
	}
	@Override
	public boolean awaitUntil(WaitService ws, Date deadline) throws InterruptedException {
		while(!checker.getAsBoolean())
			if(!ws.awaitUntil(back, deadline))
				return false;
		
		return true;
	}
	@Override
	public boolean awaitUninterruptiblyUntil(WaitService ws, Date deadline) {
		InterruptedException interrupted = null;
		try {
			while(!checker.getAsBoolean()) {
				try {
					if(!ws.awaitUntil(back, deadline))
						return false;
				} catch (InterruptedException e) {
					interrupted = e;
				}
			}
		}finally {
			if(interrupted!=null)
				ws.interruptSelf(interrupted);
		}
		return true;
	}
	@Override
	public void signal(WaitService ws) {
		if(alwaysSignal || checker.getAsBoolean())
			ws.signal(back);
	}
	@Override
	public void signalAll(WaitService ws) {
		if(alwaysSignal || checker.getAsBoolean())
			ws.signalAll(back);		
	}
	public ObservableCondition observable() {
		return new ObservableCondition(this);
	}
}
