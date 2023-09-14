package pile.aspect;

import java.util.function.Consumer;

import pile.impl.Piles;

/**
 * This interface accounts for the possibility that a reactive value may be
 *  influenced by other things than its {@link Dependencies}.
 * Currently, these other things are just the {@code owner}, and this interface is used only
 * used for the {@link Piles#superDeepRevalidate(Depender, java.util.function.Predicate, java.util.function.Predicate)
 * superDeepRevalidate} mechanism. 
 * @author bb
 *
 */
public interface HasInfluencers {
	public void giveInfluencers(Consumer<? super Object> out);
}
