package pile.aspect.combinations;

import java.util.function.BiPredicate;
import java.util.function.BooleanSupplier;

import pile.aspect.Dependency;
import pile.aspect.ReadValue;
import pile.aspect.Sealable;
import pile.aspect.listen.ListenValue;
import pile.aspect.listen.ValueEvent;
import pile.aspect.listen.ValueListener;
import pile.builder.IIndependentBuilder;
import pile.builder.ISealPileBuilder;
import pile.builder.IndependentBuilder;
import pile.builder.SealPileBuilder;
import pile.impl.Constant;
import pile.impl.Independent;
import pile.impl.Piles;
import pile.impl.SealPile;
import pile.interop.wait.WaitService;
import pile.utils.IdentitiyMemoCache;

/**
 * Combination of {@link ReadValue} and {@link ListenValue}
 * @author bb
 *
 * @param <E>
 */
public interface ReadListenValue<E> extends ReadValue<E>, ListenValue{
	public static final IdentitiyMemoCache<ReadListenValue<?>, Independent<?>> READ_ONLY_VALID_BUFFER_CACHE = new IdentitiyMemoCache<>(ReadListenValue::validBuffer);
	/**
	 * Query whether this {@link ReadListenValue} is guaranteed to never change,
	 * for example because it is a {@link Constant} or because it is a {@link Sealable}
	 * sealed with the default interceptor and having no recomputer
	 * @return
	 */
	public boolean willNeverChange();
	


	
	/**
	 * By default this delegates to {@link #readOnlyValidBuffer_memo()}, 
	 * but subclasses that also implement {@link ReadWriteListenValue} call
	 * {@link ReadWriteListenValue#writableValidBuffer_memo()} instead.
	 * @return
	 */
	public default Independent<E> validBuffer_memo(){return readOnlyValidBuffer_memo();}
	/**
	 * Retrieve or make a memoized "valid buffer" obtained from {@link #readOnlyValidBuffer()}.
	 * You should not use memoization if
	 * <ul>
	 * <li>You intend to give a debug name or debug owner reference to the result</li>
	 * <li>You intend to add {@link ValueListener}s that are never removed</li>
	 * <li>The result should act as its own independent entity, especially with regards to firing {@link ValueEvent}s</li>
	 * <li>{@code this} is only referenced locally and there is no legal way to make another validBuffer from it.
	 * (That would just cause a memoization cache entry that will never be used)</li>
	 * </ul> 
	 */
	@SuppressWarnings("unchecked")
	public default Independent<E> readOnlyValidBuffer_memo(){return (Independent<E>) READ_ONLY_VALID_BUFFER_CACHE.apply(this);}

	
	
	/**
	 * By default this delegates to {@link #readOnlyValidBuffer()}, 
	 * but subclasses that also implement {@link ReadWriteListenValue} call
	 * {@link ReadWriteListenValue#writableValidBuffer()} instead.
	 * @return
	 */
	public default Independent<E> validBuffer(){return readOnlyValidBuffer();}
	/** 
	 * By default this delegates to {@link #readOnlyValidBufferBuilder()}, 
	 * but subclasses that also implement {@link ReadWriteListenValue} call
	 * {@link ReadWriteListenValue#writableValidBufferBuilder()} instead.
	 */
	public default IndependentBuilder<? extends Independent<E>, E> validBufferBuilder() {
		return readOnlyValidBufferBuilder();
	}
	/** @see IIndependentBuilder#setupValidBuffer(ReadListenValue) */
	public default Independent<E> readOnlyValidBuffer(){return readOnlyValidBufferBuilder().build();}
	/** @see IIndependentBuilder#setupValidBuffer(ReadListenValue) */
	public default IndependentBuilder<? extends Independent<E>, E> readOnlyValidBufferBuilder() {
		return Piles.<E>ib().setupValidBuffer(this);
	}
	
	
	/** 
	 * By default this delegates to {@link #readOnlyBuffer()}, 
	 * but subclasses that also implement {@link ReadWriteListenValue} call
	 * {@link ReadWriteListenValue#writableBuffer()} instead.
	 */
	public default SealPile<E> buffer(){
		return readOnlyBuffer();
	}
	/** 
	 * By default this delegates to {@link #readOnlyBufferBuilder()}, 
	 * but subclasses that also implement {@link ReadWriteListenValue} call
	 * {@link ReadWriteListenValue#writableBufferBuilder()} instead.
	 */
	public default SealPileBuilder<? extends SealPile<E>, E> bufferBuilder() {
		return readOnlyBufferBuilder();
	}
	/** @see ISealPileBuilder#setupBuffer(ReadListenValue) */

