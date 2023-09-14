package pile.aspect.recompute;

import java.util.ArrayList;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.logging.Level;
import java.util.logging.Logger;

import pile.aspect.Dependency;
import pile.aspect.suppress.MockBlock;

/**
 * Stuff for interacting with the current recomputation.
 * 
 * @author bb
 *
 */
public class Recomputations {
	private final static Logger log=Logger.getLogger("Recomputations");

	/**
	 * The currently active {@link Recomputation} can be traced in this {@link ThreadLocal}
	 */
	static final ThreadLocal<DependencyRecorder> currentRecorder = new ThreadLocal<>();
	/**
	 * Get the last currently active recomputation that was registered using
	 * {@link #withDependencyRecorder(DependencyRecorder)} in this {@link Thread}.
	 * @return
	 */
	public static Recomputation<?> getCurrentRecomputation(){
		DependencyRecorder re = currentRecorder.get();
		return re==null?null:re.getRecomputation();
	}
	/**
	 * Get the last currently active recomputation that was registered using
	 * {@link #withDependencyRecorder(DependencyRecorder)} in this {@link Thread}.
	 * @return
	 */
	public static DependencyRecorder getCurrentRecorder(){
		return currentRecorder.get();
	}
	/**
	 * Set the currently active {@link DependencyRecorder} to the given value for the lifetime 
	 * of the returned {@link MockBlock}.
	 * Use this in a try-with-resources statement 
	 * if you need to access the ongoing recomputation/recorder via {@link #getCurrentRecomputation()}
	 * or {@link #getCurrentRecorder()}
	 * in a {@link Thread} that the recomputation was not started in.
	 * If you want to call code that should be prevented from accessing the current {@link Recomputation},
	 * call this method with a <code>null</code> argument.
	 * @implNote The threads launched by value builders will already have this current recomputation set.
	 * @apiNote The current {@link Recomputation} is needed for automatic recording of dynamic dependencies to work
	 * @param reco
	 * @return
	 */
	public static MockBlock withDependencyRecorder(DependencyRecorder reco) {
		DependencyRecorder old = currentRecorder.get();
		if(old==reco)
			return MockBlock.NOP;
		MockBlock restore = MockBlock.closeOnly(()->{
			if(currentRecorder.get()!=reco) {
				try {
					throw new Exception("Trace");
				}catch(Exception x) {
					log.log(Level.SEVERE, "MockBlocks for setDependencyRecorder closed in wrong order!", x); 
				}
			}
			currentRecorder.set(old);
		});
		currentRecorder.set(reco);
		return restore;
	}
	/**
	 * Set the currently active recomputation to the given value for the lifetime 
	 * of the returned {@link MockBlock}.
	 * Use this in a try-with-resources statement 
	 * if you need to access the ongoing recomputation via {@link #getCurrentRecomputation()}
	 * in a {@link Thread} that the recomputation was not started in.
	 * If you want to call code that should be prevented from accessing the current {@link Recomputation},
	 * call this method with a <code>null</code> argument.
	 * @implNote The threads launched by value builders will already have this current recomputation set.
	 * @apiNote The current {@link Recomputation} is needed for automatic recording of dynamic dependencies to work
	 * @param reco
	 * @return
	 */
	public static MockBlock withCurrentRecomputation(Recomputation<?> reco) {
		return withDependencyRecorder(reco);
	}
	/**
	 * Call {@link Recomputation#fulfillInvalid()} on the recomputation 
	 * currently performed by this {@link Thread}.
	 * @return <code>null</code>, for convenience so the call to this method can be written in one line with a return statement.
	 * @throws NullPointerException If there is no current recomputation or it has been set to <code>null</code>
	 * by calling {@link #withDependencyRecorder(DependencyRecorder)}.
	 */
	public Object fulfillInvalid() {
		getCurrentRecomputation().fulfillInvalid();
		return null;
	}
	/**
	 * Call {@link Recomputation#fulfillInvalid(Runnable)} on the recomputation 
	 * currently performed by this {@link Thread}.
	 * @return <code>null</code>, for convenience so the call to this method can be written in one line with a return statement.
	 * @throws NullPointerException If there is no current recomputation or it has been set to <code>null</code>
	 * by calling {@link #withDependencyRecorder(DependencyRecorder)}.
	 */
	public Object fulfillInvalid(Runnable onSuccess) {
		getCurrentRecomputation().fulfillInvalid(onSuccess);
		return null;
	}
	/**
	 * Call {@link Recomputation#fulfillRestoreOldValue()} on the recomputation 
	 * currently performed by this {@link Thread}.
	 * @return <code>null</code>, for convenience so the call to this method can be written in one line with a return statement.
	 * @throws NullPointerException If there is no current recomputation or it has been set to <code>null</code>
	 * by calling {@link #withDependencyRecorder(DependencyRecorder)}.
	 */
	public Object fulfillRestoreOldValue() {
		getCurrentRecomputation().fulfillRestoreOldValue();
		return null;
	}
	/**
	 * Call {@link Recomputation#fulfillRestoreOldValue(Runnable)} on the recomputation 
	 * currently performed by this {@link Thread}.
	 * @return <code>null</code>, for convenience so the call to this method can be written in one line with a return statement.
	 * @throws NullPointerException If there is no current recomputation or it has been set to <code>null</code>
	 * by calling {@link #withDependencyRecorder(DependencyRecorder)}.
	 */
	public Object fulfillRestoreOldValue(Runnable onSuccess) {
		getCurrentRecomputation().fulfillRestoreOldValue(onSuccess);
		return null;
	}


