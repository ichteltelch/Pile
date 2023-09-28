package pile.aspect.recompute;

import java.util.Arrays;
import java.util.Collection;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.logging.Logger;

import pile.aspect.Dependency;
import pile.aspect.combinations.Pile;
import pile.aspect.suppress.MockBlock;
import pile.impl.PileImpl;
import pile.impl.Piles;
import pile.interop.debug.DebugEnabled;
import pile.interop.exec.StandardExecutors;
import pile.interop.wait.WaitService;
import pile.utils.WeakCleanup;

/**
 * {@link Recomputation}-objects are used by code that recomputes a value to interface with the
 * value, mainly to fulfill the recomputation. They can also be used by other code to cancel an 
 * ongoing recomputation.
 * The recomputation code must be written so that at least once one of the fullfill* methods is 
 * called (Except if it is known that the computation has been cancelled).
 * 
 * @author bb
 *
 * @param <E>
 */
public interface Recomputation<E> extends DependencyRecorder{
	/**
	 * End the recomputation by providing a value
	 * @param value
	 * @return true if the recomputation was still ongoing, that is, it was not yet cancelled nor fulfilled
	 * @throws IllegalStateException if the current thread is not the Thread of the recomputation
	 */
	default public boolean fulfill(E value) {
		return fulfill(value, null);
	}
	/**
	 * End the recomputation by providing no value
	 * @throws IllegalStateException if the current thread is not the Thread of the recomputation
	 * @return true if the recomputation was still ongoing, that is, it was not yet cancelled nor fulfilled
	 */
	default public boolean fulfillInvalid() {
		return fulfillInvalid(null);
	}
	/**
	 * End the recomputation by providing a value, and run some code before the transaction is ended
	 * in case the recomputation was still ongoing.  
	 * @param value
	 * @param onSuccess This code can be used to add or remove dependencies 
	 * or to write additional recomputation results to buffers of {@link PileImpl}s that depend on this one
	 * and whose recomputations simply read a buffer.
	 * 
	 * @return true if the recomputation was still ongoing, that is, it was not yet cancelled nor fulfilled.
	 * @throws IllegalStateException if the current thread is not the Thread of the recomputation
	 */
	public boolean fulfill(E value, Runnable onSuccess);
	/**
	 * End the recomputation by providing no value, and run some code before the transaction is ended
	 * in case the recomputation was still ongoing. 
	 * @param onSuccess This code can be used to add or remove dependencies 
	 * @throws IllegalStateException if the current thread is not the Thread of the recomputation
	 * @return true if the recomputation was still ongoing, that is, it was not yet cancelled nor fulfilled
	 */
	public boolean fulfillInvalid(Runnable onSuccess);
	/**
	 * Cancel the recomputation. This interrupts the thread or interrupts the {@link Future} that 
	 * have been set with one of the setThread methods. <br>
	 * The thread may not be interrupted (but the future always can be interrupted), depending
	 * on previous calls to {@link #setInterruptible()} and {@link #setInterruptible(boolean)}
	 * @return true if the recomputation was still ongoing, that is, it was not yet cancelled nor fulfilled
	 */
	public boolean cancel();
	
	/**
	 * Test if this {@link Recomputation} has been cancelled or fulfilled
	 * @return
	 */
	public boolean isFinished();
	/**
	 * Get the {@link Thread} that is currently performing the recomputation,
	 * or the Future that is scheduled to start the task in case the recomputation is delayed.
	 * @return
	 */
	public Object getThread();
	
	/**
	 * @return A name that the recomputing {@link Thread} should bear while the
	 * recomputation is running, for debugging and logging purposes.
	 * @see DebugEnabled#RENAME_RECOMPUTATION_THREADS
	 */
	public String suggestThreadName();

//	/**
//	 * Get the thread of this {@link Recomputation}, or the {@link Future} that is scheduled to start
//	 * the thread in case the recomputation is delayed.
//	 * @return
//	 */
//	public Either<Thread, Future<?>> getThread();
	/**
	 * Change the Thread of this {@link Recomputation}.
	 * Some methods of this {@link Recomputation} object are only available from that spacial thread.
	 * @param t
	 * @throws IllegalArgumentException iff the thread is <code>null</code> or dead
	 */
	public void setThread(Thread t);
	
