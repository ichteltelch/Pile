package pile.aspect;

import java.lang.ref.WeakReference;
import java.util.function.BiConsumer;
import java.util.function.BiPredicate;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

import pile.aspect.combinations.Pile;
import pile.aspect.listen.ListenValue;
import pile.aspect.listen.ValueListener;
import pile.impl.PileImpl;
import pile.utils.SequentialQueue;

/**
 * A {@link ValueBracket} executes certain actions when an object that can hold a value
 * starts or stops holding that value.
 * When the value begins being held, the bracket is "opened". 
 * It is "closed" when the Value ceases to be held.
 * 
 * Unless otherwise noted, the same {@link ValueBracket} instance 
 * can be used on multiple owners usually because it is stateless)
 * 
 * Since the {@link #open(Object, Object)} and {@link #close(Object, Object)} methods may be invoked
 * while some internal locks are held, some things that also affect these locks, such as {@link PileImpl#destroy() destroy}ing things,
 * should be deferred into a {@link SequentialQueue}. The {@link #queued(SequentialQueue, BiPredicate, BiPredicate, BiPredicate) queued}-methods
 * can be used to do that conveniently.
 * 
 * @author bb
 *
 * @param <E> Type of the value being held
 * @param <O> Type of the object holding the value, called owner
 */
public interface ValueBracket<E, O> {
	/**
	 * A {@link ValueBracket} that does not care about the object holding the value, only the value itself.
	 * @author bb
	 *
	 * @param <V>
	 */
	public static interface ValueOnlyBracket<V> extends ValueBracket<V, Object>{}
	/**
	 * A bracket that lets one or more {@link Depender}s depend on a {@link Dependency}
	 * extracted from the value while the bracket is open.
	 * @author bb
	 *
	 * @param <V>
	 */
	public static final class StrongDependencyBracket<V> implements ValueOnlyBracket<V> {
		private final Depender[] ds;
		private final Function<? super V, ? extends Dependency> extract;
		private final boolean inheritable;
		private final Depender d0;
		boolean recompute;

		/**
		 * 
		 * @param inheritable See {@link #isInheritable()}
		 * @param extract The Function that extracts the {@link Dependency} that should be added. 
		 * It is important that this function is deterministic, otherwise the {@link Dependency} cannot be removed again properly. 
		 * @param d0 One of the {@link Depender}s. May be <code>null</code>.
		 * @param ds Possibly more {@link Depender}s
		 */
		public StrongDependencyBracket(boolean inheritable, Function<? super V, ? extends Dependency> extract,
				boolean recompute, Depender d0, Depender[] ds) {
			this.ds = ds==null?null:ds.clone();
			this.extract = extract;
			this.inheritable = inheritable;
			this.d0 = d0;
			this.recompute=recompute;
		}

		@Override
		public boolean open(V value, Object owner) {
			if(value==null)
				return true;
			Dependency fd = extract.apply(value);
			if(d0!=null)
				d0.addDependency(fd, recompute);
			if(ds!=null)
				for(Depender d: ds)
					d.addDependency(fd, recompute);
			return true;
		}

		@Override
		public boolean close(V value, Object owner) {
			if(value==null)
				return false;
			Dependency fd = extract.apply(value);
			if(d0!=null)
				d0.removeDependency(fd, recompute);
			if(ds!=null)
				for(Depender d: ds)
					d.removeDependency(fd, recompute);
			return false;
		}

		@Override
		public boolean isInheritable() {
			return inheritable;
		}
		@Override public boolean nopOpen() {return false;}
		@Override public boolean nopClose() {return false;}
	}
	/**
	 * A bracket that lets one or more {@link Depender}s depend on the value while the bracket is open.
	 * This Bracket keeps only {@link WeakReference}s to the Dependers. When all the
	 * references have been cleared, the bracket requests that it be removed from the value holder,
	 * as it will from now on have no effect.
	 * @author bb
	 *
	 * @param <V>
	 */

	public static final class WeakDependencyBracket<V> implements ValueOnlyBracket<V> {
		private final WeakReference<Depender>[] ds;
		private final Function<? super V, ? extends Dependency> extract;
		private final boolean inheritable;
		private final WeakReference<Depender> d0;

