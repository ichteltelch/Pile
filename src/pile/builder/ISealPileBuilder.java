package pile.builder;

import java.util.Collections;
import java.util.Set;
import java.util.function.BiPredicate;
import java.util.function.Consumer;
import java.util.function.Function;

import pile.aspect.Dependency;
import pile.aspect.WriteValue;
import pile.aspect.ReadValue.InvalidValueException;
import pile.aspect.combinations.ReadDependency;
import pile.aspect.combinations.ReadListenDependency;
import pile.aspect.combinations.ReadListenValue;
import pile.aspect.combinations.ReadWriteDependency;
import pile.aspect.combinations.ReadWriteListenDependency;
import pile.aspect.combinations.ReadWriteListenValue;
import pile.aspect.listen.RateLimitedValueListener;
import pile.aspect.listen.ValueListener;
import pile.aspect.recompute.GenericDependencyRecorder;
import pile.aspect.recompute.Recomputation;
import pile.aspect.recompute.Recomputations;
import pile.aspect.suppress.MockBlock;
import pile.impl.AbstractReadListenDependency;
import pile.impl.SealPile;
import pile.specialized_bool.combinations.ReadListenValueBool;
import pile.utils.Functional;
import pile.utils.WeakCleanupWithRunnable;

/**
 * Interface for builders of {@link SealPile}s.
 * @author bb
 *
 * @param <Self>
 * @param <V>
 * @param <E>
 */