	/**
	 * Change the "Thread" of this {@link Recomputation} (which can actually also be a {@link Future} that
	 * will eventually start a thread in case the recomputation is delayed)
	 * @param t
	 * @throws IllegalArgumentException iff the {@link Future} is <code>null</code> or dead
	 */
	public void setThread(Future<?> f);
	
	/**
	 * Query information about the Dependencies that changed to cause this {@link Recomputation}.
	 * May return <code>null</code> or an empty {@link Set} if this information is not available.
	 * @param copy Whether a copy of the internally used Set should be made. Otherwise,
	 * a read-only live view is returned, which may undergo concurrent modifications, 
	 * but only when this {@link Recomputation} has become obsolete anyway.
	 * @return
	 */
	public Set<? extends Dependency> queryChangedDependencies(boolean copy);
	
	/**
	 * Wait until this recomputation is either fulfilled or cancelled
	 */
	default public void join() throws InterruptedException{
		join(WaitService.get());
	}
	
	/**
	 * Wait until this recomputation is either fulfilled or cancelled
	 * @param Wait at most this many milliseconds
	 */
	default public void join(long timeout) throws InterruptedException{
		join(WaitService.get(), timeout);
	}

	/**
	 * Wait until this recomputation is either fulfilled or cancelled
	 */
	public void join(WaitService ws) throws InterruptedException;
	
	/**
	 * Wait until this recomputation is either fulfilled or cancelled
	 * @param Wait at most this many milliseconds
	 */
	public void join(WaitService ws, long timeout) throws InterruptedException;

	/**
	 * Add a dependency to the value that is being recomputed without triggering another recomputation.
	 * @param d
	 */
	public void addDependency(Dependency d);
	/**
	 * Remove a dependency from the value that is being recomputed without triggering another recomputation.
	 * @param d
	 */
	public void removeDependency(Dependency d);
	/**
	 * Utility method for calling {@link #addDependency(Dependency)} or {@link #removeDependency(Dependency)}
	 * depending on a boolean
	 * @param d
	 * @param active
	 */
	default public void setDependencyStatus(Dependency d, boolean active) {
		if(active)
			addDependency(d);
		else
			removeDependency(d);
	}
	/**
	 * Test if the value that is being recomputed currently depends on the geiven {@link Dependency}
	 * @param d
	 * @return
	 */
	public boolean dependsOn(Dependency d);
	
	/**
	 * Create a wrapper around this {@link Recomputation} that forwards all method calls and
	 * ensures that {@link #cancel()} is called when the wrapper becomes weakly reachable.
	 * @return
	 */
	public default WrapWeak<E> wrapWeak(){
		return new WrapWeak<>(this, null);
	}
	/**
	 * Create a wrapper around this {@link Recomputation} that forwards all method calls and
	 * ensures that {@link #cancel()} is called when the wrapper becomes weakly reachable.
	 * @param warn If this parameter is not <code>null</code>,
	 * a warning containing the given {@link String} will be logged if the
	 * call to {@link #cancel()} on the wrapped {@link Recomputation} returned true
	 * because it had neither been cancelled nor fulfilled. 
	 * when this wrapper became weakly reachable.
	 * @return
	 */
	public default WrapWeak<E> wrapWeak(String warn){
		return new WrapWeak<>(this, warn);
	}
	
	/**
	 * A wrapper around a {@link Recomputation} that forwards all method calls and
	 * ensures that {@link #cancel()} is called when the wrapper becomes weakly reachable.
	 * 
	 * This is used as a safety measure to ensure that no recomputation transactions linger 
	 * infinitely in case the recomputation code does not fulfill the recomputation.
	 * 
	 * @author bb
	 *
	 * @param <E>
	 */
	
