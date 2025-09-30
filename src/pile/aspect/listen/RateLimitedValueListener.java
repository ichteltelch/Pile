package pile.aspect.listen;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Predicate;

import pile.aspect.ReadValue;
import pile.impl.MutRef;
import pile.interop.exec.StandardExecutors;

/**
 * A {@link ValueListener} that runs the actual event handling at a limited rate,
 * recording all the sources of the {@link ValueEvent}s it receives in between runs.
 * @author bb
 *
 */
public abstract class RateLimitedValueListener implements ValueListener{
	/**
	 * Similar to a {@link ValueEvent}, but with multiple sources that have been accumulated
	 * in between runs of the handler code.
	 * @author bb
	 *
	 */
	public class MultiEvent{
		HashSet<Object> sources;
		public MultiEvent(Set<? extends Object> sources) {
			this.sources=sources==null || sources.contains(null)?null:new HashSet<>(sources);	
		}
		/**
		 * Query whether the given object is considered a source for this event.
		 * This is the case if a {@link ValueEvent} was fired from it to the enclosing
		 * {@link RateLimitedValueListener} or if the enclosing {@link RateLimitedValueListener}
		 * received a <code>null</code> reference instead of an event object.
		 * @param o
		 * @return
		 */
		public boolean isSource(Object o) {
			if (sources==null)
				return true;
			return sources.contains(o);
		}
		/**
		 * Query whether this event is for a run of the handler code where all possible 
		 * sources should be considered.
		 * This is the case if the enclosing {@link RateLimitedValueListener} received
		 * a <code>null</code> reference instead of an event object.
		 * @return
		 */
		public boolean allSources() {
			return sources==null;
		}
		/**
		 * Get the set of all sources for this {@link MultiEvent}, or <code>null</code>
		 *  if all possible sources should be considered.
		 * @see #allSources()
		 * @return
		 */
		public HashSet<?> getSources() {
			return sources;
		}
		/**
		 * The event handler code should call this in case it cannot proceed now 
		 * but would like to process 
		 * an event with at least the same sources again at a later time.
		 */
		public void refire() {
			if(coolDownTime<0) {
				exec.execute(()->{
					multipleValuesChanged(sources);
				});
			}else {
				multipleValuesChanged(sources);
			}
				
		}
		/**
		 * Test whether there is at least a specific source of this {@link MultiEvent}
		 * that fulfills the given predicate. Note that unspecific <q>all sources</q>-events
		 * return <code>false</code> here.
		 * @param prop
		 * @return
		 */
		public boolean anySource(Predicate<? super Object> prop) {
			if(allSources())
				return false;
			for(Object s: sources)
				if(prop.test(s))
					return true;
			return false;
		}
	}
	long coldStartTime;
	long coolDownTime;
	boolean startCoolingBefore;
	boolean allowParallel;
	long lastRun;
	Future<?> scheduledRun;
	HashSet<Object> happened;
	boolean newEventsArrived;
	boolean enabled=true;
	ScheduledExecutorService exec=StandardExecutors.delayed();
	
	/**
	 * 
	 * @param coldStartTime millisecond delay running the handler if it handler has 
	 * not been run for at least {@code coolDownTime} milliseconds
	 * @param coolDownTime At least this many milliseconds must elapse between successive invocations of the handler
	 * @param startCoolingBefore Whether the cool down period starts as soon as the handler starts running, or when it finishes.
	 * @param allowParallel Whether another handler may be started while the last one is still running
	 */
	public RateLimitedValueListener(
			long coldStartTime, 
			long coolDownTime, 
			boolean startCoolingBefore,
			boolean allowParallel,
			boolean produceMultiEvents) {
		this.coldStartTime=coldStartTime;
		this.coolDownTime=coolDownTime;
		this.startCoolingBefore=startCoolingBefore;
		happened = produceMultiEvents?new HashSet<>():null;
		lastRun=0;
	}
	@Override
	public void valueChanged(ValueEvent e) {
		synchronized(this) {
			if(happened!=null)
				happened.add(e==null?null:e.getSource());
			newEventsArrived=true;
			long now = System.currentTimeMillis();
			long runAgo = now-lastRun;
			if(runAgo>coolDownTime) {
				scheduleFutureRun(coldStartTime);
			}else {
				if(scheduledRun==null) {
					scheduleFutureRun(coolDownTime-runAgo);
				}else {
					
				}
			}
		}

	}
	/**
	 * Set a nonstandard executor for running the delayed handler.
	 * @param exa
	 * @return
	 */
	public RateLimitedValueListener setExecutor(ScheduledExecutorService exa) {
		exec=exa==null?StandardExecutors.delayed():exa;
		return this;
	}
	
