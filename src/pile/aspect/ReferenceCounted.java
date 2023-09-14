package pile.aspect;

import java.util.function.Function;

import pile.aspect.suppress.Suppressor;

/**
 * Interface for objects that explicitly count references to themselves
 * @author bb
 *
 */
public interface ReferenceCounted {
	/**
	 * Method handle for {@link #rcReferenceKeeper()}
	 */
	public static final Function<ReferenceCounted, Suppressor> KEEP_REFERENCE = ReferenceCounted::rcReferenceKeeper;
	/**
	 * Increase the reference counter
	 */
	public void increaseRefcount();
	/**
	 * Decrease the reference counter. If it reaches zero, this may trigger destruction of the object.
	 */
	public void decreaseRefcount();
	
	/**
	 * Makes a {@link Suppressor} that keeps the reference count up until it is {@link Suppressor#release() release}d
	 * @return
	 */
	public default Suppressor rcReferenceKeeper() {
		Suppressor ret = Suppressor.wrap(this::decreaseRefcount);
		increaseRefcount();
		return ret;
	}
}