	public static class WrapWeak<E> implements Recomputation<E>{
		private final static Logger log=Logger.getLogger("Recomputation.WrapWeak");
		Recomputation<E> back;
		/**
		 * 
		 * @param back The wrapped {@link Recomputation}
		 * @param warn If this parameter is not <code>null</code>, 
		 * a warning containing the given {@link String} will 
		 * be logged if the
		 * wrapped {@link Recomputation} had not yet been fulfilled or cancelled
		 * when this wrapper became weakly reachable.
		 */
		public WrapWeak(Recomputation<E> back, String warn) {
			this(StandardExecutors.unlimited(), back, warn);
		}
		/**
		 * 
		 * @param exec The {@link ExecutorService} that will be used to cancel the wrapped {@link Recomputation}
		 * if the wrapper becomes only weakly reachable.
		 * @param back The wrapped {@link Recomputation}
		 * @param warn If this parameter is not <code>null</code>, 
		 * a warning containing the given {@link String} will 
		 * be logged if the
		 * wrapped {@link Recomputation} had not yet been fulfilled or cancelled
		 * when this wrapper became weakly reachable.
		 */
		public WrapWeak(ExecutorService exec, Recomputation<E> back, String warn) {
			this.back=back;
			if(warn!=null)
				WeakCleanup.runIfWeak(this, ()->{
					if(!back.isFinishedAsync()) {
//						System.err.println("Possibly unfulfilled recomputation");

						exec.execute(()->{
							if(back.cancel()) 
								log.warning("You forgot to fulfill or cancel a Recomputation (hint: "+warn+")");
						});
					}
				});
			else
				WeakCleanup.runIfWeak(this, ()->exec.execute(back::cancel));
		}
		
		@Override
		public boolean fulfill(E value) {
			return back.fulfill(value);
		}
		@Override
		public boolean fulfillInvalid() {
			return back.fulfillInvalid();
		}
		@Override
		public boolean cancel() {
			return back.cancel();
		}
		@Override
		public boolean isFinished() {
			return back.isFinished();
		}
//		@Override
//		public Either<Thread, Future<?>> getThread() {
//			return back.getThread();
//		}
		@Override
		public Object getThread() {
			return back.getThread();
		}
		@Override
		public void setThread(Thread t) {
			back.setThread(t);
		}
		@Override
		public void setThread(Future<?> f) {
			back.setThread(f);
		}
		@Override
		public Set<? extends Dependency> queryChangedDependencies(boolean copy) {
			return back.queryChangedDependencies(copy);
		}
		@Override
		public void join()  throws InterruptedException{
			back.join();
		}
		@Override
		public void join(long timeout)  throws InterruptedException{
			back.join(timeout);
		}
		@Override
		public void join(WaitService ws)  throws InterruptedException{
			back.join(ws);
		}
		@Override
		public void join(WaitService ws, long timeout)  throws InterruptedException{
			back.join(ws, timeout);
		}
		@Override
		public void forgetOldValue() {
			back.forgetOldValue();
		}
		@Override
		public void addDependency(Dependency d) {
			back.addDependency(d);
		}
		@Override
		public void removeDependency(Dependency d) {
			back.removeDependency(d);
		}
		@Override
		public void fulfillRetry() {
			back.fulfillRetry();
		}
		
		@Override
		public boolean fulfill(E value, Runnable onSuccess) {
			return back.fulfill(value, onSuccess);
		}
		@Override
		public boolean fulfillInvalid(Runnable onSuccess) {
			return back.fulfillInvalid(onSuccess);
		}

		@Override
		public void setInterruptible() {
			back.setInterruptible();
		}
		@Override
		public void setInterruptible(boolean status) {
			back.setInterruptible(status);
		}		
		@Override
		public E oldValue() {
			return back.oldValue();
		}
		@Override
		public boolean hasOldValue() {
			return back.hasOldValue();
		}
		@Override
		public boolean dependsOn(Dependency d) {
			return back.dependsOn(d);
		}
		@Override
		public boolean fulfillRestoreOldValue() {
			return back.fulfillRestoreOldValue();
		}
		@Override
		public boolean fulfillRestoreOldValue(Runnable onSuccess) {
			return back.fulfillRestoreOldValue(onSuccess);
		}
		public void setFailHandler(Consumer<? super E> handler) {
			back.setFailHandler(handler);
		};
		public void setSuccessHandler(Consumer<? super E> handler) {
			back.setSuccessHandler(handler);
		};
		@Override
		public void activateDynamicDependencies() {
			back.activateDynamicDependencies();
		}
		@Override
		public void recordDependency(Dependency d) {
			back.recordDependency(d);
		}
		@Override
		public boolean isDependencyScout() {
			return back.isDependencyScout();
		}
		@Override
		public boolean isFinishedAsync() {
			return back.isFinishedAsync();
		}
		@Override
		public void setDependencyVeto(Predicate<Dependency> mayNotDependOn) {
			back.setDependencyVeto(mayNotDependOn);			
		}
		@Override
		public void enterDelayedMode() {
			back.enterDelayedMode();
		}
		@Override
		public String suggestThreadName() {
			return back.suggestThreadName();
		}
		@Override
		public boolean renameThread(String newName) {
			return back.renameThread(newName);
		}
		@Override
		public String toString() {
			return back.toString();
		}
//		@Override
//		public void setThreadNameOnFinish(String name) {
//			back.setThreadNameOnFinish(name);
//		}
		@Override
		public boolean isDynamic() {
			return back.isDynamic();
		}
	}

