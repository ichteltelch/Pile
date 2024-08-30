package pile.aspect.bracket;

import java.util.function.BiPredicate;
import java.util.function.Consumer;
import java.util.function.Predicate;

import pile.interop.debug.DebugEnabled;
import pile.utils.Functional;
import pile.utils.Nonreentrant;
import pile.utils.SequentialQueue;
import pile.utils.defer.Deferrer;

/**
 * A {@link ValueBracket} that does not care about the object holding the value, only the value itself.
 * @author bb
 *
 * @param <V>
 */
public interface ValueOnlyBracket<V> extends ValueBracket<V, Object>{
	@Override
	default ValueOnlyBracket<V> detectStuck() {
		return new DeadlockDetectingBracket.ValueOnly<>(this, -3000, null, null);
	}

	@Override
	default ValueOnlyBracket<V> dontDetectStuck(){
		return this;
	}
	@Override
	public default ValueOnlyBracket<V> nonreentrant(Nonreentrant nr){
		return new NonreentrantBracket.ValueOnly<>(nr, this);
	}
	
	@Override
	public default ValueOnlyBracket<V> filtered(Predicate<? super V> openFilter, Predicate<? super V> closeFilter){
		return new FilteredBracket.ValueOnly<>(this, openFilter, closeFilter);
	}
	@Override
	public default ValueOnlyBracket<V> filtered(Predicate<? super V> filter){
        return filtered(filter, filter);
    }
	@Override
	public default ValueOnlyBracket<V> nopOnNull(){
		return filtered(Functional.IS_NOT_NULL, Functional.IS_NOT_NULL);
	}
	@Override
	public default ValueOnlyBracket<V> nopOnNullOpen(){
		return filtered(Functional.IS_NOT_NULL, null);
	}
	@Override
	public default ValueOnlyBracket<V> nopOnNullClose(){
		return filtered(null, Functional.IS_NOT_NULL);
	}
	@Override
	public default ValueOnlyBracket<V> filtersFirst(){
		return this;
	}
	
	
	
	
	
