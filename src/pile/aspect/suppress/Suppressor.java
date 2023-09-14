package pile.aspect.suppress;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

import pile.interop.exec.StandardExecutors;
import pile.utils.WeakCleanup;

/**
 * A {@link Suppressor} is a reified status that suppresses a certain behavior of another
 * thing. The suppression effect starts when the {@link Suppressor} is created and ends
 * when the {@link #release()} method is called for the first time. 
 * <br>
 * The behavior is suppressed as long
 * as there is one {@link Suppressor} for it whose {@link #release()} method has not 
 * been called even if this {@link Suppressor} has already been garbage collected.
 * Use the {@link #wrapWeak()} method to ensure that the {@link #release()} method is 
 * called before the {@link Suppressor} (the one returned by {@link #wrapWeak()}) is GC'd.
 * @author bb
 *
 */
public interface Suppressor extends SafeCloseable, Runnable{
	static final Logger log = Logger.getLogger("Suppressor");
	/**
	 * A {@link Suppressor} that suppresses nothing. Use this in stead of a <code>null</code>
	 * reference if you want to avoid writing lots of <code>null</code> pointer checks.
	 */
	public static final Suppressor NOP = new NopSuppressor();
	/**
	 * The implementation for {@link Suppressor#NOP}.
	 * @author bb
	 *
	 */
	public static final class NopSuppressor implements Suppressor {
		@Override public boolean isDefinitelyReleased() {return true;}

		@Override public boolean release() {return false;}
	}
	/**
	 * Stop suppressing, as far as this {@link Suppressor} instance is concerned
	 * @return <code>true</code> If this call was the first on this {@link Suppressor} instance
	 */
	public boolean release();
	/**
	 * Calls {@link #release()}
	 */
	public default void close() {release();}
	/**
	 * Wrap this {@link Suppressor} so that {@link #release()} is called when 
	 * the wrapper becomes weakly reachable.
	 * If an {@link Exception} or {@link Error} happens during creation of the wrapper, 
	 * this {@link Suppressor} is released
	 * @return
	 */
	public default WrapWeak wrapWeak() {
		WrapWeak ret = null;
		try {
			return ret = new WrapWeak(this, null);
		}finally {
			if(ret==null)
				release();
		}
	}
	/**
	 * Wrap this {@link Suppressor} so that {@link #release()} is called when 
	 * the wrapper becomes weakly reachable.
	 * If an {@link Exception} or {@link Error} happens during creation of the wrapper, 
	 * this {@link Suppressor} is released
	 * @return
	 */
	public default WrapWeak wrapWeak(String warn) {
		WrapWeak ret = null;
		try {
			return ret = new WrapWeak(this, warn);
		}finally {
			if(ret==null)
				release();
		}
	}
	/**
	 * Make a {@link Suppressor} whose {@link #release()} method calls the
	 * {@link #release()} methods of several other {@link Suppressor}s which are yet to be
	 * {@link SuppressMany#add(Suppressor) add}ed to the returned {@link SuppressMany}
	 * object
	 * @return
	 */
	public static SuppressMany many() {
		return new SuppressMany();
	}
	/**
	 * Make a {@link Suppressor} whose {@link #release()} method calls the
	 * {@link #release()} methods of several other {@link Suppressor}s 
	 * @param sub
	 * @return
	 */
	public static SuppressMany many(Collection<? extends Suppressor> sub) {
		return new SuppressMany(sub);
	}
	/**
	 * Make a {@link Suppressor} whose {@link #release()} method calls the
	 * {@link #release()} methods of several other {@link Suppressor}s 
	 * @param sub
	 * @return
	 */
	public static SuppressMany many(Iterable<? extends Suppressor> sub) {
		return new SuppressMany(sub);
	}
	/**
	 * Make a {@link Suppressor} whose {@link #release()} method calls the
	 * {@link #release()} methods of several other {@link Suppressor}s 
	 * @param sub
	 * @return
	 */
	public static SuppressMany many(Suppressor... sub) {
		return new SuppressMany(sub);
	}
	/**
	 * Make a {@link Suppressor} whose {@link #release} method runs the given 
	 * {@link Runnable}, but at most once
	 * @param r
	 * @return
	 */
	public static Suppressor wrap(Runnable r) {
		return new Wrapped(r);
	}
	/**
	 * Composition of {@link #wrap(Runnable)} and {@link #wrapWeak()}
	 * @param r
	 * @return
	 */
	public static Suppressor wrapWeak(Runnable r) {
		return wrap(r).wrapWeak();
	}



