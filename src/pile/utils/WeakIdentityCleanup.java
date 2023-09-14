package pile.utils;

import java.lang.ref.ReferenceQueue;
import java.util.function.Supplier;

/**
 * A {@link WeakCleanup} reference that overrides {@link #equals(Object)} and 
 * {@link #hashCode()} so that
 * two {@link WeakIdentityCleanup} references are equal only if they are 
 * identical or they have the same non-null referent. 
 * @author bb
 *
 * @param <T>
 */
public class WeakIdentityCleanup<T> extends WeakCleanup<T>{

	int hc;
	@Override
	public int hashCode() {
		return hc;
	}
	public WeakIdentityCleanup(T referent) {
		super(referent);
		hc=System.identityHashCode(referent);
	}
	public WeakIdentityCleanup(T referent, Supplier<? extends ReferenceQueue<? super Object>> rq) {
		super(referent, rq);
		hc=System.identityHashCode(referent);
	}
	@Override
	public boolean equals(Object o) {
		if (o == this)
			return true;
		if (o == null)
			return false;
		if (o instanceof WeakIdentityCleanup) {
			WeakIdentityCleanup<?> a = (WeakIdentityCleanup<?>) o;
			if(hc!=a.hc)
				return false;
			Object ar=a.get();
			Object tr=get();
			if(ar!=tr || ar==null) return false;
			return true;
		}
		return false;
	}
	@Override
	public void run() {
		
	}
}
