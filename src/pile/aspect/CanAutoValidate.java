package pile.aspect;

import java.util.HashSet;

import pile.specialized_bool.combinations.ReadListenDependencyBool;

/**
 * Interface for objects that have a definite and easily accessible "is currently set to autovalidate"
 * property. "set to autovalidate" means that the object will try to recompute its value as soon as
 * all dependencies are valid and, in case validation is triggered lazily, it is requested.
 * @author bb
 *
 */
public interface CanAutoValidate extends AutoValidationSuppressible{
	/**
	 * Test whether this object is currently set to autovalidate
	 * @return
	 */
	public boolean isAutoValidating();
	
	/**
	 * A reactive boolean that can be used to observe whether this object is currently set to autovalidate.
	 * @return
	 */
	
	public ReadListenDependencyBool autoValidating();
	
	/**
	 * Only to be used from {@link #autoValidate()}.
	 * This set should be empty or null whenever no {@link #autoValidate()} call is on the stack.
	 * (It is the responsibility of the first {@link #autoValidate()} call to clean up after itself)
	 * Each call to {@link #autoValidate()} should abort itself whenever the {@link Dependency} it was called on
	 * is already an element of this set. Otherwise it should add the {@link Dependency} to the set
	 * and proceed. This ensures that even in branching and rejoining dependency graphs, {@link #autoValidate()}
	 * is called only once per Dependency, Thread and external {@link #autoValidate()}-call.
	 */
	public static ThreadLocal<HashSet<CanAutoValidate>> autoValidationInProgress=new ThreadLocal<>();

	/**
	 * Recompute the value of this value if it is invalid; recursively do the same 
	 * for all transitive {@link Dependency Dependencies}. If this instance is of a subclass that does
	 * not have a an "invalid" status, the call can be ignored.
	 * #see {@link Dependency#autoValidate()}
	 * #see {@link CanAutoValidate#autoValidationInProgress}
	 */
	public void autoValidate();

}
