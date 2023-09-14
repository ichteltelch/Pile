package pile.aspect;

import java.util.HashSet;
import java.util.function.Consumer;

import pile.impl.AbstractReadListenDependency;


/**
 * The aspect of a value concerning its dependency on other values
 * @author bb
 *
 */
public interface Depender {

	/**
	 * Add a single dependency
	 * @param d
	 * @param invalidate whether adding this dependency should trigger a recomputation
	 * @param recordChange whether this {@link Dependency} should be recorded as changed
	 */
	
	public void addDependency(Dependency d, boolean recompute, boolean recordChange);
	/**
	 * Remove a single dependency
	 * @param d
	 * @param invalidate whether removing this dependency should trigger a recomputation
	 * @param recordChange whether this {@link Dependency} should be recorded as changed
	 */
	public void removeDependency(Dependency d, boolean recompute, boolean recordChange);
	
	/**
	 * Add a single dependency
	 * @param d
	 * @param invalidate whether adding this dependency should trigger a recomputation
	 * and the {@link Dependency} should be recorded as changed
	 */
	
	public default void addDependency(Dependency d, boolean recomputeAndRecordChanged) {
		addDependency(d, recomputeAndRecordChanged, recomputeAndRecordChanged);
	}
	/**
	 * Remove a single dependency
	 * @param d
	 * @param invalidate whether removing this dependency should trigger a recomputation
	 * {@link Dependency} should be recorded as changed
	 */
	public default void removeDependency(Dependency d, boolean recomputeAndRecordChanged) {
		removeDependency(d, recomputeAndRecordChanged, recomputeAndRecordChanged);
	}
	/**
	 * Test if this currently depends on the given Dependency
	 * @param d
	 */
	public boolean dependsOn(Dependency d);
	
	
	/**
	 * Add a single dependency and trigger a recomputation
	 * @param d
	 */
	public default void addDependency(Dependency d) { addDependency(d, true); }
	/**
	 * Remove a single dependency and trigger a recomputation
	 * @param d
	 */
	public default void removeDependency(Dependency d) { removeDependency(d, true); }
	
	/**
	 * Add multiple dependencies and trigger a recomputation
	 * The dependencies are recorded as changed
	 * @param ds
	 */
	public default void addDependency(Dependency... ds) {
		for(int i=0; i<ds.length; ++i) {
			Dependency d = ds[i];
			if(d!=null)
				addDependency(d, i==ds.length-1, true);
		}
	}
	/**
	 * Remove multiple dependencies and trigger a recomputation
	 * The dependencies are recorded as changed
	 * @param ds
	 */
	public default void removeDependency(Dependency... ds) {
		for(int i=0; i<ds.length; ++i) {
			Dependency d = ds[i];
			if(d!=null)
				removeDependency(d, i==ds.length-1, true);
		}
	}
	/**
	 * Add multiple dependencies and optionally trigger a recomputation
	 * The dependencies are recorded as changed
	 * @param ds
	 */
	public default void addDependency(boolean recompute, Dependency... ds) {
		for(int i=0; i<ds.length; ++i) {
			Dependency d = ds[i];
			if(d!=null)
				addDependency(d, recompute && i==ds.length-1, true);
		}
	}

	/**
	 * Called by a dependency when it is entering a transaction that might change it.
	 * <br>
	 * Warning: do not call this method from anywhere else!
	 * @param d
	 */
	void dependencyBeginsChanging(Dependency d, boolean wasValid, boolean invalidate);
	/**
	 * Called by a dependency when it has finished a transaction that might have changed it
	 * <br>
	 * Warning: do not call this method from anywhere else!
	 * @param d
	 * @param changed whether the value or validity of the dependency actually changed
	 */
	void dependencyEndsChanging(Dependency d, boolean changed);
	
	/**
	 * Call the {@link Consumer#accept(Object)} method for each {@link Dependency} 
	 * that this {@link Depender} depends on
	 * @param out
	 */
	public void giveDependencies(Consumer<? super Dependency> out);

	/**
	 * {@link #destroy() Destroy} this object after destroying all those that depend on it transitively 
	 * through essential dependency relations
	 * @see #setDependencyEssential(boolean, Dependency)
	 */
	public void deepDestroy();
	
	/**
	 * Change whether a dependency is essential. 
	 * If a dependency is essential, its {@link #destroy() destruction}
	 * triggers destruction of this value.
	 * @param essential
	 * @param d
	 */
	public void setDependencyEssential(boolean essential, Dependency d);
	/**
	 * Change whether dependencies is essential.
	 * This utility method simply calls {@link #setDependencyEssential(boolean, Dependency)} 
	 * in a {@code for} loop. 
	 * @param essential
	 * @param ds
	 */
	public default void setDependencyEssential(boolean essential, Dependency... ds) {
		for(Dependency d: ds)
			setDependencyEssential(essential, d);
	}
	
	/**
	 * If this is a {@link Sealable} value, its dependencies can ordinarily no longer be manipulated
	 * after it has been sealed. If you still need to change them afterwards, make sure to get a proxy
	 * object from this method that allows you to call the blocked methods.
	 * @return
	 */
	public Depender getPrivilegedDepender();
	/**
	 * Only to be used from code that propagates observation of long term/observed invalidity.
	 * This set should be empty or null whenever no such call is on the stack.
	 * (It is the responsibility of the first such call to clean up after itself)
	 * Each call to should abort itself whenever the object it was called on
	 * is already an element of this set. Otherwise it should add the object to the set
	 * and proceed. This ensures that even in branching and rejoining dependency graphs,  
	 * the code for informing about long term invalidity is called only once per 
	 * object, Thread and external call.
	 * @see AbstractReadListenDependency#informLongTermInvalid
	 */
	static ThreadLocal<HashSet<Object>> informingLongTermInvalid=new ThreadLocal<>();

	/**
	 * Called by a {@link Dependency} of this {@link Depender) if it has become long term invalid.
	 * If this depender is invalid itself, it should become long term invalid too and make its
	 * invalidity observed.
	 * @see #informingLongTermInvalid
	 * @param d
	 */
	public void __dependencyBecameLongTermInvalid(Dependency d);
	/**
	 * Destroy this object. It should not be used afterwards
	 */
	public void destroy();
	/**
	 * Query whether a {@link Dependency} of the {@link Depender} is currently essential
	 * @see #setDependencyEssential(boolean, Dependency)
	 * @param value
	 * @return
	 */
	public boolean isEssential(Dependency value);
	/**
	 * Called by a {@link Dependency} of this {@link Depender} when it has become valid
	 * @param d
	 */
	public void __dependencyIsNowValid(Dependency d);
	
	/**
	 * Re-validate this {@link Depender} and all its transitive dependers that currently need
	 * deep revalidation because they or one of their transitive {@link Depender}s have
	 * become valid while some of their dependencies were invalid.
	 * @see Dependency#__dependerNeedsDeepRevalidate(Depender, boolean)
	 * @param d
	 */
	public void deepRevalidate(Dependency d);
	/**
	 * 
	 * @return true if this {@link Depender} has been destroyed and should not be used anymore
	 */
	public boolean isDestroyed();
	
	/**
	 * @return all {@link Dependency}s that this {@link Depender} currently depends on
	 */
	public Dependency[] getDependencies();

}
