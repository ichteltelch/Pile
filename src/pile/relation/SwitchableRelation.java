package pile.relation;

import pile.aspect.suppress.Suppressor;
import pile.specialized_bool.combinations.ReadListenDependencyBool;

/**
 * Superinterface for relations that can be switched of and/or suppressed
 * <br>
 * @author bb
 * @see ImplSwitchableRelation Here's a useful mixin to help with implementing
 * {@link SwitchableRelation}.
 *
 * @param <Sw> Type of some extra object that can be used for switching the relation in addition to
 * the {@link Suppressor}s obtained from {@link #disable()}.
 */
public interface SwitchableRelation<Sw> {
	/**
	 * Disable the relation for as long as the returned {@link Suppressor} is not 
	 * {@link Suppressor#release() release}d, or longer if there are more {@link Suppressor}s.
	 * Or the "should be enabled" value is false.
	 * When the relation becomes active again after the last {@link Suppressor} has been
	 * released, implementation specific measures must be employed to re-establish the validity 
	 * of the relation.
	 * @return
	 */
	public Suppressor disable();
	/**
	 * 
	 * @return A reactive value that tells whether this
	 */
	public ReadListenDependencyBool isEnabled();
	/**
	 * Get the extra object that controls the activity of this relation in addition to the
	 * {@link Suppressor}s
	 * @return
	 */	
	public Sw shouldBeEnabled();
	/**
	 * Change the extra object that controls the activity of this relation in addition to the
	 * {@link Suppressor}s
	 * @return
	 */	
	public void setShouldBeEnabled(Sw sbe);
}
