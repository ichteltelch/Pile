package pile.aspect;

import java.util.function.Consumer;
import java.util.function.Function;

import pile.aspect.recompute.DependencyRecorder;
import pile.aspect.recompute.Recomputations;
import pile.aspect.suppress.Suppressor;
import pile.interop.debug.DebugEnabled;
import pile.specialized_bool.combinations.ReadListenDependencyBool;

/**
 * The aspect of a value that other values can depend on it.
 * @author bb
 *
 */
public interface Dependency extends LazyValidatable{
	/**
	 * Reification of {@link #suppressDeepRevalidation()}
	 */
	public static final Function<Dependency, Suppressor> SUPPRESS_DEEP_REVALIDATION
	 = Dependency::suppressDeepRevalidation;
	
	/**
	 * Empty array
	 */
	public static final Dependency[] NO_DEPENDENCIES = {};
	/**
	 * @see #suppressDeepRevalidation()
	 * @return Whether deep revalidation is currently suppressed
	 */
	boolean isDeepRevalidationSuppressed();
	/**
	 * Suppress the deep revalidation behavior of this dependency.
	 * Deep revalidation happens when a value that is invalid, but 
	 * transitively has valid {@link Depender}s, is set (but not if recomputed).
	 * Deep revalidation the revalidates all dependencies.
	 * If deep revalidation is suppressed, setting this value neither 
	 * triggers it nor propagates the recursive revalidation if one of its 
	 * dependencies triggers deep revalidation
	 * TODO: The last bit should maybe behave different?
	 * @return
	 */
	public Suppressor suppressDeepRevalidation();

	/**
	 * Called by a {@link Depender} to inform this {@link Dependency} that it has started depending on it
	 * <br>
	 * Warning: do not call this method from anywhere else!
	 * @param d
	 */
	void __addDepender(Depender d);
	/**
	 * Called by a {@link Depender} to inform this {@link Dependency} that it has stopped depending on it
	 * <br>
	 * Warning: do not call this method from anywhere else!
	 * @param d
	 */
	void __removeDepender(Depender d);
	/**
	 * Call the {@link Consumer#accept(Object)} method for each {@link Depender} 
	 * that depends on this {@link Dependency}.
	 * @param out
	 */
	public void giveDependers(Consumer<? super Depender> out);
	
	/**
	 * @return Whether this dependency is currently valid.
	 * This method may block on internal mutexes and may cause other actions, such as
	 * triggering a change in observed validity.
	 * If it is invalid, its {@link Depender}s should not recompute themselves. 
	 */
	
	public boolean isValid();
	
	/**
	 * For debugging purposes only.
	 * @return A string description of the role or structure of this {@link Dependency}. 
	 * Must not be <code>null</code>; if no name has been defined, return "?".
	 */
	public String dependencyName();
	

	/**
	 * Recompute the value of this dependency if it is invalid; recursively do the same 
	 * for all transitive {@link Dependency Dependencies}. If this instance is of a subclass that does
	 * not have a an "invalid" status, the call can be ignored.
	 * #see {@link CanAutoValidate#autoValidate()}
	 * #see {@link CanAutoValidate#autoValidationInProgress}
	 */
	public void autoValidate();
	/**
	 * 
	 * @return Whether this dependency has been destroyed and should no longer be used.
	 */
	boolean isDestroyed();
	/**
	 * 
	 * @return Whether there is currently a transaction active on this {@link Dependency}
	 */
	public boolean isInTransaction();
	/**
	 * 
	 * @return A (lazily initialized) reactive boolean indicating whether there is currently
	 * a transaction active on this {@link Dependency}
	 */
	public ReadListenDependencyBool inTransactionValue();
	/**
	 * For debugging: If {@link DebugEnabled#DE} is <code>true</code>, this object may have 
	 * recorded a stack trace when it was created. Print this stack trace to the console.
	 * This is for finding a trouble source when other metadata to track it down it are not available.
	 */
	void _printConstructionStackTrace();
	/**
	 * Simply query the validity status of this object, without any locking or triggered actions.
	 * @return
	 */
	boolean isValidAsync();
	
	/**
	 * Change whether a {@link Depender} of this {@link Dependency} needs a call to
	 * {@link Depender#deepRevalidate(Dependency)} when certain things happen to the Dependency.
	 * A {@link Depender} needs this call if it or one of its transitive {@link Depender}s 
	 * has been made valid despite some of its {@link Dependency Dependencies} being invalid. 
	 * @param d If this parameter is not a {@link Depender} of this, it should be ignored.
	 * @param needs
	 */
	void __dependerNeedsDeepRevalidate(Depender d, boolean needs);
	
	/**
	 * This must only be called by {@link Depender#setDependencyEssential(boolean, Dependency)} 
	 * or {@link Depender#setDependencyEssential(boolean, Dependency...)} to inform the Dependency
	 * that its essential status has changed for that {@link Depender}.
	 * @param value
	 * @param essential
	 */
	void __setEssentialFor(Depender value, boolean essential);
	
	/**
	 * Record a read access to this Dependency in the current {@link DependencyRecorder}
	 * @see Recomputations#getCurrentRecorder()
	 */
	default void recordRead() {
		DependencyRecorder reco = Recomputations.getCurrentRecorder();
		if(reco!=null)
			reco.recordDependency(this);
	}
	

	
}
