package pile.utils;

import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.BiPredicate;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.IntConsumer;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * Functional pogramming utilities
 * @author bb
 *
 */
public class Functional {
	/**
	 * A {@link Supplier} that always returns <code>null</code> references
	 * and also the constant <code>null</code> {@link Function}
	 */
	
	public static final class NullSupplier<T> implements
	Supplier<T>,
	Function<Object, T>	{
		@Override public T apply(Object p) {return null;}
		@Override public T get() {return null;}
	}
	/**
	 * A {@link Supplier} that always returns <code>null</code> references
	 */
	public static final NullSupplier<?> NULL_SUPPLIER=new NullSupplier<>();
	/**
	 * Get the {@link #NULL_SUPPLIER} instance in a typesafe way
	 * @param <E>
	 * @return
	 */
	@SuppressWarnings("unchecked")
	public static  <E> NullSupplier<E> nullSupplier(){return (NullSupplier<E>) NULL_SUPPLIER;}
	
	/**
	 * A {@link Predicate} that tests if something is <code>null</code>
	 */
	public static final Predicate<Object> IS_NULL = o->o==null;
	/**
	 * A {@link Predicate} that tests if something is not <code>null</code>
	 */
	public static final Predicate<Object> IS_NOT_NULL = o->o!=null;
	/**
	 * A {@link Predicate} that always returns <code>true</code>
	 */
	public static final ConstBool CONST_TRUE = new ConstBool(true);
	/**
	 * A {@link Predicate} that always returns <code>false</code>
	 */
	public static final ConstBool CONST_FALSE = new ConstBool(false);
	
	/**
	 * A predicate on non-null booleans that just unboxes the {@link Boolean}
	 */
	public static final Predicate<Boolean> ID_PREDICATE = v->v;

	/**
	 * {@link #NOP} is a {@link Runnable}, {@link Consumer} and {@link BiConsumer} that does nothing.
	 */
	public static final Nop NOP = new Nop();
	
	/**
	 * The identity {@link Function}
	 */
	public static final Function<Object, Object> ID = o->o;
	
	/**
	 * @param <V>
	 * @return The Identity {@link Function} on {@code V}
	 */
	@SuppressWarnings("unchecked")
	public static <V> Function<V, V> id(){return (Function<V, V>) ID;}


	/**
	 * Make a {@link Predicate} that always returns the opposite of the given {@link Predicate}.
	 * This method is implemented so that {@code not(not(p)) == p}.
	 * @param <A>
	 * @param p
	 * @throws NullPointerException iff {@code  p == null}
	 * @return
	 */
	public static <A> Predicate<A> not(Predicate<A> p) {
		Objects.requireNonNull(p);
		class InversePredicate implements Predicate<A>{
			final Predicate<A> back;
			public InversePredicate(Predicate<A> b) {
				back=b;
			}
			@Override
			public boolean test(A t) {
				return !back.test(t);
			}			
		}
		if(p instanceof InversePredicate)
			return ((InversePredicate) p).back;
		return new InversePredicate(p);
	}
	/**
	 * Make a {@link BooleanSupplier} that always returns the opposite of the given {@link BooleanSupplier}.
	 * This method is implemented so that {@code not(not(p)) == p}.
	 * @param <A>
	 * @param p
	 * @throws NullPointerException iff {@code  p == null}
	 * @return
	 */
	public static <A> BooleanSupplier not(BooleanSupplier p) {
		Objects.requireNonNull(p);
		class InverseBooleanSupplier implements BooleanSupplier{
			final BooleanSupplier back;
			public InverseBooleanSupplier(BooleanSupplier b) {
				back=b;
			}
			@Override
			public boolean getAsBoolean() {
				return !back.getAsBoolean();
			}			
		}
		if(p instanceof InverseBooleanSupplier)
			return ((InverseBooleanSupplier) p).back;
		return new InverseBooleanSupplier(p);
	}
	/**
	 * Return a predicate that always yields the given boolean value.
	 * @param b
	 * @return Actually a {@link ConstBool}, so it can be used as more than just a {@link Predicate}
	 */
	public static ConstBool constPredicate(boolean b) {
		return b?CONST_TRUE:CONST_FALSE;
	}
	/**
	 * A do-nothing {@link Runnable}/{@link Consumer}/{@link BiConsumer}/{@link IntConsumer}
	 * @author bb
	 *
	 */
	public static final class Nop implements 
	Runnable, 
	Consumer<Object>, 
	BiConsumer<Object, Object>,
	IntConsumer
	{
		private Nop() {}
		@Override public void accept(int t) {}
		@Override public void accept(Object t) {}
		@Override public void run() {}
		@Override public void accept(Object t, Object u) {}
	}
	