		/**
		 * 
		 * @param inheritable See {@link #isInheritable()}
		 * @param extract The Function that extracts the {@link Dependency} that should be added. 
		 * It is important that this function is deterministic, otherwise the {@link Dependency} cannot be removed again properly. 
		 * @param d0 One of the {@link Depender}s. May be <code>null</code>.
		 * @param ds Possibly more {@link Depender}s
		 */
		@SuppressWarnings("unchecked")
		public WeakDependencyBracket(boolean inheritable, Function<? super V, ? extends Dependency> extract,
				Depender d0, Depender[] ds) {
			if(ds==null)
				this.ds=null;
			else {
				this.ds=new WeakReference[ds.length];
				for(int i=0; i<ds.length; ++i) {
					this.ds[i]=new WeakReference<>(ds[i]);
				}
			}			
			this.extract = extract;
			this.inheritable = inheritable;
			this.d0 = d0==null?null:new WeakReference<Depender>(d0);
		}

		@Override
		public boolean open(V value, Object owner) {
			if(value==null)
				return true;
			Dependency fd = extract.apply(value);
			boolean doneSth=false;
			if(d0!=null){
				Depender d = d0.get();
				if(d!=null) {
					d.addDependency(fd);
					doneSth=true;
				}

			}
			if(ds!=null)
				for(WeakReference<Depender> ref: ds) {
					Depender d = ref.get();
					if(d!=null) {
						d.addDependency(fd);
						doneSth=true;
					}
				}
			return doneSth;
		}

		@Override
		public boolean close(V value, Object owner) {
			if(value==null)
				return false;
			Dependency fd = extract.apply(value);
			if(d0!=null){
				Depender d = d0.get();
				if(d!=null)
					d.removeDependency(fd);

			}
			if(ds!=null)
				for(WeakReference<Depender> ref: ds) {
					Depender d = ref.get();
					if(d!=null)
						d.removeDependency(fd);
				}
			return false;
		}

		@Override
		public boolean isInheritable() {
			return inheritable;
		}
		@Override public boolean nopOpen() {return false;}
		@Override public boolean nopClose() {return false;}

	}

	/**
	 * A bracket that lets a {@link Depender} extracted from the value depend on one or more 
	 * {@link Dependencies}s while the bracket is open.
	 * @author bb
	 *
	 * @param <V>
	 */
	public static final class DependerBracket<V> implements ValueOnlyBracket<V> {
		private final Dependency[] ds;
		private final Function<? super V, ? extends Depender> extract;
		private final boolean inheritable;
		private final Dependency d0;
		boolean triggerChange;
		/**
		 * @param inheritable See {@link #isInheritable()}
		 * @param extract The Function that extracts the {@link Depender} that should be made to depend on the {@link Dependency Dependencies}
		 * It is important that this function is deterministic, otherwise the {@link Dependency Dependencies} cannot be removed again properly.
		 * @param triggerChange Whether the {@link Dependency Dependencies} should be added and removed in a way that triggers recomputation of the {@link Depender}. 
		 * @param d0 One of the {@link Dependency Dependencies}. May be <code>null</code>.
		 * @param ds Possibly more {@link Dependency Dependencies}
		 */
		public DependerBracket(boolean inheritable, Function<? super V, ? extends Depender> extract, boolean triggerChange,
				Dependency d0, Dependency[] ds) {
			this.ds = ds==null?null:ds.clone();
			this.extract = extract;
			this.inheritable = inheritable;
			this.d0 = d0;
			this.triggerChange=triggerChange;
		}

		@Override
		public boolean open(V value, Object owner) {
			if(value==null)
				return true;
			Depender fd = extract.apply(value);
			if(d0!=null)
				fd.addDependency(d0, triggerChange);
			if(ds!=null)
				for(Dependency d: ds)
					fd.addDependency(d, triggerChange);
			return true;
		}

		@Override
		public boolean close(V value, Object owner) {
			if(value==null)
				return false;
			Depender fd = extract.apply(value);
			if(d0!=null)
				fd.removeDependency(d0, triggerChange);
			if(ds!=null)
				for(Dependency d: ds)
					fd.removeDependency(d, triggerChange);
			return false;
		}

		@Override
		public boolean isInheritable() {
			return inheritable;
		}
		@Override public boolean nopOpen() {return false;}
		@Override public boolean nopClose() {return false;}

	}