	/**
	 * Call {@link Recomputation#restoreOldValue()} on the recomputation 
	 * currently performed by this {@link Thread}.
	 * @return <code>null</code>, for convenience so the call to this method can be written in one line with a return statement.
	 * @throws NullPointerException If there is no current recomputation or it has been set to <code>null</code>
	 * by calling {@link #withDependencyRecorder(DependencyRecorder)}.
	 */

	public Object restoreOldValue() {
		getCurrentRecomputation().restoreOldValue();
		return null;
	}
	/**
	 * Call {@link Recomputation#restoreOldValue(Runnable)} on the recomputation 
	 * currently performed by this {@link Thread}.
	 * @return <code>null</code>, for convenience so the call to this method can be written in one line with a return statement.
	 * @throws NullPointerException If there is no current recomputation or it has been set to <code>null</code>
	 * by calling {@link #withDependencyRecorder(DependencyRecorder)}.
	 */
	public Object restoreOldValue(Runnable onSuccess) {
		getCurrentRecomputation().restoreOldValue(onSuccess);
		return null;
	}

	/**
	 * Call {@link Recomputation#fulfill(Object) fulfill(null)} on the recomputation 
	 * currently performed by this {@link Thread}.
	 * @return <code>null</code>, for convenience so the call to this method can be written in one line with a return statement.
	 * @throws NullPointerException If there is no current recomputation or it has been set to <code>null</code>
	 * by calling {@link #withDependencyRecorder(DependencyRecorder)}.
	 */
	public Object fulfillNull() {
		getCurrentRecomputation().fulfill(null);
		return null;
	}
	/**
	 * Call {@link Recomputation#fulfill(Object, Runnable) fulfill(null, onSuccess)} 
	 * on the recomputation currently performed by this {@link Thread}.
	 * @return <code>null</code>, for convenience so the call to this method can be written in one line with a return statement.
	 * @throws NullPointerException If there is no current recomputation or it has been set to <code>null</code>
	 * by calling {@link #withDependencyRecorder(DependencyRecorder)}.
	 */
	public Object fulfillNull(Runnable onSuccess) {
		getCurrentRecomputation().fulfill(null, onSuccess);
		return null;
	}
	/**
	 * Call {@link Recomputation#getOldValue()}
	 * on the recomputation currently performed by this {@link Thread}.
	 * @return
	 * @throws NullPointerException If there is no current recomputation or it has been set to <code>null</code>
	 * by calling {@link #withDependencyRecorder(DependencyRecorder)}.
	 */
	public Object getOldValue() {
		return getCurrentRecomputation().oldValue();
	}
	/**
	 * Call {@link Recomputation#forgetOldValue()}
	 * on the recomputation currently performed by this {@link Thread}.
	 * @return
	 * @throws NullPointerException If there is no current recomputation or it has been set to <code>null</code>
	 * by calling {@link #withDependencyRecorder(DependencyRecorder)}.
	 */
	public void forgetOldValue() {
		getCurrentRecomputation().forgetOldValue();
	}
	/**
	 * Call {@link Recomputation#isFinished()}
	 * on the recomputation currently performed by this {@link Thread}.
	 * @return
	 * @throws NullPointerException If there is no current recomputation or it has been set to <code>null</code>
	 * by calling {@link #withDependencyRecorder(DependencyRecorder)}.
	 */
	public boolean isRecomputationfinished() {
		return getCurrentRecomputation().isFinished();
	}
	/**
	 * Call {@link Recomputation#hasOldValue()}
	 * on the recomputation currently performed by this {@link Thread}.
	 * @return
	 * @throws NullPointerException If there is no current recomputation or it has been set to <code>null</code>
	 * by calling {@link #withDependencyRecorder(DependencyRecorder)}.
	 */
	public boolean hasOldValue() {
		return getCurrentRecomputation().hasOldValue();
	}
	/**
	 * Call {@link Recomputation#queryChangedDependencies(boolean)}
	 * on the recomputation currently performed by this {@link Thread}.
	 * @return
	 * @throws NullPointerException If there is no current recomputation or it has been set to <code>null</code>
	 * by calling {@link #withDependencyRecorder(DependencyRecorder)}.
	 */
	public Set<? extends Dependency> queryChangedDependencies(boolean copy) {
		return getCurrentRecomputation().queryChangedDependencies(copy);
	}
	/**
	 * 
	 * @return Whether there is a current {@link Recomputation} set for this {@link Thread} and it is in dependency scouting mode
	 */
	public static boolean isScouting() {
		Recomputation<?> reco = getCurrentRecomputation();
		return reco!=null && reco.isDependencyScout();
	}
	/**
	 * Set the current {@link Recomputation} for this {@link Thread} to <code>null</code> and restore it when the {@link MockBlock} is closed.
	 * @return
	 */
	public static MockBlock dontRecord() {
		return withCurrentRecomputation(null);
	}


