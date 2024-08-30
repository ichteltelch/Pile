package pile.aspect.bracket;

import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiPredicate;

import pile.utils.SequentialQueue;

/**
 * A wrapper around another {@link ValueBracket} that executes its {@link #open(Object, Object)} and {@link #close(Object, Object)}
 * actions on a {@link SequentialQueue}. The return values are determined synchronously by {@link BiPredicate}s passed to the constructor. 
 * @author bb
 *
 * @param <E>
 * @param <O>
 */
public class QueuedValueBracket<E, O> implements ValueBracket<E, O>{
	
	private static volatile SequentialQueue defaultQueue;
	public static SequentialQueue getDefaultQueue() {
		SequentialQueue local = defaultQueue;
		if(local==null) {
			local = defaultQueue;
			synchronized (ValueBracket.class) {
				if(local==null) {
					local = new SequentialQueue("Default ValueBracket Queue");
				}
				defaultQueue = local;
			}
		}
		return local;
	}
	public static void setDefaultQueue(SequentialQueue defaultQueue) {
		Objects.requireNonNull(defaultQueue);
		synchronized(ValueBracket.class){
			QueuedValueBracket.defaultQueue = defaultQueue;
		}
	} 
	
//	private static volatile SequentialQueue defaultDependencyQueue;
//	public static SequentialQueue getDefaultDependencyQueue() {
//		SequentialQueue local = defaultDependencyQueue;
//		if(local==null) {
//			local = defaultDependencyQueue;
//			synchronized (ValueBracket.class) {
//				if(local==null) {
//					local = getDefaultQueue();
//				}
//				defaultDependencyQueue = local;
//			}
//		}
//		return local;
//	}
//	public static void setDefaultDependencyQueue(SequentialQueue defaultDependencyQueue) {
//		Objects.requireNonNull(defaultDependencyQueue);
//		synchronized(ValueBracket.class){
//			QueuedValueBracket.defaultDependencyQueue = defaultDependencyQueue;
//		}
//	} 
	
	final ValueBracket<E, O> back;
	final SequentialQueue queue;
	final BiPredicate<? super E, ? super O> filter;
	final BiPredicate<? super E, ? super O> keep;
	final BiPredicate<? super E, ? super O> remain;
	final boolean backDoesOpen;
	final boolean backDoesClose;
	final ConcurrentHashMap<O, Object> obsoleteOn;

	/**
	 * 
	 * @param back The backing {@link ValueBracket}
	 * @param queue The {@link SequentialQueue} used for running the methods of the backing {@link ValueBracket}
	 * @param filter Used to decide whether to actually schedule something on the queue. Most useful for filtering out <code>null</code> values, see {@link #NON_NULL}.
	 * If you pass <code>null</code> here, no filtering is done.
	 * @param keep Used to compute the return value of the {@link #open(Object, Object)} method. If you pass <code>null</code> here, 
	 * {@link #open(Object, Object)} will always return <code>true</code>
	 * @param remain USed to compute the return value of the {@link #close(Object, Object)} method. If you pass <code>null</code> here, 
	 * {@link #close(Object, Object)} will always return <code>false</code> 
	 */
	public QueuedValueBracket(
			ValueBracket<E, O> back, 
			SequentialQueue queue, 
			BiPredicate<? super E, ? super O> filter,
			BiPredicate<? super E, ? super O> keep,
			BiPredicate<? super E, ? super O> remain
			) {
		this.back=back;
		this.queue=queue;
		this.filter=filter;
		this.keep=keep;
		this.remain=remain;
		this.backDoesOpen = !back.openIsNop();
		this.backDoesClose = !back.closeIsNop();
		this.obsoleteOn = keep!=null || back.canBecomeObsolete()? new ConcurrentHashMap<O, Object>():null;
	}
	@Override
	public boolean canBecomeObsolete() {
		return obsoleteOn!=null;
	}
	@Override
	public boolean open(E value, O owner) {
		if(obsoleteOn!=null && obsoleteOn.remove(owner)!=null)
			return false;
		if(backDoesOpen && (filter==null || filter.test(value, owner))) {
			queue.enqueue(()->{if(!back.open(value, owner) && obsoleteOn!=null) obsoleteOn.put(owner, this);});
		}
		return remain==null || remain.test(value, owner);
	}
	@Override
	public boolean close(E value, O owner) {
		if(backDoesClose && (filter==null || filter.test(value, owner))) {
			queue.enqueue(()->back.close(value, owner));
		}
		return keep!=null && keep.test(value, owner);
	}
	@Override
	public boolean isInheritable() {
		return back.isInheritable();
	}
	@Override public boolean openIsNop() {return keep==null & !backDoesOpen;}
	@Override public boolean closeIsNop() {return remain==null & ! backDoesClose;}
	
	public static class ValueOnly<V> extends QueuedValueBracket<V, Object> implements ValueOnlyBracket<V>{

		public ValueOnly(ValueOnlyBracket<V> back, SequentialQueue queue,
				BiPredicate<? super V, ? super Object> filter, BiPredicate<? super V, ? super Object> keep,
				BiPredicate<? super V, ? super Object> remain) {
			super(back, queue, filter, keep, remain);
		}
		
	    @Override
	    public ValueOnlyBracket<V> filtersFirst() {
	    	ValueOnlyBracket<V> cback = (ValueOnlyBracket<V>)back;
			ValueOnlyBracket<V> tback = cback.filtersFirst();
	    	if(tback==back)
	    		return this;
	        if(!(tback instanceof FilteredBracket.ValueOnly<?>)) {
	        	return new QueuedValueBracket.ValueOnly<>(tback, queue, filter, keep, remain);
	        }
	        FilteredBracket.ValueOnly<V> cast = (FilteredBracket.ValueOnly<V>) tback;
	        return new FilteredBracket.ValueOnly<V>(new QueuedValueBracket.ValueOnly<V>(cast.getWrapped(), queue, filter, keep, remain), cast.openFilter, cast.closeFilter);
	    }

		
    }
    @Override
    public ValueBracket<E, O> filtersFirst() {
    	ValueBracket<E, O> tback = back.filtersFirst();
    	if(tback==back)
    		return this;
        if(!(tback instanceof FilteredBracket<?,?>)) {
        	return new QueuedValueBracket<E, O>(tback, queue, filter, keep, remain);
        }
        FilteredBracket<E, O> cast = (FilteredBracket<E, O>) tback;
        return new FilteredBracket<E, O>(new QueuedValueBracket<>(cast.getWrapped(), queue, filter, keep, remain), cast.openFilter, cast.closeFilter);
    }

}