	/**
	 * Open the bracket on the value
	 * @param value the value
	 * @param owner the object that starts holding the value
	 * @return <code>true</code> iff the bracket should remain installed, 
	 * <code>false</code> iff it has become obsolete
	 */
	public boolean open(E value, O owner);
	/**
	 * Close the bracket on the value.
	 * @param value the value
	 * @param owner the object that stops holding the value
	 * @return whether the reference to the value should be kept. 
	 * If all brackets that were open return <code>false</code> from this method, 
	 * the reference to the value should be <code>null</code>ed.
	 */
	public boolean close(E value, O owner);
	/**
	 * A {@link ValueBracket} can be declared inheritable,
	 * which causes certain value holders derived from an existing one to inherit the bracket
	 * @see HasBrackets#bequeathBrackets(HasBrackets)
	 * @return
	 */
	public boolean isInheritable();
	
	/**
	 * @return <code>true</code> is opening the bracket is a guaranteed no-op.
	 */
	public boolean nopOpen();
	/**
	 * @return <code>true</code> is closing the bracket is a guaranteed no-op.
	 */
	public boolean nopClose();
	

	/**
	 * Make a {@link ValueBracket} that only does something when it is opened
	 * @param <E>
	 * @param <O>
	 * @param inheritable
	 * @param open
	 * @return
	 */
	public static <E, O> ValueBracket<E, O> openOnly(boolean inheritable, BiConsumer<? super E, ? super O> open) {
		return new ValueBracket<E, O>() {
			@Override public boolean close(E value, O owner) {return false;}
			@Override public boolean open(E value, O owner) {open.accept(value, owner); return true;}
			@Override public boolean isInheritable() {return inheritable;	}
			@Override public boolean nopOpen() {return false;}
			@Override public boolean nopClose() {return true;}
		};
	}

	/**
	 * Make a {@link ValueBracket} that only does something when it is closed
	 * @param <E>
	 * @param <O>
	 * @param inheritable
	 * @param close
	 * @return
	 */
	public static <E, O> ValueBracket<E, O> closeOnly(boolean inheritable, BiPredicate<? super E, ? super O> close) {
		return new ValueBracket<E, O>() {
			@Override public boolean close(E value, O owner) {return close.test(value, owner);}
			@Override public boolean open(E value, O owner) {return true;}
			@Override public boolean isInheritable() {return inheritable;	}
			@Override public boolean nopOpen() {return true;}
			@Override public boolean nopClose() {return false;}
		};
	}
	/**
	 * Make a {@link ValueBracket} defined in terms of its open and close action,
	 * which usually will be lambdas.
	 * @param <E>
	 * @param <O>
	 * @param inheritable
	 * @param open
	 * @param close
	 * @return
	 */
	public static <E, O> ValueBracket<E, O> make(boolean inheritable, BiConsumer<? super E, ? super O> open, BiPredicate<? super E, ? super O> close) {
		return new ValueBracket<E, O>() {
			@Override public boolean close(E value, O owner) {return close.test(value, owner);}
			@Override public boolean open(E value, O owner) {open.accept(value, owner); return true;}
			@Override public boolean isInheritable() {return inheritable;	}
			@Override public boolean nopOpen() {return false;}
			@Override public boolean nopClose() {return false;}
		};
	}
	/**
	 * Make a {@link ValueBracket} that only does something when it is opened
	 * @param <E>
	 * @param <O>
	 * @param inheritable
	 * @param open
	 * @return
	 */
	public static <E> ValueOnlyBracket<E> openOnly(boolean inheritable, Consumer<? super E> open) {
		return new ValueOnlyBracket<E>() {
			@Override public boolean close(E value, Object owner) {return false;}
			@Override public boolean open(E value, Object owner) {open.accept(value); return true;}
			@Override public boolean isInheritable() {return inheritable;	}
			@Override public boolean nopOpen() {return false;}
			@Override public boolean nopClose() {return true;}
		};
	}
	/**
	 * Make a {@link ValueBracket} that only does something when it is closed
	 * @param <E>
	 * @param <O>
	 * @param inheritable
	 * @param close
	 * @return
	 */
	public static <E> ValueOnlyBracket<E> closeOnly(boolean inheritable, Predicate<? super E> close) {
		return new ValueOnlyBracket<E>() {
			@Override public boolean close(E value, Object owner) {return close.test(value);}
			@Override public boolean open(E value, Object owner) {return true;}
			@Override public boolean isInheritable() {return inheritable;	}
			@Override public boolean nopOpen() {return true;}
			@Override public boolean nopClose() {return false;}
		};
	}
	/**
	 * Make a {@link ValueBracket} that only does something when it is closed.
	 * It does not want the reference to the value to be kept.
	 * @param <E>
	 * @param <O>
	 * @param inheritable
	 * @param close
	 * @return
	 */
	public static <E> ValueOnlyBracket<E> closeOnly(boolean inheritable, Consumer<? super E> close) {
		return new ValueOnlyBracket<E>() {
			@Override public boolean close(E value, Object owner) {close.accept(value); return false;}
			@Override public boolean open(E value, Object owner) {return true;}
			@Override public boolean isInheritable() {return inheritable;	}
			@Override public boolean nopOpen() {return true;}
			@Override public boolean nopClose() {return false;}
		};
	}
	/**
	 * Make a {@link ValueBracket} defined in terms of its open and close action,
	 * which usually will be lambdas.
	 * @param <E>
	 * @param <O>
	 * @param inheritable
	 * @param open
	 * @param close
	 * @return
	 */
	public static <E> ValueOnlyBracket<E> make(boolean inheritable, Consumer<? super E> open, Predicate<? super E> close) {
		return new ValueOnlyBracket<E>() {
			@Override public boolean close(E value, Object owner) {return close.test(value);}
			@Override public boolean open(E value, Object owner) {open.accept(value); return true;}
			@Override public boolean isInheritable() {return inheritable;	}
			@Override public boolean nopOpen() {return false;}
			@Override public boolean nopClose() {return false;}
		};
	}

