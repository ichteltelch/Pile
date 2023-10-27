package pile.impl;

import java.util.Objects;

import pile.interop.debug.DebugEnabled;

/**
 * A {@link TransactionTracker} is used to document the reasons for a transaction.
 * This class is only for debugging purposes. It is public in case alternative
 * implementations find it useful.
 * @author bb
 *
 */
public class TransactionTracker {
	public final Object originator;
	public final String reason;
	public final Object id;
	public final StackTraceElement[] trace;
	public TransactionTracker(Object originator, String reason, Object id) {
		this.originator=originator;
		this.reason=reason;
		this.id=id;
		trace = DebugEnabled.TRANSACTION_TRACES?Thread.currentThread().getStackTrace():null;
	}
	@Override
	public int hashCode() {
		return Objects.hash(originator, reason);
	}
	@Override
	public boolean equals(Object o) {
		if (o == this)
			return true;
		if (o == null)
			return false;
		if (o instanceof TransactionTracker) {
			TransactionTracker a = (TransactionTracker) o;
			if(originator!=a.originator)
				return false;
			if(!Objects.equals(reason, a.reason))
				return false;
			if(id != a.id)
				return false;
			return true;
		}
		return false;
	}
	@Override
	public String toString() {
		return reason + ": "+originator;
	}
}
