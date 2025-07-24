package pile.aspect.combinations;

import java.util.Objects;
import java.util.function.BiPredicate;
import java.util.function.Consumer;
import java.util.logging.Logger;

import pile.aspect.Dependency;
import pile.aspect.ReadValue;
import pile.aspect.VetoException;
import pile.aspect.WriteValue;
import pile.aspect.suppress.Suppressor;
import pile.builder.SealPileBuilder;
import pile.impl.SealPile;
import pile.specialized_bool.PileBool;
import pile.specialized_bool.SealBool;
import pile.specialized_double.PileDouble;
import pile.specialized_double.SealDouble;
import pile.specialized_int.PileInt;
import pile.specialized_int.SealInt;
import pile.utils.Bijection;
import pile.utils.Nonreentrant;

/**
 * Combination of {@link ReadValue}, {@link WriteValue} and {@link Dependency}
 * @author bb
 *
 * @param <E>
 */
public interface ReadWriteDependency<E> extends ReadWriteValue<E>, ReadDependency<E> {
	final static Logger log=Logger.getLogger("ReadWriteDependency");

	/**
	 * The default equivalence relation used by the various biject methods
	 */
	public static final BiPredicate<Object, Object> DEFAULT_BIJECT_EQUIVALENCE=Objects::equals;

	/**
	 * Configure a unsealed {@link SealPile} to be the result of mapping a bijection over this
	 * {@link ReadWriteDependency}. The resulting value will be sealed but writable, and calling
	 * its {@link WriteValue#set(Object)} method will set this {@link ReadWriteDependency} to
	 * the preimage of the written value (modulo corrections; there is no guard against an endless cycle
	 * of mutual updates if the corrections behave in a non-convergent way)
	 * @param <F>
	 * @param <V>
	 * @param v
	 * @param mapFunction
	 * @param consistenyCheck If this parameter is given, then after  {@link #set(Object) set}ing this
	 * {@link ReadWriteDependency} when the {@link #set(Object) set()}-method of {@code v} has been called, 
	 * the value of this {@link ReadWriteDependency} will be mapped through the bijection 
	 * and compared to the value that has originally been set; in case of disagreement, 
	 * {@code v} will be set again with the former value. All this happens while both v and 
	 * this {@link ReadWriteDependency} are in a transaction. 
	 * All This is needed if there are non-trivial corrections in play
	 * @param dependencies Additional dependencies that should be associated with the value being built because the bijection function depends on them
	 * @return
	 */
	public default <F, V extends SealPile<F>> 
	V _bijectSetup(V v, Bijection<E, F> mapFunction, 
			BiPredicate<? super F, ? super F> consistenyCheck, Dependency... dependencies){
		return _bijectSetup(v, mapFunction, false, consistenyCheck, dependencies);
	}
	/**
	 * Configure a unsealed {@link SealPile} to be the result of mapping a bijection over this
	 * {@link ReadWriteDependency}. The resulting value will be sealed but writable, and calling
	 * its {@link WriteValue#set(Object)} method will set this {@link ReadWriteDependency} to
	 * the preimage of the written value (modulo corrections; there is no guard against an endless cycle
	 * of mutual updates if the corrections behave in a non-convergent way)
	 * @param <F>
	 * @param <V>
	 * @param v
	 * @param mapFunction
	 * @param consistenyCheck If this parameter is given, then after  {@link #set(Object) set}ing this
	 * {@link ReadWriteDependency} when the {@link #set(Object) set()}-method of {@code v} has been called, 
	 * the value of this {@link ReadWriteDependency} will be mapped through the bijection 
	 * and compared to the value that has originally been set; in case of disagreement, 
	 * {@code v} will be set again with the former value. All this happens while both v and 
	 * this {@link ReadWriteDependency} are in a transaction. 
	 * All This is needed if there are non-trivial corrections in play
	 * @return
	 */
	public default <F, V extends SealPile<F>> 
	V _bijectSetup(V v, Bijection<E, F> mapFunction, 
			BiPredicate<? super F, ? super F> consistenyCheck){
		return _bijectSetup(v, mapFunction, false, consistenyCheck, (Dependency[]) null);
	}
	/**
	 * Configure a unsealed {@link SealPile} to be the result of mapping a bijection over this
	 * {@link ReadWriteDependency}. The resulting value will be sealed but writable, and calling
	 * its {@link WriteValue#set(Object)} method will set this {@link ReadWriteDependency} to
	 * the preimage of the written value (modulo corrections; to guard against an endless cycle
	 * of mutual updates if the corrections behave in a non-convergent way, 
	 * set the {@code reentryGuard} parameter)
	 * @param <F>
	 * @param <V>
	 * @param v
	 * @param mapFunction
	 * @param consistenyCheck If this parameter is given, then after  {@link #set(Object) set}ing this
	 * {@link ReadWriteDependency} when the {@link #set(Object) set()}-method of {@code v} has been called, 
	 * the value of this {@link ReadWriteDependency} will be mapped through the bijection 
	 * and compared to the value that has originally been set; in case of disagreement, 
	 * {@code v} will be set again with the former value. All this happens while {@code v}
	 * is in a transaction.
	 * All This is needed if there are non-trivial corrections in play

	 * @return
	 */
	//TODO: Give users access to the builder for more detailed configuration needs
	public default <F, V extends SealPile<F>> 
	V _bijectSetup(V v, Bijection<E, F> mapFunction, 
			boolean reentryGuard, 
			BiPredicate<? super F, ? super F> consistenyCheck,
			Dependency... dependencies
			){
		Consumer<? super F> setter = v.makeSetter();
		Consumer<? super F> interceptor = value->{
			try(Suppressor vta=v.transaction()){
				try(Suppressor ta=transaction()){
					setter.accept(value);
					set(mapFunction.applyInverse(value));

				}
				if(consistenyCheck!=null) {
					F re = mapFunction.apply(get());
					if(!consistenyCheck.test(value, re))
						setter.accept(re);
				}
			}

		};
		if(reentryGuard)
			interceptor=new Nonreentrant().fixed(interceptor, a->{
				log.warning("Cycle detected in bijected value "+a);
			});
		SealPileBuilder<V, F> ret = new SealPileBuilder<>(v)
				.seal(interceptor)
				.recompute(()->mapFunction.apply(get()));
		if(dependencies!=null)
			ret = ret.dependOn(dependencies);
		return ret.whenChanged(this);
	}
	/**
	 * Make a {@link Pile} that maintains a one-to-one correspondence between its value 
	 * and this {@link ReadWriteDependency}'s value.
	 * @see ReadWriteDependency#_bijectSetup(SealPile, Bijection, BiPredicate)
	 * @param <F>
	 * @param mapFunction
	 * @param consistencyCheck
	 * @return
	 */
	public default <F> 
	SealPile<? extends F> biject(Bijection<E, F> mapFunction, BiPredicate<? super F, ? super F> consistencyCheck) {
		return _bijectSetup(new SealPile<F>(), mapFunction, consistencyCheck);
	}
	