	/**
	 * Make a {@link ValueBracket} defined in terms of its open and close action,
	 * which usually will be lambdas.
	 * It does not want the reference to the value to be kept. 
	 * @param <E>
	 * @param <O>
	 * @param inheritable
	 * @param open
	 * @param close
	 * @return
	 */
	public static <E> ValueOnlyBracket<E> make(boolean inheritable, Consumer<? super E> open, Consumer<? super E> close) {
		return new ValueOnlyBracket<E>() {
			@Override public boolean close(E value, Object owner) {close.accept(value); return false;}
			@Override public boolean open(E value, Object owner) {open.accept(value); return true;}
			@Override public boolean isInheritable() {return inheritable;	}
			@Override public boolean nopOpen() {return false;}
			@Override public boolean nopClose() {return false;}
		};
	}
	/**
	 * Make a bracket that adds and removes a {@link ValueListener} to/from the owner.
	 * When the {@link ValueListener} is added, it is called with a <code>null</code>
	 * {@link ValueListener} argument}.
	 * The bracket will not be inheritable.
	 * @param l A {@link ValueListener} that should be registered as an eager listener
	 * on the owner
	 * as long as the bracket is opened.
	 * @return A non-inheritable bracket
	 */
	public static ValueBracket<Object, ListenValue> ownerValueListenerBracket(ValueListener l){
		return ownerValueListenerBracket(false, l);
	}
	/**
	 * Make a bracket that adds and removes a {@link ValueListener} to/from the owner.
	 * When the {@link ValueListener} is added, it is called with a <code>null</code>
	 * {@link ValueListener} argument}
	 * TODO: Is there really a use case for that?
	 * @param l A {@link ValueListener} that should be registered as an eager listener
	 * on the owner
	 * as long as the bracket is opened.
	 * @param inheritable Whether the bracket should be inheritable
	 * @return A bracket
	 */
	public static ValueBracket<Object, ListenValue> ownerValueListenerBracket(boolean inheritable, ValueListener l){
		return new ValueBracket<Object, ListenValue>() {
			@Override
			public boolean open(Object value, ListenValue owner) {
				owner.addValueListener(l);
				l.valueChanged(null);
				return true;
			}

			@Override
			public boolean close(Object value, ListenValue owner) {
				owner.removeValueListener(l);
				return false;
			}

			@Override
			public boolean isInheritable() {
				return inheritable;
			}
			@Override public boolean nopOpen() {return false;}
			@Override public boolean nopClose() {return false;}

		};
	}
	/**
	 * Make a bracket that keeps a {@link ValueListener} added to a {@link ListenValue}
	 * extracted from the value while the bracket is open.
	 * When the {@link ValueListener} is added, it is called with a <code>null</code>
	 * {@link ValueListener} argument}
	 * @param extract A function to extract a {@link ListenValue} from the value on which the bracket
	 * is opened or closed. It is important that his {@link Function} is deterministic, because
	 * otherwise the {@link ValueListener} cannot be removed again properly.
	 * @param l A {@link ValueListener} that should be registered as on the result of {@code extract}
	 * as long as the bracket is opened.
	 * @param inheritable Whether the bracket should be inheritable
	 * @return A bracket
	 */
	public static <E> ValueOnlyBracket<E> valueListenerBracket(boolean inheritable, Function<? super E, ? extends ListenValue> extract, ValueListener l){
		return new ValueOnlyBracket<E>() {
			@Override
			public boolean open(E value, Object owner) {
				extract.apply(value).addValueListener_(l);
				return true;
			}

			@Override
			public boolean close(E value, Object owner) {
				extract.apply(value).removeValueListener(l);
				return false;
			}

			@Override
			public boolean isInheritable() {
				return inheritable;
			}
			@Override public boolean nopOpen() {return false;}
			@Override public boolean nopClose() {return false;}

		};
	}