	/**
	 * A {@link Predicate} / {@link Supplier} / {@link Function} / {@link BooleanSupplier} / {@link BiPredicate}
	 * that always returns the same boolean value.
	 * @author bb
	 *
	 */
	public static final class ConstBool implements
	Predicate<Object>, Supplier<Boolean>, Function<Object, Boolean>,
	BooleanSupplier, BiPredicate<Object, Object>{
		public final boolean value;
		ConstBool(boolean value) {
			this.value=value;
		}
		@Override
		public Boolean apply(Object t) {
			return value;
		}

		@Override
		public Boolean get() {
			return value;
		}

		@Override
		public boolean test(Object t) {
			return value;
		}
		@Override
		public int hashCode() {
			return value?4242342:579475943;
		}
		@Override
		public boolean equals(Object o) {
			if (o == this)
				return true;
			if (o == null)
				return false;
			if (o instanceof Functional.ConstBool) {
				Functional.ConstBool a = (Functional.ConstBool) o;
				return a.value == value;
			}
			return false;
		}
		@Override
		public boolean getAsBoolean() {
			return value;
		}
		@Override
		public boolean test(Object t, Object u) {
			return value;
		}
		@Override
		public ConstBool negate() {
			return value?CONST_FALSE:CONST_TRUE;
		}
			
	}

	/**
	 * Make a {@link Consumer} that forwards its argument to the given {@code consumer}
	 * only if it is not <code>null</code>
	 * @param <T>
	 * @param consumer
	 * @return
	 */
	public static <T> Consumer<T> ifNotNull(Consumer<? super T> consumer) {
		return x->{
			if(x!=null)
				consumer.accept(x);
		};
	}
	/**
	 * Make a {@link Function} that returns its argument unless it is <code>null</code>, 
	 * in which case the given {@code defaultValue} is returned.
	 * @param <T>
	 * @param defaultValue
	 * @return
	 */
	public static <T>  Function<T, T> ifNullConst(T defaultValue) {
		return v->v==null?defaultValue:v;
	}
	/**
	 * Make a {@link Function} that returns its argument unless it is <code>null</code>, 
	 * in which case a value obtained from the given {@code defaultSupplier} is returned.
	 * @param <T>
	 * @param defaultSupplier
	 * @return
	 */
	public static <T>  Function<T, T> ifNull(Supplier<? extends T> defaultSupplier) {
		return v->v==null?defaultSupplier.get():v;
	}
	/**
	 * Make a {@link Predicate} that computes the logical conjunction of the given
	 * {@code predicates}. 
	 * Optimizations may be applied that prevent some or all of the {@code predicates} 
	 * from being evaluated.
	 * @param <T>
	 * @param predicates
	 * @return
	 */
	@SafeVarargs
	public static <T> Predicate<? super T> conjunction(Predicate<? super T>... predicates) {
		if(predicates==null || predicates.length==0)
			return CONST_TRUE;
		if(predicates.length==1) {
			Predicate<? super T> ret = predicates[0];
			Objects.requireNonNull(ret);
			return ret;
		}

		class ConjunctionPredicate<O> implements Predicate<O>{
			final Predicate<? super O>[] ps;
			@SafeVarargs
			public ConjunctionPredicate(Predicate<? super O>... predicates) {
				ps = predicates.clone();
				for(Predicate<? super O> p: ps)
					Objects.requireNonNull(p);
			}
			@Override
			public boolean test(O t) {
				for(Predicate<? super O> p: ps)
					if(!p.test(t))
						return false;
				return true;
			}
			
		}
		
		return new ConjunctionPredicate<>(predicates);
	}
	/**
	 * Make a {@link Predicate} that computes the logical disjunction of the given
	 * {@code predicates}.  
	 * Optimizations may be applied that prevent some or all of the {@code predicates}
	 * from being evaluated.
	 * @param <T>
	 * @param predicates
	 * @return
	 */
	@SafeVarargs
	public static <T> Predicate<? super T> disjunction(Predicate<? super T>... predicates) {
		if(predicates==null || predicates.length==0)
			return CONST_FALSE;
		if(predicates.length==1) {
			Predicate<? super T> ret = predicates[0];
			Objects.requireNonNull(ret);
			return ret;
		}
		class DisjunctionPredicate<O> implements Predicate<O>{
			final Predicate<? super O>[] ps;
			@SafeVarargs
			public DisjunctionPredicate(Predicate<? super O>... predicates) {
				ps = predicates.clone();
				for(Predicate<? super O> p: ps)
					Objects.requireNonNull(p);
			}
			@Override
			public boolean test(O t) {
				for(Predicate<? super O> p: ps)
					if(p.test(t))
						return true;
				return false;
			}
			
		}
		
		return new DisjunctionPredicate<>(predicates);
	}
}
