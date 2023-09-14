package pile.aspect.transform;

import java.util.function.Function;

import pile.aspect.transform.TransformHandler.TypedReaction;
import pile.aspect.transform.TransformReaction.ReactionType;

/**
 * A {@link TransformReaction} with type {@link ReactionType#REPLACE}.
 * It replaces the value of the {@link TransformableValue} with the result of the 
 * transform.
 * @author bb
 *
 * @param <E>
 */
public class ReplaceReaction<E> extends TypedReaction<E>{
	@Override final public ReactionType getType() {
		return ReactionType.REPLACE;
	}
	protected final TransformableValue<E> value;
	protected final Function<? super E, ? extends E> transform;
	public ReplaceReaction(TransformableValue<E> value, Function<? super E, ? extends E> transform) {
		this(value, false, transform, null);
	}
	public ReplaceReaction(TransformableValue<E> value, Function<? super E, ? extends E> transform, Runnable cancelCode) {
		this(value, false, transform, cancelCode);
	}
	public ReplaceReaction(TransformableValue<E> value, boolean fast, Function<? super E, ? extends E> transform) {
		this(value, fast, transform, null);
	}
	public ReplaceReaction(TransformableValue<E> value, boolean fast, Function<? super E, ? extends E> transform, Runnable cancelCode) {
		super(cancelCode);
		this.transform=transform;
		this.fast=fast;
		this.value=value;
	}
	@Override
	public E apply(E in) {
		return transform.apply(in);
	}
	final boolean fast;
	@Override
	public boolean fast() {
		return fast;
	}
	@Override
	public void run() {
		String oldName = Thread.currentThread().getName();
		try {
			Thread.currentThread().setName("Replace transform: "+value);
			value.runTransform(this);
		}finally {
			Thread.currentThread().setName(oldName);;
		}
	}
}