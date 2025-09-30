package pile.relation;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Supplier;

import pile.aspect.ReadValue;
import pile.aspect.ReadValue.InvalidValueException;
import pile.aspect.WriteValue;
import pile.aspect.bracket.ValueBracket;
import pile.aspect.combinations.ReadDependency;
import pile.aspect.combinations.ReadListenDependency;
import pile.aspect.combinations.ReadListenValue;
import pile.aspect.combinations.ReadWriteListenDependency;
import pile.aspect.combinations.ReadWriteListenValue;
import pile.aspect.listen.ListenValue;
import pile.aspect.listen.RateLimitedValueListener.MultiEvent;
import pile.aspect.listen.ValueEvent;
import pile.aspect.listen.ValueListener;
import pile.builder.SealPileBuilder;
import pile.impl.Constant;
import pile.impl.Independent;
import pile.impl.Piles;
import pile.impl.SealPile;
import pile.specialized_bool.IndependentBool;
import pile.specialized_bool.combinations.ReadListenDependencyBool;

public class MultiSourceIntegrator<T> {
	static final Constant<?> CONST_EMPTY_SET = Piles.constant(Collections.EMPTY_SET);
	@SuppressWarnings("unchecked")
	static <T> Constant<Set<T>> constEmptySet(){
		return (Constant<Set<T>>)CONST_EMPTY_SET;
	}

	private final ValueListener sourceListener = ValueListener.rateLimited(10, 100, this::sourceChanged);
	private final ValueListener targetListener = this::targetChanged;



	public final 
	Independent<ReadDependency<? extends Set<? extends ReadWriteListenValue<T>>>>
	sourcesIndir=Piles.<ReadDependency<? extends Set<? extends ReadWriteListenValue<T>>>>independent(MultiSourceIntegrator.<ReadWriteListenValue<T>>constEmptySet())
	.name("sourcesIndir").parent(this)
	.build();

	IndependentBool monotonous__ = Piles
			.independent(false)
			.name("monotonous__")
			.parent(this)
			.build();
	public final ReadListenDependencyBool monotonous = monotonous__.readOnly();

	public final ReadWriteListenDependency<T> target;

	public final ReadListenDependency<? extends Set<? extends ReadWriteListenValue<T>>>
	sources = new SealPileBuilder<>(new SealPile<Set<? extends ReadWriteListenValue<T>>>())
	.name("sources").parent(this)
	.setupDeref(sourcesIndir)
	.bracket(ValueBracket.<Set<? extends ReadListenValue<T>>>make(false, set->{
		for(ListenValue l: set)
			l.addWeakValueListener(sourceListener);
		Object target = target();
		if(target!=null) {
			monotonous__.set(set.contains(target));
			monotonous.doOnceWhenValid(x->sourceListener.runImmediately());
			//Recomputations.NOT_NOW.run(sourceListener::runImmediately);
		}
	}, set->{
		for(ListenValue l: set)
			l.removeWeakValueListener(sourceListener);
	})
			.defer(ListenValue.DEFER)
			
			.nopOnNull())
	.build();


	Supplier<? extends T> neutral;
	BiFunction<? super T, ? super T, ? extends T> integrate;


	public MultiSourceIntegrator(ReadWriteListenDependency<T> target, Supplier<? extends T> neutral, BiFunction<? super T,? super T, ? extends T> integrate) {
		this.target = target;
		this.neutral = neutral;
		this.integrate = integrate;
		target.addWeakValueListener(targetListener);
	}
	private Object target() {
		return target;
	}
	private void targetChanged(ValueEvent e) {
		if(target.isValid()) {
			try {
				synchronized (this) {
					Set<? extends WriteValue<T>> sources = this.sources.get();
					if(sources != null &&!sources.isEmpty()) {
						T val = target.getValidOrThrow();
						for(WriteValue<T> source: sources) {
							if(source!=target)
								source.set(val);
						}
					}
				}	
			} catch (InvalidValueException e1) {
			}
		}
	}

	@SuppressWarnings("unchecked")
	void sourceChanged(MultiEvent e) {
		try {

			ListenValue.DEFER.__incrementSuppressors();
			synchronized (this) {
				T old, accu;
				boolean oldValid, accuValid;
				Set<? extends ReadValue<T>> sources;

				if(monotonous.get()) {
					if(e.allSources()) {
						sources = this.sources.get();
						if(sources==null || sources.isEmpty()) {

							return;
						}
					}
					else
						sources = (Set<? extends ReadValue<T>>) e.getSources();
					old = target.getValid(10);
					accu = old;
					oldValid = target.isValid();
					accuValid = oldValid;
				}else {
					old = null;
					accu = null;
					oldValid = false;
					accuValid = false;
					sources = this.sources.get();
					if(sources==null) {
						target.set(neutral.get());
						return;
					}
				}
				IdentityHashMap<T, T> distinct = new IdentityHashMap<>();
				if(oldValid)
					distinct.put(old, old);
				for(ReadValue<T> source: sources) {
					T val = source.getValid(1000);
					if(source.isValid())
						distinct.put(val, val);
				}
				if(distinct.size()<=1)
					return;

				for(T source: distinct.keySet()) {
					if(oldValid && source==old)
						continue;
					if(accuValid)
						accu = integrate.apply(accu, source);
					else {
						accu = source;
					}
				}
				if(!accuValid)
					accu = neutral.get();
				T actuallySet = target.set(accu);

				//If the set of sources has changed,
				//we call the listener in any case to distribute the current
				//value any new sources.
				if(e.allSources())
					//If the value has not changed, target will not have fired an event, so we need to do it now
					//because new sources may be out of sync
					if(!oldValid || old==actuallySet)
						targetChanged(null);
			
			}	
		} catch (InterruptedException e1) {
			Thread.currentThread().interrupt();
			return;
	    }finally {
			ListenValue.DEFER.__decrementSuppressors();
		}
	}


}