	public default SealPile<E> readOnlyBuffer(){
		return readOnlyBufferBuilder().build();
	}
	/** @see ISealPileBuilder#setupBuffer(ReadListenValue) */

	public default SealPileBuilder<? extends SealPile<E>, E> readOnlyBufferBuilder() {
		return Piles.<E>sb().setupBuffer(this);
	}
	
	
	
	
	/** 
	 * By default this delegates to {@link #readOnlyRateLimited(long, long)}, 
	 * but subclasses that also implement {@link ReadWriteListenValue} call
	 * {@link ReadWriteListenValue#writableRateLimited()} instead.
	 */
	public default SealPile<E> rateLimited(long coldStartTime, long coolDownTime){
		return readOnlyRateLimited(coldStartTime, coolDownTime);
	}
	/** 
	 * By default this delegates to {@link #readOnlyRateLimitedBuilder(long, long)}, 
	 * but subclasses that also implement {@link ReadWriteListenValue} call
	 * {@link ReadWriteListenValue#writableRateLimitedBuilder()} instead.
	 */
	public default SealPileBuilder<? extends SealPile<E>, E> rateLimitedBuilder(long coldStartTime, long coolDownTime) {
		return readOnlyRateLimitedBuilder(coldStartTime, coolDownTime);
	}
	/** @see ISealPileBuilder#setupRateLimited(ReadListenValue, long, long) */
	public default SealPile<E> readOnlyRateLimited(long coldStartTime, long coolDownTime){
		return readOnlyRateLimitedBuilder(coldStartTime, coolDownTime).build();
	}
	/** @see ISealPileBuilder#setupRateLimited(ReadListenValue, long, long) */
	public default SealPileBuilder<? extends SealPile<E>, E> readOnlyRateLimitedBuilder(long coldStartTime, long coolDownTime) {
		return Piles.<E>sb().setupRateLimited(this, coldStartTime, coolDownTime);
	}
	
	
	
	
	
	
	
	
	
	
	
	/**
	 * @see Dependency#dependencyName()
	 * @return
	 */
	String dependencyName();
	/**
	 * Wait until the condition becomes fulfilled. The condition will be re-checked whenever this 
	 * {@link ReadListenValue} changes and also periodically with a slow period.
	 * @param c
	 * @throws InterruptedException
	 */
	default public void await(BooleanSupplier c) throws InterruptedException{
		await(WaitService.get(), c);
	}
	/**
	 * Wait until the condition becomes fulfilled or until some time has elapsed. 
	 * The condition will be re-checked whenever this 
	 * {@link ReadListenValue} changes and also periodically with a slow period.
	 * @param c
	 * @param timeout Maximum number of milliseconds to wait
	 * @return The state of the condition as evaluated at the end of the wait
	 * @throws InterruptedException
	 */
	default public boolean await(BooleanSupplier c, long millis) throws InterruptedException{
		return await(WaitService.get(), c, millis);
	}
	/**
	 * Wait until the condition becomes fulfilled. The condition will be re-checked whenever this 
	 * {@link ReadListenValue} changes and also periodically with a slow period.
	 * @param c
	 * @throws InterruptedException
	 */
	public void await(WaitService ws, BooleanSupplier c) throws InterruptedException;
	/**
	 * Wait until the condition becomes fulfilled or until some time has elapsed. 
	 * The condition will be re-checked whenever this 
	 * {@link ReadListenValue} changes and also periodically with a slow period.
	 * @param c
	 * @param timeout Maximum number of milliseconds to wait
	 * @return The state of the condition as evaluated at the end of the wait
	 * @throws InterruptedException
	 */
	public boolean await(WaitService ws, BooleanSupplier c, long millis) throws InterruptedException;
	/**
	 * Get the equivalence relation used by this {@link ReadListenValue} 
	 * to decide whether a change in the wrapped value really counts as a change.
	 * @return
	 */
	public BiPredicate<? super E, ? super E> _getEquivalence();

	/**
	 * Add a {@link ValueListener} to this {@link ReadListenValue} that runs 
	 * the given {@link Runnable} the first time that the condition becomes true.
	 * @param cond
	 * @param run
	 */
	public default void runWhen(BooleanSupplier cond, Runnable run) {
		addValueListener_(new ValueListener() {
			@Override
			public void valueChanged(ValueEvent e) {
				if(cond.getAsBoolean()) {
					removeValueListener(this);
					run.run();
				}
			}
		});
	}



}
