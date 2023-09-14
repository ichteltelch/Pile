package pile.specialized_bool.combinations;

import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;

import pile.aspect.combinations.Pile;
import pile.aspect.combinations.ReadListenDependency;
import pile.impl.Piles;
import pile.interop.exec.StandardExecutors;
import pile.specialized_bool.PileBool;
import pile.specialized_bool.SealBool;

public interface ReadListenDependencyBool extends ReadListenDependency<Boolean>, ReadListenValueBool, ReadDependencyBool{
	public default SealBool fallback(Boolean v){
		return Piles.fallback(this, v);
	}
	public ReadListenDependencyBool setName(String s);

	/**
	 * Repeatedly executes the given job whenever this reactive boolean is true.
	 * @param intervalMillis
	 * @param job
	 * @return A {@link Pile} (which is actually holding a {@link ScheduledFuture})
	 * that can be invalidated and/or autovalidation-suppressed 
	 * to pause this behavior until it recomputes,
	 * or can be destroyed to stop this behavior forever.
	 */
	public default Pile<?> whileTrueRepeat(long intervalMillis, Runnable job){
		return whileTrueRepeat(intervalMillis, false, StandardExecutors.delayed(), job);
	}
	/**
	 * Delegates to {@link PileBool#whileTrueRepeat(ReadListenDependency, long, boolean, ScheduledExecutorService, Runnable)}
	 */
	public default Pile<?> whileTrueRepeat(long intervalMillis, boolean mayInterrupt, ScheduledExecutorService scheduler, Runnable job){
		return PileBool.whileTrueRepeat(this, intervalMillis, mayInterrupt, scheduler, job);
	}


}
