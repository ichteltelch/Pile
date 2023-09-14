package pile.aspect.recompute;

import java.util.function.Consumer;

import pile.aspect.Dependency;
import pile.aspect.Depender;
import pile.impl.PileImpl;

/**
 * An interface for specifying how to fulfill a {@link Recomputation}.
 * It also allows to configure some behavior with respect to dependency scouting.
 * @author bb
 *
 * @param <E>
 */
@FunctionalInterface
public interface Recomputer<E> extends Consumer<Recomputation<E>>{
	/**
	 * @return whether <em> dependency scouting </em> should be used:
	 * if the Value is {@link PileImpl#set(Object) set} explicitly or
	 * is {@link PileImpl#permaInvalidate() invalidated}, the Recomputer 
	 * would be given a {@link Recomputation} in 
	 * {@linkplain} Recomputation#isDependencyScout() dependency scouting mode}
	 * in order to reconfigure the dependencies (typically via the 
	 * {@linkplain} Recomputation#recordDependendy(Dependency) dynamic dependency}
	 * feature for when the {@link PileImpl} needs to be recomputed.
	 */
	default boolean useDependencyScouting() {return false;}
	/**
	 * 
	 * @param d If this parameter is <code>null</code>, return <code>true</code>
	 * except if it is certain that <code>false</code> would returned for all arguments.
	 * @return Whether to run dependency scouting if it is enabled, another 
	 * {@link Dependency} has finished changing, but the {@link PileImpl} depends
	 * on the given invalid non-essential {@link Dependency} <code>d</code>.
	 * This may be necessary if {@code d} can become long-term invalid but other 
	 * dependencies my change the execution flow in the {@link Recomputer} so that
	 * {@code d} is actually no longer needed. In that case, {@code d}'s invalidity
	 * would prevent the {@link PileImpl} from being recomputed even though 
	 * the {@link PileImpl} should no longer depend on it. 
	 * Dependency scouting then should be run to 
	 * remove the spurious {@link Dependency}/ies.
	 */
	default boolean useDependencyScoutingIfInvalid(Dependency d) {return useDependencyScouting();}
	
	/**
	 * Test whether it is OK to remove a {@link Dependency} from a {@link Depender}.
	 * The default implementation returns <code>true</code> iff the {@link Dependency}
	 * is non-essential to the {@link Depender}.
	 * @param dy
	 * @param dr
	 * @return
	 */
	default boolean mayRemoveDynamicDependency(Dependency dy, Depender dr) {
		return !dr.isEssential(dy);
	}
}
