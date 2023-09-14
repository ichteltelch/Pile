package pile.aspect.combinations;

import pile.aspect.Dependency;
import pile.aspect.transform.TransformableValue;

/**
 * Combination of {@link TransformableValue} and {@link Dependency}
 * @author bb
 *
 * @param <E>
 */
public interface TransformableDependency<E> extends TransformableValue<E>, Dependency {
	/**
	 * Give this object a name for debugging purposes.
	 * @param s
	 * @return {@code this}. Subclasses and -interfaces should adapt the return type to be themselves 
	 */
	TransformableDependency<E> setName(String s);
}
