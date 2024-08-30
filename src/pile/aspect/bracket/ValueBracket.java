package pile.aspect.bracket;

import java.util.function.BiConsumer;
import java.util.function.BiPredicate;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

import pile.aspect.Dependency;
import pile.aspect.Depender;
import pile.aspect.ReferenceCounted;
import pile.aspect.combinations.Pile;
import pile.aspect.listen.ListenValue;
import pile.aspect.listen.ValueListener;
import pile.impl.PileImpl;
import pile.interop.debug.DebugEnabled;
import pile.utils.Functional;
import pile.utils.Nonreentrant;
import pile.utils.SequentialQueue;
import pile.utils.defer.Deferrer;

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
	public boolean openIsNop();
	/**
	 * @return <code>true</code> is closing the bracket is a guaranteed no-op.
	 */
	public boolean closeIsNop();

	/**
	 * 
	 * @return whether open can ever return false.
	 */
	public boolean canBecomeObsolete();


	/**
	 * Make a {@link ValueBracket} that only does something when it is opened
	 * @param <E>
	 * @param <O>
	 * @param inheritable
	 * @param open
	 * @return
	 */
	public static <E, O> ValueBracket<E, O> openOnly(boolean inheritable, BiConsumer<? super E, ? super O> open) {
		ValueBracket<E, O> ret = new ValueBracket<E, O>() {
			@Override public boolean close(E value, O owner) {return false;}
			@Override public boolean open(E value, O owner) {open.accept(value, owner); return true;}
			@Override public boolean isInheritable() {return inheritable;	}
			@Override public boolean openIsNop() {return false;}
			@Override public boolean closeIsNop() {return true;}
			@Override public boolean canBecomeObsolete() {return false;}
		};
		if(DebugEnabled.DETECT_STUCK_BRACKETS)
			ret=ret.detectStuck();
		return ret;
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
		ValueBracket<E, O> ret = new ValueBracket<E, O>() {
			@Override public boolean close(E value, O owner) {return close.test(value, owner);}
			@Override public boolean open(E value, O owner) {return true;}
			@Override public boolean isInheritable() {return inheritable;	}
			@Override public boolean openIsNop() {return true;}
			@Override public boolean closeIsNop() {return false;}
			@Override public boolean canBecomeObsolete() {return false;}
		};
		if(DebugEnabled.DETECT_STUCK_BRACKETS)
			ret=ret.detectStuck();
		return ret;
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
		ValueBracket<E, O> ret = new ValueBracket<E, O>() {
			@Override public boolean close(E value, O owner) {return close.test(value, owner);}
			@Override public boolean open(E value, O owner) {open.accept(value, owner); return true;}
			@Override public boolean isInheritable() {return inheritable;	}
			@Override public boolean openIsNop() {return false;}
			@Override public boolean closeIsNop() {return false;}
			@Override public boolean canBecomeObsolete() {return false;}
		};
		if(DebugEnabled.DETECT_STUCK_BRACKETS)
			ret=ret.detectStuck();
		return ret;
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
		ValueOnlyBracket<E> ret = new ValueOnlyBracket<E>() {
			@Override public boolean close(E value, Object owner) {return false;}
			@Override public boolean open(E value, Object owner) {open.accept(value); return true;}
			@Override public boolean isInheritable() {return inheritable;	}
			@Override public boolean openIsNop() {return false;}
			@Override public boolean closeIsNop() {return true;}
			@Override public boolean canBecomeObsolete() {return false;}
		};
		if(DebugEnabled.DETECT_STUCK_BRACKETS)
			ret=ret.detectStuck();
		return ret;
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
		ValueOnlyBracket<E> ret = new ValueOnlyBracket<E>() {
			@Override public boolean close(E value, Object owner) {return close.test(value);}
			@Override public boolean open(E value, Object owner) {return true;}
			@Override public boolean isInheritable() {return inheritable;	}
			@Override public boolean openIsNop() {return true;}
			@Override public boolean closeIsNop() {return false;}
			@Override public boolean canBecomeObsolete() {return false;}
		};
		if(DebugEnabled.DETECT_STUCK_BRACKETS)
			ret=ret.detectStuck();
		return ret;
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
		ValueOnlyBracket<E> ret = new ValueOnlyBracket<E>() {
			@Override public boolean close(E value, Object owner) {close.accept(value); return false;}
			@Override public boolean open(E value, Object owner) {return true;}
			@Override public boolean isInheritable() {return inheritable;	}
			@Override public boolean openIsNop() {return true;}
			@Override public boolean closeIsNop() {return false;}
			@Override public boolean canBecomeObsolete() {return false;}
		};
		if(DebugEnabled.DETECT_STUCK_BRACKETS)
			ret=ret.detectStuck();
		return ret;
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
		ValueOnlyBracket<E> ret = new ValueOnlyBracket<E>() {
			@Override public boolean close(E value, Object owner) {return close.test(value);}
			@Override public boolean open(E value, Object owner) {open.accept(value); return true;}
			@Override public boolean isInheritable() {return inheritable;	}
			@Override public boolean openIsNop() {return false;}
			@Override public boolean closeIsNop() {return false;}
			@Override public boolean canBecomeObsolete() {return false;}
		};
		if(DebugEnabled.DETECT_STUCK_BRACKETS)
			ret=ret.detectStuck();
		return ret;
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
		ValueOnlyBracket<E> ret = new ValueOnlyBracket<E>() {
			@Override public boolean close(E value, Object owner) {close.accept(value); return false;}
			@Override public boolean open(E value, Object owner) {open.accept(value); return true;}
			@Override public boolean isInheritable() {return inheritable;	}
			@Override public boolean openIsNop() {return false;}
			@Override public boolean closeIsNop() {return false;}
			@Override public boolean canBecomeObsolete() {return false;}
		};
		if(DebugEnabled.DETECT_STUCK_BRACKETS)
			ret=ret.detectStuck();
		return ret;
	}
	/**
	 * Make a bracket that adds and removes a {@link ValueListener} to/from the owner.
	 * When the {@link ValueListener} is added, it is called with a <code>null</code>
	 * {@link ValueListener} argument}.
	 * If your {@link ValueListener} does anything slightly more complex, you
	 * should probably defer the execution to a {@link SequentialQueue} by calling one of the
	 * {@link #queued()}-methods
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
	 * If your {@link ValueListener} does anything slightly more complex, you
	 * should probably defer the execution to a {@link SequentialQueue} by calling one of the
	 * {@link #queued()}-methods
	 * TODO: Is there really a use case for that?
	 * @param l A {@link ValueListener} that should be registered as an eager listener
	 * on the owner
	 * as long as the bracket is opened.
	 * @param inheritable Whether the bracket should be inheritable
	 * @return A bracket
	 */
	public static ValueBracket<Object, ListenValue> ownerValueListenerBracket(boolean inheritable, ValueListener l){
		ValueBracket<Object, ListenValue> ret = new ValueBracket<Object, ListenValue>() {
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
			@Override public boolean openIsNop() {return false;}
			@Override public boolean closeIsNop() {return false;}
			@Override public boolean canBecomeObsolete() {return false;}

		};
		if(DebugEnabled.DETECT_STUCK_BRACKETS)
			ret=ret.detectStuck();
		return ret;
	}
	/**
	 * Make a bracket that keeps a {@link ValueListener} added to a {@link ListenValue}
	 * extracted from the value while the bracket is open.
	 * When the {@link ValueListener} is added, it is called with a <code>null</code>
	 * {@link ValueListener} argument}
	 * If your {@link ValueListener} does anything slightly more complex, you
	 * should probably defer the execution to a {@link SequentialQueue} by calling one of the
	 * {@link #queued()}-methods
	 * @param extract A function to extract a {@link ListenValue} from the value on which the bracket
	 * is opened or closed. It is important that his {@link Function} is deterministic, because
	 * otherwise the {@link ValueListener} cannot be removed again properly.
	 * @param l A {@link ValueListener} that should be registered as on the result of {@code extract}
	 * as long as the bracket is opened.
	 * @param inheritable Whether the bracket should be inheritable
	 * @return A bracket
	 */
	public static <E> ValueOnlyBracket<E> valueListenerBracket(boolean inheritable, Function<? super E, ? extends ListenValue> extract, ValueListener l){
		ValueOnlyBracket<E> ret = new ValueOnlyBracket<E>() {
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
			@Override public boolean openIsNop() {return false;}
			@Override public boolean closeIsNop() {return false;}
			@Override public boolean canBecomeObsolete() {return false;}

		};
		if(DebugEnabled.DETECT_STUCK_BRACKETS)
			ret=ret.detectStuck();
		return ret;
	}


	/**
	 * Factory method for making a {@link StrongDependencyBracket} that makes a single {@link Depender}
	 * depend on the value while it is open
	 * @param <V>
	 * @param d0
	 * @return
	 */
	public static <V extends Dependency> ValueOnlyBracket<V> dependencyBracket(
			Depender d0
			){
		return StrongDependencyBracket.create(false, (Function<? super V, ? extends Dependency>) d->d, true, d0, (Depender[])null);
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
	public static <V> ValueOnlyBracket<V> dependencyBracket(
			Function<? super V, ? extends Dependency> extract,
			Depender d0
			){
		return StrongDependencyBracket.create(false, extract, true, d0, (Depender[])null);
	}
	/**
	 * Factory method for making a {@link StrongDependencyBracket} that makes multiple {@link Depender}s
	 * depend on the value while it is open
	 * @param <V>
	 * @param d0
	 * @return
	 */
	public static <V extends Dependency> ValueOnlyBracket<V> dependencyBracket(
			Depender... ds
			){
		return StrongDependencyBracket.create(false, (Function<? super V, ? extends Dependency>) d->d, true, (Depender) null, ds);
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
	public static <V> ValueOnlyBracket<V> dependencyBracket(
			Function<? super V, ? extends Dependency> extract,
			Depender... ds
			){
		return StrongDependencyBracket.create(false, extract, true, (Depender) null, ds);
	}


	/**
	 * Factory method for making a {@link StrongDependencyBracket} that makes a single {@link Depender}
	 * depend on the value while it is open
	 * @param <V>
	 * @param d0
	 * @return
	 */
	public static <V extends Dependency> ValueOnlyBracket<V> dependencyBracket( boolean recompute,
			Depender d0
			){
		return StrongDependencyBracket.create(false, (Function<? super V, ? extends Dependency>) d->d, recompute, d0, (Depender[])null);
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
	public static <V> ValueOnlyBracket<V> dependencyBracket(
			Function<? super V, ? extends Dependency> extract, boolean recompute,
			Depender d0
			){
		return StrongDependencyBracket.create(false, extract, recompute, d0, (Depender[])null);
	}
	/**
	 * Factory method for making a {@link StrongDependencyBracket} that makes multiple {@link Depender}s
	 * depend on the value while it is open
	 * @param <V>
	 * @param d0
	 * @return
	 */
	public static <V extends Dependency> ValueOnlyBracket<V> dependencyBracket( boolean recompute,
			Depender... ds
			){
		return StrongDependencyBracket.create(false, (Function<? super V, ? extends Dependency>) d->d, recompute, (Depender) null, ds);
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
	public static <V> ValueOnlyBracket<V> dependencyBracket(
			Function<? super V, ? extends Dependency> extract, boolean recompute,
			Depender... ds
			){
		return StrongDependencyBracket.create(false, extract, recompute, (Depender) null, ds);
	}


	/**
	 * Factory method for making a {@link WeakDependencyBracket} that makes a single {@link Depender}
	 * depend on the value while it is open
	 * @param <V>
	 * @param d0
	 * @return
	 */
	public static <V extends Dependency> ValueOnlyBracket<V> dependencyBracket_weak(
			Depender d0
			){
		return WeakDependencyBracket.create(false, (Function<? super V, ? extends Dependency>) d->d, d0, (Depender[])null);
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
	public static <V> ValueOnlyBracket<V> dependencyBracket_weak(
			Function<? super V, ? extends Dependency> extract,
			Depender d0
			){
		return WeakDependencyBracket.create(false, extract, d0, (Depender[])null);
	}
	/**
	 * Factory method for making a {@link WeakDependencyBracket} that makes multiple {@link Depender}s
	 * depend on the value while it is open
	 * @param <V>
	 * @param d0
	 * @return
	 */
	public static <V extends Dependency> ValueOnlyBracket<V> dependencyBracket_weak(
			Depender... ds
			){
		return WeakDependencyBracket.create(false, (Function<? super V, ? extends Dependency>) d->d, (Depender) null, ds);
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
	public static <V> ValueOnlyBracket<V> dependencyBracket_weak(
			Function<? super V, ? extends Dependency> extract,
			Depender... ds
			){
		return WeakDependencyBracket.create(false, extract, (Depender) null, ds);
	}


	/**
	 * Factory method for making a {@link DependerBracket} that makes the value depend on a single {@link Dependency}
	 * @param <V>
	 * @param d0
	 * @return
	 */
	public static <V extends Depender> ValueOnlyBracket<V> dependerBracket(
			Dependency d0
			){
		ValueOnlyBracket<V> ret = new DependerBracket<V>(false, (Function<? super V, ? extends Depender>) d->d, true, d0, (Dependency[])null);
		if(DebugEnabled.DETECT_STUCK_BRACKETS)
			ret=ret.detectStuck();
		return ret;
	}
	/**
	 * Factory method for making a {@link DependerBracket} that extracts something from the value and then makes that depend on a a single {@link Dependency}
	 * @param <V>
	 * @param extract The Function that extracts the {@link Depender} that should be made to depend on the {@link Dependency Dependencies}
	 * It is important that this function is deterministic, otherwise the {@link Dependency Dependencies} cannot be removed again properly.
	 * @param d0
	 * @return
	 */
	public static <V> ValueOnlyBracket<V> dependerBracket(
			Function<? super V, ? extends Depender> extract,
			Dependency d0
			){
		ValueOnlyBracket<V> ret = new DependerBracket<V>(false, extract, true, d0, (Dependency[])null);
		if(DebugEnabled.DETECT_STUCK_BRACKETS)
			ret=ret.detectStuck();
		return ret;
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
	public static <V> ValueOnlyBracket<V> dependerBracket(
			Function<? super V, ? extends Depender> extract,
			boolean triggerChange, 
			Dependency d0
			){
		ValueOnlyBracket<V> ret = new DependerBracket<V>(false, extract, triggerChange, d0, (Dependency[])null);
		if(DebugEnabled.DETECT_STUCK_BRACKETS)
			ret=ret.detectStuck();
		return ret;
	}
	/**
	 * Factory method for making a {@link DependerBracket} that makes the value depend on a multiple {@link Dependency Dependencies}
	 * @param <V>
	 * @param d0
	 * @return
	 */
	public static <V extends Depender> ValueOnlyBracket<V> dependerBracket(
			Dependency... ds
			){
		ValueOnlyBracket<V> ret = new DependerBracket<V>(false, (Function<? super V, ? extends Depender>) d->d, true, (Dependency) null, ds);
		if(DebugEnabled.DETECT_STUCK_BRACKETS)
			ret=ret.detectStuck();
		return ret;
	}
	/**
	 * Factory method for making a {@link DependerBracket} that extracts something from the value and then makes that depend on multiple  {@link Dependency Dependencies}
	 * @param <V>
	 * @param extract The Function that extracts the {@link Depender} that should be made to depend on the {@link Dependency Dependencies}
	 * It is important that this function is deterministic, otherwise the {@link Dependency Dependencies} cannot be removed again properly.
	 * @param d0
	 * @return
	 */
	public static <V> ValueOnlyBracket<V> dependerBracket(
			Function<? super V, ? extends Depender> extract,
			Dependency... ds
			){
		ValueOnlyBracket<V> ret = new DependerBracket<V>(false, extract, true, (Dependency) null, ds);
		if(DebugEnabled.DETECT_STUCK_BRACKETS)
			ret=ret.detectStuck();
		return ret;
	}


	/**
	 * A bracket for {@link ReferenceCounted} values. 
	 * It increments the reference counter when opened and decrements it when closed.
	 */
	public static final ValueOnlyBracket<ReferenceCounted> REF_COUNT_BRACKET = make(true, ReferenceCounted::increaseRefcount, ReferenceCounted::decreaseRefcount).nopOnNull();
	/**
	 * A bracket for collections of {@link ReferenceCounted} values. 
	 * It increments the reference counter for each element when opened and decrements it when closed.
	 * The collection must not be mutated, otherwise reference counters will not reach zero, or worse, will reach zero when they shouldn't
	 */
	public static final ValueOnlyBracket<Iterable<? extends ReferenceCounted>> COLLECTION_REF_COUNT_BRACKET = make(true, 
			(Iterable<? extends ReferenceCounted> v)->{
				for(ReferenceCounted e : v)
					if(e!=null)
						e.increaseRefcount();
			}, v->{
				for(ReferenceCounted e : v)
					if(e!=null)
						e.decreaseRefcount();
				return false;
			}).nopOnNull();

	/**
	 * A bracket for {@link ReferenceCounted} values. 
	 * It increments the reference counter when opened and decrements it when closed.
	 */
	public static ValueOnlyBracket<ReferenceCounted> queuedRefCountBracket(SequentialQueue queue) {
		return closeOnly(true, ReferenceCounted::decreaseRefcount)
				.queued(queue)
				.beforeOpening(ReferenceCounted::increaseRefcount)
				.nopOnNull();
	}
	/**
	 * A bracket for collections of {@link ReferenceCounted} values. 
	 * It increments the reference counter for each element when opened and decrements it when closed.
	 * The collection must not be mutated, otherwise reference counters will not reach zero, or worse, will reach zero when they shouldn't
	 */
	public static ValueOnlyBracket<Iterable<? extends ReferenceCounted>> queuedCollectionRefCountBracket(SequentialQueue queue){
		return closeOnly(true, (Iterable<? extends ReferenceCounted> v)->{
			for(ReferenceCounted e : v)
				if(e!=null)
					e.decreaseRefcount();
			return false;
		})
				.queued(queue)
				.beforeOpening((Iterable<? extends ReferenceCounted> v)->{
					for(ReferenceCounted e : v)
						if(e!=null)
							e.increaseRefcount();
				})
				.nopOnNull();
	}

	/**
	 * A bracket that prevents the reference to the value from being <code>null</code>ed. You can use this for simple values that should be able to be restored by a recomputation
	 * (they can then be obtained using {@link PileImpl#getAsync()}).
	 * An alternative is to rely on the old value that may be available to the recomputation.
	 */
	ValueOnlyBracket<Object> KEEP = new ValueOnlyBracket<Object>() {
		@Override public boolean open(Object value, Object owner) {return true;}
		@Override public boolean close(Object value, Object owner) {return true;}
		@Override public boolean isInheritable() {return false;}
		@Override public boolean openIsNop() {return true;}
		@Override public boolean closeIsNop() {return false;}
		@Override public boolean canBecomeObsolete() {return false;}

	};

	/**
	 * Construct a {@link QueuedValueBracket} backed by this {@link ValueBracket}
	 * @see QueuedValueBracket
	 * @param q
	 * @param filter
	 * @param keep
	 * @param remain
	 * @return
	 */
	default ValueBracket<E, O> queued(SequentialQueue q, BiPredicate<? super E, ? super O> filter, BiPredicate<? super E, ? super O> keep, BiPredicate<? super E, ? super O> remain){
		ValueBracket<E, O> ret = new QueuedValueBracket<>(this, q, filter, keep, remain);
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
	default ValueBracket<E, O> queued(SequentialQueue q, BiPredicate<? super E, ? super O> filter){
		return queued(q, filter, null, null);
	}
	/**
	 * Construct a {@link QueuedValueBracket} backed by this {@link ValueBracket} that filters out <code>null</code> values
	 * @see QueuedValueBracket
	 * @param q
	 * @return
	 */
	default ValueBracket<E, O> queued(SequentialQueue q){
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
	default ValueBracket<E, O> queued(String name, BiPredicate<? super E, ? super O> filter){
		return queued(new SequentialQueue(name), filter, null, null);
	}
	/**
	 * Construct a {@link QueuedValueBracket} backed by this {@link ValueBracket} that filters out <code>null</code> values that uses a new {@link SequentialQueue}.
	 * Warning: Each SequentialQueue can have its own Thread, so you might want to reuse SequentialQueues for several {@link ValueBracket}s
	 * @see QueuedValueBracket
	 * @param name the name for the {@link SequentialQueue}
	 * @return
	 */
	default ValueBracket<E, O> queued(String name){
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
	default ValueBracket<E, O> queued(BiPredicate<? super E, ? super O> filter){
		return queued(QueuedValueBracket.getDefaultQueue(), filter, null, null);
	}
	/**
	 * Construct a {@link QueuedValueBracket} backed by this {@link ValueBracket} that filters out <code>null</code> values that uses a new {@link SequentialQueue}.
	 * Warning: Each SequentialQueue can have its own Thread, so you might want to reuse SequentialQueues for several {@link ValueBracket}s
	 * @see QueuedValueBracket
	 * @param name the name for the {@link SequentialQueue}
	 * @return
	 */
	default ValueBracket<E, O> queued(){
		return queued(QueuedValueBracket.getDefaultQueue(), null, null, null);
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

	public default ValueBracket<E, O> detectStuck(){
		return new DeadlockDetectingBracket<>(this, -3000, null, null);
	}
	public default ValueBracket<E, O> dontDetectStuck(){
		return this;
	}

	public default ValueBracket<E, O> nonreentrant(Nonreentrant nr){
		return new NonreentrantBracket<E, O>(nr, this);
	}
	public default ValueBracket<E, O> filtered(Predicate<? super E> openFilter, Predicate<? super E> closeFilter){
		return new FilteredBracket<E, O>(this, openFilter, closeFilter);
	}
	public default ValueBracket<E, O> filtered(Predicate<? super E> filter){
		return filtered(filter, filter);
	}
	public default ValueBracket<E, O> nopOnNull(){
		return filtered(Functional.IS_NOT_NULL, Functional.IS_NOT_NULL);
	}
	public default ValueBracket<E, O> nopOnNullOpen(){
		return filtered(Functional.IS_NOT_NULL, null);
	}
	public default ValueBracket<E, O> nopOnNullClose(){
		return filtered(null, Functional.IS_NOT_NULL);
	}
	public default ValueBracket<E, O> filtersFirst(){
		return this;
	}
	public default ValueBracket<E, O> beforeOpening(BiConsumer<? super E, ? super O> preOpen){
		return new AugmentedBracket<>(preOpen, null, this);
	}
	public default ValueBracket<E, O> beforeClosing(BiConsumer<? super E, ? super O> postClose){
		return new AugmentedBracket<>(null, postClose, this);
	}
	public default ValueBracket<E, O> beforeOpening(Consumer<? super E> preOpen){
		return new AugmentedBracket<>((v, o)->preOpen.accept(v), null, this);
	}
	public default ValueBracket<E, O> beforeClosing(Consumer<? super E> postClose){
		return new AugmentedBracket<>(null, (v, o)->postClose.accept(v), this);
	}
	public default ValueBracket<E, O> defer(Deferrer d){
        return new DeferredValueBracket<>(this, d, null, null, null);
    }
}