	/**
	 * Make a {@link Pile} that maintains a one-to-one correspondence between its value 
	 * and this {@link ReadWriteDependency}'s value.
	 * @see ReadWriteDependency#_bijectSetup(SealPile, Bijection, BiPredicate)
	 * @param <F>
	 * @param mapFunction
	 * @param consistencyCheck
	 * @param dependencies of the bijection function, which will also become dependencies of the resulting SealPile
	 * @return
	 */
	public default <F> 
	SealPile<? extends F> biject(Bijection<E, F> mapFunction, BiPredicate<? super F, ? super F> consistencyCheck, Dependency...dependencies) {
		return _bijectSetup(new SealPile<F>(), mapFunction, consistencyCheck, dependencies);
	}
	/**
	 * Make a {@link PileBool} that maintains a one-to-one correspondence between its value 
	 * and this {@link ReadWriteDependency}'s value.
	 * @see ReadWriteDependency#_bijectSetup(SealPile, Bijection, BiPredicate)
	 * @param <F>
	 * @param mapFunction
	 * @param consistencyCheck
	 * @return
	 */
	public default  
	SealBool bijectToBool(Bijection<E, Boolean> mapFunction, BiPredicate<? super Boolean, ? super Boolean> consistencyCheck) {
		return _bijectSetup(new SealBool(), mapFunction, consistencyCheck);
	}
	/**
	 * Make a {@link PileBool} that maintains a one-to-one correspondence between its value 
	 * and this {@link ReadWriteDependency}'s value.
	 * @see ReadWriteDependency#_bijectSetup(SealPile, Bijection, BiPredicate)
	 * @param <F>
	 * @param mapFunction
	 * @param consistencyCheck
	 * @param dependencies of the bijection function, which will also become dependencies of the resulting SealPile
	 * @return
	 */
	public default  
	SealBool bijectToBool(Bijection<E, Boolean> mapFunction, BiPredicate<? super Boolean, ? super Boolean> consistencyCheck, Dependency...dependencies) {
		return _bijectSetup(new SealBool(), mapFunction, consistencyCheck, dependencies);
	}
	/**
	 * Make a {@link PileInt} that maintains a one-to-one correspondence between its value 
	 * and this {@link ReadWriteDependency}'s value.
	 * @see ReadWriteDependency#_bijectSetup(SealPile, Bijection, BiPredicate)
	 * @param <F>
	 * @param mapFunction
	 * @param consistencyCheck
	 * @return
	 */
	public default  
	SealInt bijectToInt(Bijection<E, Integer> mapFunction, BiPredicate<? super Integer, ? super Integer> consistencyCheck) {
		return _bijectSetup(new SealInt(), mapFunction, consistencyCheck);
	}
	/**
	 * Make a {@link PileInt} that maintains a one-to-one correspondence between its value 
	 * and this {@link ReadWriteDependency}'s value.
	 * @see ReadWriteDependency#_bijectSetup(SealPile, Bijection, BiPredicate)
	 * @param <F>
	 * @param mapFunction
	 * @param consistencyCheck
	 * @param dependencies of the bijection function, which will also become dependencies of the resulting SealPile
	 * @return
	 */
	public default  
	SealInt bijectToInt(Bijection<E, Integer> mapFunction, BiPredicate<? super Integer, ? super Integer> consistencyCheck, Dependency...dependencies) {
		return _bijectSetup(new SealInt(), mapFunction, consistencyCheck, dependencies);
	}
	/**
	 * Make a {@link PileDouble} that maintains a one-to-one correspondence between its value 
	 * and this {@link ReadWriteDependency}'s value.
	 * @see ReadWriteDependency#_bijectSetup(SealPile, Bijection, BiPredicate)
	 * @param <F>
	 * @param mapFunction
	 * @param consistencyCheck
	 * @return
	 */
	public default  
	SealDouble bijectToDouble(Bijection<E, Double> mapFunction, BiPredicate<? super Double, ? super Double> consistencyCheck) {
		return _bijectSetup(new SealDouble(), mapFunction, consistencyCheck);
	}
	/**
	 * Make a {@link PileDouble} that maintains a one-to-one correspondence between its value 
	 * and this {@link ReadWriteDependency}'s value.
	 * @see ReadWriteDependency#_bijectSetup(SealPile, Bijection, BiPredicate)
	 * @param <F>
	 * @param mapFunction
	 * @param consistencyCheck
	 * @param dependencies of the bijection function, which will also become dependencies of the resulting SealPile
	 * @return
	 */
	public default  
	SealDouble bijectToDouble(Bijection<E, Double> mapFunction, BiPredicate<? super Double, ? super Double> consistencyCheck, Dependency...dependencies) {
		return _bijectSetup(new SealDouble(), mapFunction, consistencyCheck, dependencies);
	}

