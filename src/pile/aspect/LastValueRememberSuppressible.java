package pile.aspect;

import java.util.Collection;
import java.util.function.BiConsumer;
import java.util.function.Function;

import pile.aspect.suppress.SuppressionSwitcher;
import pile.aspect.suppress.Suppressor;
import pile.aspect.suppress.Suppressor.SuppressMany;

/**
 * Common superinterface for suppressing the behavior that values are remembered 
 * in persistent storage.
 * 
 * <p>
 * The main reason for this interface's existence is that several such values can be put into an array
 * or a {@link Collection} and passed to a {@link SuppressMany}-instance together with this method handle that
 * tells the {@link SuppressMany}'s method how to derive a {@link Suppressor} from this object, and ditto
 * for methods of {@link SuppressionSwitcher}
 * <p>
 * Consider implementing one of the subinterfaces of {@link LastValueRememberSuppressible}, namely
 * {@link Multi} or {@link Single}.
 * 
 * @see Suppressor
 * @see Suppressor#many(Function, Collection)
 * @see Suppressor#many(Function, Object...)
 * @author bb
 *
 */
public interface LastValueRememberSuppressible {

	/**
	 * Method handle for simply generating a {@link Suppressor}
	 */
	public static final Function<LastValueRememberSuppressible, Suppressor> 
	SUPPRESS_LAST_VALUE_REMEMBERING=LastValueRememberSuppressible::suppressRememberLastValues;
	/**
	 * Method handle for adding a newly generated {@link Suppressor} to a given {@link SuppressMany} instance.
	 */
	public static final BiConsumer<LastValueRememberSuppressible, SuppressMany> 
	SUPPRESS_LAST_VALUE_REMEMBERING_ADD=LastValueRememberSuppressible::suppressRememberLastValues;
	/**
	 * Method handle for adding a several {@link Suppressor}s newly generated 
	 * from a collection of {@link AutoValidationSuppressible}s
	 * to a given {@link SuppressMany}
	 */
	public static final BiConsumer<Iterable<? extends LastValueRememberSuppressible>, SuppressMany> 
	SUPPRESS_LAST_VALUE_REMEMBERING_COLLECTION=Suppressor.lift(SUPPRESS_LAST_VALUE_REMEMBERING_ADD);


	/**
	 * This interface provides a default implementation for {@link #suppressRememberLastValues()} which relies on {@link #suppressRememberLastValues(SuppressMany)}.
	 * Implement it if your implementation produces multiple {@link Suppressor}s.
	 * @author bb
	 *
	 */
	interface Multi extends LastValueRememberSuppressible{
		@Override
		default Suppressor suppressRememberLastValues() {
			SuppressMany ret = new SuppressMany();
			boolean fail = true;
			try {
				suppressRememberLastValues(ret);
				fail = false;
				return ret;
			}finally {
				if(fail)
					ret.release();
			}
		}
	}
	/**
	 * This interface provides a default implementation for {@link #suppressRememberLastValues(SuppressMany)} which relies on {@link #suppressRememberLastValues()}.
	 * Implement it if your implementation produces just a single {@link Suppressor}, or 
	 * if you want the {@link Suppressor}s organized hierarchically.
	 * @author bb
	 *
	 */
	interface Single extends LastValueRememberSuppressible{
		@Override
		default SuppressMany suppressRememberLastValues(SuppressMany s){
			s.makePlaceFor1().add(this.suppressRememberLastValues());
			return s;
		}
	}
	/**
	 * This interface provides a do-nothing implementation.
	 * Implement it if a superclass requires that you implement {@link LastValueRememberSuppressible},
	 * but there is nothing to suppress.
	 * @author bb
	 *
	 */
	interface None extends LastValueRememberSuppressible.Multi{
		@Override
		default SuppressMany suppressRememberLastValues(SuppressMany s) {
			return s;
		}
		@Override
		default Suppressor suppressRememberLastValues() {
			return Suppressor.NOP;
		}
	}

	/**
	 * Make a {@link Suppressor} which suppresses last value remembering behavior of this object 
	 * until it is released.
	 * @return
	 */
	Suppressor suppressRememberLastValues();
	/**
	 * {@link SuppressMany#add(Suppressor) Add}s the Suppressor(s) needed to suppress the 
	 * last value remembering behavior of this object to the given {@link SuppressMany} instance.
	 * 
	 * @param s Must not be <code>null</code>. Do not try to allocate a new suppressor when null is passed here,
	 * because if an exception occurs and the code is not written in a non-straightforward way,
	 * the {@link Suppressor} will be leaked. It's not worth the effort. 
	 * Just require the caller to pass something non-null.
	 * 
	 * @return {@code s}
	 */
	SuppressMany suppressRememberLastValues(SuppressMany s);
}