	/**
	 * A {@link Suppressor} whose {@link #release} method runs a given {@link Runnable},
	 * but at most once
	 * @author bb
	 *
	 */
	public static class Wrapped implements Suppressor{
		Runnable release;
		public Wrapped(Runnable r) {
			Objects.requireNonNull(r);
			release=r;
		}
		@Override
		public boolean release() {
			Runnable r;
			synchronized (this) {
				r=release;
				release=null;
			}
			if(r!=null) {
				r.run();
				return true;
			}else {
				return false;
			}
		}
		@Override
		public boolean isDefinitelyReleased() {
			return release==null;
		}
	}
	
	/**
	 * A Wrapper around another {@link Suppressor} that ensures its 
	 * {@link #release()} method is called once the Wrapper becomes weakly reachable
	 * @author bb
	 *
	 */
	public static class WrapWeak implements Suppressor{
		private final static Logger log=Logger.getLogger("Suppressor.WrapWeak");
		static final boolean TRACE = true;
		Suppressor back;
		Exception generationTrace;
		{
			if(TRACE) {
				try {
					throw new RuntimeException();
				} catch(RuntimeException x) {
					generationTrace = x;
				}
			}
		}
		/**
		 * 
		 * @param back The wrapped {@link Suppressor}
		 * @param warn If this parameter is not <code>null</code>, 
		 * a warning containing the given {@link String} will be logged if the
		 * wrapped {@link Suppressor} had not yet been {@link #release() release}d
		 * when this wrapper became weakly reachable.
		 */
		public WrapWeak(Suppressor back, String warn) {
			this(StandardExecutors.unlimited(), back, warn);
		}
		@Override
		public boolean isDefinitelyReleased() {
			return back.isDefinitelyReleased();
		}
		/**
		 * 
		 * @param back The wrapped {@link Suppressor}
		 * @param warn If this parameter is not <code>null</code>, 
		 * a warning containing the given {@link String} will be logged if the
		 * wrapped {@link Suppressor} had not yet been {@link #release() release}d
		 * when this wrapper became weakly reachable.
		 * @param exec The {@link ExecutorService} to run the {@link #release()} method and log the warning
		 */
		public WrapWeak(ExecutorService exec, Suppressor back, String warn) {
			this.back=back;
			if(warn!=null)
				WeakCleanup.runIfWeak(this, ()->{
					if(!back.isDefinitelyReleased())
						exec.execute(()->{
							if(back.release()) 
								log.warning("You forgot to release a Suppressor (hint: "+warn+")");
						});
				});
			else
				WeakCleanup.runIfWeak(this, ()->exec.execute(back::release));
		}
		@Override
		public boolean release() {
			try {
				return back.release();
			}finally {
				back=NOP;
			}
		}
		@Override
		public String toString() {
			if(generationTrace==null)
				return super.toString();
			StringBuilder sb = new StringBuilder();
			for(StackTraceElement e: generationTrace.getStackTrace()) {
				sb.append(e).append('\n');
			}
			return sb.toString();
		}
	}

	/**
	 * A wrapper around several sub-{@link Suppressor}s, whose {@link #release()} method
	 * calls all the {@link #release()} methods of the sub-{@link Suppressor}s
	 * @author bb
	 *
	 */
	public static class SuppressMany implements Suppressor{
		ArrayList<Suppressor> children=new ArrayList<>();
		public SuppressMany() {
			children=new ArrayList<>();
		}
		public SuppressMany(Collection<? extends Suppressor> sub) {
			children=new ArrayList<>(sub);
		}
		public SuppressMany(Iterable<? extends Suppressor> sub) {
			children=new ArrayList<>();
			sub.forEach(children::add);
		}
		public SuppressMany(Suppressor... sub) {
			if(sub!=null) {
				children=new ArrayList<>(sub.length);
				for(int i=0; i<sub.length; ++i)
					children.add(sub[i]);
			}else {
				children=new ArrayList<>(0);
			}
		}


