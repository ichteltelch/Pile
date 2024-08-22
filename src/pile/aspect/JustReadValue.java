package pile.aspect;

import pile.aspect.listen.ListenValue;
import pile.impl.Piles;
import pile.specialized_bool.combinations.ReadListenDependencyBool;

/**
 * Interface with default methods for implementations of {@link ReadValue} that neither support
 * the {@link ListenValue} nor the {@link Dependency} aspects or any other fancy stuff.
 * @author bb
 *
 * @param <E>
 */
@FunctionalInterface
public interface JustReadValue<E> extends ReadValue<E>, AlwaysValid<E>{

	@Override default public boolean isInTransaction() {return false;}

	@Override default public ReadListenDependencyBool inTransactionValue() {return Piles.FALSE;}


	@Override default public boolean isDestroyed() {return false;}
	
	
	@Override default void __beginTransaction(boolean b) {}
	
	@Override default void __endTransaction() {}
	
	
}
