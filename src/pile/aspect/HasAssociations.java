package pile.aspect;

import java.lang.ref.Reference;
import java.lang.ref.ReferenceQueue;
import java.lang.ref.SoftReference;
import java.lang.ref.WeakReference;
import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.Function;
import java.util.function.Supplier;

import pile.aspect.listen.ListenValue;
import pile.interop.exec.StandardExecutors;

/**
 * An object that can store values associated with keys.
 * @author bb
 *
 */
public interface HasAssociations {
	class __PrivateStuff{
		/**
		 * Sentinel value used to indicate that a <code>null</code> has been stored by a
		 * {@link ReferencePolicy}
		 */
		static final Object NULL = new Object();

		/**
		 * The key for which the corresponding value stores the references that were given to
		 * {@link #keepStrong(Object)} 
		 */
		static final AssociationKey<HashSet<Object>> KEEP_STRONG=new SimpleAssociationKey<>();

		/**
		 * The key for marking as disposable
		 */
		static final AssociationKey<Boolean> DISPOSABLE = new SimpleAssociationKey<>();

	}
	/**
	 * A {@link ReferencePolicy} specifies how the reference to an associated value should be stored.
	 * @author bb
	 *
	 */
	public interface ReferencePolicy {
		/**
		 * wrap an object into whatever reference type the policy uses. 
		 * @param o Object to be wrapped.
		 * @param rq ReferenceQueue to be used to register the reference.
		 * @param makeRunnable A runnable that will be run after the reference is cleared.
		 * @return
		 */
		abstract Object wrap(Object o, ReferenceQueue<Object> rq, Runnable makeRunnable);
		/**
		 * unwrap an object from whatever reference type the policy uses. 
		 * @param o
		 * @return
		 */
		abstract Object unwrap(Object o);
		/**
		 * Test if the given object reference represents an absent value. 
		 * (For example, because it is a {@link Reference} that has been cleared.)
		 * @param o
		 * @return
		 */
		abstract boolean isAbsent(Object o);
		/**
		 * 
		 * @return Whether this policy makes use of a reference queue.
		 */
		abstract boolean needsReferenceQueue();

		public default <K> Object wrapRemoving(Object o, ReferenceQueue<Object> rq, Collection<? super K> removeFrom, K removeThis) {
			return rq==null?wrap(o, null, null):wrap(o, rq, ()->removeFrom.remove(removeThis));
		}

		public default <K> Object wrapRemovingRef(Object o, ReferenceQueue<Object> rq, Collection<? super K> removeFrom, Reference<? extends K> removeThis) {
			return rq==null?wrap(o, null, null):wrap(o, rq, ()->{
				K k = removeThis.get();
				if(k!=null)
					removeFrom.remove(k);
			});
		}
		public default <K> Object wrapRemoving(Object o, ReferenceQueue<Object> rq, Map<? super K, ?> removeFrom, K removeThis) {
			return rq==null?wrap(o, null, null):wrap(o, rq, ()->removeFrom.remove(removeThis));
		}

		public default <K> Object wrapRemovingRef(Object o, ReferenceQueue<Object> rq, Map<? super K, ?> removeFrom, Reference<? extends K> removeThis) {
			return rq==null?wrap(o, null, null):wrap(o, rq, ()->{
				K k = removeThis.get();
				if(k!=null)
					removeFrom.remove(k);
			});
		}
		public default <K> Object wrapRemovingWeakRef(Object o, ReferenceQueue<Object> rq, Collection<? super K> removeFrom, K removeThis) {
			return rq==null?wrap(o, null, null):wrapRemovingRef(o, rq, removeFrom, new WeakReference<K>(removeThis));
		}
		public default <K> Object wrapRemovingWeakRef(Object o, ReferenceQueue<Object> rq, Map<? super K, ?> removeFrom, K removeThis) {
			return rq==null?wrap(o, null, null):wrapRemovingRef(o, rq, removeFrom, new WeakReference<K>(removeThis));
		}
		/**
		 * Policy: Store references as themselves, except for <code>null</code>, which uses
		 * the {@link #NULL} sentinel value.
		 */
		public static final ReferencePolicy STRONG=new ReferencePolicy() {

			@Override
			public Object wrap(Object o, ReferenceQueue<Object> rq, Runnable makeRunnable) {
				return o==null?__PrivateStuff.NULL:o;
			}
			@Override
			public Object unwrap(Object o) {
				return o==__PrivateStuff.NULL?null:o;
			}
			@Override 
			public boolean isAbsent(Object o) {
				return o==null;
			}
			@Override public boolean needsReferenceQueue() {return false;}
		};
		/**
		 * A {@link WeakReference} that also implements {@link Runnable}
		 * @author bb
		 *
		 * @param <T>
		 */
		public static abstract class RunnableWeakReference<T> extends WeakReference<T> implements Runnable{