		@Override
		public boolean release() {
			ArrayList<Suppressor> c;
			synchronized (this) {
				c = children;
				children=null;
			}
			if(c!=null) {
				for(Suppressor s: c)
					StandardExecutors.safe(s::release);
				return true;
			}else {
				return false;
			}
		}
		@Override
		public boolean isDefinitelyReleased() {
			return children==null;
		}
		/**
		 * Add a {@link Suppressor} to the list of sub-{@link Suppressor}s 
		 * @param s
		 * @return
		 * @throws IllegalStateException if {@link #release()} had already been called
		 */
		public synchronized SuppressMany add(Suppressor s) {
			if(children==null)
				throw new IllegalStateException("This Suppressor has already been released!");
			children.add(s);
			return this;
		}
		/**
		 * Ensure that the next call to {@link #add(Suppressor)} cannot result in an 
		 * {@link OutOfMemoryError}
		 * @return {@code this}
		 */
		public SuppressMany makePlaceFor1() {
			children.ensureCapacity(children.size()+1);
			return this;
		}
		/**
		 * Add all the {@link Suppressor}s obtained by calling the give {@code method} on the given objects.
		 * @param <E>
		 * @param method
		 * @param suppressThese
		 * @return
		 */
		public <E> SuppressMany add(Function<? super E, ? extends Suppressor> method, Iterable<? extends E> suppressThese) {
			for(E e: suppressThese) {
				if(e!=null)
					makePlaceFor1().add(method.apply(e));
			}
			return this;
		}
		/**
		 * Add all the {@link Suppressor}s obtained by calling the give {@code method} on the given objects.
		 * @param <E>
		 * @param method
		 * @param suppressThese
		 * @return
		 */
		@SafeVarargs
		public final <E> SuppressMany add(Function<? super E, ? extends Suppressor> method, E... suppressThese) {
			for(E e: suppressThese) {
				if(e!=null)
					makePlaceFor1().add(method.apply(e));
			}
			return this;
		}
		/**
		 * Add all the {@link Suppressor}s obtained by calling the give {@code method} on the given objects.
		 * The second argument for the {@link BiConsumer} will be {@code this} {@link SuppressMany} instance
		 * @param <E>
		 * @param method
		 * @param suppressThese
		 * @return
		 */
		public  <E> SuppressMany add(BiConsumer<? super E, ? super SuppressMany> method, Iterable<? extends E> suppressThese) {
			for(E e: suppressThese) {
				if(e!=null)
					method.accept(e, this);
			}
			return this;
		}
		/**
		 * Add all the {@link Suppressor}s obtained by calling the give {@code method} on the given objects.
		 * The second argument for the {@link BiConsumer} will be {@code this} {@link SuppressMany} instance
		 * @param <E>
		 * @param method
		 * @param suppressThese
		 * @return
		 */
		@SafeVarargs
		public final <E> SuppressMany add(BiConsumer<? super E, ? super SuppressMany> method, E... suppressThese) {
			for(E e: suppressThese) {
				if(e!=null)
					method.accept(e, this);
			}
			return this;
		}
		/**
		 * Add all the {@link Suppressor}s obtained by calling the give {@code method} on the given objects.
		 * If anything is thrown, {@link #release()} will be called.
		 * @param <E>
		 * @param method
		 * @param suppressThese
		 * @return
		 */		
		public <E> SuppressMany more(Function<? super E, ? extends Suppressor> method, Iterable<? extends E> suppressThese) {
			boolean fail = true;
			try {
				for(E e: suppressThese) {
					if(e!=null)
						makePlaceFor1().add(method.apply(e));
				}
				return this;
			}finally {
				if(fail)
					release();
			}
		}
		/**
		 * Add all the {@link Suppressor}s obtained by calling the give {@code method} on the given objects.
		 * If anything is thrown, {@link #release()} will be called.
		 * @param <E>
		 * @param method
		 * @param suppressThese
		 * @return
		 */	
		@SafeVarargs
		public final <E> SuppressMany more(Function<? super E, ? extends Suppressor> method, E... suppressThese) {
			boolean fail = true;
			try {
				for(E e: suppressThese) {
					if(e!=null)
						makePlaceFor1().add(method.apply(e));
				}
				return this;
			}finally {
				if(fail)
					release();		
			}
		}
		/**
		 * Add all the {@link Suppressor}s obtained by calling the give {@code method} on the given objects.
		 * The second argument for the {@link BiConsumer} will be {@code this} {@link SuppressMany} instance
		 * If anything is thrown, {@link #release()} will be called.
		 * @param <E>
		 * @param method
		 * @param suppressThese
		 * @return
		 */
		public  <E> SuppressMany more(BiConsumer<? super E, ? super SuppressMany> method, Iterable<? extends E> suppressThese) {
			boolean fail = true;
			try {
				for(E e: suppressThese) {
					if(e!=null)
						method.accept(e, this);
				}
				return this;
			}finally {
				if(fail)
					release();
			}
		}
		/**
		 * Add all the {@link Suppressor}s obtained by calling the give {@code method} on the given objects.
		 * The second argument for the {@link BiConsumer} will be {@code this} {@link SuppressMany} instance
		 * If anything is thrown, {@link #release()} will be called.
		 * @param <E>
		 * @param method
		 * @param suppressThese
		 * @return
		 */
		@SafeVarargs
		public final <E> SuppressMany more(BiConsumer<? super E, ? super SuppressMany> method, E... suppressThese) {
			boolean fail = true;
			try {
				for(E e: suppressThese) {
					if(e!=null)
						method.accept(e, this);
				}
				return this;
			}finally {
				if(fail)
					release();
			}
		}
	}

