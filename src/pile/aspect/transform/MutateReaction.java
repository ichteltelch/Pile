package pile.aspect.transform;

import java.util.function.Consumer;

import pile.aspect.ReadValue.InvalidValueException;
import pile.aspect.transform.TransformHandler.TypedReaction;
import pile.aspect.transform.TransformReaction.ReactionType;

/**
 * A {@link TransformReaction} with type {@link ReactionType#MUTATE}.
 * It mutates the value of the {@link TransformableValue} but it stays the same reference.
 * @author bb
 *
 * @param <E>
 */
public class MutateReaction<E> extends TypedReaction<E>{
	@Override final public ReactionType getType() {
		return ReactionType.MUTATE;
	}
	protected final TransformableValue<E> value;
	protected final Consumer<? super E> transform;
	final E expectedValue;
	final boolean expectedValid;
	public MutateReaction(TransformableValue<E> value, Consumer<? super E> transform) {
		this(value, false, transform, null);
	}
	public MutateReaction(TransformableValue<E> value, boolean fast, Consumer<? super E> transform) {
		this(value, fast, transform, null);
	}
	public MutateReaction(TransformableValue<E> value, Consumer<? super E> transform, Runnable cancelCode) {
		this(value, false, transform, cancelCode);
	}
	public MutateReaction(TransformableValue<E> value, boolean fast, Consumer<? super E> transform, Runnable cancelCode) {
		super(cancelCode);
		this.transform=transform;
		this.fast=fast;
		this.value=value;
		boolean evalid = false;
		E evalue = null;
		if(value.isValid())
			try {
				evalue = value.getValidOrThrow();
				evalid=true;
			} catch (InvalidValueException e) {
		}
		expectedValid=evalid;
		expectedValue=evalue;
	}

	@Override
	public E apply(E in) throws InvalidValueException {
		if(expectedValid && in == expectedValue)
			transform.accept(in);
		else {
			cancel();
			throw new InvalidValueException();
		}
		return in;
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
			Thread.currentThread().setName("Mutate transform: "+value);
			value.runTransform(this);
		}finally {
			Thread.currentThread().setName(oldName);;
		}
	}
}