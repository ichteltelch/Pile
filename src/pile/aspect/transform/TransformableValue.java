package pile.aspect.transform;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import pile.aspect.CorrigibleValue;
import pile.aspect.Dependency;
import pile.aspect.Depender;
import pile.aspect.combinations.ReadWriteValue;
import pile.aspect.suppress.Suppressor;
import pile.aspect.suppress.Suppressor.SuppressMany;
import pile.aspect.transform.TransformHandler.TypedReaction;
import pile.interop.exec.StandardExecutors;

/**
 * The aspect of a value that it can undergo covariant transformations.
 * If a value is transformed, all its {@link Depender}s will be asked to transform, too, unless
 * the {@link TransformReaction} is {@link TransformReaction#IGNORE}.
 * A value asked to transform will ask its {@link TransformHandler} for a {@link TransformReaction}.
 * Then, depending on their reactions, the {@link TransformableValue}s will enter transactions and possibly 
 * transform or recompute their values.
 * 
 * <p>
 * Warning: The transforming feature is quite primitive. 
 * It does what I need it to do, but it could be much more sophisticated,
 * such as supporting multiple concurrent transformations in overlapping parts of the graph,
 * or use of homomorphisms for forwarding transform requests, ans so on.
 * @author bb
 * 
 *
 * @param <E>
 */
public interface TransformableValue<E> extends ReadWriteValue<E>{
	/**
	 * Get the {@link TransformHandler} for this {@link TransformableValue} and 
	 * the given transform object
	 * @param transform
	 * @return The handler for that kind of transform, or <code>null</code> if this kind of transform is not handled
	 */
	public TransformHandler<E> getTransformHandler(Object transform);

	/**
	 * Cast this {@link TransformableValue} to {@link Dependency},
	 * or return <code>null</code> if it isn't one.
	 * @return
	 */
	public Dependency asDependency();

	/**
	 * Cast this {@link TransformableValue} to {@link Depender},
	 * or return <code>null</code> if it isn't one.
	 * @return
	 */
	public Depender asDepender();

	/**
	 * Start a special transaction indicating hat the value is undergoing a transformation,
	 * and maintain it until the returned {@link Suppressor} is {@link Suppressor#release() released}.
	 * <br>
	 * {@link TransformingException} transaction are not to be confused with standard
	 * {@link #transaction() transaction}s; they merely mark the object as undergoing transformation.
	 * While a transform transaction is active on an object, certain operations block until the transform is done,
	 * most importantly {@link #set(Object)} and {@link #permaInvalidate()}. As a consequence, 
	 * avoid calling these methods from code running during transformations
	 * @return
	 * @throws InterruptedException
	 */
	default public Suppressor transformTransaction()  throws InterruptedException{
		Suppressor ret = Suppressor.wrap(this::endTransformTransaction);
		beginTransformTransaction();
		return ret;
	}
	/**
	 * Start a transaction indicating hat the value is undergoing a transformation.
	 * This method must always e paired with {@link #endTransformTransaction()}.
	 * Best use {@link #transformTransaction()} to ensure that.
	 * @throws InterruptedException
	 */
	void beginTransformTransaction() throws InterruptedException;
	/**
	 * End a special transaction indicating hat the value is undergoing a transformation.
	 * This method must always e paired with {@link #beginTransformTransaction()}.
	 * Best use {@link #transformTransaction()} to ensure that.
	 * @throws InterruptedException
	 */
	void endTransformTransaction();


	/**
	 * Collect the {@link TransformReaction}s of this {@link TransformableValue}, and,
	 * if it is a {@link Dependency}, of all {@link Depender}s that depend on it transitively.
	 * If a call to {@link #getTransformHandler(Object)} return <code>null</code>, 
	 * the {@link TransformReaction} is assumed to be {@link TransformReaction#RECOMPUTE}.
	 * If a {@link TransformReaction} is <code>null</code>, {@link TransformReaction#IGNORE}
	 * is used instead.
	 * @param transform The Object describing the transformation
	 * @param reactions The reaction collected so far. If this {@link TransformableValue} already is
	 * a key in the map, it is ignored.
	 * @param tts 
	 * @throws InterruptedException 
	 */
	public static final Object GLOBAL_TRANSFORM_COLLECT_MUTEX = new Object();
	default public void collectTransformReactions(
			Object transform, 
			Map<? super TransformableValue<?>, ? super TransformReaction> reactions, SuppressMany tts,
			SuppressMany releaseAfterCollect
			) throws InterruptedException {
		if(reactions.containsKey(this))
			return;
		Suppressor tt;
		tts.makePlaceFor1().add(tt = transformTransaction());
		TransformHandler<E> th = getTransformHandler(transform);
		TransformReaction r;
		if(th==null)
			r=TransformReaction.RECOMPUTE;
		else {
			r=th.react(this, transform);
			if(r==null)
				r=TransformReaction.IGNORE;
		}
		reactions.put(this, r);
		//		if(r.getType()==ReactionType.IGNORE)
		switch(r.getType()) {
		case IGNORE:
		case RECOMPUTE:
			releaseAfterCollect.makePlaceFor1().add(tt);
			break;
		case JUST_PROPAGATE_NO_TRANSACTION:
		case JUST_PROPAGATE_WITH_TRANSACTION:
		case MUTATE:
		case REPLACE:
			Dependency d = asDependency();
			if(d!=null) {
				d.giveDependers(dd->{
					if(dd instanceof TransformableValue)
						try {
							((TransformableValue<?>)dd).collectTransformReactions(transform, reactions, tts, releaseAfterCollect);
						} catch (InterruptedException e) {
							StandardExecutors.interruptSelf();
						}
				});
				StandardExecutors.checkInterrupt();
			}
			break;
		}
	}

