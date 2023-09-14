package pile.relation;

import java.util.Objects;
import java.util.function.BiPredicate;

import pile.aspect.combinations.ReadDependency;
import pile.aspect.combinations.ReadWriteDependency;
import pile.builder.SealPileBuilder;
import pile.impl.Piles;
import pile.specialized_bool.SealBool;

/**
 * A reactive boolean that reflects whether two reactive values are equal.
 * Also, if you {@link #set(Boolean) set} it to <code>true</code>,
 * one value will be overwritten with the other, making them equal (unless
 * a corrector installed on the {@link #receiver} prohibits it)  
 * Setting to <code>null</code> or <code>false</code> will be ignored.
 * @author bb
 *
 * @param <T>
 */

public class Equalizer<T> extends SealBool{
	/**
	 * The value that would be changed to make the equality true when requested 
	 */
	public final ReadWriteDependency<T> receiver;
	/**
	 * The value that would be read but not changed to make the equality true when requested 
	 */
	public final ReadDependency<? extends T> giver;
	
	/**
	 * Make an {@link Equalizer} with a constant {@link #giver} and a custom equivalence relation.
	 * @param <T>
	 * @param receiver
	 * @param giver
	 * @param equivalence
	 * @return
	 */
	public static <T> Equalizer<T> make(
			ReadWriteDependency<T> receiver,
			T giver,
			BiPredicate<? super T, ? super T> equivalence
			){
		return make(receiver, Piles.constant(giver), equivalence);
	}
	/**
	 * Make an {@link Equalizer} with a constant {@link #giver}.
	 * {@link Objects#equals(Object) Value equality} will be used as the equivalence relation 
	 * @param <T>
	 * @param receiver
	 * @param giver
	 * @return
	 */
	public static <T> Equalizer<T> make(
			ReadWriteDependency<T> receiver,
			T giver			
			){
		return make(receiver, Piles.constant(giver));
	}
	/**
	 * Make an {@link Equalizer} with a custom equivalence relation.
	 * @param <T>
	 * @param receiver
	 * @param giver
	 * @param equivalence
	 * @return
	 */
	public static <T> Equalizer<T> make(
			ReadWriteDependency<T> receiver,
			ReadDependency<? extends T> giver,
			BiPredicate<? super T, ? super T> equivalence
			){
		Equalizer<T> ret = new Equalizer<>(receiver, giver);
		return new SealPileBuilder<>(ret)
				.recompute(()->equivalence.test(giver.get(), receiver.get()))
				.seal(ret::interceptor)
				.whenChanged(giver, receiver);
	}
	/**
	 * Make an Equalizer.
	 * {@link Objects#equals(Object) Value equality} will be used as the equivalence relation 
	 * @param <T>
	 * @param receiver
	 * @param giver
	 * @return
	 */
	public static <T> Equalizer<T> make(
			ReadWriteDependency<T> receiver,
			ReadDependency<? extends T> giver			
			){
		return make(receiver, giver, Objects::equals);
	}
	private Equalizer(
			ReadWriteDependency<T> receiver,
			ReadDependency<? extends T> giver
			){
		this.receiver = receiver;
		this.giver = giver;
	}
	private void interceptor(Boolean newValue) {
		if(Boolean.TRUE.equals(newValue)) {
			receiver.set(giver.get());
		}
	}

}
