package pile.aspect;

import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.prefs.Preferences;

import pile.aspect.HasAssociations.AssociationKey;
import pile.aspect.HasAssociations.ReferencePolicy;
import pile.aspect.combinations.Prosumer;
import pile.impl.Independent;
import pile.interop.preferences.PrefInterop;

/**
 * Interface for saving a value to some external store when it changes, and restoring it from the store
 * @see PrefInterop for methods that return implementations based on the java {@link Preferences} api.
 * @author bb
 *
 * @param <E>
 */
public interface LastValueRememberer<E> 
extends Prosumer<E>{
	/**
	 * The {@link AssociationKey} used to associate a {@link LastValueRememberer} with
	 * an object that holds the value
	 * @see Independent for where it's used
	 * @see #KEY the singleton instance
	 * @author bb
	 *
	 * @param <E>
	 */
	public static final class LastValueAssociationKey<E> implements AssociationKey<LastValueRememberer<E>>{
		private LastValueAssociationKey() {}
		@Override
		public ReferencePolicy referenceStrength() {
			return ReferencePolicy.STRONG;
		}
	}
	/**
	 * The singleton instance of {@link LastValueAssociationKey}
	 * @see #key()
	 */
	public static final LastValueAssociationKey<?> KEY = new LastValueAssociationKey<>();
	/**
	 * Get the singleton instance of {@link LastValueAssociationKey}, 
	 * cast for use with the desired type
	 * @param <E>
	 * @return
	 */
	@SuppressWarnings("unchecked")
	public static <E> LastValueAssociationKey<E> key(){
		return (LastValueAssociationKey<E>) KEY;
	}
	/**
	 * Write the value to the store
	 * @param e
	 */
	public void storeLastValue(E e);
	/**
	 * Obtain the last stored value; If none had been stored so far, return a default value.
	 * @return
	 */
	public E recallLastValue();
	
	/**
	 * Create a {@link LastValueRememberer} that stores and restores the last value using the given
	 * {@link Prosumer}
	 * @param <E>
	 * @param i
	 * @return
	 */
	public static <E> LastValueRememberer<E> of(Prosumer<E> i){
		return new LastValueRememberer<E>() {
			@Override
			public E recallLastValue() {
				return i.get();
			}

			@Override
			public void storeLastValue(E e) {
				i.accept(e);
			}
		}; 
	}
	
	/**
	 * Create a {@link LastValueRememberer} that stores and restores the last value using the given
	 * {@link Supplier} and {@link Consumer}
	 * @param <E>
	 * @param g
	 * @param s
	 * @return
	 */
	public static <E> LastValueRememberer<E> make(Supplier<? extends E> g, Consumer<? super E> s){
		return new LastValueRememberer<E>() {
			@Override
			public E recallLastValue() {
				return g.get();
			}

			@Override
			public void storeLastValue(E e) {
				s.accept(e);
			}
		}; 
	}
	
	@Override
	default void accept(E v) {
		storeLastValue(v);
	}
	
	@Override
	default E get() {
		return recallLastValue();
	}
	

}
