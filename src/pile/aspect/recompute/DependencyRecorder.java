package pile.aspect.recompute;

import pile.aspect.Dependency;

/**
 * An interface for taking note that {@link Dependency Dependencies} are being accessed.
 * This is part of the dynamic dependency feature, where the current {@link Recomputation}
 * tracks the dependencies that are being accessed, but you can make your own 
 * {@link DependencyRecorder} implementation for other purposes and install it with
 * {@link Recomputations#withDependencyRecorder(DependencyRecorder)}
 * @author bb
 *
 */
public interface DependencyRecorder {
	/**
	 * If this {@link DependencyRecorder} implements {@link Recomputation}, return {@code this}.
	 * If this {@link DependencyRecorder} dispatches the information about accessed Dependencies,
	 * to another {@link DependencyRecorder}, then delegate to that recorder's 
	 * {@link #getRecomputation()} method. Else, return {@code null}.
	 * @return
	 */
	Recomputation<?> getRecomputation();
	/**
	 * Called when a {@link Dependency} is accessed.
	 * This method is not guaranteed to be thread safe.
	 * @param d
	 */
	public void recordDependency(Dependency d);
	/**
	 * A {@link DependencyRecorder} that does nothing.
	 */
	public static final DependencyRecorder NOP = new DependencyRecorder(){
		@Override public Recomputation<?> getRecomputation() {return null;}
		@Override public void recordDependency(Dependency d) {}
	};
}