public interface ISealPileBuilder<Self extends ISealPileBuilder<Self, V, E>, V extends SealPile<E>, E> 
extends IPileBuilder<Self, V, E>, ISealableBuilder<Self, V, E>{
	/**
	 * Configure the {@link #valueBeingBuilt()} to follow the given leader with a certain delay.
	 * During the delay, it will be invalid.
	 * If it is set explicitly, the leader is set too and the the {@link #valueBeingBuilt()} immediately
	 * takes on the new value of the leader without announcing long term invalidity.
	 * @param delay
	 * @param leader
	 * @return
	 */
	public default Self setupDelayed(long delay, ReadWriteListenDependency<E> leader) {
		BiPredicate<? super E, ? super E> eq = leader._getEquivalence();
		ThreadLocal<Boolean> doDelay = new ThreadLocal<>();
		leader.bequeathBrackets(false, valueBeingBuilt());
		if(leader.isValid())
			try {
				init(leader.getValidOrThrow());
			} catch (InvalidValueException e) {
			}
		return recompute(leader)
				.dependOn(true, leader)
				.delay(delay).setDelaySwitch(()->Boolean.FALSE.equals(doDelay.get()))
				.equivalence(eq)
				.seal(v->{
					Boolean oldDoDelay = doDelay.get();
					try {
						doDelay.set(false);
						leader.set(v);
					}finally {
						if(oldDoDelay==null)
							doDelay.remove();
						else
							doDelay.set(oldDoDelay);
					}
				});
	}
	/**
	 * Configure the {@link #valueBeingBuilt()} to follow the given leader with a certain delay.
	 * During the delay, it will be invalid.
	 * If cannot be set explicitly.
	 * @param delay
	 * @param leader
	 * @return
	 */
	public default Self setupDelayedRO(long delay, ReadWriteListenDependency<E> leader) {
		BiPredicate<? super E, ? super E> eq = leader._getEquivalence();
		if(leader.isValid())
			try {
				init(leader.getValidOrThrow());
			} catch (InvalidValueException e) {
			}
		return recompute(leader)
				.dependOn(true, leader)
				.delay(delay)
				.equivalence(eq)
				.seal();
	}

	/**
	 * Configure the {@link #valueBeingBuilt() value being built}
	 * to dereference the given {@link ReadDependency}-valued {@link ReadDependency}.
	 * @param <C>
	 * @param derefThis
	 * @return
	 */
	public default <C extends ReadDependency<? extends E>> Self setupDeref(
			ReadDependency<? extends C> derefThis
			) {
		V putHere = valueBeingBuilt();
		if(putHere.avName==null)
			putHere.avName=("* ("+derefThis.dependencyName()+")");
		return setupField(derefThis, false, Functional.id());
	}
	
	/**
	 * Configure the {@link #valueBeingBuilt() value being built} to take on the value of 
	 * a reactive {@link ReadListenDependency value} extracted from of another 
	 * reactive {@link ReadDependency value}
	 * 
	 * @param <C>
	 * @param <I>
	 * @param derefThis Extract from this
	 * @param nullable Whether the {@code extract} function should be called even if its argument is <code>null</code>
	 * @param extract How to extract the field
	 * @return
	 */
	public default 
	<C , I extends ReadDependency<? extends E>>
	Self setupField(		
			ReadDependency<? extends C> derefThis, 
			boolean nullable,
			Function<? super C, ? extends I> extract) 
	{
		V putHere = valueBeingBuilt();
		if(putHere.avName==null)
			putHere.avName=("("+derefThis.dependencyName()+")->?");
		return this
				.dependOn(derefThis)
				.recompute(new Consumer<Recomputation<E>>() {
					public void accept(Recomputation<E> reco) {
						I f;
						C c = derefThis.get();
						f = c == null && !nullable ? null : extract.apply(c);
						if(f==null || f.validity().isFalse()) {
							reco.fulfillInvalid();
							return;
						}
						try {
							reco.fulfill(f.getValidOrThrow());
						} catch (InvalidValueException e) {
							reco.fulfillInvalid();
						}
					}
				})
				.parent(derefThis)
				.dynamicDependencies()
				.essential(derefThis)
				.seal();


	}
	
	/**
	 * Configure the {@link #valueBeingBuilt() value being built} to take on the value of 
	 * a reactive {@link ReadListenDependency value} extracted from of another 
	 * reactive {@link ReadDependency value}
	 * 
	 * This implementation avoids unnecessary applications of the {@code extract} function,
	 * at the cost of some overhead.
     *
	 * @param <C>
	 * @param <I>
	 * @param derefThis
	 * @param extract How to extract the field
	 * @return
	 */
	public default 
	<C , I extends ReadDependency<? extends E>>
	Self setupField_lowExtract(
			ReadDependency<? extends C> derefThis, 
			boolean nullable,
			Function<? super C, ? extends I> extract, 
			BiPredicate<? super E, ? super E> equiv) {
		V putHere = valueBeingBuilt();
		if(equiv!=null)
			putHere._setEquivalence(equiv);
		if(putHere.avName==null)
			putHere.avName=("("+derefThis.dependencyName()+")->?");
		return this
				.dependOn(derefThis)
				.recompute(new Consumer<Recomputation<E>>() {
					I lastF;
					C lastC;
					Set<Dependency> recorded;
					public void accept(Recomputation<E> reco) {
						I f;

						if(recorded==null || reco.queryChangedDependencies(false).contains(derefThis)) {
							Set<Dependency> recorded = null;
							C c = derefThis.get();

							
							b:{
								synchronized (this) {
									if(c==null && !nullable) {
										f=null;
										recorded=Collections.emptySet();
										break b;
									}else if(c!=null && c==lastC) {
										f=lastF;
										recorded=this.recorded;
										if(recorded!=null)
											recorded.forEach(reco::recordDependency);
										break b;
									}
								}
								{
									GenericDependencyRecorder recorder = new GenericDependencyRecorder(reco);
									try(MockBlock b=Recomputations.withDependencyRecorder(recorder)){
										f = extract.apply(c);
									}
									recorded = recorder.getRecorded();
								}
							}
							synchronized (this) {
								if(!reco.isFinished()) {
									lastF = f;
									lastC = c;
									this.recorded = recorded;
								}
							}
						}else {
							synchronized (this) {
								if(recorded!=null)
									recorded.forEach(reco::recordDependency);
								f=lastF;
							}
						}

						if(f==null || f.validity().isFalse()) {
							reco.fulfillInvalid();
							return;
						}
						try {
							reco.fulfill(f.getValidOrThrow());
						} catch (InvalidValueException e) {
							reco.fulfillInvalid();
						}
					}

				})
				.parent(derefThis)
				.dynamicDependencies()
				.essential(derefThis)
				.seal();


	}
	/**
	 * Configure the {@link #valueBeingBuilt() value being built}
	 * to dereference the given {@link ReadDependency}-valued {@link ReadDependency}.
	 * Attempts to write will write to the value currently held by the {@link #valueBeingBuilt() value being built}
	 * or fail silently if it does not exist. 
	 * @param <C>
	 * @param derefThis
	 * @return
	 */
	public default <C extends ReadWriteDependency<E>> Self setupWritableDeref(
			ReadDependency<? extends C> derefThis
			) {
		V putHere = valueBeingBuilt();
		if(putHere.avName==null)
			putHere.avName=("* ("+derefThis.dependencyName()+")");

		return setupWritableField(derefThis, false, Functional.id());
	}
	
	/**
	 * Configure the {@link #valueBeingBuilt() value being built} to take on the value of 
	 * a reactive {@link ReadListenDependency value} extracted from of another 
	 * reactive {@link ReadDependency value}, and to forward writes to it.
	 * 
	 * @param <C>
	 * @param <I>
	 * @param derefThis
	 * @param extract How to extract the field
	 * @return
	 */
	public default <C , I extends ReadWriteDependency<E>>
	Self setupWritableField(
			ReadDependency<? extends C> derefThis,
			boolean nullable,
			Function<? super C, ? extends I> extract) {
		V putHere = valueBeingBuilt();
		if(putHere.avName==null)
			putHere.avName=("("+derefThis.dependencyName()+")->?");
		class MyRecomputer implements Consumer<Recomputation<E>>{
			volatile I current;
			public void accept(Recomputation<E> reco) {
//				String pan = putHere.avName;
				I f;
				C c = derefThis.get();
				f = c == null && !nullable ? null : extract.apply(c);
				if(f==null || f.validity().isFalse()) {
					reco.fulfillInvalid(()->current=f);
					return;
				}
				try {
					reco.fulfill(f.getValidOrThrow(), ()->current=f);
				} catch (InvalidValueException e) {
					reco.fulfillInvalid(()->current=f);
				}
			}
		};
		MyRecomputer recomputer = new MyRecomputer();
		return this
				.dependOn(derefThis)
				.recompute(recomputer)
				.seal(v->{
					WriteValue<E> oc;
					synchronized (recomputer) {
						oc=recomputer.current;
					}
					if(oc!=null)
						oc.set(v);
				})
				.parent(derefThis)
				.dynamicDependencies()
				.essential(derefThis)
				;


	}
	

			
	/**
	 * Configure the {@link #valueBeingBuilt() value being built} to take on the value of 
	 * a reactive {@link ReadListenDependency value} extracted from of another 
	 * reactive {@link ReadDependency value}, and to forward writes to it.
	 * 
	 * This implementation avoids unnecessary applications of the {@code extract} function,
	 * at the cost of some overhead.
	 * 
	 * @param <C>
	 * @param <I>
	 * @param derefThis
	 * @param extract How to extract the field
	 * @return
	 */
	public default <C , I extends ReadWriteDependency<E>>
	Self setupWritableField_lowExtract(
			ReadDependency<? extends C> derefThis, 
			boolean nullable,
			Function<? super C, ? extends I> extract) {
		V putHere = valueBeingBuilt();
		if(putHere.avName==null)
			putHere.avName="("+derefThis.dependencyName()+")->?";
		class MyRecomputer implements Consumer<Recomputation<E>>{
			volatile I current;
			I lastF;
			C lastC;
			Set<Dependency> recorded;
			public void accept(Recomputation<E> reco) {
				I f;

				if(recorded==null || reco.queryChangedDependencies(false).contains(derefThis)) {
					Set<Dependency> recorded = null;
					C c = derefThis.get();

					b:{
						synchronized (this) {
							if(c==null && !nullable) {
								f=null;
								recorded=Collections.emptySet();
								break b;
							}else if(c!=null && c==lastC) {
								f=lastF;
								recorded=this.recorded;
								if(recorded!=null)
									recorded.forEach(reco::recordDependency);
								break b;
							}
						}
						{
							GenericDependencyRecorder recorder = new GenericDependencyRecorder(reco);
							try(MockBlock b=Recomputations.withDependencyRecorder(recorder)){
								f = extract.apply(c);
							}
							recorded = recorder.getRecorded();
						}
					}
					synchronized (this) {
						if(!reco.isFinished()) {
							lastF = f;
							lastC = c;
							this.recorded = recorded;
						}
					}
				}else {
					synchronized (this) {
						if(recorded!=null)
							recorded.forEach(reco::recordDependency);
						f=lastF;
					}
				}

				if(f==null || f.validity().isFalse()) {
					reco.fulfillInvalid(()->current=f);
					return;
				}
				try {
					reco.fulfill(f.getValidOrThrow(), ()->current=f);
				} catch (InvalidValueException e) {
					reco.fulfillInvalid(()->current=f);
				}
			}
		};
		MyRecomputer recomputer = new MyRecomputer();
		return this
				.dependOn(derefThis)
				.recompute(recomputer)
				.seal(v->{
					WriteValue<E> oc;
					synchronized (recomputer) {
						oc=recomputer.current;
					}
					if(oc!=null)
						oc.set(v);
				})
				.parent(derefThis)
				.dynamicDependencies()
				.essential(derefThis)
				;
	}
	
	/**
	 * Configures the {@link #valueBeingBuilt()} to be a read-write buffer.
     *
     * This is achieved by means of a {@link ValueListener} that keeps no strong references to the
	 * buffer. 
	 * The main use case I intend for this is to shorten transaction cascades, as the follower value will not
	 * enter a transaction if the leader does.
	 * The buffer keeps a strong reference to its leader in its {@link AbstractReadListenDependency#owner owner} field, unless the owner field is already set.
	 * The buffer inherits brackets and equivalence relation from the {@code leader}.
	 * Writes to the buffer will first change the {@code leader}, then the buffer.
	 * @param leader
	 * @return
	 */
	public default Self setupWritableBuffer(ReadWriteListenValue<E> leader) {
		V follower = valueBeingBuilt();
		if(follower.avName==null)
			follower.avName=("buffered ("+leader.dependencyName()+")");
		if(follower.owner==null)
			follower.owner=leader;

		WriteValue<? super E> followerSetter = follower.makeSetter();
		WeakCleanupWithRunnable<WriteValue<? super E>> followerRef = new WeakCleanupWithRunnable<>(followerSetter, null);

		ReadListenValueBool valid=leader.validity();

		ValueListener cl=e->{
			WriteValue<? super E> setter = followerRef.get();
			if(setter==null)
				return;
			if(valid.isTrue()) {
				try {
					E value;
					value = leader.getValidOrThrow();
					setter.set(value);
				} catch (InvalidValueException x) {
					setter.permaInvalidate();
				}
			}else {
				setter.permaInvalidate();
			}
		};

		inheritBrackets(false, leader);
		


		leader.addValueListener(cl);
		valid.addValueListener(cl);
		followerRef.setCleanupAction(()->{
			leader.removeValueListener(cl);	
			valid.removeValueListener(cl);	
		});


		follower._setEquivalence(leader._getEquivalence());
		follower._addCorrector(leader::applyCorrection);


		cl.valueChanged(null);

		return seal(newValue->{
			followerSetter.set(leader.set(newValue));
		}, false);

	}
	/**
	 * Configures the {@link #valueBeingBuilt() value being built} to be a read-only buffer.
	 * This is achieved by means of a {@link ValueListener} that keeps no strong references to the
	 * buffer. 
	 * The main use case I intend for this is to shorten transaction cascades, as the follower value will not
	 * enter a transaction if the leader does.
	 * The buffer keeps a strong reference to its leader in its {@link AbstractReadListenDependency#owner owner} field, unless the owner field is already set.
	 * The buffer inherits brackets and equivalence relation from the {@code leader}
	 * @param leader
	 * @return
	 */
	public default Self setupBuffer(ReadListenValue<E> leader) {
		V follower = valueBeingBuilt();
		if(follower.avName==null)
			follower.avName=("buffered ("+leader.dependencyName()+")");
		if(follower.owner==null)
			follower.owner=leader;

		WriteValue<? super E> followerSetter = follower.makeSetter();
		WeakCleanupWithRunnable<WriteValue<? super E>> followerRef = new WeakCleanupWithRunnable<>(followerSetter, null);

		ReadListenValueBool valid=leader.validity();

		ValueListener cl=e->{
			WriteValue<? super E> setter = followerRef.get();
			if(setter==null)
				return;
			if(valid.isTrue()) {
				try {
					E value;
					value = leader.getValidOrThrow();
					setter.accept(value);
				} catch (InvalidValueException x) {
					setter.permaInvalidate();
				}
			}else {
				setter.permaInvalidate();
			}
		};

		inheritBrackets(false, leader);


		leader.addValueListener(cl);
		valid.addValueListener(cl);
		followerRef.setCleanupAction(()->{
			leader.removeValueListener(cl);	
			valid.removeValueListener(cl);	
		});


		follower._setEquivalence(leader._getEquivalence());


		cl.valueChanged(null);

		


		return seal();
	}

	
	
	/**
	 * Configures the {@link #valueBeingBuilt() value being built} to be a read-write rate limited buffer
	 * that reflects the value and validity of a given leader value
	 * but has limits on the rate with which it changes. 
	 * This is achieved by means of a {@link RateLimitedValueListener} that keeps no strong references to the
	 * rate limited buffer. The rate limited buffer keeps a strong reference to its leader in its {@link AbstractReadListenDependency#owner owner} field,
	 * if that field was not already set.
	 * Writes to the buffer will first change the leader, then the buffer.
	 * TODO: Invalidating the buffer directly does not work yet
	 * @param leader
	 * @param coldStartTime
	 * @param coolDownTime
	 * @return
	 */
	public default Self 
	setupWritableRateLimited(ReadWriteListenValue<E> leader, long coldStartTime, long coolDownTime) {
		V follower = valueBeingBuilt();
		if(follower.avName==null)
			follower.avName = ("rate limited ("+leader.dependencyName()+")");
		if(follower.owner==null)
			follower.owner=leader;
		inheritBrackets(false, leader);
		follower.transferFrom(leader, true);
		
		WriteValue<E> followerSetter = follower.makeSetter();
		WeakCleanupWithRunnable<WriteValue<? super E>> followerRef = new WeakCleanupWithRunnable<>(followerSetter, null);
		ReadListenValueBool valid=leader.validity();

		ValueListener cl=ValueListener.rateLimited(coldStartTime, coolDownTime, ()->{
			WriteValue<? super E> setter = followerRef.get();
			if(setter==null)
				return;
			if(valid.isTrue()) {
				try {
					E value;
					value = leader.getValidOrThrow();
					setter.accept(value);
				} catch (InvalidValueException e) {
					setter.permaInvalidate();
				}
			}else {
				setter.permaInvalidate();
			}

		});

		if(leader instanceof AbstractReadListenDependency) {
			@SuppressWarnings("unchecked")
			AbstractReadListenDependency<E> cast = (AbstractReadListenDependency<E>) leader;
			cast.bequeathBrackets(false, follower);
		}

		leader.addValueListener(cl);
		valid.addValueListener(cl);
		followerRef.setCleanupAction(()->{
			valid.removeValueListener(cl);
			leader.removeValueListener(cl);
		});

		follower._setEquivalence(leader._getEquivalence());
		follower._addCorrector(leader::applyCorrection);


		cl.valueChanged(null);
		return seal(newValue->{
			followerSetter.set(leader.set(newValue));
		}, false);
	}






	/**
	 * Configures the {@link #valueBeingBuilt() value being built} to be a read-write rate limited buffer
	 * that reflects the value and validity of a given leader value
	 * but has limits on the rate with which it changes. 
	 * This is achieved by means of a {@link RateLimitedValueListener} that keeps no strong references to the
	 * rate limited buffer. The rate limited buffer keeps a strong reference to its leader in its {@link AbstractReadListenDependency#owner owner} field,
	 * if that field was not already set.
	 * @param leader
	 * @param coldStartTime
	 * @param coolDownTime
	 * @return
	 */
	public default Self setupRateLimited(ReadListenValue<E> leader, long coldStartTime, long coolDownTime) {
		V follower = valueBeingBuilt();
		if(follower.avName==null)
			follower.avName = ("rate limited ("+leader.dependencyName()+")");
		if(follower.owner==null)
			follower.owner=leader;
		inheritBrackets(false, leader);
		follower.transferFrom(leader, true);
		WriteValue<? super E> followerSetter = follower.makeSetter();
		WeakCleanupWithRunnable<WriteValue<? super E>> followerRef = new WeakCleanupWithRunnable<>(followerSetter, null);
	
		ReadListenValueBool valid=leader.validity();
	
	
		ValueListener cl=ValueListener.rateLimited(coldStartTime, coolDownTime, ()->{
			WriteValue<? super E> setter = followerRef.get();
			if(setter==null)
				return;
			if(valid.isTrue()) {
				try {
					E value;
					value = leader.getValidOrThrow();
					setter.accept(value);
				} catch (InvalidValueException e) {
					setter.permaInvalidate();
				}
			}else {
				setter.permaInvalidate();
			}
		});
	
	
		leader.addValueListener(cl);
		valid.addValueListener(cl);
		followerRef.setCleanupAction(()->{
			valid.removeValueListener(cl);
			leader.removeValueListener(cl);
		});
	
		follower._setEquivalence(leader._getEquivalence());
	
	
		cl.valueChanged(null);
		return seal();
	}
	/**
	 * Configures the {@link #valueBeingBuilt() value being built} to follow
	 * a given backing {@link ReadWriteListenDependency} but take on a default value
	 * whenever the backing value is invalid.
	 * @param back
	 * @param defaultValue
	 * @return
	 */
	public default Self setupDefaultable(ReadWriteListenDependency<E> back, E defaultValue) {
//		V vbb = valueBeingBuilt();
//		WriteValue<E> setter = vbb.makeSetter();
		return recomputeS(()->{
			E s = back.get();
			if(s!=null)
				return s;
			return defaultValue;
		})
		.seal(v->{
			if(defaultValue.equals(v))
				back.set(null);
			else
				back.set(v);
		})
		.dependOn(true, back);
	}
	
	//	static long start = System.currentTimeMillis();
	//	static void printNow(String what) {
	//		System.out.println((System.currentTimeMillis()-start)+": "+what);
	//	}
	//
	//	public static void main(String[] args) throws InterruptedException {
	//		IndependentInt leader = Values.independent(1)
	//				.corrector(i->Math.min(i, 10))
	//				.onChange_f(v->e->printNow("Current value of "+v))
	//				.name("leader")
	//				.build();
	//		ValueInt follower = Values.sealedNoInitInt()
	//				.setupDelayed(1000, leader)
	//				.onChange_f(v->e->printNow("Current value of "+v))
	//				.name("follower")
	//				.build();
	//		System.out.println();
	//		printNow("Set leader to 2");
	//		leader.set(2);
	//		Thread.sleep(2000);
	//
	//		System.out.println();
	//		printNow("Set leader to 2");
	//		leader.set(2);
	//		Thread.sleep(2000);
	//
	//		System.out.println();
	//		printNow("Set follower to 2");
	//		follower.set(2);
	//		Thread.sleep(2000);
	//
	//		System.out.println();
	//		printNow("Set follower to 3");
	//		follower.set(3);
	//		Thread.sleep(2000);
	//		
	//		System.out.println();
	//		printNow("Set follower to 3");
	//		follower.set(3);
	//		Thread.sleep(2000);
	//		
	//		System.out.println();
	//		printNow("Set follower to 20");
	//		follower.set(20);
	//		Thread.sleep(2000);
	//
	//		System.out.println();
	//		printNow("Set follower to 30");
	//		follower.set(30);
	//		Thread.sleep(2000);
	//		
	//		System.out.println();
	//		printNow("Set leader to 3");
	//		leader.set(3);
	//		Thread.sleep(2000);
	//		
	//		System.out.println();
	//		printNow("Set leader to 20");
	//		leader.set(20);
	//		Thread.sleep(2000);
	//
	//		System.out.println();
	//		printNow("Set leader to 30");
	//		leader.set(30);
	//		Thread.sleep(2000);
	//
	//	}

}