	/**
	 * Factory method for making a {@link StrongDependencyBracket} that makes a single {@link Depender}
	 * depend on the value while it is open
	 * @param <V>
	 * @param d0
	 * @return
	 */
	public static <V extends Dependency> StrongDependencyBracket<V> dependencyBracket(
			Depender d0
			){
		return new StrongDependencyBracket<V>(false, (Function<? super V, ? extends Dependency>) d->d, true, d0, (Depender[])null);
	}
	/**
	 * Factory method for making a {@link StrongDependencyBracket} that makes a single {@link Depender}
	 * depend on something extracted from the value while it is open
	 * @param <V>
	 * @param extract The Function that extracts the {@link Dependency} that should be added. 
	 * It is important that this function is deterministic, otherwise the {@link Dependency} cannot be removed again properly. 
	 * @param d0
	 * @return
	 */
	public static <V> StrongDependencyBracket<V> dependencyBracket(
			Function<? super V, ? extends Dependency> extract,
			Depender d0
			){
		return new StrongDependencyBracket<V>(false, extract, true, d0, (Depender[])null);
	}
	/**
	 * Factory method for making a {@link StrongDependencyBracket} that makes multiple {@link Depender}s
	 * depend on the value while it is open
	 * @param <V>
	 * @param d0
	 * @return
	 */
	public static <V extends Dependency> StrongDependencyBracket<V> dependencyBracket(
			Depender... ds
			){
		return new StrongDependencyBracket<V>(false, (Function<? super V, ? extends Dependency>) d->d, true, (Depender) null, ds);
	}
	/**
	 * Factory method for making a {@link StrongDependencyBracket} that makes multiple {@link Depender}s
	 * depend on on something extracted from the value while it is open
	 * @param extract The Function that extracts the {@link Dependency} that should be added. 
	 * It is important that this function is deterministic, otherwise the {@link Dependency} cannot be removed again properly. 
	 * @param <V>
	 * @param d0
	 * @return
	 */
	public static <V> StrongDependencyBracket<V> dependencyBracket(
			Function<? super V, ? extends Dependency> extract,
			Depender... ds
			){
		return new StrongDependencyBracket<V>(false, extract, true, (Depender) null, ds);
	}


