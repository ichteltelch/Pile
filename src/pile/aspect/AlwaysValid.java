package pile.aspect;

import pile.impl.Piles;
import pile.interop.wait.WaitService;
import pile.specialized_bool.combinations.JustReadValueBool;
import pile.specialized_bool.combinations.ReadListenDependencyBool;
import pile.specialized_bool.combinations.ReadValueBool;

public interface AlwaysValid<E> extends ReadValue<E> {
	@Override default public E getAsync() {return get();}

	@Override default public E getValid() {return get();}

	@Override default public E getValid(long timeout) {return get();}

	@Override default public E getValid(WaitService ws) {return get();}

	@Override default public E getValid(WaitService ws, long timeout) {return get();}

	@Override default public E getValidOrThrow() {return get();}

	@Override default public boolean isValid() {return true;}

	@Override default public ReadListenDependencyBool validity() {return Piles.TRUE;}

	@Override default public boolean isValidNull() {return get()==null;}
	
	@Override default public ReadValueBool nullOrInvalid() {return (JustReadValueBool)this::isNull;}
	@Override default E getOldIfInvalid() {return get();}
	
	@Override default boolean isValidAsync() {return isValid();}

}
