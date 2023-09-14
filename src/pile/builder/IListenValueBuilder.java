package pile.builder;

import java.util.function.BiConsumer;
import java.util.function.Function;

import pile.aspect.listen.ListenValue;
import pile.aspect.listen.ValueListener;
import pile.aspect.listen.WeakValueListener;

/**
 * Common interface for builders that build {@link ListenValue}s
 * @author bb
 *
 * @param <Self> Implementing class
 * @param <V> concrete subtype of the {@link ListenValue} being build
 */
public interface IListenValueBuilder <Self extends IListenValueBuilder<Self, V>, V extends ListenValue>
extends IBuilder<Self, V>
{

	/**
	 * Add a {@link ValueListener} to the value
	 * @param l
	 * @return {@code this} builder
	 */
	Self onChange(ValueListener l);
	/**
	 * Add a {@link ValueListener} to the value, bringing in scope the value itself
	 * @param l Will be called with the {@link ListenValue} being build as its argument
	 * @return {@code this} builder
	 */
	default Self onChange_f(Function<? super V, ? extends ValueListener> l) {
		return onChange(l.apply(valueBeingBuilt()));
	}
	/**
	 * Add a {@link ValueListener} to the {@link ListenValue} for as long as the listener
	 * is strongly reachable.
	 * @param l
	 * @return {@code this} builder
	 */
	default Self onChange_weak(ValueListener l) {
		return onChange(new WeakValueListener(l));
	}
	/**
	 * Add a {@link ValueListener} to the {@link ListenValue} for as long as it is strongly reachable.
	 * @param l Will be called with the {@link ListenValue} being build as its argument
	 * @return {@code this} builder
	 */
	default Self onChange_weak_f(Function<? super V, ? extends ValueListener> l) {
		return onChange_f((V v)->new WeakValueListener(l.apply(v)));
	}
	/**
	 * Register a {@link ValueListener} with the {@link ListenValue}
	 * under construction in such a way that the {@link ListenValue}
	 * does not keep strong references to it. 
	 * @param l
	 * @param out This {@link BiConsumer}'s {@link BiConsumer#accept(Object, Object) accept}
	 * method will be called with two {@link ValueListener} arguments: The first is the 
	 * reference that needs to be passed to 
	 * {@link ListenValue#removeValueListener(ValueListener)} in order to manually remove
	 * the listener from the constructed {@link ListenValue} and the second it the reference
	 * that must remain strongly reachable unless the listener should be removed
	 * @return {@code this} builder
	 */
	default Self onChange_weak(ValueListener l, BiConsumer<? super ValueListener, ? super ValueListener> out) {
		WeakValueListener weak = new WeakValueListener(l);
		onChange(weak);
		out.accept(weak, l);
		return self();
	}
	/**
	 * Register a {@link ValueListener} with the {@link ListenValue}
	 * under construction in such a way that the {@link ListenValue}
	 * does not keep strong references to it. 
	 * @param l
	 * @param out This {@link BiConsumer}'s {@link BiConsumer#accept(Object, Object) accept}
	 * method will be called with two {@link ValueListener} arguments: The first is the 
	 * reference that needs to be passed to 
	 * {@link ListenValue#removeValueListener(ValueListener)} in order to manually remove
	 * the listener from the constructed {@link ListenValue} and the second it the reference
	 * that must remain strongly reachable unless the listener should be removed
	 * @return {@code this} builder
	 */
	default Self onChange_weak_f(Function<? super V, ? extends ValueListener> l, BiConsumer<? super ValueListener, ? super ValueListener> out) {
		return onChange_weak(l.apply(valueBeingBuilt()), out);
	}


}