	/**
	 * Use the given {@code method} to suppress several objects of the same type
	 * @param <E>
	 * @param method
	 * @param suppressThese
	 * @return
	 */
	public static <E> Suppressor many(Function<? super E, ? extends Suppressor> method, Iterable<? extends E> suppressThese) {
		SuppressMany ret = Suppressor.many();
		boolean fail = true;
		try {
			ret.add(method, suppressThese);
			fail = false;
			return ret;
		}finally {
			if(fail)
				ret.release();
		}
	}
	/**
	 * Use the given {@code method} to suppress several objects of the same type
	 * @param <E>
	 * @param method
	 * @param suppressThese
	 * @return
	 */
	public static <E> SuppressMany many(BiConsumer<? super E, ? super SuppressMany> method, Iterable<? extends E> suppressThese) {
		SuppressMany ret = Suppressor.many();
		boolean fail = true;
		try {
			ret.add(method, suppressThese);
			fail = false;
			return ret;
		}finally {
			if(fail)
				ret.release();
		}
	}
	/**
	 * Return {@code true} if this {@link Suppressor} has surely been released, {@code false} if it has not or it is not certain.
	 * @return
	 */
	public boolean isDefinitelyReleased();
	/**
	 * Use the given {@code method} to suppress several objects of the same type
	 * @param <E>
	 * @param method
	 * @param suppressThese
	 * @return
	 */
	@SafeVarargs
	public static <E> SuppressMany many(Function<? super E, ? extends Suppressor> method, E... suppressThese) {
		SuppressMany ret = Suppressor.many();
		boolean fail = true;
		try {
			ret.add(method, suppressThese);
			fail = false;
			return ret;
		}finally {
			if(fail)
				ret.release();
		}
	}
	/**
	 * Use the given {@code method} to suppress several objects of the same type
	 * @param <E>
	 * @param method
	 * @param suppressThese
	 * @return
	 */
	@SafeVarargs
	public static <E> SuppressMany many(BiConsumer<? super E, ? super SuppressMany> method, E... suppressThese) {
		SuppressMany ret = Suppressor.many();
		boolean fail = true;
		try {
			ret.add(method, suppressThese);
			fail = false;
			return ret;
		}finally {
			if(fail)
				ret.release();
		}
	}
	/**
	 * Create a new suppressor and return it, but before returning, {@link #release() release} this one
	 * @param s
	 * @return
	 */
	public default WrapWeak replace(Supplier<? extends Suppressor> s) {
		WrapWeak ret = s.get().wrapWeak();
		Suppressor emergencyRelease = ret;
		try {
			release();
			emergencyRelease = null;
			return ret;
		}finally {
			if(emergencyRelease!=null)
				emergencyRelease.release();
		}
	}
	/**
	 * Create a new suppressor and return it, but before returning, {@link #release() release} this one
	 * @param s
	 * @return
	 */
	public default <T> WrapWeak replace(Function<? super T, ? extends Suppressor> method, T object) {
		WrapWeak ret = method.apply(object).wrapWeak();
		Suppressor emergencyRelease = ret;
		try {
			release();
			emergencyRelease = null;
			return ret;
		}finally {
			if(emergencyRelease!=null)
				emergencyRelease.release();
		}
	}
	/**
	 * Create a new suppressor and return it, but before returning, {@link #release() release} this one
	 * @param s
	 * @return
	 */
	public default <T> WrapWeak replace(Function<? super T, ? extends Suppressor> method, @SuppressWarnings("unchecked") T... objects) {
		WrapWeak ret = many(method, objects).wrapWeak();
		Suppressor emergencyRelease = ret;
		try {
			release();
			emergencyRelease = null;
			return ret;
		}finally {
			if(emergencyRelease!=null)
				emergencyRelease.release();
		}
	}
	/**
	 * Calls {@link #release()}. This default method should not be overridden.
	 */
	@Override default void run() {
		release();
	}
	/**
	 * Use this if you want to be able cancel the release of this {@link Suppressor} by a try-with-resources block, in order to defer it somewhere else,
	 * for example a listener that waits for some event or another thread.
	 * 
	 * Use like this:
	 * <code>
	 * <pre>
	 * Suppressor s = getSomeSuppressor().wrapWeak();
	 * try(CancelClose cc = s.cancellableRelease()) {
	 * 	stuffThatMayThrowExceptions();
	 * 	new Thread(()->{
	 * 		try(Suppressor s2=s){
	 * 	        moreStuff();
	 * 	    }
	 * 	}).start();
	 * 	//responsibility for releasing s has been handed off to the other Thread
	 * 	cc.release();
	 * }
	 * @return
	 */
	public default CancelClose cancellableRelease() {
		boolean success = false;
		try {
			CancellableRelease ret =  new CancellableRelease(this);
			success=true;
			return ret;
		}finally {
			if(!success)
				release();
		}
	}
	/**
	 * Make a {@link Suppressor} that {@link #release() release}s this one 
	 * in a job executing on the given {@link ExecutorService}.
	 * 
	 * If the {@link ExecutorService} rejects the execution, this {@link Suppressor}
	 * is released immediately in the {@link Thread} that called the returned
	 * {@link Suppressor}'s {@link #release()} method.
	 * 
	 * Note: it is assumed that the default implementation of {@link #run()} 
	 * is not overridden.
	 * @param s
	 * @return
	 */
	public default Suppressor wrapAsync(ExecutorService s) {
		Objects.requireNonNull(s);
		Suppressor ret = null;
		try {
			return ret = wrap(()->{
				try {
					s.execute(this);
				}catch(RejectedExecutionException x) {
					try {
						release();
					}finally {
						log.log(Level.SEVERE, "Asynchronous release of suppressor failed", x);
					}
				}
			});
		}finally {
			if(ret==null)
				release();
		}
	}
	/**
	 * Make a {@link Suppressor} that {@link #release() release}s this one 
	 * in a job executing on the standard {@link ExecutorService}.
	 * @see #wrapAsync(ExecutorService)
	 * @see StandardExecutors#unlimited()
	 * @return
	 */
	public default Suppressor wrapAsync() {
		return wrapAsync(StandardExecutors.unlimited());
	}
	/**
	 * Lift a suppression method for some type to a suppression method for {@link Iterable}s of that type.
	 * @param <E>
	 * @param method
	 * @return
	 */
	public static <E> BiConsumer<Iterable<? extends E>, SuppressMany> lift(BiConsumer<? super E, ? super SuppressMany> method){
		return (iterable, supp) -> {
			if(iterable!=null)
				supp.add(method, iterable);
		};
	}
	/**
	 * Lift a suppression method for some type to a suppression method for arrays of that type.
	 * @param <E>
	 * @param method
	 * @return
	 */
	public static <E> BiConsumer<E[], SuppressMany> liftArray(BiConsumer<? super E, ? super SuppressMany> method){
		return (array, supp) -> {
			if(array!=null)
				supp.add(method, array);
		};
	}
}
