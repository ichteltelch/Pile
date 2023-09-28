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
	 * 
	 * @return Usually the same as {@link #getRecomputation()}, but <code>null</code>
	 * if {@link #recordDependency(Dependency)} does not forward the information
	 * about recorded dependencies to a {@link Recomputation}.
	 * 	 */
	Recomputation<?> getReceivingRecomputation();

	
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
		@Override public Recomputation<?> getReceivingRecomputation() {return null;}
		@Override public void recordDependency(Dependency d) {}
	};
	/**
	 * Make a {@link DependencyRecorder} that does nothing except provide
	 * info about the enclosing {@link Recomputation}.
	 * USe it to suspend recording dependencies using {@link Recomputations#withDependencyRecorder(DependencyRecorder)}
	 * while still allowing the program to obtain the current {@link Recomputation}
	 * using {@link Recomputations#getCurrentRecomputation()}.
	 * @return
	 */
	public default DependencyRecorder nonForwarding() {
		Recomputation<?> reco = this.getRecomputation();
		return new DependencyRecorder() {
			
			@Override
			public void recordDependency(Dependency d) {
			}
			
			
			@Override
			public Recomputation<?> getRecomputation() {
				return reco;
			}
			@Override
			public Recomputation<?> getReceivingRecomputation() {
				return null;
			}
		};
	}
	
}