	/**
	 * Factory method for making a {@link StrongDependencyBracket} that makes a single {@link Depender}
	 * depend on the value while it is open
	 * @param <V>
	 * @param d0
	 * @return
	 */
	public static <V extends Dependency> StrongDependencyBracket<V> dependencyBracket( boolean recompute,
			Depender d0
			){
		return new StrongDependencyBracket<V>(false, (Function<? super V, ? extends Dependency>) d->d, recompute, d0, (Depender[])null);
	}
	/**
	 * Factory method for making a {@link StrongDependencyBracket} that makes a single {@link Depender}
	 * depend on something extracted from the value while it is open
	 * @param <V>
	 * @param extract The Function that extracts the {@link Dependency} that should be added. 
	 * It is important that this function is deterministic, otherwise the {@link Dependency} cannot be removed again properly. 
	 * @param d0
	 * @return
	 */
	public static <V> StrongDependencyBracket<V> dependencyBracket(
			Function<? super V, ? extends Dependency> extract, boolean recompute,
			Depender d0
			){
		return new StrongDependencyBracket<V>(false, extract, recompute, d0, (Depender[])null);
	}
	/**
	 * Factory method for making a {@link StrongDependencyBracket} that makes multiple {@link Depender}s
	 * depend on the value while it is open
	 * @param <V>
	 * @param d0
	 * @return
	 */
	public static <V extends Dependency> StrongDependencyBracket<V> dependencyBracket( boolean recompute,
			Depender... ds
			){
		return new StrongDependencyBracket<V>(false, (Function<? super V, ? extends Dependency>) d->d, recompute, (Depender) null, ds);
	}
	/**
	 * Factory method for making a {@link StrongDependencyBracket} that makes multiple {@link Depender}s
	 * depend on on something extracted from the value while it is open
	 * @param extract The Function that extracts the {@link Dependency} that should be added. 
	 * It is important that this function is deterministic, otherwise the {@link Dependency} cannot be removed again properly. 
	 * @param <V>
	 * @param d0
	 * @return
	 */
	public static <V> StrongDependencyBracket<V> dependencyBracket(
			Function<? super V, ? extends Dependency> extract, boolean recompute,
			Depender... ds
			){
		return new StrongDependencyBracket<V>(false, extract, recompute, (Depender) null, ds);
	}


	/**
	 * Factory method for making a {@link WeakDependencyBracket} that makes a single {@link Depender}
	 * depend on the value while it is open
	 * @param <V>
	 * @param d0
	 * @return
	 */
	public static <V extends Dependency> WeakDependencyBracket<V> dependencyBracket_weak(
			Depender d0
			){
		return new WeakDependencyBracket<V>(false, (Function<? super V, ? extends Dependency>) d->d, d0, (Depender[])null);
	}
	/**
	 * Factory method for making a {@link WeakDependencyBracket} that makes a single {@link Depender}
	 * depend on something extracted from the value while it is open
	 * @param <V>
	 * @param extract The Function that extracts the {@link Dependency} that should be added. 
	 * It is important that this function is deterministic, otherwise the {@link Dependency} cannot be removed again properly. 
	 * @param d0
	 * @return
	 */
	public static <V> WeakDependencyBracket<V> dependencyBracket_weak(
			Function<? super V, ? extends Dependency> extract,
			Depender d0
			){
		return new WeakDependencyBracket<V>(false, extract, d0, (Depender[])null);
	}
	/**
	 * Factory method for making a {@link WeakDependencyBracket} that makes multiple {@link Depender}s
	 * depend on the value while it is open
	 * @param <V>
	 * @param d0
	 * @return
	 */
	public static <V extends Dependency> WeakDependencyBracket<V> dependencyBracket_weak(
			Depender... ds
			){
		return new WeakDependencyBracket<V>(false, (Function<? super V, ? extends Dependency>) d->d, (Depender) null, ds);
	}
	/**
	 * Factory method for making a {@link WeakDependencyBracket} that makes multiple {@link Depender}s
	 * depend on on something extracted from the value while it is open
	 * @param extract The Function that extracts the {@link Dependency} that should be added. 
	 * It is important that this function is deterministic, otherwise the {@link Dependency} cannot be removed again properly. 
	 * @param <V>
	 * @param d0
	 * @return
	 */
	public static <V> WeakDependencyBracket<V> dependencyBracket_weak(
			Function<? super V, ? extends Dependency> extract,
			Depender... ds
			){
		return new WeakDependencyBracket<V>(false, extract, (Depender) null, ds);
	}