			public RunnableWeakReference(T referent, ReferenceQueue<? super T> q) {
				super(referent, q);
			}
			public RunnableWeakReference(T referent) {
				super(referent);
			}
		}
		/**
		 * A {@link SoftReference} that also implements {@link Runnable}
		 * @author bb
		 *
		 * @param <T>
		 */
		public static abstract class RunnableSoftReference<T> extends WeakReference<T> implements Runnable{

			public RunnableSoftReference(T referent, ReferenceQueue<? super T> q) {
				super(referent, q);
			}
			public RunnableSoftReference(T referent) {
				super(referent);
			}
		}
		/**
		 * Policy: store references as weak references, except for <code>null</code>, which uses
		 * the {@link #NULL} sentinel value.
		 */
		public static final ReferencePolicy WEAK=new ReferencePolicy() {
			@Override
			public Object wrap(Object o, ReferenceQueue<Object> rq, Runnable makeRunnable) {
				return o==null?__PrivateStuff.NULL:makeRunnable==null?new WeakReference<>(o):new RunnableWeakReference<Object>(o, rq) {public void run() {makeRunnable.run();}};
			}
			@Override
			public Object unwrap(Object o) {
				return (o==null||o==__PrivateStuff.NULL)?null:((Reference<?>)o).get();
			}
			@Override 
			public boolean isAbsent(Object o) {
				return o==null || o!=__PrivateStuff.NULL && ((Reference<?>)o).get()==null;
			}
			@Override public boolean needsReferenceQueue() {return true;}

		};
		/**
		 * Policy: store references as soft references, except for <code>null</code>, which uses
		 * the {@link #NULL} sentinel value.
		 */
		public static final ReferencePolicy SOFT=new ReferencePolicy() {
			@Override
			public Object wrap(Object o, ReferenceQueue<Object> rq, Runnable makeRunnable) {
				return o==null?__PrivateStuff.NULL:makeRunnable==null?new SoftReference<>(o):new RunnableSoftReference<Object>(o, rq) {public void run() {makeRunnable.run();}};
			}
			@Override
			public Object unwrap(Object o) {
				return (o==null||o==__PrivateStuff.NULL)?null:((Reference<?>)o).get();
			}
			@Override 
			public boolean isAbsent(Object o) {
				return o==null || o!=__PrivateStuff.NULL && ((Reference<?>)o).get()==null;
			}	
			@Override public boolean needsReferenceQueue() {return true;}
		};
	}


	/**
	 * Interface for key objects, annotated with the type of the associated value
	 * @author bb
	 *
	 * @param <E> The type of the value associated with the key
	 */
	public interface AssociationKey<E>{
		/**
		 * @return The {@link ReferencePolicy} used for storing the values
		 */
		ReferencePolicy referenceStrength();
	}
	/**
	 * Default implementation of {@link AssociationKey}
	 * @author bb
	 *
	 * @param <E>
	 */
	public class SimpleAssociationKey<E> implements AssociationKey<E>{
		final ReferencePolicy rpol;
		/**
		 * 
		 * @param pol Which {@link ReferencePolicy} to use
		 */
		public SimpleAssociationKey(ReferencePolicy pol){rpol = pol;}
		/**
		 * Uses {@link ReferencePolicy#STRONG}
		 */
		public SimpleAssociationKey(){this(ReferencePolicy.STRONG);}
		public ReferencePolicy referenceStrength() {return rpol;}

	}
	/**
	 * Default implementation of {@link AssociationKey} that allows giving a name for debuging purposes
	 * @author bb
	 *
	 * @param <E>
	 */
	public class NamedAssociationKey<E> implements AssociationKey<E>{
		public final String name;
		final ReferencePolicy rpol;
		public ReferencePolicy referenceStrength() {return rpol;}
		public NamedAssociationKey(String s, ReferencePolicy pol) {name = s; rpol = pol;}
		public NamedAssociationKey(String s) {this(s, ReferencePolicy.STRONG);}
		@Override public String toString() {return name;}
	}
	/**
	 * Get the value associated with the given key
	 * @param <E>
	 * @param key
	 * @return
	 */
	public <E> E getAssociation(AssociationKey<E> key);
	/**
	 * Set the value associated with the given key
	 * @param <E>
	 * @param key
	 * @param value
	 */
	public <E> void putAssociation(AssociationKey<? super E> key, E value);
	/**
	 * Get the value associated with the given key; if it is absent, generate it.
	 * @param <E>
	 * @param key
	 * @param value This {@link Supplier} will be called to generate the value if it is absent
	 * @return the value now associated with the key
	 */
	public <E> E putAssociationIfAbsentAndGet(AssociationKey<? super E> key, Supplier<? extends E> value);

