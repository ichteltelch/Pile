package pile.builder;

import java.util.function.Consumer;

import pile.impl.SealPile;
/**
 * Abstract implementation of {@link ISealPileBuilder}  
 * @author bb
 *
 * @param <Self> The concrete implementing class. Because of this type parameter, 
 * the {@link AbstractSealPileBuilder} class needs to abstract
 * @param <V>
 * @param <E>
 */
public abstract class 
AbstractSealPileBuilder<Self extends AbstractSealPileBuilder<Self, V, E>, V extends SealPile<E>, E>
extends AbstractPileBuilder<Self, V, E>
implements ISealPileBuilder<Self, V, E>{

	/**
	 * @param value The value that this builder should act on, which must not already be sealed
	 */
	public AbstractSealPileBuilder(V value) {
		super(value);
		if(value.isSealed())
			throw new IllegalArgumentException("The value is already sealed!");
	}
	boolean sealOnBuild;
	boolean allowInvalidation;
	Consumer<? super E> interceptor;

	@Override
	public Self seal() {
		sealOnBuild=true;
		return self();
	}
	@Override
	public Self seal(Consumer<? super E> interceptor) {
		this.interceptor=interceptor;
		sealOnBuild=true;
		allowInvalidation=false;
		return self();
	}
	@Override
	public Self seal(Consumer<? super E> interceptor, boolean allowInvalidation) {
		this.interceptor=interceptor;
		sealOnBuild=true;
		this.allowInvalidation=allowInvalidation;
		return self();
	}

	@Override
	public Consumer<? super E> makeSetter(){
		return value.makeSetter();
	}
	@Override
	public V build() {
		V ret = super.build();
		if(sealOnBuild)
			value.seal(interceptor, allowInvalidation);
		return ret;
	}
	
	

}