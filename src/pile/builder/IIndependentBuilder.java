package pile.builder;

import java.util.function.Consumer;
import java.util.function.Function;

import pile.aspect.Dependency;
import pile.aspect.Depender;
import pile.aspect.LastValueRememberer;
import pile.aspect.ReadValue;
import pile.aspect.ReadValue.InvalidValueException;
import pile.aspect.bracket.HasBrackets;
import pile.aspect.bracket.ValueBracket;
import pile.aspect.combinations.ReadListenDependency;
import pile.aspect.combinations.ReadListenValue;
import pile.aspect.combinations.ReadWriteListenValue;
import pile.aspect.listen.ValueListener;
import pile.aspect.suppress.MockBlock;
import pile.impl.AbstractReadListenDependency;
import pile.impl.DebugCallback;
import pile.impl.Independent;
import pile.impl.Piles;
import pile.interop.exec.StandardExecutors;
import pile.relation.CoupleEqual;
import pile.utils.WeakCleanupWithRunnable;

/**
 * The interface for builders of {@link Independent} values.
 * @author bb
 *
 * @param <Self> Implementing class
 * @param <V> concrete subtype of the {@link Independent} value being build
 * @param <E> Type of the value's content
 */
public interface IIndependentBuilder<Self extends IIndependentBuilder<Self, V, E>, V extends Independent<E>, E>
extends ICorrigibleBuilder<Self, V, E>, IListenValueBuilder<Self, V>, ISealableBuilder<Self, V, E>{

	/**
	 * Set the initial value. Note that this calls {@link Independent#set(Object)} only later when the value is built, 
	 * so {@link ValueBracket}s and corrections can take effect appropriately. 
	 * @param initValue
	 * @return {@code this} builder
	 */
	Self init(E initValue);
	/**
	 * Set the value. Note that this immediately calls {@link Independent#set(Object)}; 
	 * {@link ValueBracket}s and corrections that are not added yet will have no effect.
	 * @param initValue
	 * @return {@code this} builder
	 */
	Self initNow(E initValue);
	/**
	 * Cause the {@link Independent} value to be backed by a persistent storage
	 * @param remember
	 * @param correctNulls If true, attempting to writing <code>null</code> will trigger recalling the last remembererd value instead
	 * @return {@code this} builder
	 */
	Self fromStore(LastValueRememberer<E> remember, boolean correctNulls);
	/**
	 * Set the {@link DebugCallback} for the {@link Independent} being built
	 * @param dc
	 * @return {@code this} builder
	 */
	Self debug(DebugCallback dc);
	/**
	 * {@link CoupleEqual couple} the value being built to another one. The initial value will be the one
	 * from from the value being built.
	 * @param partner
	 * @return {@code this} builder
	 */
	public Self equalTo(ReadWriteListenValue<E> partner);
	/**
	 * {@link CoupleEqual couple} the value being built to another one. The initial value will be the one
	 * from from the other ReadWriteListenValue.
	 * @param partner
	 * @return {@code this} builder
	 */
	public Self equalFrom(ReadWriteListenValue<E> partner);
	/**
	 * Make the {@link Independent} value being build follow the given {@link ReadListenValue}
	 * by means of a {@link ValueListener}
	 * @param leader
	 * @param alsoInvalidate
	 * @return {@code this} builder
	 */
	public Self follow(ReadListenValue<? extends E> leader);
	/**
	 * Add a bracket for the current value of the {@link Independent} value being built
	 * @param bracket
	 * @see HasBrackets#_addValueBracket(boolean, ValueBracket)
	 * @return {@code this} builder
	 */
	default Self bracket(ValueBracket<? super E, ? super ReadListenDependency<? extends E>> bracket) {
		return bracket(false, bracket);
	}
	/**
	 * Add a bracket for the old value of the {@link Independent} value being built
	 * @param bracket
	 * @see HasBrackets#_addOldValueBracket(boolean, ValueBracket)
	 * @return {@code this} builder
	 */
	default Self oldValueBracket(ValueBracket<? super E, ? super ReadListenDependency<? extends E>> bracket) {
		return oldValueBracket(false, bracket);
	}
	/**
	 * Add a bracket for both the current or 
	 * the old value of the {@link Independent} value being built built
	 * @param bracket
	 * @see HasBrackets#_addAnyValueBracket(boolean, ValueBracket)
	 * @return {@code this} builder
	 */	
	default Self anyBracket(ValueBracket<? super E, ? super ReadListenDependency<? extends E>> bracket) {
		return anyBracket(false, bracket);
	}

	/**
	 * Add a bracket for the current value of the {@link Independent} value being built
	 * @param bracket
	 * @see HasBrackets#_addValueBracket(boolean, ValueBracket)
	 * @return {@code this} builder
	 */
	Self bracket(boolean openNow, ValueBracket<? super E, ? super ReadListenDependency<? extends E>> bracket);
	/**
	 * Add a bracket for the old value of the {@link Independent} value being built
	 * @param bracket
	 * @see HasBrackets#_addOldValueBracket(boolean, ValueBracket)
	 * @return {@code this} builder
	 */
	Self oldValueBracket(boolean openNow, ValueBracket<? super E, ? super ReadListenDependency<? extends E>> bracket);
	/**
	 * Add a bracket for both the current or 
	 * the old value of the {@link Independent} value being built built
	 * @param bracket
	 * @see HasBrackets#_addAnyValueBracket(boolean, ValueBracket)
	 * @return {@code this} builder
	 */	
	Self anyBracket(boolean openNow, ValueBracket<? super E, ? super ReadListenDependency<? extends E>> bracket);

	/**
	 * Set the {@link Independent#owner} field of the {@link Independent} value being built
	 * @param p
	 * @return {@code this} builder
	 */
	Self parent(Object p);
	/**
	 * Don't add {@link ValueListener}s to the bounds; just check them whenever the value is changed.
	 * @return {@code this} builder
	 */
	public Self dontDependOnBounds();
	/**
	 * Build the {@link Independent} value and return it.
	 * This is when the correctors and listeners for the bounds are installed.
	 * @return
	 */
	V build();
	/**
	 * Inherit all brackets from the given {@link ReadValue}, 
	 * if it is an {@link AbstractReadListenDependency}
	 * @param openNow
	 * @param template
	 */
	public default void inheritBrackets(boolean openNow, ReadValue<E> template) {
		if(template instanceof AbstractReadListenDependency) {
			AbstractReadListenDependency<E> cast = (AbstractReadListenDependency<E>) template;
			cast.bequeathBrackets(openNow, valueBeingBuilt());
		}
	}

	/**
	 * Configures the {@link #valueBeingBuilt()} to be a read-only valid buffer.
	 * It is always valid and reflects the last valid value of the leader value.
	 * This is achieved by means of a {@link ValueListener} that keeps no strong references to the
	 * valid buffer. The valid buffer keeps a strong reference to its leader in its 
	 * {@link AbstractReadListenDependency#owner owner} field, unless the owner was already set.
	 * @param leader
	 * @return
	 */
	//TODO: Why does the buffer sometimes seem to fail to update?
	public default Self setupValidBuffer(ReadListenValue<E> leader){
		V follower = valueBeingBuilt();
		if(follower.dependencyName()=="?")
			follower.setName("last valid ("+leader.dependencyName()+")");
		if(follower.owner==null)
			follower.owner=leader;
		Consumer<? super E> followerSetter = follower.makeSetter();
		WeakCleanupWithRunnable<Consumer<? super E>> followerRef = new WeakCleanupWithRunnable<>(followerSetter, null);

		ValueListener cl=e->{
			Consumer<? super E> setter = followerRef.get();
			if(setter==null)
				return;

			if(leader.isValid()) {
				try {
					E value = leader.getValidOrThrow();
					try(MockBlock _s = Piles.withShouldDeepRevalidate(false)){
						setter.accept(value);
					}
				}catch(InvalidValueException x) {
					StandardExecutors.unlimited().execute(leader::fireValueChange);
				}
			}else {
			}
		};

		inheritBrackets(false, leader);
		follower.set(leader.get());

		leader.addValueListener(cl);
		//		leader.validity().addValueListener(cl); //FIX?
		followerRef.setCleanupAction(()->leader.removeValueListener(cl));


		follower._setEquivalence(leader._getEquivalence());

		//		leader.syncWithInternalMutex();
		cl.runImmediately();
		return seal();
	}

	/**
	 * Configures a given {@link Independent} instance to be a read-write "valid buffer".
	 * It is always valid and reflects the last valid value of the leader value.
	 * This is achieved by means of a {@link ValueListener} that keeps no strong references to the
	 * valid buffer. The valid buffer keeps a strong reference to 
	 * its leader in its {@link AbstractReadListenDependency#owner owner} field.
	 * Writes to the buffer will first change the leader, then the buffer.
	 * @param <V>
	 * @param <E>
	 * @param leader
	 * @return
	 */
	public default Self setupWritableValidBuffer(ReadWriteListenValue<E> leader) {
		return setupWritableValidBuffer(leader, null);
	}
	/**
	 * Configures a given {@link Independent} instance to be a read-write "valid buffer".
	 * It is always valid and reflects the last valid value of the leader value.
	 * This is achieved by means of a {@link ValueListener} that keeps no strong references to the
	 * valid buffer. The valid buffer keeps a strong reference to 
	 * its leader in its {@link AbstractReadListenDependency#owner owner} field.
	 * Writes to the buffer will first change the leader, then the buffer.
	 * @param <V>
	 * @param <E>
	 * @param leader
	 * @param deferWrites a function that takes the leader's setter and returns a new setter that possible executes writes later or in a different thread.
	 * @return
	 */
	public default Self setupWritableValidBuffer(
			ReadWriteListenValue<E> leader,
			Function<Consumer<? super E>, Consumer<? super E>> deferWrites
			){
		V follower = valueBeingBuilt();
		if(follower.dependencyName()=="?")
			follower.setName("last valid ("+leader.dependencyName()+")");
		if(follower.owner==null)
			follower.owner=leader;
		Consumer<? super E> leaderSetter = deferWrites==null?leader:deferWrites.apply(leader);
		Consumer<? super E> followerSetter = follower.makeSetter();
		WeakCleanupWithRunnable<Consumer<? super E>> followerRef = new WeakCleanupWithRunnable<>(followerSetter, null);

		ValueListener cl=e->{
			Consumer<? super E> setter = followerRef.get();
			if(setter==null)
				return;
			if(leader.isValid())
				try {
					E value = leader.getValidOrThrow();
					try(MockBlock _s = Piles.withShouldDeepRevalidate(false)){
						setter.accept(value);
					}
				}catch(InvalidValueException x) {
					StandardExecutors.unlimited().execute(leader::fireValueChange);
				}
		};

		inheritBrackets(false, leader);
		follower.set(leader.get());

		leader.addValueListener(cl);
		followerRef.setCleanupAction(()->leader.removeValueListener(cl));

		follower._setEquivalence(leader._getEquivalence());
		follower._addCorrector(leader::applyCorrection);


		cl.runImmediately(true);
		return seal(newValue->{
			E oldValue=follower.get();
			if(oldValue!=newValue) {
				leaderSetter.accept(newValue);
				followerSetter.accept(newValue);
				leader.doOnceWhenValid(setValue -> followerSetter.accept(setValue));
			}else if(!leader.isValid()) {
				newValue = leader.set(newValue);
				if(newValue != oldValue)
					followerSetter.accept(newValue);
			}
		}, false);
	}
	/**
	 * Add the {@link #valueBeingBuilt() value being built} as a 
	 * {@link Dependency} to the given {@link Depender}
	 * @param dep
	 * @return
	 */
	public default Self depender(Depender dep) {
		dep.addDependency(valueBeingBuilt(), false, false);
		return self();
	}
}
