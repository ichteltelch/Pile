package pile.aspect.combinations;

import java.util.Set;
import java.util.function.BiPredicate;

import pile.aspect.AutoValidationSuppressible;
import pile.aspect.CanAutoValidate;
import pile.aspect.CorrigibleValue;
import pile.aspect.Dependency;
import pile.aspect.Depender;
import pile.aspect.HasAssociations;
import pile.aspect.HasInfluencers;
import pile.aspect.LazyValidatable;
import pile.aspect.recompute.Recomputation;
import pile.aspect.recompute.Recomputer;
import pile.aspect.transform.BehaviorDuringTransform;
import pile.aspect.transform.TransformHandler;
import pile.impl.DebugCallback;
import pile.impl.PileImpl;
import pile.interop.debug.DebugEnabled;
import pile.interop.exec.StandardExecutors;
import pile.specialized_bool.combinations.ReadListenDependencyBool;

/**
 * This Interface bundles all the aspects that make a {@link PileImpl}.
 * A specialization of this interface for type [T] should have its own version of this
 * called Pile[T], while the actual implementation should be called Pile[T]Impl.
 * @author bb
 *
 * @param <E>
 */
public interface Pile<E> 
extends ReadWriteListenDependency<E>, 
WriteDepender<E>,
CorrigibleValue<E>,
HasAssociations,
HasInfluencers,
LazyValidatable,
CanAutoValidate,
TransformableDependency<E>,
AutoValidationSuppressible.Single
{
	public Pile<E> setName(String s);
	E set(E v);
	public default Pile<E> setNull() {
		set(null);
		return this;
	}
	public ReadListenDependencyBool computing();
	public boolean isComputing();

	@Override
	default ReadListenDependencyBool validNull() {
		return ReadWriteListenDependency.super.validNull();
	}
	public Set<Dependency> changedDependencies();
	/**
	 * Set the {@link Recomputer} to use for this {@link Pile}.
	 * @param recomputer
	 */
	public void _setRecompute(Recomputer<E> recomputer);
	/**
	 * Test whether there is a pending or ongoing Recomputation.
	 * This includes dependency scouting Recomputations.
	 * @return
	 */
	public boolean _isRecomputationPendingOrOngoing();
	/**
	 * Wait till the currently ongoing recomputation is finished, it there is one.
	 * @throws InterruptedException
	 * @see Recomputation#join()
	 */
	public void joinRecomputation() throws InterruptedException;
	/**
	 * Wait till the currently ongoing recomputation is finished, it there is one,
	 * or until the timeout has passed.
	 * @param timeoutMillis The maximum amount of time to wait for the recomputation to finish, 
	 * in milliseconds.
	 * @throws InterruptedException
	 * @see Recomputation#join(long)
	 */
	public void joinRecomputation(long timeoutMillis) throws InterruptedException;
	/**
	 * Set the {@link TransformHandler} to use for this {@link Pile}.
	 * @param t
	 */
	public void _setTransformHandler(TransformHandler<E> t);
	/**
	 * 
	 * @return whether a {@link Recomputer} has been set for this {@link Pile}.
	 */
	public boolean _isRecomputerDefined();
	/**
	 * Set the equivalence relation that is used to decide whether a prospective new
	 * value differs from the old value.
	 */
	public void _setEquivalence(BiPredicate<? super E, ? super E> equivalence);
	/**
	 * Get the <q>observed validity</q> status of this {@link Pile}. This does not trigger
	 * recording a dependency on the {@link #validity()} or cause the validity to become observed.
	 * @return
	 */
	public abstract boolean observedValid();
	/**
	 * Set a callback whose methods are invoked at the appropriate times, depending on what happens
	 * to this {@link Pile}. {@link DebugEnabled#DE} must be <code>true</code> for this to 
	 * have any effect. 
	 */
	public void _setDebugCallback(DebugCallback dc);
	/**
     * Print a stack trace to standard error that was taken from within the consrtuctor
     * of this {@link Pile}. This is meant as a debugging helper for identifying 
     * where a problematic Pile was created if name, owner or other information available
     * to the debugger are insufficient to determine what part of the code 
     * is responsible for its behavior 
     * 
     * {@link DebugEnabled#DE} must be <code>true</code> for this to 
	 * have any effect. 
     */
	public void _printConstructionStackTrace();
	/**
	 * Set the <q>owner</q> or <q>parent</q> of this {@link Pile}.
	 * This is mainly for debugging purposes so you know what larger structure the {@link Pile}
	 * belongs to, but sometimes can also be used to keep a strong reference to a 
	 * value that this one is derived from, so as to prevent it from being garbage collected.
	 * @param o
	 */
	public void _setOwner(Object o);
	/**
	 * Cancel the currently pending or even ongoing recomputation, if pending.
	 * @param cancelOngoing
	 * @return
	 */
	public boolean cancelPendingRecomputation(boolean cancelOngoing);
	/**
	 * Deeply revalidate all {@link Depender}s of this {@link Pile} that are marked as need deep revalidation.
	 * This method should only be called from within the default implementation of
	 * {@link #deepRevalidate()} 
	 */
	void __fireDeepRevalidate();
	/**
	 * Revalidate this pile and deeply revalidate all {@link Depender}s of this {@link Pile} that are marked as need deep
	 */
	default void deepRevalidate() {

		try {
			__beginTransaction();
			revalidate();
			__fireDeepRevalidate();
		}finally {
			__endTransaction();
		}
	}
	@Override
	public default boolean destroyIfMarkedDisposable() {
		if(isMarkedDisposable()) {
			destroy();
			return true;
		}else {
			return false;
		}
	}
	/**
	 * Request that the value be computed, for use with lazy recomputation mode.
	 * This works by calling the {@link #get()} method asynchronously if this {@link Pile} is not valid.
	 * @param recordRead Whether to record the dependency as being read by the current {@link Recomputation}.
	 */
	public default void lazyRequest(boolean recordRead) {
		if(recordRead)
			recordRead();
		if(!isValid()) {
			StandardExecutors.unlimited().execute(this::get);
		}
	}
	public boolean isSealed();
	
	
	@Override
	default Pile<E> asDependency() {
		return this;
	}
	public Pile<E> setBehaviorDuringTransform(BehaviorDuringTransform b);

}