	/**
	 * Utility method for making the current thread the thread that is responsible for fulfilling the
	 * recomputation.
	 * Calls {@link #setThread(Thread)} with the {@linkplain Thread#currentThread() current thread}.
	 * 
	 */
	default public void setThread() {
		setThread(Thread.currentThread());
	}
	/**
	 * This must be called before calling {@link #cancel()} can interrupt the current thread of this
	 * recomputation.
	 */
	public default void setInterruptible() {
		setInterruptible(true);
	}
	/**
	 * Change whether calling {@link #cancel()} can interrupt the current thread of this
	 * recomputation.
	 * @param status
	 */
	public void setInterruptible(boolean status);
	/**
	 * Basically the same as {@link #fulfillInvalid()}
	 */
	public void fulfillRetry();
	

	/**
	 * If there was a previous value that is to be replaced by the result of this recomputation, 
	 * return it. Else, return <code>null</code>.
	 * @return
	 */
	public E oldValue();
	/**
	 * Query whether there was a previous value that is to be replaced by the result of this recomputation
	 * @return
	 */
	public boolean hasOldValue();
	/**
	 * Fulfill this {@link Recomputation} by restoring the previous value that was to be
	 * replaced by the result of this recomputation. 
	 * If there is no old value, nothing happens and the Recomputation may still be ongoing.
	 * @return true if there was an old value and the recomputation was still ongoing, that is, it was not yet cancelled nor fulfilled
	 * @throws IllegalStateException if the current thread is not the Thread of the recomputation

	 * @return
	 */
	public boolean fulfillRestoreOldValue();
	/**
	 * Fulfill this {@link Recomputation} by restoring the previous value that was to be
	 * replaced by the result of this recomputation.
	 * If there is no old value, nothing happens and the Recomputation may still be ongoing.
	 * @param onSuccess This code can be used to add or remove dependencies 
	 * or to write additional recomputation results to buffers of {@link PileImpl}s that depend on this one
	 * and whose recomputations simply read a buffer.
	 * @return true if there was an old value and the recomputation was still ongoing, that is, it was not yet cancelled nor fulfilled
	 * @throws IllegalStateException if the current thread is not the Thread of the recomputation

	 * @return
	 */
	public boolean fulfillRestoreOldValue(Runnable onSuccess);
	/**
	 * Forget the old value that is to be replaced by the result of this recomputation
	 * @throws IllegalStateException if the current thread is not the Thread of the recomputation
	 */
	public void forgetOldValue();

	/**
	 * Set some code that destroys the value if the recomputation is later attempted to be fulfilled,
	 * but actually was obsolete and the value wasn't set after all
	 */
	public void setFailHandler(Consumer<? super E> handler);

	/**
	 * Set some code that run immediately before the recomputation is fulfilled.
	 * It is not run if the recomputation is obsolete. 
	 * Note that the recomputation may become obsolete between the running 
	 * of the handler and the fulfillment.
	 */
	public void setSuccessHandler(Consumer<? super E> handler);

	/**
	 * If {@link #activateDynamicDependencies()} had been called previously,
	 * the given {@link Dependency} is added to the record
	 * @param d
	 */
	public void recordDependency(Dependency d);
	
	/**
	 * If this is activated, that after this {@link Recomputation} is successfully 
	 * fulfilled in any way, the set of non-essential dependencies of the underlying value
	 * will be made equal to the set of dependecies recorded by calls to
	 * {@link #recordDependency(Dependency)} 
	 */
	public void activateDynamicDependencies();
	
