package pile.utils;

import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.util.HashMap;
import java.util.function.Function;
import java.util.function.Supplier;

import pile.aspect.recompute.Recomputations;
import pile.aspect.suppress.MockBlock;

/**
 * A memoization cache that compares its keys by identity und uses
 * {@link WeakReference}s for both its keys and its values
 * @author bb
 *
 * @param <K>
 * @param <V>
 */
public final class IdentitiyMemoCache<K, V> implements Function<K, V>{
	private final HashMap<WeakIdentityCleanup<K>, WeakCleanup<V>> cache = new HashMap<>();
	private final Function<? super K, ? extends V> derive;
	private Supplier<? extends ReferenceQueue<? super Object>> rq;
	/**
	 * @param derive Function to be memoized
	 */
	public IdentitiyMemoCache(Function<? super K, ? extends V> derive) {
		this(derive, null);
	}

	/**
	 * @param derive Function to be memoized
	 * @param rq Reference queue to be used for cleanup
	 */
	public IdentitiyMemoCache(Function<? super K, ? extends V> derive, Supplier<? extends ReferenceQueue<? super Object>> rq) {
		this.derive = derive;
		this.rq = rq;
	}
	@Override
	public V apply(K key) {
		if(rq==null)
			rq = AbstractReferenceManager.Std();
		WeakIdentityCleanup<K> keyRef = new WeakIdentityCleanup<K>(key, rq) {
			@Override
			public void run() {
				synchronized (cache) {
					cache.remove(this);
				}
			}
		};
		synchronized (cache) {
			if(cache.containsKey(keyRef)) {
				WeakReference<V> valueRef = cache.get(keyRef);
				if(valueRef==null)
					return null;
				V value = valueRef.get();
				if(value!=null)
					return value;
				cache.remove(keyRef);
			}
		}
		
		//Memoized reactive values can safely be created during depdendency recording,
		//as this will happen only once
		try(MockBlock b = Recomputations.withoutRecomputation()) {

			while(true) {


				V value = derive.apply(key);
				synchronized (cache) {
					if(cache.containsKey(keyRef)) {
						WeakReference<V> valueRef = cache.get(keyRef);
						if(valueRef==null)
							return null;
						V newValue = valueRef.get();
						if(newValue!=null)
							return newValue;
						cache.remove(keyRef);
						continue;
					}
					if(value==null) {
						cache.put(keyRef, null);
						return null;
					}
					WeakCleanup<V> valueRef = new WeakCleanup<V>(value, rq) {
						@Override
						public void run() {
							synchronized (cache) {
								cache.remove(keyRef);
							}
						}
					};
					cache.put(keyRef, valueRef);
				}
			}
		}
	}
}