	/**
	 * Make a {@link Pile} that maintains a one-to-one correspondence between its value 
	 * and this {@link ReadWriteDependency}'s value.
	 * @see ReadWriteDependency#_bijectSetup(SealPile, Bijection, BiPredicate)
	 * @see #DEFAULT_BIJECT_EQUIVALENCE
	 * @param <F>
	 * @param mapFunction
	 * @param consistencyCheck
	 * @return
	 */
	public default <F> 
	SealPile<F> biject(Bijection<E, F> mapFunction) {
		return _bijectSetup(new SealPile<F>(), mapFunction, DEFAULT_BIJECT_EQUIVALENCE);
	}
	/**
	 * Make a {@link Pile} that maintains a one-to-one correspondence between its value 
	 * and this {@link ReadWriteDependency}'s value.
	 * @see ReadWriteDependency#_bijectSetup(SealPile, Bijection, BiPredicate)
	 * @see #DEFAULT_BIJECT_EQUIVALENCE
	 * @param <F>
	 * @param mapFunction
	 * @param consistencyCheck
	 * @param dependencies of the bijection function, which will also become dependencies of the resulting SealPile
	 * @return
	 */
	public default <F> 
	SealPile<F> biject(Bijection<E, F> mapFunction, Dependency... dependencies) {
		return _bijectSetup(new SealPile<F>(), mapFunction, DEFAULT_BIJECT_EQUIVALENCE, dependencies);
	}
	/**
	 * Make a {@link PileBool} that maintains a one-to-one correspondence between its value 
	 * and this {@link ReadWriteDependency}'s value.
	 * @see ReadWriteDependency#_bijectSetup(SealPile, Bijection, BiPredicate)
	 * @see #DEFAULT_BIJECT_EQUIVALENCE
	 * @param <F>
	 * @param mapFunction
	 * @param consistencyCheck
	 * @return
	 */
	public default  
	SealBool bijectToBool(Bijection<E, Boolean> mapFunction) {
		return _bijectSetup(new SealBool(), mapFunction, DEFAULT_BIJECT_EQUIVALENCE);
	}	
	