	/**
	 * Transform the Value. This first collects the {@link TransformReaction}s of this value and all
	 * its transitive {@link Depender}s (The transformation request is not propagated from a {@link Dependency}
	 * if its reaction was {@link TransformReaction#IGNORE}).
	 * The transactions are started and the transformations are run in parallel {@link Thread}s
	 * (except if they specify that they are {@link TransformReaction#fast() fast}; these are run 
	 * sequentially in the current {@link Thread}). The the transactions are ended, causing those values
	 * whose reactions were {@link TransformReaction#RECOMPUTE} to revalidate. 
	 * @param transform
	 * @param afterCollect
	 * @throws InterruptedException
	 */
	default public void transform(Object transform, Runnable afterCollect) throws InterruptedException {
		Map<TransformableValue<?>, TransformReaction> reactions=new HashMap<>();
		SuppressMany tts = Suppressor.many(); 
		try(Suppressor tts2=tts; SuppressMany rac=Suppressor.many()){
			synchronized(GLOBAL_TRANSFORM_COLLECT_MUTEX) {
				collectTransformReactions(transform, reactions, tts, rac);
			}
			rac.release();
			ArrayList<Runnable> asyncJobs=new ArrayList<>();
			ArrayList<Runnable> syncJobs=new ArrayList<>();
			Thread ct = Thread.currentThread();
			String oldName = ct.getName();

			try(SuppressMany recomputationSuppressed=Suppressor.many();
					SuppressMany ts = Suppressor.many()){
				ct.setName("Transformation main thread");

				for(Map.Entry<TransformableValue<?>, TransformReaction> e: reactions.entrySet()) {
					TransformableValue<?> v = e.getKey();
					TransformReaction r = e.getValue();
					switch(r.getType()) {
					case IGNORE:
					case JUST_PROPAGATE_NO_TRANSACTION:
						break;
					case JUST_PROPAGATE_WITH_TRANSACTION:
						ts.makePlaceFor1().add(v.transaction());
						break;
					case RECOMPUTE:
						recomputationSuppressed.makePlaceFor1().add(v.transaction());
						v.runTransformRevalidate();
						break;
					case MUTATE: 
					case REPLACE:
						(r.fast()?syncJobs:asyncJobs).add(r);
						ts.makePlaceFor1().add(v.transaction());
						break;
					}
				}
				Runnable syncJob = ()->syncJobs.forEach(Runnable::run);
				StandardExecutors.safe(afterCollect);
				StandardExecutors.parallel(asyncJobs, syncJob);

				// End the transform transactions before the regular transactions, in case that ending the 
				// regular transactions invokes code (recomputation or event handlers or so) 
				// that would block while the transform transactions are active
				tts.release();
			}finally {
				ct.setName(oldName);
			}
		}
	}


	//	public void runTransform(UntypedReaction reaction);
	public void runTransformRevalidate();
	/**
	 * Actually transform the value based on a {@link MutateReaction} or a {@link ReplaceReaction}.
	 * @param reaction
	 */
	public void runTransform(TypedReaction<E> reaction);

	/**
	 * React in a certain way it a transform is ongoing, based on an implementation specific
	 * {@link BehaviorDuringTransform}
	 * @throws TransformingException Possibly
	 */
	public void checkForTransformEnd();

	/**
	 * React in a certain way it a transform is ongoing, based on a given {@link BehaviorDuringTransform}
	 * @throws TransformingException Possibly
	 * @param bdt2
	 */
	public void checkForTransformEnd(BehaviorDuringTransform bdt2);


	@Override E set(E v);
	@Override public default TransformableValue<E> setNull() {
		set(null);
		return this;
	}

	/**
	 * {@link CorrigibleValue#applyCorrection(Object)}
	 * @param p
	 * @return
	 */
	public E applyCorrection(E p);
	/**
	 * This is called after a transformation has mutated the value.
	 * It should fire a {@link TransformValueEvent}
	 */
	public void valueTransformMutated();


}