	/**
	 * Factory method for making a {@link DependerBracket} that makes the value depend on a single {@link Dependency}
	 * @param <V>
	 * @param d0
	 * @return
	 */
	public static <V extends Depender> DependerBracket<V> dependerBracket(
			Dependency d0
			){
		return new DependerBracket<V>(false, (Function<? super V, ? extends Depender>) d->d, true, d0, (Dependency[])null);
	}
	/**
	 * Factory method for making a {@link DependerBracket} that extracts something from the value and then makes that depend on a a single {@link Dependency}
	 * @param <V>
	 * @param extract The Function that extracts the {@link Depender} that should be made to depend on the {@link Dependency Dependencies}
	 * It is important that this function is deterministic, otherwise the {@link Dependency Dependencies} cannot be removed again properly.
	 * @param d0
	 * @return
	 */
	public static <V> DependerBracket<V> dependerBracket(
			Function<? super V, ? extends Depender> extract,
			Dependency d0
			){
		return new DependerBracket<V>(false, extract, true, d0, (Dependency[])null);
	}
	/**
	 * Factory method for making a {@link DependerBracket} that extracts something from the value and then makes that depend on a a single {@link Dependency}
	 * @param <V>
	 * @param extract The Function that extracts the {@link Depender} that should be made to depend on the {@link Dependency Dependencies}
	 * It is important that this function is deterministic, otherwise the {@link Dependency Dependencies} cannot be removed again properly.
	 * @param triggerChange Whether the {@link Dependency Dependencies} should be added and removed in a way that triggers recomputation of the {@link Depender}. 
	 * @param d0
	 * @return
	 */
	public static <V> DependerBracket<V> dependerBracket(
			Function<? super V, ? extends Depender> extract,
			boolean triggerChange, 
			Dependency d0
			){
		return new DependerBracket<V>(false, extract, triggerChange, d0, (Dependency[])null);
	}
	/**
	 * Factory method for making a {@link DependerBracket} that makes the value depend on a multiple {@link Dependency Dependencies}
	 * @param <V>
	 * @param d0
	 * @return
	 */
	public static <V extends Depender> DependerBracket<V> dependerBracket(
			Dependency... ds
			){
		return new DependerBracket<V>(false, (Function<? super V, ? extends Depender>) d->d, true, (Dependency) null, ds);
	}
	/**
	 * Factory method for making a {@link DependerBracket} that extracts something from the value and then makes that depend on multiple  {@link Dependency Dependencies}
	 * @param <V>
	 * @param extract The Function that extracts the {@link Depender} that should be made to depend on the {@link Dependency Dependencies}
	 * It is important that this function is deterministic, otherwise the {@link Dependency Dependencies} cannot be removed again properly.
	 * @param d0
	 * @return
	 */
	public static <V> DependerBracket<V> dependerBracket(
			Function<? super V, ? extends Depender> extract,
			Dependency... ds
			){
		return new DependerBracket<V>(false, extract, true, (Dependency) null, ds);
	}


	/**
	 * A bracket for {@link ReferenceCounted} values. 
	 * It increments the reference counter when opened and decrements it when closed.
	 */
	public static final ValueOnlyBracket<ReferenceCounted> REF_COUNT_BRACKET = make(true, v->{
		if(v!=null)
			v.increaseRefcount();
	}, v->{
		if(v!=null)
			v.decreaseRefcount(); 
		return false;
	});
	/**
	 * A bracket for collections of {@link ReferenceCounted} values. 
	 * It increments the reference counter for each element when opened and decrements it when closed.
	 * The collection must not be mutated, otherwise reference counters will not reach zero, or worse, will reach zero when they shouldn't
	 */
	public static final ValueOnlyBracket<Iterable<? extends ReferenceCounted>> COLLECTION_REF_COUNT_BRACKET = make(true, v->{
		if(v!=null)
			for(ReferenceCounted e : v)
				if(e!=null)
					e.increaseRefcount();
	}, v->{
		if(v!=null)
			for(ReferenceCounted e : v)
				if(e!=null)
					e.decreaseRefcount();
		return false;
	});
	/**
	 * A bracket that prevents the reference to the value from being <code>null</code>ed. You can use this for simple values that should be able to be restored by a recomputation
	 * (they can then be obtained using {@link PileImpl#getAsync()}).
	 * An alternative is to rely on the old value that may be available to the recomputation.
	 */
	ValueOnlyBracket<Object> KEEP = new ValueOnlyBracket<Object>() {
		@Override public boolean open(Object value, Object owner) {return true;}
		@Override public boolean close(Object value, Object owner) {return true;}
		@Override public boolean isInheritable() {return false;}
		@Override public boolean nopOpen() {return true;}
		@Override public boolean nopClose() {return false;}

	};