	/**
	 * Either immediately runs the given {@link Runnable} or waits until a {@link MockBlock}
	 * opened by {@link #suspendRecomputationRequests(ExecutorService)} is closed, if there is one.
	 * @param r
	 */
	public static void possiblySuspendRecomputation(Runnable r) {
		if(r==null)
			return;
		ArrayList<Runnable> waiting = suspendedRecomputationsRequests.get();
		if(waiting==null)
			r.run();
		else
			waiting.add(r);
	}
	/**
	 * Test whether there is an open {@link MockBlock} created by {@link #suspendRecomputationRequests(ExecutorService)} for this Thread
	 * @return
	 */
	public static boolean areRecomputationsSuspended() {
		return suspendedRecomputationsRequests.get()!=null;
	}

	private static final ThreadLocal<ArrayList<Runnable>> 
	suspendedRecomputationsRequests = new ThreadLocal<>();

	/**
	 * For the lifetime of the returned {@link MockBlock}, jobs submitted to {@link #possiblySuspendRecomputation(Runnable)}
	 * are not run immediately. After all such {@link MockBlock}s have been closed, the jobs are executed in the given {@link ExecutorService} asynchronously.
	 * @param async
	 * @return
	 */
	public static MockBlock suspendRecomputationRequests(ExecutorService async) {
		return new MockBlock() {
			ArrayList<Runnable> old;
			ArrayList<Runnable> suspl;
			@Override
			protected void open() {
				old = suspendedRecomputationsRequests.get();
				if(old==null)
					suspendedRecomputationsRequests.set(suspl = new ArrayList<>());
			}

			@Override
			protected void close_impl() {
				if(old==null) {
					if(suspl!=null) {
						ArrayList<Runnable> runThese = suspl;
						suspl=null;
						if(async==null) {
							for(Runnable r: runThese) {
								try {
									if(r!=null) {
										r.run();
									}
								}catch(RuntimeException x) {
									log.log(Level.WARNING, "Unsuspended recomputation threw RuntimeException!", x);
								}catch(Error e) {
									log.log(Level.WARNING, "Unsuspended recomputation threw Error!", e);
									throw e;
								}
							}
						}else {
							for(Runnable r: runThese) {
								if(r!=null) {
									async.execute(r);
								}
							}
						}
					}
					suspendedRecomputationsRequests.remove();
				}
			}
		};

	}




}