	/**
	 * Receive events from multiple sources.
	 * @param e If this parameter is or contains <code>null</code>,
	 * then all possible event sources should be assumed (see {@link MultiEvent#allSources()}
	 */
	public void multipleValuesChanged(Collection<?> e) {
		synchronized(this) {
			if(happened!=null) {
				if(e==null)
					happened.add(null);
				else
					happened.addAll(e);
			}
			newEventsArrived=true;
			long now = System.currentTimeMillis();
			long runAgo = now-lastRun;
			if(runAgo>coolDownTime) {
				scheduleFutureRun(coldStartTime);
			}else {
				if(scheduledRun==null) {
					scheduleFutureRun(coolDownTime-runAgo);
				}else {
					
				}
			}
		}

	}
	private void scheduleFutureRun(long wait) {
		assert Thread.holdsLock(this);
		if(scheduledRun!=null)
			if(!scheduledRun.isDone())
				return;
			else
				System.out.println("what???");
		if(enabled && wait>=0) {
			MutRef<Future<?>> selfHolder = new MutRef<>();
			scheduledRun = exec.schedule(()->this.runBody(false, selfHolder),
					wait, TimeUnit.MILLISECONDS);
			selfHolder.set(scheduledRun);
		}
		else
			runBody(true, null);
	}
	private void runBody(boolean itt, ReadValue<Future<?>> selfHolder) {
		Future<?> self=null;
		try {
			MultiEvent e;
			synchronized(this) {
				self = selfHolder==null?null:selfHolder.get();
				assert itt || self!=null;

				if(happened==null) {
					e=null;
				}else {
					e = new MultiEvent(happened);
					happened.clear();
				}
				newEventsArrived=false;

				if(!itt && allowParallel && self==scheduledRun)
					scheduledRun=null;
				if(startCoolingBefore) {
					lastRun=System.currentTimeMillis();
				}
			}
			doRun(e);

		} catch (InterruptedException e1) {
			
		}finally {
			synchronized(this) {
				if(!itt && self==scheduledRun)
					scheduledRun=null;
				long now = System.currentTimeMillis();
				if(!startCoolingBefore) {
					lastRun=now;
				}
//				if(scheduledRun!=null)
//					System.out.println();
				if(newEventsArrived) {
//					System.out.println("New events have accumulated!");
					if(scheduledRun==null) {
						scheduleFutureRun(Math.max(0, coolDownTime - (now - lastRun)));
					}
				}

			}
		}
	}
	/**
	 * Process an <q>all sources</q> event immediately in the current {@link Thread}.
	 */
	public void runImmediately() {
		synchronized(this) {
			if(happened!=null)
				happened.add(null);
			newEventsArrived=true;
//			long now = System.currentTimeMillis();
//			long runAgo = now-lastRun;
				scheduleFutureRun(-1);
		
		}
	}
	public void runImmediately(boolean inThisThread) {
		if(inThisThread)
			runImmediately();
		else
			StandardExecutors.unlimited().execute(this::runImmediately);
	}
	/**
	 * The handler method
	 * @param e
	 * @throws InterruptedException
	 */
	abstract protected void doRun(MultiEvent e) throws InterruptedException;
	/**
	 * Make a {@link RateLimitedValueListener} from a {@link Consumer} of MultiEvents.
	 * @param coldStartTime millisecond delay running the handler if it handler has 
	 * not been run for at least {@code coolDownTime} milliseconds
	 * @param coolDownTime At least this many milliseconds must elapse between successive invocations of the handler
	 * @param startCoolingBefore Whether the cool down period starts as soon as the handler starts running, or when it finishes.
	 * @param allowParallel Whether another handler may be started while the last one is still running
	 * @param run The actual handler 
	 * @return
	 */
	public static RateLimitedValueListener wrap(
			long coldStartTime, 
			long coolDownTime, 
			boolean startCoolingBefore,
			boolean allowParallel,
			Consumer<? super MultiEvent> run) {
		return new RateLimitedValueListener(
				coldStartTime, coolDownTime, 
				startCoolingBefore, allowParallel, true) {
			@Override
			protected void doRun(MultiEvent e) throws InterruptedException {
				run.accept(e);
			}
		};
	}
	
	
	/**
	 * Make a {@link RateLimitedValueListener} from a {@link Runnable} handler. 
	 * MultiEvent objects may not be generated, as the Handler has no way to receive them. 
	 * @param coldStartTime millisecond delay running the handler if it handler has 
	 * not been run for at least {@code coolDownTime} milliseconds
	 * @param coolDownTime At least this many milliseconds must elapse between successive invocations of the handler
	 * @param startCoolingBefore Whether the cool down period starts as soon as the handler starts running, or when it finishes.
	 * @param allowParallel Whether another handler may be started while the last one is still running
	 * @param run The actual handler 
	 * @return
	 */
	public static RateLimitedValueListener wrap(
			long coldStartTime, 
			long coolDownTime, 
			boolean startCoolingBefore,
			boolean allowParallel,
			Runnable run) {
		return new RateLimitedValueListener(
				coldStartTime, coolDownTime, 
				startCoolingBefore, allowParallel, false) {
			@Override
			protected void doRun(MultiEvent e) throws InterruptedException {
				run.run();
			}
		};
	}
	
	/**
	 * Break this {@link RateLimitedValueListener}: Events will be handled immediately in the same thread.
	 * This is meant for debugging to find out what's wrong with event handler code.
	 * @return {@code this}
	 */
	public RateLimitedValueListener disable() {
		enabled=false;
		return this;
	}

}
