package pile.builder;

import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;

import pile.aspect.Sealable;
import pile.aspect.WriteValue;
import pile.impl.Independent;

/**
 * Common interface for builders that build {@link Sealable}s
 * @author bb
 *
 * @param <Self>
 * @param <V>
 * @param <E>
 */
public interface ISealableBuilder<Self extends ISealableBuilder<Self, V, E>, V extends Sealable<E>, E>
extends IBuilder<Self, V>{
	/**
	 * {@link Sealable#seal() Seal} the {@link Independent}. This takes effect when {@link #build()} is called, so you can continue to
	 * use this builder to modify the value.
	 * @return {@code this} builder
	 */
	public Self seal();
	/**
	 * {@link Sealable#seal(Consumer) Seal} the {@link Independent}. 
	 * This takes effect when {@link #build()} is called, so you can continue to
	 * use this builder to modify the value.
	 * @param interceptor Attempts to write to the {@link Sealable} will be redirected to the interceptor.
	 * @return {@code this} builder
	 */
	public Self seal(Consumer<? super E> interceptor);
	/**
	 * {@link Sealable#seal(Consumer) Seal} the {@link Independent}. 
	 * This takes effect when {@link #build()} is called, so you can continue to
	 * use this builder to modify the value.
	 * @param makeInterceptor This function is used to define the interceptor, given the value 
	 * being built. Attempts to write to the {@link Sealable} will be redirected to the interceptor.
	 * @return {@code this} builder
	 */
	default Self seal(Function<? super V, ? extends Consumer<? super E>> makeInterceptor) {
		return seal(makeInterceptor.apply(valueBeingBuilt()));
	}
	
	/**
	 * {@link Sealable#seal(Consumer) Seal} the {@link Independent}. 
	 * This takes effect when {@link #build()} is called, so you can continue to
	 * use this builder to modify the value.
	 * @param interceptor Attempts to write to the {@link Sealable} will be redirected to the interceptor.
	 * The first argument given to the interceptor is the value being built.
	 * @return {@code this} builder
	 */
	default Self seal(BiConsumer<? super V, ? super E> interceptor) {
		V v = valueBeingBuilt();
		return seal((E e)->interceptor.accept(v, e));
	}
	/**
	 * {@link Sealable#seal(Consumer) Seal} the {@link Independent}. 
	 * This takes effect when {@link #build()} is called, so you can continue to
	 * use this builder to modify the value.
	 * @param interceptor Attempts to write to the {@link Sealable} will be redirected to the interceptor.
	 * The first argument given to the interceptor is privileged setter obtained from {@link #makeSetter()}.
	 * @return {@code this} builder
	 */
	default Self sealWithSetter(BiConsumer<? super WriteValue<? super E>, ? super E> interceptor) {
		WriteValue<? super E> v = valueBeingBuilt().makeSetter();
		return seal((E e)->interceptor.accept(v, e));
	}
	/**
	 * {@link Sealable#seal(Consumer) Seal} the {@link Independent}. 
	 * This takes effect when {@link #build()} is called, so you can continue to
	 * use this builder to modify the value.
	 * @param interceptor Attempts to write to the {@link Sealable} will be redirected to the interceptor.
	 * @param allowInvalidation Whether it will be allowed to call {@link #permaInvalidate()}.
	 * @return {@code this} builder
	 */
	public Self seal(Consumer<? super E> interceptor, boolean allowInvalidation);
	/**
	 * {@link Sealable#seal(Consumer) Seal} the {@link Independent}. 
	 * This takes effect when {@link #build()} is called, so you can continue to
	 * use this builder to modify the value.
	 * @param makeInterceptor This function is used to define the interceptor, given the value 
	 * being built. Attempts to write to the {@link Sealable} will be redirected to the interceptor.
	 * @param allowInvalidation Whether it will be allowed to call {@link #permaInvalidate()}.
	 * @return {@code this} builder
	 */
	default Self seal(Function<? super V, ? extends Consumer<? super E>> makeInterceptor, boolean allowInvalidation) {
		return seal(makeInterceptor.apply(valueBeingBuilt()), allowInvalidation);
	}
	/**
	 * {@link Sealable#seal(Consumer) Seal} the {@link Independent}. 
	 * This takes effect when {@link #build()} is called, so you can continue to
	 * use this builder to modify the value.
	 * @param interceptor Attempts to write to the {@link Sealable} will be redirected to the interceptor.
	 * The first argument given to the interceptor is the value being built.
	 * @param allowInvalidation Whether it will be allowed to call {@link #permaInvalidate()}.
	 * @return {@code this} builder
	 */
	default Self seal(BiConsumer<? super V, ? super E> interceptor, boolean allowInvalidation) {
		V v = valueBeingBuilt();
		return seal((E e)->interceptor.accept(v, e), allowInvalidation);
	}
	/**
	 * Call {@link Sealable#makeSetter()} in the {@link Independent} that is being build and hand the result
	 * to the given {@link Consumer}
	 */
	public default Self giveSetter(Consumer<? super Consumer<? super E>> out) {
		if(out!=null)
			out.accept(makeSetter());
		return self();
	}
	/**
	 * @return the result of {@link Independent#makeSetter()} called on the {@link Independent} value being built
	 */
	public Consumer<? super E> makeSetter();

}