	/**
	 * A wrapper around another {@link ValueBracket} that executes its {@link #open(Object, Object)} and {@link #close(Object, Object)}
	 * actions on a {@link SequentialQueue}. The return values are determined synchronously by {@link BiPredicate}s passed to the constructor. 
	 * @author bb
	 *
	 * @param <E>
	 * @param <O>
	 */
	public static class QueuedValueBracket<E, O> implements ValueBracket<E, O>{
		/**
		 * Tests whether the first argument is non-null
		 */
		public static final BiPredicate<Object,Object> NON_NULL=(o, x)->o!=null;
		final ValueBracket<E, O> back;
		final SequentialQueue queue;
		final BiPredicate<? super E, ? super O> filter;
		final BiPredicate<? super E, ? super O> keep;
		final BiPredicate<? super E, ? super O> remain;
		final boolean backDoesOpen;
		final boolean backDoesClose;

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
			this.backDoesOpen = !back.nopOpen();
			this.backDoesClose = !back.nopClose();
		}
		@Override
		public boolean open(E value, O owner) {
			if(backDoesOpen && (filter==null || filter.test(value, owner))) {
				queue.enqueue(()->back.open(value, owner));
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
		@Override public boolean nopOpen() {return keep==null & !backDoesOpen;}
		@Override public boolean nopClose() {return remain==null & ! backDoesClose;}

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
	default QueuedValueBracket<E, O> queued(SequentialQueue q, BiPredicate<? super E, ? super O> filter, BiPredicate<? super E, ? super O> keep, BiPredicate<? super E, ? super O> remain){
		return new QueuedValueBracket<>(this, q, filter, keep, remain);
	}
	/**
	 * Construct a {@link QueuedValueBracket} backed by this {@link ValueBracket}
	 * @see QueuedValueBracket
	 * @param q
	 * @param filter
	 * @return
	 */
	default QueuedValueBracket<E, O> queued(SequentialQueue q, BiPredicate<? super E, ? super O> filter){
		return queued(q, filter, null, null);
	}
	/**
	 * Construct a {@link QueuedValueBracket} backed by this {@link ValueBracket} that filters out <code>null</code> values
	 * @see QueuedValueBracket
	 * @param q
	 * @return
	 */
	default QueuedValueBracket<E, O> queued(SequentialQueue q){
		return queued(q, QueuedValueBracket.NON_NULL, null, null);
	}
	/**
	 * Construct a {@link QueuedValueBracket} backed by this {@link ValueBracket} that uses a new {@link SequentialQueue}.
	 * Warning: Each SequentialQueue can have its own Thread, so you might want to reuse SequentialQueues for several {@link ValueBracket}s
	 * @see QueuedValueBracket
	 * @param name the name for the {@link SequentialQueue}
	 * @param filter
	 * @return
	 */
	default QueuedValueBracket<E, O> queued(String name, BiPredicate<? super E, ? super O> filter){
		return queued(new SequentialQueue(name), filter, null, null);
	}
	/**
	 * Construct a {@link QueuedValueBracket} backed by this {@link ValueBracket} that filters out <code>null</code> values that uses a new {@link SequentialQueue}.
	 * Warning: Each SequentialQueue can have its own Thread, so you might want to reuse SequentialQueues for several {@link ValueBracket}s
	 * @see QueuedValueBracket
	 * @param name the name for the {@link SequentialQueue}
	 * @return
	 */
	default QueuedValueBracket<E, O> queued(String name){
		return queued(new SequentialQueue(name), QueuedValueBracket.NON_NULL, null, null);
	}
	/**
	 * A bracket that {@link Pile#revalidate() revalidate}s the given {@link Pile} whenever it is opened or closed.
	 * @param <E>
	 * @param toRevalidate
	 * @return
	 */
	public static <E> ValueOnlyBracket<E> revalidateBracket(Pile<Object> toRevalidate) {
		Consumer<? super Object> oc = _o->toRevalidate.revalidate();
		return make(false, oc, oc);
	}


}
