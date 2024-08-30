package pile.aspect;

/**
 * An object that can have an internal lock.
 * Allows for testing whether the lock is held by the current Thread.
 */
public interface HasInternalLock {
	/**
	 * 
	 * @return Whether the lock is currently held by this Thread.
	 */
	public boolean holdsLock();
}