	/**
	 * Make a {@link PileBool} that maintains a one-to-one correspondence between its value 
	 * and this {@link ReadWriteDependency}'s value.
	 * @see ReadWriteDependency#_bijectSetup(SealPile, Bijection, BiPredicate)
	 * @see #DEFAULT_BIJECT_EQUIVALENCE
	 * @param <F>
	 * @param mapFunction
	 * @param consistencyCheck
	 * @param dependencies of the bijection function, which will also become dependencies of the resulting SealPile
	 * @return
	 */
	public default  
	SealBool bijectToBool(Bijection<E, Boolean> mapFunction, Dependency... dependencies) {
		return _bijectSetup(new SealBool(), mapFunction, DEFAULT_BIJECT_EQUIVALENCE, dependencies);
	}
	
	/**
	 * Make a {@link PileInt} that maintains a one-to-one correspondence between its value 
	 * and this {@link ReadWriteDependency}'s value.
	 * @see ReadWriteDependency#_bijectSetup(SealPile, Bijection, BiPredicate)
	 * @see #DEFAULT_BIJECT_EQUIVALENCE
	 * @param <F>
	 * @param mapFunction
	 * @param consistencyCheck
	 * @return
	 */
	public default  
	SealInt bijectToInt(Bijection<E, Integer> mapFunction) {
		return _bijectSetup(new SealInt(), mapFunction, DEFAULT_BIJECT_EQUIVALENCE);
	}	
	/**
	 * Make a {@link PileInt} that maintains a one-to-one correspondence between its value 
	 * and this {@link ReadWriteDependency}'s value.
	 * @see ReadWriteDependency#_bijectSetup(SealPile, Bijection, BiPredicate)
	 * @see #DEFAULT_BIJECT_EQUIVALENCE
	 * @param <F>
	 * @param mapFunction
	 * @param consistencyCheck
	 * @param dependencies of the bijection function, which will also become dependencies of the resulting SealPile
	 * @return
	 */
	public default  
	SealInt bijectToInt(Bijection<E, Integer> mapFunction, Dependency... dependencies) {
		return _bijectSetup(new SealInt(), mapFunction, DEFAULT_BIJECT_EQUIVALENCE, dependencies);
	}	
	/**
	 * Make a {@link PileDouble} that maintains a one-to-one correspondence between its value 
	 * and this {@link ReadWriteDependency}'s value.
	 * @see ReadWriteDependency#_bijectSetup(SealPile, Bijection, BiPredicate)
	 * @see #DEFAULT_BIJECT_EQUIVALENCE
	 * @param <F>
	 * @param mapFunction
	 * @param consistencyCheck
	 * @return
	 */
	public default  
	SealDouble bijectToDouble(Bijection<E, Double> mapFunction) {
		return _bijectSetup(new SealDouble(), mapFunction, DEFAULT_BIJECT_EQUIVALENCE);
	}
	/**
	 * Make a {@link PileDouble} that maintains a one-to-one correspondence between its value 
	 * and this {@link ReadWriteDependency}'s value.
	 * @see ReadWriteDependency#_bijectSetup(SealPile, Bijection, BiPredicate)
	 * @see #DEFAULT_BIJECT_EQUIVALENCE
	 * @param <F>
	 * @param mapFunction
	 * @param dependencies of the bijection function, which will also become dependencies of the resulting SealPile
	 * @return
	 */
	public default  
	SealDouble bijectToDouble(Bijection<E, Double> mapFunction, Dependency... dependencies) {
		return _bijectSetup(new SealDouble(), mapFunction, DEFAULT_BIJECT_EQUIVALENCE, dependencies);
	}
	E set(E v);
	public default ReadWriteDependency<E> setNull() {		
		set(null);
		return this;
	}

	/**
	 * Make a reactive boolean that reflects whether this value is currently equal to the given constant.
	 * When an attempt is made to write to the returned reactive boolean, then setting it to true will cause the constant to be written to this value,
	 * and setting it to false will cause this value to be set to null, but only if the current value is equal to the constant.
	 * @param value
	 * @return
	 */
	public default SealBool isEqualConstRW(E value) {
		return isEqualConstRW(value, Pile.DEFAULT_BIJECT_EQUIVALENCE);
	}
	/**
	 * Make a reactive boolean that reflects whether this value is currently equal to the given constant.
	 * When an attempt is made to write to the returned reactive boolean, then setting it to true will cause the constant to be written to this value,
	 * and setting it to false will cause this value to be set to null, but only if the current value is equal to the constant.
	 * @param value
	 * @param eq The equivalence relation to be used
	 * @return
	 */
	public default SealBool isEqualConstRW(E value, BiPredicate<? super E, ? super E> eq) {
		return PileBool.sb()
				.recompute(()->eq.test(value, get()))
				.seal((nv)->{
					if(nv==null)
						throw new VetoException();
					if(nv) {
						set(value);
					}else if(eq.test(value, get())) {
						set(null);
					}
				})
				.parent(this)
				.whenChanged(this);
	}

}
