package pile.utils.defer;

import java.util.ArrayList;

import pile.aspect.suppress.Suppressor;
import pile.interop.exec.StandardExecutors;

public class DefererImpl implements Deferrer {
	static final boolean DEBUG = false;
	int shouldBeDeferring;
	volatile int hasStartedRunningDeferred;
	final DeferrerQueue q;
	public DefererImpl(DeferrerQueue q) {
		this.q = q;
	}
	ArrayList<Throwable> trace = DEBUG? new ArrayList<Throwable>() : null;
	
	protected void runDeferredIfNotDeferring() {
		while(shouldBeDeferring==0 && hasStartedRunningDeferred==0 && !isQueueEmpty()) {
			boolean decremented = false;
			try {
				hasStartedRunningDeferred++;
				while(shouldBeDeferring==0) {
					Runnable r = pollQueue();
					if(q.isQueueEmpty()) {
						decremented = true;
						hasStartedRunningDeferred--;
					}
					if(r!=null)
						StandardExecutors.safe(r);
					else
						break;
					if(decremented)
						break;
				}
			}finally {
				if(!decremented)
					hasStartedRunningDeferred--;
			}
		}
	}

	protected Runnable pollQueue() {
		return q.pollQueue();
	}

	protected boolean isQueueEmpty() {
		return q.isQueueEmpty();
	}
	protected void enqueue(Runnable r) {
		if(r==null)
			return;
		q.enqueue(r);
	}
	
	@Override
	public void run(Runnable r) {
		if(isDeferring()) {
			enqueue(r);
			runDeferredIfNotDeferring();
			return;
		}
		runDeferredIfNotDeferring();
		try {
			//hasStartedRunningDeferred++;
			StandardExecutors.safe(r);
		}finally {
			//hasStartedRunningDeferred--;
			runDeferredIfNotDeferring();
		}
	}


	
	
	@Override
	public Suppressor suppressRunningImmediately() {
		Suppressor ret = Suppressor.wrap(()->{
			__decrementSuppressors();
		});
		__incrementSuppressors();
		return ret;
	}
	@Override
	public boolean isRunningImmediately() {
		return !isDeferring();
	}
	@Override
	public boolean isDeferring() {
		return shouldBeDeferring>0 || hasStartedRunningDeferred();
	}
	@Override
	public boolean hasStartedRunningDeferred() {
		return hasStartedRunningDeferred>0;
	}

	@Override
	public void __incrementSuppressors() {
		shouldBeDeferring++;
		if(DEBUG)
			try {
				throw new RuntimeException();
			}catch(RuntimeException x) {
				trace.add(x);
			}
	}
	@Override
	public void __decrementSuppressors() {
		shouldBeDeferring--;
		if(DEBUG) {
			trace.remove(trace.size()-1);
		}
		if(shouldBeDeferring<=0) {
			runDeferredIfNotDeferring();
			//TODO: warn if negative
			shouldBeDeferring=0;
		}
	}
	
	@Override
	public Deferrer makeSynchronized(Object monitor) {
		return new DefererImpl(q) {
			final Object mon = defaultMonitor(monitor);
			protected Object defaultMonitor(Object monitor) {
				return monitor==null? q : monitor;
			}
			@Override
			protected void enqueue(Runnable r) {
				synchronized (mon) {
					super.enqueue(r);
				}
			}
			@Override
            protected Runnable pollQueue() {
				synchronized (mon) {
                    return super.pollQueue();
                }
			}
			@Override
            protected boolean isQueueEmpty() {
				synchronized (mon) {
                    return super.isQueueEmpty();
                }
			}
			@Override
            protected void runDeferredIfNotDeferring() {
				synchronized (mon) {
                    super.runDeferredIfNotDeferring();
                }
			}
			@Override
            public Suppressor suppressRunningImmediately() {
				synchronized (mon) {
					return super.suppressRunningImmediately();
				}
			}
			@Override
            public boolean isRunningImmediately() {
                return !isDeferring();
            }
			@Override
            public boolean isDeferring() {
				synchronized (mon) 
				{
					return shouldBeDeferring>0 || hasStartedRunningDeferred();
				}
            }
			@Override
			public boolean hasStartedRunningDeferred() {
                synchronized (mon)
                {
                    return hasStartedRunningDeferred>0;
                }
            }
			@Override
			public Deferrer makeSynchronized(Object monitor) {
				monitor = defaultMonitor(monitor);
                return mon==monitor?this:super.makeSynchronized(monitor);
            }
			@Override
			public void __decrementSuppressors() {
                synchronized (mon) {
					super.__decrementSuppressors();
                }
			}
			@Override
            public void __incrementSuppressors() {
				synchronized (mon) {
                    super.__incrementSuppressors();
                }
			}
			

			
		};
	}
	
}
