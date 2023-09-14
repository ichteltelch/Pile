package pile.aspect.combinations;

import pile.aspect.Depender;
import pile.aspect.WriteValue;

/**
 * Combination of {@link WriteValue} and {@link Depender}
 * @author bb
 *
 * @param <E>
 */
public interface WriteDepender<E> extends WriteValue<E>, Depender {

}
