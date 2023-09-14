package pile.aspect;

import java.util.Collection;
import java.util.function.BiConsumer;
import java.util.function.Function;

import pile.aspect.suppress.ReactiveSuppressionSwitcher;
import pile.aspect.suppress.SuppressionSwitcher;
import pile.aspect.suppress.Suppressor;
import pile.aspect.suppress.Suppressor.SuppressMany;
import pile.impl.PileImpl;

/**
 * Common superinterface for all single {@link PileImpl}s or groups of {@link PileImpl}s and such,
 * that have some kind if autovalidating behavior which can be temporarily suppressed.
 * <p>
 * The main reason for this interface's existence is that several such values can be put into an array
 * or a {@link Collection} and passed to a {@link SuppressMany}-instance together with this method handle that
 * tells the {@link SuppressMany}'s method how to derive a {@link Suppressor} from this object, and ditto
 * for methods of {@link SuppressionSwitcher}.
 * <p>
 * Consider implementing one of the subinterfaces of {@link AutoValidationSuppressible}, namely
 * {@link Multi} or {@link Single}.
 * @see Suppressor
 * @see Suppressor#many(Function, Collection)
 * @see Suppressor#many(Function, Object...)
 * @author bb
 *
 */
public interface AutoValidationSuppressible {
	/**
	 * Method handle for simply generating a {@link Suppressor}
	 */
	public static final Function<? super AutoValidationSuppressible, Suppressor>
	SUPPRESS_AUTO_VALIDATION = AutoValidationSuppressible::suppressAutoValidation;
	/**
	 * Method handle for adding a newly generated {@link Suppressor} to a given {@link SuppressMany} instance.
	 */
	public static final BiConsumer<AutoValidationSuppressible, SuppressMany> 
	SUPPRESS_AUTO_VALIDATION_ADD=AutoValidationSuppressible::suppressAutoValidation;
	/**
	 * Method handle for adding a several {@link Suppressor}s newly generated 
	 * from a collection of {@link AutoValidationSuppressible}s
	 * to a given {@link SuppressMany}
	 */
	public static final BiConsumer<Iterable<? extends AutoValidationSuppressible>, SuppressMany> 
	SUPPRESS_AUTO_VALIDATION_COLLECTION=Suppressor.lift(SUPPRESS_AUTO_VALIDATION_ADD);

	/**
	 * Make a {@link Suppressor} which suppresses the autovalidation behavior of this object until it is released
	 * @return
	 */
	public Suppressor suppressAutoValidation();
	/**
	 * {@link SuppressMany#add(Suppressor) Add}s the Suppressor(s) needed to suppress the 
	 * autovalidation behavior of this object to the given {@link SuppressMany} instance.
	 * 
	 * @param s
	 * @return {@code s}
	 */
	public SuppressMany suppressAutoValidation(SuppressMany s);



	/**
	 * This interface provides a default implementation for {@link #suppressAutoValidation()} which relies on {@link #suppressAutoValidation(SuppressMany)}.
	 * Implement it if your implementation produces multiple {@link Suppressor}s.
	 * @author bb
	 *
	 */
	interface Multi extends AutoValidationSuppressible{
		@Override
		default Suppressor suppressAutoValidation() {
			SuppressMany ret = new SuppressMany();
			boolean fail = true;
			try {
				suppressAutoValidation(ret);
				fail = false;
				return ret;
			}finally {
				if(fail)
					ret.release();
			}
		}
		public static Multi of(AutoValidationSuppressible... sub) {
			return s->s.add(SUPPRESS_AUTO_VALIDATION_ADD, sub);
		}
		public static Multi of(Iterable<? extends AutoValidationSuppressible> sub) {
			return s->s.add(SUPPRESS_AUTO_VALIDATION_ADD, sub);
		}
		@SafeVarargs
		public static Multi of(Iterable<? extends AutoValidationSuppressible>... sub) {
			return s->s.add(SUPPRESS_AUTO_VALIDATION_COLLECTION, sub);
		}
	}
	/**
	 * This interface provides a default implementation for {@link #suppressAutoValidation(SuppressMany)} which relies on {@link #suppressAutoValidation()}.
	 * Implement it if your implementation produces just a single {@link Suppressor}, or 
	 * if you want the {@link Suppressor}s organized hierarchically.
	 * @author bb
	 *
	 */
	interface Single extends AutoValidationSuppressible{
		@Override
		default SuppressMany suppressAutoValidation(SuppressMany s){
			s.makePlaceFor1().add(this.suppressAutoValidation());
			return s;
		}
	}

	/**
	 * This interface provides a do-nothing implementation
	 * Implement it if a superclass requires that you implement {@link AutoValidationSuppressible},
	 * but there is nothing to suppress.
	 * @author bb
	 *
	 */
	interface None extends AutoValidationSuppressible.Multi{
		AutoValidationSuppressible JUST = new None() {};
		@Override
		default SuppressMany suppressAutoValidation(SuppressMany s) {
			return s;
		}
		@Override
		default Suppressor suppressAutoValidation() {
			return Suppressor.NOP;
		}
	}

	/**
	 * Make a {@link SuppressionSwitcher} that uses the {@link AutoValidationSuppressible#suppressAutoValidation()}
	 * {@link #SUPPRESS_AUTO_VALIDATION} to generate {@link Suppressor}s for the Objects it controls.
	 * @return
	 */
	public static SuppressionSwitcher.Final<AutoValidationSuppressible> makeSwitcher(){
		return new SuppressionSwitcher.Final<>(SUPPRESS_AUTO_VALIDATION);
	}
	/**
	 * Make a {@link ReactiveSuppressionSwitcher} that uses the {@link AutoValidationSuppressible#suppressAutoValidation()}
	 * {@link #SUPPRESS_AUTO_VALIDATION} to generate {@link Suppressor}s for the Objects it controls.
	 * @return
	 */
	public static ReactiveSuppressionSwitcher<AutoValidationSuppressible> makeReactiveSwitcher(){
		return new ReactiveSuppressionSwitcher<>(SUPPRESS_AUTO_VALIDATION);
	}

}
