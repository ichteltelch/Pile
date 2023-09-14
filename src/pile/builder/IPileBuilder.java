package pile.builder;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import java.util.function.BiPredicate;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

import pile.aspect.Dependency;
import pile.aspect.Depender;
import pile.aspect.HasBrackets;
import pile.aspect.ReadValue;
import pile.aspect.ValueBracket;
import pile.aspect.combinations.Pile;
import pile.aspect.combinations.ReadListenDependency;
import pile.aspect.recompute.Recomputation;
import pile.aspect.transform.TransformHandler;
import pile.impl.AbstractReadListenDependency;
import pile.impl.DebugCallback;
import pile.impl.Piles;
import pile.interop.debug.DebugEnabled;
import pile.interop.exec.StandardExecutors;
import pile.utils.Functional;

/**
 * Interface for builders that build {@link Pile}s
 * @author bb
 *
 * @param <Self> Implementing class
 * @param <V> concrete subtype of the {@link Pile} being build
 * @param <E> Type of the value's content

 */
public interface IPileBuilder<Self extends IPileBuilder<Self, V, E>, V extends Pile<E>, E>
extends ICorrigibleBuilder<Self, V, E>, IListenValueBuilder<Self, V>{
	/**
	 * Define the code that recomputes the {@link Pile}'s value.
	 * The code defined here will run in a different thread than the one that started the recomputation
	 * if you define a non-negative {@link #delay(long) delay} or define additional code for
	 * {@link #recomputeImmediate(Consumer) immediate recomputation}
	 * @param recomputer
	 * @return {@code this} builder
	 */
	Self recompute(Consumer<? super Recomputation<E>> recomputer) ;
	/**
	 * Define code that recomputes the {@link Pile}'s value.
	 * If you also called one of the {@link #recompute(Consumer) recompute?} methods, the recomputation 
	 * defined by it will run in a different thread than the one that started the recomputation 
	 * if the code defined here does not fulfill the recomputation.
	 * @param recomputer
	 * @return {@code this} builder
	 */
	Self recomputeImmediate(Consumer<? super Recomputation<E>> recomputer);
	/**
	 * Define code that recomputes the {@link Pile}'s value in two stages: 
	 * one is immediate and returns a <code>null</code> 
	 * or a {@link Runnable} that should continue the recomputation in a separate thread in case that
	 * the immediately run code did not fulfill the {@link Recomputation}.
	 * Do not call one of the other {@link #recompute(Consumer) recompute*}-methods of this 
	 * builder if you use this method to define how recomputation works.
	 * @see Piles#FULFILL_INVALID {@link Piles#FULFILL_INVALID}: 
	 * Let the {@link Function} return this special value in order to immediately 
	 * call {@link Recomputation#fulfillInvalid()} 
	 * @see Piles#FULFILL_NULL {@link Piles#FULFILL_NULL}: 
	 * Let the {@link Function} return this special value in order to immediately call
	 * {@link Recomputation#fulfill(Object) Recomputation.fulfill(null)}
	 * @param recomputer
	 * @return {@code this} builder
	 */
	Self recomputeStaged(Function<? super Recomputation<E>, ? extends Runnable> recomputer);

	/**
	 * Call this method instead of {@link #recompute(Supplier)} if the compiler is unable
	 * to infer the type of the lambda you pass as the argument.
	 * @param recomputer
	 * @return {@code this} builder
	 */
	default Self recomputeS(Supplier<? extends E> recomputer) {
		return recompute(recomputer);
	}
	/**
	 * Define the code that recomputes the {@link Pile}'s value. 
	 * This simplified interface does not give you access to the {@link Recomputation} object;
	 * you can only return a value that is then used to {@link Recomputation#fulfill(Object) fulfill}
	 * the {@link Recomputation}.
	 * The code defined here will run in a different thread than the one that started the recomputation
	 * if you define a non-negative {@link #delay(long) delay} or define additional code for
	 * {@link #recomputeImmediate(Consumer) immediate recomputation}
	 * @param recomputer
	 * @return {@code this} builder
	 */
	default Self recompute(Supplier<? extends E> recomputer) {
		return recompute(re->{
			try {
				E value;
				try {
					value = recomputer.get();
				}catch(FulfillInvalid x) {
					re.fulfillInvalid();
					return;
				}catch(RuntimeException x) {
					if(re.isFinished())
						return;//ignore errors that occur after the recomputation is finished (most likely, it has been cancelled)					
					else{
						x.printStackTrace();
						throw x;
					}
				}
				re.fulfill(value);
			}finally {
				re.fulfillInvalid();
			}
		});
	}
	/**
	 * Start the second stage recomputation after a certain delay
	 * in a separate thread.
	 * @param millis The delay in milliseconds. Give a zero value here to start the recomputation
	 * immediately in a separate {@link Thread}, or a negative value do restore the default behavior.
	 * @return {@code this} builder
	 */

	Self delay(long millis);
	/**
	 * Add a bracket for the current value of the {@link Pile} being built
	 * @param bracket
	 * @see HasBrackets#_addValueBracket(boolean, ValueBracket)
	 * @return {@code this} builder
	 */
	default Self bracket(ValueBracket<? super E, ? super ReadListenDependency<? extends E>> bracket) {
		return bracket(false, bracket);
	}
	/**
	 * Add a bracket for both the the current value and the old value of the {@link Pile} being built.
	 * If the current value and the old value are identical, the bracket will be open only once.
	 * @param bracket
	 * @see HasBrackets#_addOldValueBracket(boolean, ValueBracket)
	 * @return {@code this} builder
	 */
	default Self anyBracket(ValueBracket<? super E, ? super ReadListenDependency<? extends E>> bracket) {
		return anyBracket(false, bracket);
	}
	/**
	 * Add a bracket for the old value of the {@link Pile} being built
	 * @param bracket
	 * @see HasBrackets#_addAnyValueBracket(boolean, ValueBracket)
	 * @return {@code this} builder
	 */
	default Self oldBracket(ValueBracket<? super E, ? super ReadListenDependency<? extends E>> bracket) {
		return oldBracket(false, bracket);
	}

	
	/**
	 * Add a bracket for the current value of the {@link Pile} being built
	 * @param bracket
	 * @see HasBrackets#_addValueBracket(boolean, ValueBracket)
	 * @return {@code this} builder
	 */
	Self bracket(boolean openNow, ValueBracket<? super E, ? super ReadListenDependency<? extends E>> bracket);
	/**
	 * Add a bracket for both the the current value and the old value of the {@link Pile} being built.
	 * If the current value and the old value are identical, the bracket will be open only once.
	 * @param bracket
	 * @see HasBrackets#_addOldValueBracket(boolean, ValueBracket)
	 * @return {@code this} builder
	 */
	Self anyBracket(boolean openNow, ValueBracket<? super E, ? super ReadListenDependency<? extends E>> bracket);
	/**
	 * Add a bracket for the old value of the {@link Pile} being built
	 * @param bracket
	 * @see HasBrackets#_addAnyValueBracket(boolean, ValueBracket)
	 * @return {@code this} builder
	 */
	Self oldBracket(boolean openNow, ValueBracket<? super E, ? super ReadListenDependency<? extends E>> bracket);

	/**
	 * Add several brackets for the current value of the {@link Pile} being built
	 * @param bracket
	 * @see IPileBuilder#bracket(ValueBracket)
	 * @return {@code this} builder
	 */
	default Self bracket(Iterable<? extends ValueBracket<? super E, ? super ReadListenDependency<? extends E>>> brackets) {
		if(brackets!=null) brackets.forEach(this::bracket);
		return self();
	}
	/**
	 * Add several brackets both the the current value and the old value of the {@link Pile} being built.
	 * If the current value and the old value are identical, the bracket will be open only once.
	 * @param bracket
	 * @see IPileBuilder#anyBracket(ValueBracket)
	 * @return {@code this} builder
	 */
	default Self anyBracket(Iterable<? extends ValueBracket<? super E, ? super ReadListenDependency<? extends E>>> brackets) {
		if(brackets!=null) brackets.forEach(this::anyBracket);
		return self();
	}
	/**
	 * Add several brackets for the old value of the {@link Pile} being built
	 * @param bracket
	 * @see IPileBuilder#oldBracket(ValueBracket)
	 * @return {@code this} builder
	 */
	default Self oldBracket(Iterable<? extends ValueBracket<? super E, ? super ReadListenDependency<? extends E>>> brackets) {
		if(brackets!=null) brackets.forEach(this::oldBracket);
		return self();
	}
	
	/**
	 * Add several brackets for the current value of the {@link Pile} being built
	 * @param bracket
	 * @see IPileBuilder#bracket(boolean, ValueBracket)
	 * @return {@code this} builder
	 */
	default Self bracket(boolean openNow, Iterable<? extends ValueBracket<? super E, ? super ReadListenDependency<? extends E>>> brackets) {
		if(brackets!=null) 
			for(ValueBracket<? super E, ? super ReadListenDependency<? extends E>> b: brackets)
				bracket(openNow, b);
		return self();
	}
	/**
	 * Add several brackets both the the current value and the old value of the {@link Pile} being built.
	 * If the current value and the old value are identical, the bracket will be open only once.
	 * @param bracket
	 * @see IPileBuilder#anyBracket(boolean, ValueBracket)
	 * @return {@code this} builder
	 */
	default Self anyBracket(boolean openNow, Iterable<? extends ValueBracket<? super E, ? super ReadListenDependency<? extends E>>> brackets) {
		if(brackets!=null) 
			for(ValueBracket<? super E, ? super ReadListenDependency<? extends E>> b: brackets)
				anyBracket(openNow, b);
		return self();
	}
	/**
	 * Add several brackets for the old value of the {@link Pile} being built
	 * @param bracket
	 * @see IPileBuilder#oldBracket(boolean, ValueBracket)
	 * @return {@code this} builder
	 */
	default Self oldBracket(boolean openNow, Iterable<? extends ValueBracket<? super E, ? super ReadListenDependency<? extends E>>> brackets) {
		if(brackets!=null) 
			for(ValueBracket<? super E, ? super ReadListenDependency<? extends E>> b: brackets)
				oldBracket(openNow, b);
		return self();
	}
	/**
	 * Set the value. Note that this immediately calls {@link Independent#set(Object)}; 
	 * {@link ValueBracket}s and corrections that are added yet will have no effect.
	 * @param initValue
	 * @return {@code this} builder
	 */
	Self init(E val);
	/**
	 * Give a name to the {@link Pile} for debugging purposes 
	 * @see Pile#avName
	 * @param n
	 * @return
	 */
	Self name(String n);
	
	/**
	 * Give a name to the {@link Pile} for debugging purposes, if the current name is <code>null</code>.
	 * @see Pile#avName
	 * @param n
	 * @return
	 */
	Self nameIfUnnamed(String name);
	/**
	 * Set the {@link PileImpl#owner} field of the {@link Pile}. This is mostly for debugging purposes, 
	 * but some stuff may rely on the reference being present to prevent the garbage collector
	 * from collecting the object, and is also used by {@link Piles#superDeepRevalidate(Depender, Predicate, Predicate)}.
	 * @param o
	 * @return
	 */
	Self parent(Object o);
	/**
	 * Make the {@link Pile} depend on the given {@link Dependency}
	 * @param d
	 * @return
	 */
	Self dependOn(Dependency d);
	/**
	 * Make the {@link Pile} depend on the given {@link Dependency Dependencies}
	 * @param d
	 * @return
	 */
	Self dependOn(Dependency... d);
	/**
	 * Make the {@link Pile} depend on the given {@link Dependency}
	 * @param essential Declare the {@link Dependency} to be essential 
	 * (See {@link Pile#setDependencyEssential(boolean, Dependency)})
	 * @param d
	 * @return
	 */
	Self dependOn(boolean essential, Dependency d);
	/**
	 * Make the {@link Pile} depend on the given {@link Dependency Dependencies}
	 * @param essential Declare the {@link Dependency Dependencies} to be essential 
	 * (See {@link Pile#setDependencyEssential(boolean, Dependency)})
	 * @param d
	 * @return
	 */
	Self dependOn(boolean essential, Dependency... d);
	/**
	 * Add the {@link #valueBeingBuilt() value being built} as a 
	 * {@link Dependency} to the given {@link Depender}
	 * @param dep
	 * @return
	 */
	public default Self depender(Depender dep) {
		dep.addDependency(valueBeingBuilt(), false, false);
		return self();
	}
	/**
	 * Set the {@link DebugCallback} for the {@link Pile} being built
	 * @param dc
	 * @return {@code this} builder
	 */
	Self debug(DebugCallback dc);
//	Self dontRetry();
	/**
	 * Set the {@link Pile#setLazyValidating(boolean) lazy-validating} flag of the value being build.
	 * The {@link Pile} will then not recompute itself as soon as possible, but only if also its value 
	 * has been requested.
	 * @return
	 */
	Self lazy();
	/**
	 * Set the {@link TransformHandler} of the {@link Pile} to {@link TransformHandler#RECOMPUTE}
	 * @return
	 */
	default Self recomputeOnTransform() {
		return transformHandler(TransformHandler.recompute());
	}
	/**
	 * Set the {@link TransformHandler} of the {@link Pile}
	 * @param th
	 * @return
	 */
	public Self transformHandler(TransformHandler<E> th);
	/**
	 * Make it so that the old value is forgotten as soon as recomputation in a separate thread starts.
	 * @return
	 */
	Self forgetOldValueOnDelayedRecompute();

	/**
	 * Do not check whether the {@link Recomputation}s have been fulfilled by the handler code. 
	 * Do not log warnings about it and don't fulfill the {@link Recomputation}s.
	 * The method should be called you you're doing your own thing to transfer the
	 * computation to another thread.
	 * @return
	 */
	Self noUnfulfilledGuard();
	
	/**
	 * Like {@link #build()}, but also adds some {@link Dependency Dependencies} and declares them to
	 * be {@link Pile#setDependencyEssential(boolean, Dependency) essential}
	 * @param deps
	 * @return
	 */
	public default V whenChanged(Dependency... deps) {
		return dependOn(true, deps).build();
	}
	/**
	 * Like {@link #build()}, but also adds a {@link Dependency} and declares it to
	 * be {@link Pile#setDependencyEssential(boolean, Dependency) essential}
	 * @param deps
	 * @return
	 */
	public default V whenChanged(Dependency dep) {	
		return dependOn(true, dep).build();
	}
	
	/**
	 * Specify what to do to a value that was handed to the one of the fulfill methods,
	 * but was not accepted because the {@link Recomputation} had already become obsolete.
	 * @param handler
	 * @return
	 */
	Self onFailedFulfill(Consumer<? super E> handler);
	
	/**
	 * Enable dynamic dependency recording.
	 * NOTE: If you use dynamic dependency recording, respect the following rules for doing the recomputation:
	 * <ul>
	 * <li>Most important: Access identical {@link Dependency} instances under equal conditions. Reason: 
	 * If you access a {@link Dependency} that is not currently depended on, the {@link Recomputation}
	 * will switch to dependency scouting mode if it is not already, 
	 * and it will be repeated with the Dependency now active.
	 * If the identity of the dependency has changed, this will repeat until {@link StackOverflowError} occurs.
	 * </li>
	 * <li>Access all relevant dependencies before doing any significant computation. If you don't need their value,
	 * at least record the access using their {@link Dependency#recordRead()} 
	 * methods or {@link Recomputation#recordDependency(Dependency)}
	 * </li>
	 * <li>If and before significant computation is performed, check whether the {@link Recomputation} is in
	 * dependency scouting mode. If so, terminate it (use {@link Recomputation#terminateDependencyScout()})</li>
	 * <li>Do not start a delayed recomputation when the {@link Recomputation} is in dependency scouting mode. </li>
	 * <li>Take care to suspend dependency recording using {@link Piles#withCurrentRecomputation(Recomputation) } 
	 * when executing code that possibly accesses fields you do not want to depend on 
	 * (typically, this happens in the return value's constructor)<li>
	 * </ul>
	 * @return
	 * @see Recomputation#activateDynamicDependencies()
	 */
	Self dynamicDependencies();
	
	
	/**
	 * Enable dynamic dependency recording and call {@link #build()}
	 * @return The {@link Pile} that has been built
	 */
	public default V dd() {
		dynamicDependencies();
		return build();
	}

	/**
	 * Use the {@linkplain} StandardExecutors#limited() standard limited pool}
	 * for running non-immediate recomputations.
	 * @return {@code this}
	 */
	public default Self limitedPool() {
		return pool(StandardExecutors.limited());
	}
	/**
	 * Set the thread pool that should be used for non-immediate recomputations.
	 * Note: If the value being constructed has a positive {@link #delay(long) delay},
	 * the given {@link ExecutorService} must actually be a {@link ScheduledExecutorService}.
	 * If it is not, {@link #build()} will throw {@link ClassCastException}
	 * @param exec
	 * @return {@code this}
	 */
	public Self pool(ExecutorService exec);
	public Self scoutIfInvalid(Predicate<? super Dependency> p);
	public default Self scoutIfInvalid(Collection<? super Dependency> s) {
		return scoutIfInvalid(s::contains);
	}
	public default Self scoutIfInvalid(Dependency... ds) {
		if(ds==null || ds.length==0) {
			dontScoutIfInvalid();
		}
		HashSet<Dependency> s = new HashSet<>(ds.length);
		for(Dependency d: ds)
			s.add(d);
		return scoutIfInvalid(s::contains);
	}
	public default Self dontScoutIfInvalid() {
		return scoutIfInvalid(Functional.CONST_FALSE);
	}

	public abstract Self mayRemoveDynamicDependency(BiPredicate<? super Dependency, ? super Depender> crit);
	public default Self mayRemoveDynamicDependency(Predicate<? super Dependency> crit) {
		return mayRemoveDynamicDependency((d, _d)->crit.test(d));
	}
	public default Self mayRemoveDynamicDependency(Collection<? extends Dependency> crit) {
		return mayRemoveDynamicDependency(crit::contains);
	}
	public default Self mayRemoveDynamicDependency(Dependency... crit) {
		List<Dependency> ds = Arrays.asList(crit);
		return mayRemoveDynamicDependency(crit.length<5?ds:new HashSet<>(ds));
	}
	public default Self mayRemoveDynamicDependency(Dependency d) {
		return mayRemoveDynamicDependency(d2->d==d2);
	}
	public default Self mayRemoveDynamicDependency(boolean notNegated, BiPredicate<? super Dependency, ? super Depender> crit) {
		return mayRemoveDynamicDependency(notNegated?crit:crit.negate());
	}
	public default Self mayRemoveDynamicDependency(boolean notNegated, Predicate<? super Dependency> crit){
		return mayRemoveDynamicDependency((d, _d)->crit.test(d)==notNegated);
	}
	public default Self mayRemoveDynamicDependency(boolean notNegated, Collection<? extends Dependency> crit) {
		return mayRemoveDynamicDependency(notNegated, crit::contains);
	}
	public default Self mayRemoveDynamicDependency(boolean notNegated, Dependency... crit) {
		List<Dependency> ds = Arrays.asList(crit);
		return mayRemoveDynamicDependency(notNegated, crit.length<5?ds:new HashSet<>(ds));
	}
	public default Self mayRemoveDynamicDependency(boolean notNegated, Dependency d) {
		return mayRemoveDynamicDependency(notNegated?d2->d==d2:d2->d!=d2);
	}
	public default Self mayRemoveNonessentialDependencies() {
		return mayRemoveDynamicDependency((BiPredicate<? super Dependency, ? super Depender>)null);
	}
	public default Self mayNotRemoveDynamicDependency(BiPredicate<? super Dependency, ? super Depender> crit) {
		return mayRemoveDynamicDependency(crit.negate());
	}
	public default Self mayNotRemoveDynamicDependency(Predicate<? super Dependency> crit){
		return mayRemoveDynamicDependency((d, _d)->!crit.test(d));
	}
	public default Self mayNotRemoveDynamicDependency(Collection<? extends Dependency> crit) {
		return mayRemoveDynamicDependency(false, crit::contains);
	}
	public default Self mayNotRemoveDynamicDependency(Dependency... crit) {
		List<Dependency> ds = Arrays.asList(crit);
		return mayRemoveDynamicDependency(false, crit.length<5?ds:new HashSet<>(ds));
	}
	public default Self mayNotRemoveDynamicDependency(Dependency d) {
		return mayRemoveDynamicDependency(d2->d!=d2);
	}

	public default Self essential(Dependency d) {
		valueBeingBuilt().setDependencyEssential(true, d);
		return self();
	}
	public default Self essential(Dependency... d) {
		valueBeingBuilt().setDependencyEssential(true, d);
		return self();
	}
	/**
	 * Enable trace recording for the value being built. Requires {@link DebugEnabled#ET_TRACE}
	 * to be true in order to have any effect.
	 * <br>
	 * Note: This creates a memory leak and should only be in your code while debugging.
	 * @return
	 */
	public default Self tron() {
		if(DebugEnabled.ET_TRACE)
			DebugEnabled.trace.add(valueBeingBuilt());
		return self();
	}
	/**
	 * Set a supplier for booleans that, when it returns false,
	 * causes any recomputations to execute synchronously and without delay.  
	 * <br>
	 * If this method is called multiple times, the conjunction of the given conditions is used.
	 * @param delaySwitch
	 * @return
	 */
	public Self setDelaySwitch(BooleanSupplier delaySwitch);
	
	public default void inheritBrackets(boolean openNow, ReadValue<E> template) {
		if(template instanceof AbstractReadListenDependency) {
			AbstractReadListenDependency<E> cast = (AbstractReadListenDependency<E>) template;
			cast.bequeathBrackets(openNow, valueBeingBuilt());
		}
	}

}