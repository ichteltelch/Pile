package pile.builder;

import java.util.function.Consumer;

import pile.utils.WeakCleanup;

/**
 * Common interface for builders
 * @author bb
 *
 * @param <Self> The concrete implementing builder class
 * @param <V> The type of value being built
 */
public interface IBuilder<Self extends IBuilder<Self, V>, V> {
	
	/** 
	 * @return {@code this} builder
	 */
	Self self();
	/**
	 * Hand {@code this} builder to the given consumer
	 * @param presets code that calls some of this builder's methods in order to configure some specific behavior.
	 * @return {@code this} builder
	 */
	public default Self configure(Consumer<? super Self> presets) {
		Self self = self();
		presets.accept(self);
		return self;
	}
	/**
	 * Build the object that is under construction and return it.
	 * @return
	 */
	V build();
	
	/**
	 * @return The object being build by this builder (without actually finishing to build it)
	 */
	public V valueBeingBuilt();

	/**
	 * Register a {@link Runnable} that will be run when the garbage collector detects
	 * that the {@link #valueBeingBuilt() value being built} has become weakly reachable.
	 * Note that the given {@link Runnable} should not contain any strong references to
	 * the value, otherwise you get a memory leak. And any weak references it contains
	 * will be cleared before it is run.
	 * @param run
	 * @return
	 */
	public default Self runIfWeak(Runnable run) {
		WeakCleanup.runIfWeak(valueBeingBuilt(), run);
		return self();
	}
}
