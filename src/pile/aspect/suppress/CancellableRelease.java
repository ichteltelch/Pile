package pile.aspect.suppress;

import java.io.Closeable;

/**
 * A {@link Closeable} implementation whose {@link #close()}-method
 * {@linkplain} Suppressor#release() releases} a {@link Suppressor},
 * unless its {@link #cancel()}-method had been called.
 * @author bb
 *
 */
public final class CancellableRelease implements CancelClose{
	Suppressor s;
	public CancellableRelease(Suppressor s) {
		this.s=s;
	}
	@Override
	@Deprecated
	public void close() {
		if(s!=null)
			s.release();
	}
	@Override
	public void cancel() {s=null;}
}