package pile.aspect;

import pile.aspect.recompute.Recomputation;
import pile.impl.PileImpl;

/**
 * An Exception thrown to veto a change.
 * 
 * Thrown from a corrector of a {@link CorrigibleValue}.
 * @see CorrigibleValue#applyCorrection(Object)
 * @see PileImpl#set(Object)
 * @see Recomputation#fulfill(Object)
 *
 */
public class VetoException extends RuntimeException {
	/**
	 * 
	 */
	private static final long serialVersionUID = -1595431520217814016L;
	/**
	 * Whether the veto should trigger a revalidation of a value that can revalidate.
	 * Warning: If you use this, make sure that such a veto is never triggered by the recomputation
	 * itself. Otherwise, you may get an endless cycle of recomputations that are rejected, 
	 * only to be started again.
	 */
	public final boolean revalidate;
	public VetoException(boolean revalidate) {
		this.revalidate=revalidate;
	}
	public VetoException(boolean revalidate, String msg) {
		super(msg);
		this.revalidate=revalidate;
	}
	public VetoException(boolean revalidate, Throwable cause) {
		super(cause);
		this.revalidate=revalidate;
	}
	public VetoException(boolean revalidate, String msg, Throwable cause) {
		super(msg, cause);
		this.revalidate=revalidate;
	}
	public VetoException() {
		this(false);
	}
	public VetoException(String msg) {
		this(false, msg);
	}
	public VetoException(Throwable cause) {
		this(false, cause);
	}
	public VetoException(String msg, Throwable cause) {
		this(false, msg, cause);
	}

}
