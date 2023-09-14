package pile.aspect.suppress;

/**
 * Interface for a closeable object that can be cancelled, that is, if its {@link #cancel()}
 * method has been called, its {@link #close()} method will do nothing.
 * @author bb
 *
 */
public interface CancelClose extends SafeCloseable{
	public static final CancelClose NOP = new CancelClose() {
		@Override public void close() {}
		@Override public void cancel() {}
	};
	public void cancel();
	/**
	 * @deprecated Should only be called by a try-with-resources block
	 */
	@Deprecated
	public void close();
}
