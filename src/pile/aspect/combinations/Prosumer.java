package pile.aspect.combinations;

import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * A combination of a {@link Supplier} and a {@link Consumer} for the same type
 * @author bb
 *
 * @param <E>
 */
public interface Prosumer<E> extends Supplier<E>, Consumer<E> {

}
