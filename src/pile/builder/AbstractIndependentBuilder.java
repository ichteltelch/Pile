package pile.builder;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.function.BiPredicate;
import java.util.function.Consumer;
import java.util.function.Function;

import pile.aspect.LastValueRememberer;
import pile.aspect.ValueBracket;
import pile.aspect.VetoException;
import pile.aspect.combinations.ReadListenDependency;
import pile.aspect.combinations.ReadListenValue;
import pile.aspect.combinations.ReadWriteListenValue;
import pile.aspect.listen.ValueListener;
import pile.impl.DebugCallback;
import pile.impl.Independent;
import pile.relation.CoupleEqual;
/**
 * Abstract implementation of {@link IIndependentBuilder}  
 * @author bb
 *
 * @param <Self> The concrete implementing class. Because of this type parameter, 
 * the {@link AbstractIndependentBuilder} class needs to abstract
 * @param <V>
 * @param <E>
 */
public abstract class AbstractIndependentBuilder<Self extends AbstractIndependentBuilder<Self, V, E>, V extends Independent<E>, E>
implements IIndependentBuilder<Self, V, E>{
	E init;
	boolean initSet;
	V value;
	/**
	 * @param value The value that this builder should act on, which must not already be sealed
	 */
	public AbstractIndependentBuilder(V value) {
		if(value.isSealed())
			throw new IllegalArgumentException("The builder may only work on an unsealed value");
		this.value=value;
	}
	@Override
	public V build() {
		V value = this.value;
		
		ICorrigibleBuilder.applyBounds(value, lowerBounds, upperBounds, ordering);
		ReadListenDependency<? extends E> ub = ICorrigibleBuilder.getUpperBound(value);
		ReadListenDependency<? extends E> lb = ICorrigibleBuilder.getLowerBound(value);
		if(dob && (ub!=null || lb!=null)) {
			ValueListener vl;
			if(value.isSealed()) {
				if(value.isDefaultSealed())
					vl=null;
				vl = e->value.set(value.get());
			}else {
				Consumer<? super E> setter = value.makeSetter();
				vl = e->setter.accept(value.get());
			}
			if(vl!=null){
				if(ub!=null)
					ub.addWeakValueListener(vl);
				if(lb!=null)
					lb.addWeakValueListener(vl);
			}
		}

		if(remember!=null) {
			LastValueRememberer<E> remember = this.remember;
			value.set(remember.recallLastValue());
			value.putAssociation(LastValueRememberer.key(), remember);
			value.addValueListener(e->{
				if(value.remembersLastValue())
					remember.storeLastValue(value.get());
			});
		}
		if(initSet) {
			value.set(init);
		}
		if(sealOnBuild)
			value.seal(interceptor, false);
		return value;
	}
	@Override
	public Self init(E initValue) {
		init=initValue;
		initSet=true;
		return self();
	}
	@Override
	public Self initNow(E initValue) {
		value.set(initValue);
		return self();
	}
	public Self name(String name) {
		value.setName(name);
		return self();
	}

	ArrayList<ReadListenDependency<? extends E>> upperBounds;
	ArrayList<ReadListenDependency<? extends E>> lowerBounds;
	Comparator<? super E> ordering;
	LastValueRememberer<E> remember;
	boolean sealOnBuild;
	Consumer<? super E> interceptor;

	@Override public Comparator<? super E> getOrdering() {return ordering;}
	@Override
	public Self upperBound(ReadListenDependency<? extends E> bound) {
		if(upperBounds==null)
			upperBounds=new ArrayList<>(1);
		upperBounds.add(bound);
		return self();
	}
	@Override
	public Self lowerBound(ReadListenDependency<? extends E> bound) {
		if(lowerBounds==null)
			lowerBounds=new ArrayList<>(1);
		lowerBounds.add(bound);
		return self();
	}
	@Override
	public Self orderingRaw(Comparator<? super E> comp) {
		ordering=comp;
		return self();
	}
	@Override
	public Self ordering(Comparator<? super E> comp) {
		ordering=(v1, v2)->{
			if(v1==null || v2==null) {
				if(v1==v2)
					return 0;
				throw new VetoException("One of the values is null");
			}
			return comp.compare(v1, v2);
		};
		return self();
	}
	@Override
	public Self corrector(Function<? super E, ? extends E> corr) {
		value._addCorrector(corr);
		return self();
	}
	@Override
	public Self fromStore(LastValueRememberer<E> remember, boolean correctNulls) {
		this.remember=remember;
		if(correctNulls){
			corrector(v->v==null?remember.recallLastValue():v);
		}
		return self();
	}
	@Override
	public Self seal() {
		sealOnBuild=true;
		return self();
	}
	@Override
	public Self seal(Consumer<? super E> interceptor) {
		this.interceptor=interceptor;
		sealOnBuild=true;
		return self();
	}
	@Override
	public Self seal(Consumer<? super E> interceptor, boolean allowInvalidation) {
		this.interceptor=interceptor;
		sealOnBuild=true;
		return self();
	}
	@Override
	public Consumer<? super E> makeSetter() {
		return value.makeSetter();
	}
	@Override
	public Self onChange(ValueListener l) {
		value.addValueListener(l);
		return self();
	}

	public Self debug(DebugCallback dc) {
		value.dc=dc;
		return self();
	}
	public V valueBeingBuilt() {
		return value;
	}
	@Override
	public Self equalFrom(ReadWriteListenValue<E> partner) {
		value.keepStrong(new CoupleEqual<>(value, partner, null));
		return self();
	}
	@Override
	public Self equalTo(ReadWriteListenValue<E> partner) {
		value.keepStrong(new CoupleEqual<>(partner, value, null));
		return self();
	}
	@Override
	public Self follow(ReadListenValue<? extends E> leader) {
		value.follow(leader, false);
		return self();
	}
	@Override
	public Self bracket(boolean openNow, ValueBracket<? super E, ? super ReadListenDependency<? extends E>> bracket) {
		value._addValueBracket(openNow, bracket);
		return self();
	}
	@Override
	public Self anyBracket(boolean openNow, ValueBracket<? super E, ? super ReadListenDependency<? extends E>> bracket) {
		value._addAnyValueBracket(openNow, bracket);
		return self();
	}

	@Override
	public Self oldValueBracket(boolean openNow, ValueBracket<? super E, ? super ReadListenDependency<? extends E>> bracket) {
		value._addOldValueBracket(openNow, bracket);
		return self();
	}
	@Override
	public Self parent(Object p) {
		value.owner=p;
		return self();
	}
	@Override
	public Self equivalence(BiPredicate<? super E, ? super E> equiv) {
		value._setEquivalence(equiv);
		return self();
	}
	private boolean dob=true;
	@Override
	public Self dontDependOnBounds() {
		dob=false;
		return self();
	}

	
}
	
	

