package pile.aspect.transform;

import java.util.ArrayList;
import java.util.Objects;
import java.util.function.Predicate;

import pile.utils.Functional;

/**
 * A {@link TransformHandler} that chooses among several {@link TransformHandler}s
 * based on some criteria; A criterion matches if a {@link Predicate} evaluates to
 * <code>true</code> on the object representing the transformation and the 
 * {@link TransformHandler} returns a non-<code>null</code> {@link TransformReaction}.
 * @author bb
 *
 * @param <E>
 */
public class MultiTransformHandler<E> implements TransformHandler<E>{
	/**
	 * The criteria for choosing the actual {@link TransformHandler}.
	 * This list is parallel to {@link #sub}
	 */
	ArrayList<Predicate<? super Object>> crits=new ArrayList<>();
	/**
	 * The {@link TransformHandler}s to choose from.
	 * This list is parallel to {@link #crits} 
	 */
	ArrayList<TransformHandler<E>> sub=new ArrayList<>();
	/**
	 * The {@link TransformReaction} when none of the criteria match
	 */
	private TransformReaction defaultReaction=TransformReaction.ignore();
	/**
	 * Add another sub-{@link TransformHandler}. It will be chosen if the {@code crit}erion matches
	 * and none of the criteria of previously added {@link TransformHandler} match.
	 * <br>
	 * <b>Warning: </b> This method is not synchronized. Concurrent access may break this
	 * {@link MultiTransformHandler}
	 * @param crit
	 * @param then
	 * @return {@code this}
	 */
	public MultiTransformHandler<E> add(Predicate<? super Object> crit, TransformHandler<E> then){
		Objects.requireNonNull(crit);
		Objects.requireNonNull(then);
		crits.add(crit);
		sub.add(then);
		return this;
	}
	/**
	 * Add another sub-{@link TransformHandler}. It will be chosen if it returns a non-<code>null</code>
	 * {@link TransformReaction} and none of the criteria of previously added {@link TransformHandler} match.
	 * <br>
	 * <b>Warning: </b> This method is not synchronized. Concurrent access may break this
	 * {@link MultiTransformHandler}
	 * @param crit
	 * @param then
	 * @return {@code this}
	 */
	public MultiTransformHandler<E> add(TransformHandler<E> then){
		Objects.requireNonNull(then);
		crits.add(Functional.CONST_TRUE);
		sub.add(then);
		return this;
	}
	/**
	 * Change the {@link TransformReaction} that is chosen when none of the criteria match.
	 * The default is {@link TransformReaction#IGNORE}
	 * @param newDefault
	 * @return
	 */
	public MultiTransformHandler<E> setDefault(TransformReaction newDefault){
		Objects.requireNonNull(newDefault);
		defaultReaction=newDefault;
		return this;
	}
	@Override
	public TransformReaction react(TransformableValue<E> v, Object transform) {
		for(int i=0; i<crits.size(); ++i)
			if(crits.get(i).test(transform)) {
				TransformReaction reaction = sub.get(i).react(v, transform);
				if(reaction!=null)
					return reaction;
			}
		return defaultReaction;
	}
	
}
