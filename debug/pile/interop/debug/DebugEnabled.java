package pile.interop.debug;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

import pile.aspect.Dependency;
import pile.aspect.ValueBracket;
import pile.aspect.combinations.Pile;
import pile.aspect.recompute.Recomputation;
import pile.impl.AbstractReadListenDependency;
import pile.impl.DebugCallback;
import pile.impl.Independent;
import pile.impl.PileImpl;
import pile.specialized_int.MutInt;

/**
 * Configure here what runtime effort is made for tracking debugging relevant data
 * @author bb
 *
 */
public class DebugEnabled {
	/**
	 * Whether debugging is enabled at all.
	 * If this is <code>false</code>, {@link DebugCallback}s are not invoked.
	 */
	public static final boolean DE=false;
	/**
	 * Whether detailed traces are saved in all {@link AbstractReadListenDependency}
	 * for which {@link #traceEnabledFor(Dependency)} returns <code>true</code>.
	 * The traces document the decisions that were made since the last call to 
	 * endTransaction, together with stack traces for where these decisions happened.
	 * It can make the program very slow.
	 */
	public static final boolean ET_TRACE=DE && true;
	
	public static final boolean TRANSACTION_TRACES = false;
	
	/**
	 * The set of reactive values for which a detailed trace is saved.
	 * The trace documents the decisions that were made since the last call to 
	 * endTransaction, together with stack traces for where these decisions happened.
	 * <p>
	 * This only has an effect if {@link #ET_TRACE} is <code>true</code>.
	 * <p>
	 * 
	 * This is a {@link Collections#synchronizedSet(Set) synchronized} set. 
	 */
	public static final Set<Object> trace = Collections.synchronizedSet(new HashSet<>()); 
	
	/**
	 * This method is queried by the implementations to determine whether they should
	 * save a detailed trace of their activities. You can change it to include other criteria
	 * besides membership in {@link #trace}.
	 * @param d
	 * @return
	 */
	public static boolean traceEnabledFor(Object d) {
		return trace.contains(d);
	}
	/**
	 * {@link ValueBracket}s are 
	 * {@linkplain ValueBracket#queued(pile.utils.SequentialQueue) 
	 * normally} opened and closed while the internal mutex of an {@link AbstractReadListenDependency}
	 * is held. Since they can execute arbitrary user code, certain things, most notably 
	 * {@link Pile#destroy() destroy}ing the {@link AbstractReadListenDependency}, can lead to deadlocks.
	 * This flag enables counting of the mutices held due to {@link ValueBracket} operations
	 * (using the {@link ThreadLocal} integer {@link #lockedValueMutices}) so that problematic operation
	 * can log a warning while run while mutices are locked in the same thread.
	 */
	public static final boolean COUNT_BRACKET_LOCKS = false;
	/**
	 * {@link ValueBracket}s are 
	 * {@linkplain ValueBracket#queued(pile.utils.SequentialQueue) 
	 * normally} opened and closed while the internal mutex of an {@link AbstractReadListenDependency}
	 * is held. Since they can execute arbitrary user code, certain things, most notably 
	 * {@link Pile#destroy() destroy}ing the {@link AbstractReadListenDependency}, can lead to deadlocks.
     * This flag enables logging a warning if an attempt is made to 
     * destroy a {@link PileImpl} or {@link Independent} while mutices are locked in the same thread.
	 */
	public static final boolean WARN_ON_DESTROY_WHILE_LOCKED = true;
	
	/**
	 * Rename threads for the duration of them performing a {@link Recomputation}.
	 */
	public static final boolean RENAME_RECOMPUTATION_THREADS = !false;

	/**
	 * Log a warning together with a synthetic exception to provide a stack trace.
	 * @param log
	 * @param string
	 */
	public static void warn(Logger log, String string) {
		try {
			throw new Exception(string);
		}catch(Exception x){
			if(log==null)
				x.printStackTrace();
			else
				log.log(Level.WARNING, "Warning, trace: ", x);
		}
		
	}
	
	
	/**
	 * Counter for how many {@link #mutex}es are currently locked by this thread due to
	 * opening or closing {@link ValueBracket}s. This is only used for debugging, when
	 * {@link DebugEnabled#COUNT_BRACKET_LOCKS} is true.
	 */
	public static final ThreadLocal<MutInt> lockedValueMutices=
			DebugEnabled.COUNT_BRACKET_LOCKS
			?ThreadLocal.withInitial(MutInt::new):
				null;
	
	/**
	 * Causes an {@link IllegalStateException} to be thrown from the constructors of
	 * reactive values if they run during a dependency recording {@link Recomputation}.
	 */
	public static final boolean ERROR_ON_CREATE_IN_DYNAMIC_RECOMPUTATION = true;
	
	static final private WeakHashMap<Thread, Object> STOP_REQUESTED = new WeakHashMap<>();
	
	public static boolean isStopRequested(Thread t) {
		synchronized (STOP_REQUESTED) {
			return STOP_REQUESTED.containsKey(t);
		}
	}
	public static void requestStop(Thread t, Object o) {
		synchronized (STOP_REQUESTED) {
			STOP_REQUESTED.put(t, o);
		}
	}
	public static Object clearStopRequested(Thread t) {
		synchronized (STOP_REQUESTED) {
            return STOP_REQUESTED.remove(t);
        }
	}
	public static void requestStop(Thread t) {
		requestStop(t, null);
	}
	public static void stopIfRequested(Thread t) {
		if(isStopRequested(t)) {
            Object message = clearStopRequested(t);
            System.out.println(message);
		}
	}
	public static void stopIfRequested() {
		stopIfRequested(Thread.currentThread());
	}
	
}