	/**
	 * Construct a {@link QueuedValueBracket} backed by this {@link ValueBracket}
	 * @see QueuedValueBracket
	 * @param q
	 * @param filter
	 * @param keep
	 * @param remain
	 * @return
	 */
	default ValueOnlyBracket<V> queued(SequentialQueue q, 
			BiPredicate<? super V, ? super Object> filter, 
			BiPredicate<? super V, ? super Object> keep, 
			BiPredicate<? super V, ? super Object> remain){
		ValueOnlyBracket<V> ret = new QueuedValueBracket.ValueOnly<>(this, q, filter, keep, remain);
		if(DebugEnabled.DETECT_STUCK_BRACKETS)
			ret=ret.detectStuck();
		return ret;
	}
	/**
	 * Construct a {@link QueuedValueBracket} backed by this {@link ValueBracket}
	 * @see QueuedValueBracket
	 * @param q
	 * @param filter
	 * @return
	 */
	default ValueOnlyBracket<V> queued(SequentialQueue q, BiPredicate<? super V, ? super Object> filter){
		return queued(q, filter, null, null);
	}
	/**
	 * Construct a {@link QueuedValueBracket} backed by this {@link ValueBracket}
	 * @see QueuedValueBracket
	 * @param q
	 * @param filter
	 * @return
	 */
	default ValueOnlyBracket<V> queued(SequentialQueue q, Predicate<? super V> filter){
		return queued(q, null, null, null).filtered(filter);
	}
	/**
	 * Construct a {@link QueuedValueBracket} backed by this {@link ValueBracket} that filters out <code>null</code> values
	 * @see QueuedValueBracket
	 * @param q
	 * @return
	 */
	default ValueOnlyBracket<V> queued(SequentialQueue q){
		return queued(q, null, null, null);
	}
	/**
	 * Construct a {@link QueuedValueBracket} backed by this {@link ValueBracket} that uses a new {@link SequentialQueue}.
	 * Warning: Each SequentialQueue can have its own Thread, so you might want to reuse SequentialQueues for several {@link ValueBracket}s
	 * @see QueuedValueBracket
	 * @param name the name for the {@link SequentialQueue}
	 * @param filter
	 * @return
	 */
	default ValueOnlyBracket<V> queued(String name, BiPredicate<? super V, ? super Object> filter){
		return queued(new SequentialQueue(name), filter, null, null);
	}
	/**
	 * Construct a {@link QueuedValueBracket} backed by this {@link ValueBracket} that uses a new {@link SequentialQueue}.
	 * Warning: Each SequentialQueue can have its own Thread, so you might want to reuse SequentialQueues for several {@link ValueBracket}s
	 * @see QueuedValueBracket
	 * @param name the name for the {@link SequentialQueue}
	 * @param filter
	 * @return
	 */
	default ValueOnlyBracket<V> queued(String name, Predicate<? super V> filter){
		return queued(new SequentialQueue(name), (v, _o)->filter.test(v), null, null);
	}
	/**
	 * Construct a {@link QueuedValueBracket} backed by this {@link ValueBracket} that filters out <code>null</code> values that uses a new {@link SequentialQueue}.
	 * Warning: Each SequentialQueue can have its own Thread, so you might want to reuse SequentialQueues for several {@link ValueBracket}s
	 * @see QueuedValueBracket
	 * @param name the name for the {@link SequentialQueue}
	 * @return
	 */
	default ValueOnlyBracket<V> queued(String name){
		return queued(new SequentialQueue(name), null, null, null);
	}
	/**
	 * Construct a {@link QueuedValueBracket} backed by this {@link ValueBracket} that uses a new {@link SequentialQueue}.
	 * Warning: Each SequentialQueue can have its own Thread, so you might want to reuse SequentialQueues for several {@link ValueBracket}s
	 * @see QueuedValueBracket
	 * @param name the name for the {@link SequentialQueue}
	 * @param filter
	 * @return
	 */
	default ValueOnlyBracket<V> queued(BiPredicate<? super V, ? super Object> filter){
		return queued(QueuedValueBracket.getDefaultQueue(), filter, null, null);
	}
	/**
	 * Construct a {@link QueuedValueBracket} backed by this {@link ValueBracket} that uses a new {@link SequentialQueue}.
	 * Warning: Each SequentialQueue can have its own Thread, so you might want to reuse SequentialQueues for several {@link ValueBracket}s
	 * @see QueuedValueBracket
	 * @param name the name for the {@link SequentialQueue}
	 * @param filter
	 * @return
	 */
	default ValueOnlyBracket<V> queued(Predicate<? super V> filter){
		return queued(QueuedValueBracket.getDefaultQueue(), null, null, null).filtered(filter);
	}
	/**
	 * Construct a {@link QueuedValueBracket} backed by this {@link ValueBracket} that filters out <code>null</code> values that uses a new {@link SequentialQueue}.
	 * Warning: Each SequentialQueue can have its own Thread, so you might want to reuse SequentialQueues for several {@link ValueBracket}s
	 * @see QueuedValueBracket
	 * @param name the name for the {@link SequentialQueue}
	 * @return
	 */
	default ValueOnlyBracket<V> queued(){
		return queued(QueuedValueBracket.getDefaultQueue(), null, null, null);
	}
	
	
	
	public default ValueOnlyBracket<V> beforeOpening(Consumer<? super V> preOpen){
		return new AugmentedBracket.ValueOnly<>(preOpen, null, this);
	}
	public default ValueOnlyBracket<V> beforeClosing(Consumer<? super V> postClose){
		return new AugmentedBracket.ValueOnly<>(null, postClose, this);
	}
	public default ValueOnlyBracket<V> defer(Deferrer d){
        return new DeferredValueBracket.ValueOnly<>(this, d, null, null, null);
    }
	
	
	
}