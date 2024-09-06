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

	public final ReadListenDependency<? extends Set<? extends ReadWriteListenValue<T>>>
	sources = new SealPileBuilder<>(new SealPile<Set<? extends ReadWriteListenValue<T>>>())
	.name("sources").parent(this)
	.setupDeref(sourcesIndir)
	.bracket(ValueBracket.<Set<? extends ReadListenValue<T>>>make(false, set->{
		for(ListenValue l: set)
			l.addWeakValueListener(sourceListener);
		sourceListener.valueChanged(null);
	}, set->{
		for(ListenValue l: set)
			l.removeWeakValueListener(sourceListener);
	}).defer(ListenValue.DEFER).nopOnNull())
	.build().validBuffer();


	public final ReadWriteListenDependency<T> target;
	Supplier<? extends T> neutral;
	BiFunction<? super T, ? super T, ? extends T> integrate;

	public final ReadListenDependencyBool monotonous;

	public MultiSourceIntegrator(ReadWriteListenDependency<T> target, Supplier<? extends T> neutral, BiFunction<? super T,? super T, ? extends T> integrate) {
		this.target = target;
		this.neutral = neutral;
		this.integrate = integrate;
		monotonous = sources.mapToBool(s->s.contains(target)).validBuffer();
		target.addWeakValueListener(targetListener);
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
		synchronized (this) {

			try {
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
				target.set(accu);
			} catch (InterruptedException e1) {
				Thread.currentThread().interrupt();
				return;
			}
		}	
	}


}
