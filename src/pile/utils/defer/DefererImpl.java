package pile.utils.defer;

import pile.aspect.suppress.Suppressor;
import pile.interop.exec.StandardExecutors;

public class DefererImpl implements Deferrer {
	int shouldBeDeferring;
	volatile int hasStartedRunningDeferred;
	final DeferrerQueue q;
	public DefererImpl(DeferrerQueue q) {
		this.q = q;
	}
	
	protected void runDeferredIfNotDeferring() {
		if(shouldBeDeferring==0 && !isQueueEmpty()) {
			try {
				hasStartedRunningDeferred++;
				while(shouldBeDeferring==0) {
					Runnable r = pollQueue();
					if(r!=null)
						StandardExecutors.safe(r);
					else
						break;
				}
			}finally {
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
		StandardExecutors.safe(r);
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
	}
	@Override
	public void __decrementSuppressors() {
		shouldBeDeferring--;
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
