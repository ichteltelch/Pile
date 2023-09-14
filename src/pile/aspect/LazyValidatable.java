package pile.aspect;

import java.util.HashSet;

import pile.impl.PileImpl;

/**
 * The aspect of a {@link PileImpl} that is can lazy-validate, that is, if it is lazy-validating,
 * recomputation is not started until the value is actually requested. 
 * 
 * <p>
 * Warning: The lazy-validating feature of Piles is not mature and will probably change in the future!
 * @author bb
 *
 */
public interface LazyValidatable {
	/**
	 * Only to be used from {@link #lazyValidate()}.
	 * This set should be empty or null whenever no {@link #lazyValidate()} call is on the stack.
	 * (It is the responsibility of the first {@link #lazyValidate()} call to clean up after itself)
	 * Each call to {@link #lazyValidate()} should abort itself whenever the {@link LazyValidatable} 
	 * instance it was called on is already an element of this set. 
	 * Otherwise it should add the instance to the set and proceed. 
	 * This ensures that even in branching and rejoining dependency graphs, {@link #lazyValidate()}
	 * is called only once per object, Thread and external {@link #autoValidate()}-call.
	 */
	static ThreadLocal<HashSet<LazyValidatable>> lazyValidatingItt=new ThreadLocal<>();

	/**
	 * @return Whether this object is lazy-validating
	 */
	public boolean isLazyValidating();
	/**
	 * Set Whether this object is lazy-validating.
	 * If it was lazy-validating and now should not be, and no other reasons speak against it,
	 * an attempt at recomputation should be made.
	 * @param newState
	 */
	public void setLazyValidating(boolean newState);
	/**
	 * If the value is already valid or is currently recomputing itself, this method does nothing.
	 * Else it recursively calls itself on all {@link Dependency Dependencies} of this object
	 * and then triggers recomputation of the value if the only reason is has not been computed already
	 * is that the lazy-validating status of this object has been set to true or this object
	 * has been invalidated explicitly.
	 * Note: If lazy-validating the dependencies has not made all of them valid immediately, this 
	 * object's value may not be recomputed.
	 */
	public void lazyValidate();
//	public boolean couldBeValid(boolean onlyIfLazyValidating);
//	public default boolean couldBeValid() {
//		return couldBeValid(true);
//	}
}
