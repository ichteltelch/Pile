package pile.aspect;

import pile.aspect.listen.ListenValue;
import pile.impl.Piles;
import pile.interop.wait.WaitService;
import pile.specialized_bool.combinations.JustReadValueBool;
import pile.specialized_bool.combinations.ReadListenDependencyBool;
import pile.specialized_bool.combinations.ReadValueBool;

/**
 * Interface with default methods for implementations of {@link ReadValue} that neither support
 * the {@link ListenValue} nor the {@link Dependency} aspects or any other fancy stuff.
 * @author bb
 *
 * @param <E>
 */
@FunctionalInterface
public interface JustReadValue<E> extends ReadValue<E>{
	@Override default public E getAsync() {return get();}

	@Override default public E getValid() {return get();}

	@Override default public E getValid(long timeout) {return get();}

	@Override default public E getValid(WaitService ws) {return get();}

	@Override default public E getValid(WaitService ws, long timeout) {return get();}

	@Override default public E getValidOrThrow() {return get();}

	@Override default public boolean isValid() {return true;}

	@Override default public ReadListenDependencyBool validity() {return Piles.TRUE;}

	@Override default public boolean isValidNull() {return get()==null;}

	@Override default public boolean isInTransaction() {return false;}

	@Override default public ReadListenDependencyBool inTransactionValue() {return Piles.FALSE;}

	@Override default public ReadValueBool nullOrInvalid() {return (JustReadValueBool)this::isNull;}

	@Override default public boolean isDestroyed() {return false;}
	
	@Override default E getOldIfInvalid() {return get();}
	
	@Override default boolean isValidAsync() {return isValid();}
	
	@Override default void __beginTransaction(boolean b) {}
	
	@Override default void __endTransaction() {}
	
	
}
