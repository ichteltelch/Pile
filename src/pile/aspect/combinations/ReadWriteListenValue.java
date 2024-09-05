package pile.aspect.combinations;

import pile.aspect.ReadValue;
import pile.aspect.WriteValue;
import pile.aspect.listen.ListenValue;
import pile.aspect.listen.ValueEvent;
import pile.aspect.listen.ValueListener;
import pile.builder.ISealPileBuilder;
import pile.builder.IndependentBuilder;
import pile.builder.SealPileBuilder;
import pile.impl.Independent;
import pile.impl.Piles;
import pile.impl.SealPile;
import pile.utils.IdentitiyMemoCache;

/**
 * Combination of {@link ReadValue}, {@link WriteValue} and {@link ListenValue}
 * @author bb
 *
 * @param <E>
 */
public interface ReadWriteListenValue<E> extends ReadListenValue<E>, ReadWriteValue<E>{

	public static final IdentitiyMemoCache<ReadWriteListenValue<?>, Independent<?>> WRITABLE_VALID_BUFFER_CACHE = new IdentitiyMemoCache<>(ReadWriteListenValue::writableValidBuffer);
	
	
	
	/** Delegates to {@link #writableValidBuffer_memo()} */
	public default Independent<E> validBuffer_memo(){return readOnlyValidBuffer_memo();}
	/**
	 * Retrieve or make a memoized "valid buffer" obtained from {@link #writableValidBuffer()}.
	 * You should not use memoization if
	 * <ul>
	 * <li>You intend to give a debug name or debug owner reference to the result</li>
	 * <li>You intend to add {@link ValueListener}s that are never removed</li>
	 * <li>The result should act as its own independent entity, expecially with regards to firing {@link ValueEvent}s</li>
	 * <li>{@code this} is only referenced locally and there is no legal way to make another validBuffer from it.
	 * (That would just cause a memoization cache entry that will never be used)</li>
	 * </ul> 
	 */
	@SuppressWarnings("unchecked")
	public default Independent<E> writableValidBuffer_memo(){return (Independent<E>) WRITABLE_VALID_BUFFER_CACHE.apply(this);}

	
	@Override public default Independent<E> validBuffer(){return writableValidBuffer();}
	@Override public default IndependentBuilder<? extends Independent<E>, E> validBufferBuilder() {return writableValidBufferBuilder();}
	public default Independent<E> writableValidBuffer() {return writableValidBufferBuilder().build();}
	public default IndependentBuilder<? extends Independent<E>, E> writableValidBufferBuilder() {return Piles.<E>ib().setupWritableValidBuffer(this);}

	
	
	@Override public default SealPile<E> buffer(){return writableBuffer();}
	/** delegates to {@link #writableBufferBuilder()} */
	@Override public default SealPileBuilder<? extends SealPile<E>, E> bufferBuilder() {return writableBufferBuilder();}
	/** @see ISealPileBuilder#setupWritableBuffer(ReadWriteListenValue) */
	public default SealPile<E> writableBuffer() {return writableBufferBuilder().build();}
	public default SealPileBuilder<? extends SealPile<E>, E> writableBufferBuilder() {return Piles.<E>sb().setupWritableBuffer(this);}

	
	@Override public default SealPile<E> rateLimited(long coldStartTime, long coolDownTime){return writableRateLimited(coldStartTime, coolDownTime);}
	/** delegates to {@link #writableRateLimitedBuilder(long, long)} */
	@Override public default SealPileBuilder<? extends SealPile<E>, E> rateLimitedBuilder(long coldStartTime, long coolDownTime) {return writableRateLimitedBuilder(coldStartTime, coolDownTime);}
	/** @see ISealPileBuilder#setupWritbaleRateLimited(ReadWriteListenValue, long, long) */
	public default SealPile<E> writableRateLimited(long coldStartTime, long coolDownTime) {return writableRateLimitedBuilder(coldStartTime, coolDownTime).build();}
	public default SealPileBuilder<? extends SealPile<E>, E> writableRateLimitedBuilder(long coldStartTime, long coolDownTime) {return Piles.<E>sb().setupWritableRateLimited(this, coldStartTime, coolDownTime);}
	
	/**
	 * Obtain something like this value that can be depended on.
	 * If the value implements Dependency, it should return itself.
	 * Otherwise, a memoized (possibly writable) validBuffer will be returned.
	 */
	public default ReadWriteListenDependency<E> asDependency() {
		return validBuffer_memo();
	}
}