	/**
	 * Compute the value associated with the given key; if it is absent, generate it.
	 * @param <E>
	 * @param key
	 * @param value This {@link Function} will be called on the {@code param}eter to generate the value if it is absent
	 * @param param A parameter from which to compute the value if necessary
	 * @return the value now associated with the key
	 */
	public <E, P> E computeAssociationIfAbsentAndGet(AssociationKey<? super E> key, Function<? super P, ? extends E> valueMaker, P param);

	/**
	 * calls {@link #putAssociation(AssociationKey, Object)} and returns the object
	 */
	public static <T extends HasAssociations, E> T putAssociation(T object, AssociationKey<? super E> key, E value){
		object.putAssociation(key, value);
		return object;
	}

	/**
	 * Keep a strong reference to the given object
	 * @param o
	 */
	public default void keepStrong(Object o) {
		HashSet<Object> ks = putAssociationIfAbsentAndGet(__PrivateStuff.KEEP_STRONG, HashSet::new);
		synchronized (ks) {
			ks.add(o);
		}
	}

	/**
	 * Put a mark on the object that it it was created for one purpose and can be destroyed
	 * when it is no longer needed. This is to differentiate it from shared objects that may not be destroyed.
	 * @param <T>
	 * @param object
	 * @return
	 */
	public static <T extends HasAssociations> T markAsDisposable(T object) {
		return putAssociation(object, __PrivateStuff.DISPOSABLE, Boolean.TRUE);
	}
	/**
	 * @return whether the object is marked as disposable
	 * @see #markAsDisposable(HasAssociations)
	 */
	default boolean isMarkedDisposable() {
		return Boolean.TRUE.equals(getAssociation(__PrivateStuff.DISPOSABLE));
	}



	/**
	 * Lift a given function to a memoized function that remembers previous results by storing them
	 * in the associations of its argument.
	 * @param <I> Argument type
	 * @param <R> Result type
	 * @param rpol Controls the memory sensitivity of the cache
	 * @param f
	 * @return
	 */
	public static 
	<I extends HasAssociations, R>
	Function<I, R> memoize(ReferencePolicy rpol, Function<? super I, ? extends R> f){		
		class MemoizedFunction implements Function<I, R>, AssociationKey<R> {
			final Function<? super I, ? extends R> back;
			private final ReferencePolicy rpol;
			MemoizedFunction(ReferencePolicy rpol, Function<? super I, ? extends R> f){
				back = f;
				this.rpol = rpol;
			}
			@Override
			public R apply(I t) {
				return t.computeAssociationIfAbsentAndGet(this, back, t);
			}
			@Override
			public ReferencePolicy referenceStrength() {
				return rpol;
			}
		}
		return new MemoizedFunction(rpol, f);
	}

	/**
	 * Generic implementation of {@link HasAssociations} that can be implemented
	 * to provide the functionality in a consistent way.
	 * @author bb
	 *
	 */
	public static interface Mixin extends HasAssociations{

		/**
		 * Must not be called from outside {@link Mixin}'s methods.
		 * @return The Object to be used as a mutex for coordinating access to the map.
		 */
		public Object __HasAssocitations_Mixin_getMutex();
		/**
		 * Must not be called from outside {@link Mixin}'s methods.
		 * @return The map used to store the associations
		 */
		public WeakHashMap<Object, Object> __HasAssocitations_Mixin_getMap();
		/**
		 * Must not be called from outside {@link Mixin}'s methods.
		 * Called to initialize the map reference
		 * @param map
		 */
		public void __HasAssocitations_Mixin_setMap(WeakHashMap<Object, Object> map);
		/**
		 * Must not be called from outside {@link Mixin}'s methods.
		 * @return The {@link ReferenceQueue} used to clean up garbage collected associations
		 */		
		public ReferenceQueue<Object> __HasAssocitations_Mixin_getQueue();
		/**
		 * Must not be called from outside {@link Mixin}'s methods.
		 * Called to initialize the {@link ReferenceQueue}
		 * used to clean up garbage collected associations
		 * @param queue
		 */
		public void __HasAssocitations_Mixin_setQueue(ReferenceQueue<Object> queue);


		default public <K> void putAssociation(AssociationKey<? super K> key, K value) {
			synchronized (__HasAssocitations_Mixin_getMutex()) {
				ReferencePolicy refHandler = key.referenceStrength();
				WeakHashMap<Object, Object> associations = __HasAssocitations_Mixin_getMap();
				ReferenceQueue<Object> associationRq;
				if(associations==null) {
					__HasAssocitations_Mixin_setMap(associations=new WeakHashMap<>());
					if(refHandler.needsReferenceQueue()) 
						__HasAssocitations_Mixin_setQueue(associationRq = new ReferenceQueue<>());
					else
						associationRq = null;

				}else {
					if((associationRq = __HasAssocitations_Mixin_getQueue())==null && refHandler.needsReferenceQueue()) 
						__HasAssocitations_Mixin_setQueue(associationRq = new ReferenceQueue<>());
				}
				associations.put(key, refHandler.wrapRemoving(value, associationRq, associations.keySet(), key));
			}
		}