	/**
	 * Calls {@link Piles#withCurrentRecomputation(Recomputation) Values.setCurrentRecomputation(this)}
	 * @return
	 */
	public default MockBlock setAsCurrent() {
		return Recomputations.withCurrentRecomputation(this);
	}
	
//	public void setThreadNameOnFinish(String name);
	/**
	 * Rename the thread. The original name will be restored when the {@link Recomputation} is handed off to another {@link Thread}
	 * or {@link Future}, or when it is deactivated. This method only has an effect and returns true when called from the 
	 * current recomputing {@link Thread}. The mechanism only works correctly if {@link DebugEnabled#RENAME_RECOMPUTATION_THREADS}
	 *  is true. If this method is called multiple times before the name is restored, the name that will be restored is that from before it was called first.
	 * @param newName If this is <code>null</code>, the original name will be restored.
	 * @return
	 */
	public boolean renameThread(String newName);
	
	/**
	 * <q>Recompute</q> a {@link PileImpl} by restoring its old value.
	 * If there is no old value, {@link Recomputation#fulfillInvalid()} will be called.
	 * @see Recomputation#fulfillRestoreOldValue()
	 * @param reco
	 */
	public default boolean restoreOldValue() {
		if(hasOldValue())
			return fulfillRestoreOldValue();
		else
			return fulfillInvalid();
	}
	
	/**
	 * <q>Recompute</q> a {@link PileImpl} by restoring its old value.
	 * If there is no old value, {@link Recomputation#fulfillInvalid(Runnable)} will be called.
	 * @see Recomputation#fulfillRestoreOldValue(Runnable)
	 * @param reco
	 */
	public default boolean restoreOldValue(Runnable onSuccess) {
		if(hasOldValue())
			return fulfillRestoreOldValue(onSuccess);
		else
			return fulfillInvalid(onSuccess);
	}
	/**
	 * 
	 * @param dep
	 * @return Whether the given {@link Dependency} is the only one
	 * that changed, triggering this {@link Recomputation}.
	 */
	public default boolean onlyChanged(Dependency dep) {
		Set<? extends Dependency> cd = queryChangedDependencies(false);
		return cd.size()<=1 && cd.contains(dep);
	}
	/**
	 * 
	 * @param dep
	 * @return Whether the given {@link Dependency Dependencies} are the only ones
	 * that possibly changed, triggering this {@link Recomputation}.
	 */
	public default boolean onlyChanged(Dependency... dep) {
		Collection<Dependency> dc = Arrays.asList(dep);
		Set<? extends Dependency> cd = queryChangedDependencies(false);
		return dc.containsAll(cd);
	}
	/**
	 * Test whether this {@link Recomputation} is in dependency scouting mode.
	 * It may be in that mode from the start, or because it accessed an undeclared dependency.
	 * @return
	 */
	public boolean isDependencyScout();
	/**
	 * End this recomputation if it is in dependency scouting mode.
	 * you should call {@link #terminateDependencyScout()} after all dependencies 
	 * have been accessed but before significant effort is expended or a delayed 
	 * recomputation is started, and return from the {@link Recomputer}'s code
	 * if this method returns <code>true</code>.
	 * @return Whether the recomputation is now finished. 
	 */
	public default boolean terminateDependencyScout() {
		if(isDependencyScout()) {
			fulfillInvalid();
			return true;
		}else {
			return isFinished();
		}
			
	}
	/**
	 * An unsynchronized lightweight test for the {@link Recomputation} being finished. 
	 * May return false negatives.
	 * @return
	 */
	boolean isFinishedAsync();
	/**
	 * Debugging utility: If a {@link Pile} ends up depending on something that it shouldn't,
	 * you can declare the criterion for a forbidden {@link Dependency} at the beginning
	 * of the {@link Recomputer} 's code and get an {@link Exception} that is thrown when a
	 * forbidden {@link Dependency} is accessed. 
	 * {@link DebugEnabled#DE} must be <code>true</code> for this to work.
	 * @param mayNotDependOn
	 */
	public void setDependencyVeto(Predicate<Dependency> mayNotDependOn);
	@Override
	default Recomputation<?> getRecomputation() {
		return this;
	}
	@Override
	default Recomputation<?> getReceivingRecomputation() {
		return this;
	}
	/**
	 * Called to announce that the immediate recomputation phase is over and, if
	 * the {@link Recomputation} is still not fulfilled, the recomputation may
	 * continue in a different {@link Thread}. Accessing an undeclared {@link Dependency}
	 * in delayed mode results in a warning being logged.
	 */
	public void enterDelayedMode();
	/**
	 * 
	 * @return Whether this {@link Recomputation} uses dynamic {@link Dependency} recording
	 */
	public boolean isDynamic();
}