		@Override
		default public <K, P> K computeAssociationIfAbsentAndGet(AssociationKey<? super K> key,
				Function<? super P, ? extends K> valueMaker, P param) {
			synchronized (__HasAssocitations_Mixin_getMutex()) {
				ReferencePolicy refHandler = key.referenceStrength();
				WeakHashMap<Object, Object> associations = __HasAssocitations_Mixin_getMap();
				ReferenceQueue<Object> associationRq;
				if(associations==null) {
					__HasAssocitations_Mixin_setMap(associations=new WeakHashMap<>());
					if(refHandler.needsReferenceQueue()) 
						__HasAssocitations_Mixin_setQueue(associationRq = new ReferenceQueue<>());
					else
						associationRq = null;
					K value;
					try {
						ListenValue.DEFER.__incrementSuppressors();		
						value = valueMaker.apply(param);
					}finally {
						ListenValue.DEFER.__decrementSuppressors();		
					}
					associations.put(key, refHandler.wrapRemovingWeakRef(value, associationRq, associations, key));
					return value;
				}
				Object wv = associations.get(key);
				if(!key.referenceStrength().isAbsent(wv)) {
					@SuppressWarnings("unchecked")
					K value = (K)refHandler.unwrap(wv);
					return value;
				}
				K value;
				try {
					ListenValue.DEFER.__incrementSuppressors();		
					value = valueMaker.apply(param);
				}finally {
					ListenValue.DEFER.__decrementSuppressors();		
				}
				if((associationRq = __HasAssocitations_Mixin_getQueue())==null && refHandler.needsReferenceQueue()) 
					__HasAssocitations_Mixin_setQueue(associationRq = new ReferenceQueue<>());
				associations.put(key, refHandler.wrapRemovingWeakRef(value, associationRq, associations, key));
				return value;
			}	
		}
		@Override
		default public <K> K putAssociationIfAbsentAndGet(AssociationKey<? super K> key, Supplier<? extends K> valueMaker) {
			synchronized (__HasAssocitations_Mixin_getMutex()) {
				ReferencePolicy refHandler = key.referenceStrength();
				WeakHashMap<Object, Object> associations = __HasAssocitations_Mixin_getMap();
				ReferenceQueue<Object> associationRq;
				if(associations==null) {
					__HasAssocitations_Mixin_setMap(associations=new WeakHashMap<>());
					if(refHandler.needsReferenceQueue()) 
						__HasAssocitations_Mixin_setQueue(associationRq = new ReferenceQueue<>());
					else
						associationRq = null;

					K value;
					try {
						ListenValue.DEFER.__incrementSuppressors();		
						value = valueMaker.get();
					}finally {
						ListenValue.DEFER.__decrementSuppressors();		
					}
					associations.put(key, refHandler.wrapRemovingWeakRef(value, associationRq, associations, key));
					return value;
				}
				Object wv = associations.get(key);
				if(!key.referenceStrength().isAbsent(wv)) {
					@SuppressWarnings("unchecked")
					K value = (K)refHandler.unwrap(wv);
					return value;
				}
				K value;
				try {
					ListenValue.DEFER.__incrementSuppressors();		
					value = valueMaker.get();
				}finally {
					ListenValue.DEFER.__decrementSuppressors();		
				}
				if((associationRq = __HasAssocitations_Mixin_getQueue())==null && refHandler.needsReferenceQueue()) 
					__HasAssocitations_Mixin_setQueue(associationRq = new ReferenceQueue<>());
				associations.put(key, refHandler.wrapRemovingWeakRef(value, associationRq, associations, key));
				return value;
			}		
		}
		@Override
		default public <K> K getAssociation(AssociationKey<K> key) {
			WeakHashMap<Object, Object> associations;
			ReferenceQueue<Object> associationRq;
			Object mutex = __HasAssocitations_Mixin_getMutex();
			synchronized (mutex) {
				associations = __HasAssocitations_Mixin_getMap();
				if(associations==null)
					return null;
				associationRq = __HasAssocitations_Mixin_getQueue();
			}
			if(associationRq!=null)
				synchronized (associationRq) {
					while(true) {
						Reference<? extends Object> next = associationRq.poll();
						if(next==null)
							break;
						if(next instanceof Runnable) {
							StandardExecutors.safe((Runnable) next);
						}
					}
				}

			synchronized (mutex) {
				ReferencePolicy refHandler = key.referenceStrength();
				Object wv = associations.get(key);
				@SuppressWarnings("unchecked")
				K value = (K)refHandler.unwrap(wv);
				return value;
			}
		}
	}

}
