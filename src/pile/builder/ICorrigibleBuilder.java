package pile.builder;

import java.util.Comparator;
import java.util.List;
import java.util.function.BiPredicate;
import java.util.function.Function;

import pile.aspect.CorrigibleValue;
import pile.aspect.HasAssociations;
import pile.aspect.VetoException;
import pile.aspect.HasAssociations.AssociationKey;
import pile.aspect.ReadValue.InvalidValueException;
import pile.aspect.combinations.ReadListenDependency;
import pile.impl.Constant;
import pile.impl.Piles;

/**
 * Common superinterface for builders of {@link CorrigibleValue}s.
 * This is mainly to have a common interface for putting bounds on the value range.
 * If you define multiple bounds, they should all be used.
 * @author bb
 *
 * @param <Self> Implementing class
 * @param <V> concrete subtype of the {@link CorrigibleValue} being build
 * @param <E> Type of the value's content
 */
public interface ICorrigibleBuilder<Self extends ICorrigibleBuilder<Self, V, E>, V extends CorrigibleValue<E>, E> 
extends IBuilder<Self, V>
{
	
	/**
	 * Define a constant upper bound.
	 * @param bound
	 * @return {@code this} builder
	 */
	default Self upperBound(E bound) {return upperBound(new Constant<>(bound));}
	/**
	 * Define a constant lower bound.
	 * @param bound
	 * @return {@code this} builder
	 */
	default Self lowerBound(E bound) {return lowerBound(new Constant<>(bound));}
	/**
	 * Define a variable upper bound.
	 * @param bound
	 * @return {@code this} builder
	 */
	Self upperBound(ReadListenDependency<? extends E> bound);
	/**
	 * Define a variable lower bound.
	 * @param bound
	 * @return {@code this} builder
	 */
	Self lowerBound(ReadListenDependency<? extends E> bound);
	/**
	 * Combination of {@link #upperBound(Object)} and {@link #lowerBound(Object)}
	 * @param lower
	 * @param upper
	 * @return {@code this} builder
	 */
	default Self bounds(E lower, E upper) {return lowerBound(lower).upperBound(upper);}
	/**
	 * Combination of {@link #upperBound(Object)} and {@link #lowerBound(Object)}.
	 * Which bound is upper nd which is lower is determined by automatically based
	 * on the ordering that must have been defined previously using {@link #ordering(Comparator)}.
	 * @param bound1
	 * @param bound2
	 * @return {@code this} builder
	 */
	default Self unorderedBounds(E bound1, E bound2) {
		E lower, upper;
		if(getOrdering().compare(bound1, bound2)<=0) {
			lower = bound1;
			upper = bound2;
		}else {
			lower = bound2;
			upper = bound1;
		}
			
		return lowerBound(lower).upperBound(upper);
	}
	/**
	 * Get the ordering that has been defined for the value range.
	 * @return
	 */
	Comparator<? super E> getOrdering();
	/**
	 * Combination of {@link #lowerBound(ReadListenDependency)} and {@link #upperBound(Object)}
	 * @param lower
	 * @param upper
	 * @return {@code this} builder
	 */
	default Self bounds(ReadListenDependency<? extends E> lower, E upper) {return lowerBound(lower).upperBound(upper);}
	/**
	 * Combination of {@link #lowerBound(Object)} and {@link #upperBound(ReadListenDependency)}
	 * @param lower
	 * @param upper
	 * @return {@code this} builder
	 */
	default Self bounds(E lower, ReadListenDependency<? extends E> upper) {return lowerBound(lower).upperBound(upper);}
	/**
	 * Combination of {@link #lowerBound(ReadListenDependency)} and {@link #upperBound(ReadListenDependency)}
	 * @param lower
	 * @param upper
	 * @return {@code this} builder
	 */
	default Self bounds(ReadListenDependency<? extends E> lower, ReadListenDependency<? extends E> upper) {return lowerBound(lower).upperBound(upper);}
	/**
	 * Add a corrector to the value being build. The corrector is added immediately and will influence future
	 * attempts to change the value.
	 * @param corr
	 * @return {@code this} builder
	 */
	Self corrector(Function<? super E, ? extends E> corr);
	/**
	 * Define the ordering relation that should be used to compare the value with its bounds when applying the correction.
	 * This overwrites previous calls to {@link #ordering(Comparator)} or {@link #orderingRaw(Comparator)}.
	 * If either the value or the bound is <code>null</code>, the correction code will throw a {@link VetoException}
	 * @param comp
	 * @return {@code this} builder
	 */
	Self ordering(Comparator<? super E> comp);
	/**
	 * Define the ordering relation that should be used to compare the value with its bounds when applying the correction.
	 * This overwrites previous calls to {@link #ordering(Comparator)} or {@link #orderingRaw(Comparator)}.
	 * The given {@link Comparator} must handle <code>null</code> values by itself.
	 * @param comp
	 * @return {@code this} builder
	 */
	Self orderingRaw(Comparator<? super E> comp);

	/**
	 * Add a corrector that throws a {@link VetoException} if an attempt is 
	 * made to set the value to <code>null</code>
	 * @return {@code this} builder
	 */
	default Self neverNull() {
		return corrector(v->{
			if(v==null)
				throw new VetoException("This value may not be set to null!");
			return v;
		});
	}
	/**
	 * @return The object that is being built by this builder, without building it
	 */
	public V valueBeingBuilt();
	/**
	 * Define the equivalence relation that is used to decide whether the value has changed
	 * @param equiv
	 * @return {@code this} builder
	 */
	public Self equivalence(BiPredicate<? super E, ? super E> equiv);


	/**
	 * Compile the bounds, put them as association in the {@link CorrigibleValue},
	 * and install correction code on the {@link CorrigibleValue} that ensures
	 * the bounds are respected.
	 * @param <E>
	 * @param <V>
	 * @param val
	 * @param lowerBounds
	 * @param upperBounds
	 * @param ordering
	 */
	public static <E, V extends HasAssociations & CorrigibleValue<E> > void 
	applyBounds(
			CorrigibleValue<E> val,
			List<? extends ReadListenDependency<? extends E> > lowerBounds,
					List<? extends ReadListenDependency<? extends E> > upperBounds,
							Comparator<? super E> ordering
			) {
		ReadListenDependency<? extends E> upperBound;
		ReadListenDependency<? extends E> lowerBound;
		if(upperBounds!=null && !upperBounds.isEmpty()) {
			if(ordering==null)
				throw new IllegalStateException("Cannot build Value with bounds: no ordering relation has been defined");
			if(upperBounds.size()==1)
				upperBound=upperBounds.get(0);
			else
				upperBound=Piles.aggregate(Piles.minAggregation(null, ordering), upperBounds);
			val._addCorrector(value->{
				E bound;
				try {
					bound = upperBound.getValidOrThrow();
				}catch (InvalidValueException e) {
					throw new VetoException("applyCorrection veto: upper bound not valid", e);
				}
				if(ordering.compare(value, bound)>0)
					return bound;
				return value;
			});
			putUpperBound((HasAssociations)val, upperBound);
		}else {
			upperBound=null;
		}
		if(lowerBounds!=null && !lowerBounds.isEmpty()) {
			if(ordering==null)
				throw new IllegalStateException("Cannot build Value with bounds: no ordering relation has been defined");
			if(lowerBounds.size()==1)
				lowerBound=lowerBounds.get(0);
			else
				lowerBound=Piles.aggregate(Piles.maxAggregation(null, ordering), lowerBounds);
			val._addCorrector(value->{
				E bound;
				try {
					bound = lowerBound.getValidOrThrow();
				}catch (InvalidValueException e) {
					throw new VetoException("applyCorrection veto: lower bound not valid", e);
				}
				if(ordering.compare(value, bound)<0)
					return bound;
				return value;
			});
			putLowerBound((HasAssociations)val, lowerBound);
		}else {
			lowerBound=null;
		}
		if(upperBound!=null && lowerBound!=null) {
			val._addCorrector(ignore->{
				try {
					E lower = lowerBound.getValidOrThrow();
					E upper = upperBound.getValidOrThrow();
					if(ordering.compare(lower, upper)>0)
						throw new VetoException("applyCorrection veto: lower bound is greater than upper bound");
				}catch (InvalidValueException e) {
					throw new VetoException("applyCorrection veto: some bound not valid", e);
				}
				return ignore;
			});
		}
	}
	/**
	 * But the upper bound association.
	 * Note: this will not changes the correctors, listeners, and/or dependencies needed to maintain the bound
	 * @param <E>
	 * @param v
	 * @param bound
	 */
	public
	//protected 
	static <E> void putUpperBound(HasAssociations v, ReadListenDependency<? extends E> bound){
		@SuppressWarnings("unchecked")
		AssociationKey<ReadListenDependency<? extends E>> key = (AssociationKey<ReadListenDependency<? extends E>>) AbstractPileBuilder.upperBound;
		v.putAssociation(key, bound);
	}
	/**
	 * But the lower bound association.
	 * Note: this will not changes the correctors, listeners, and/or dependencies needed to maintain the bound
	 * @param <E>
	 * @param v
	 * @param bound
	 */
	public
	//protected 
	static <E> void putLowerBound(HasAssociations v, ReadListenDependency<? extends E> bound){
		@SuppressWarnings("unchecked")
		AssociationKey<ReadListenDependency<? extends E>> key = (AssociationKey<ReadListenDependency<? extends E>>) AbstractPileBuilder.lowerBound;
		v.putAssociation(key, bound);
	}
	/**
	 * Query the upper bound association of a value. May return null if there is no bound.
	 */
	public static <E> ReadListenDependency<? extends E> getUpperBound(HasAssociations v){
		@SuppressWarnings("unchecked")
		AssociationKey<ReadListenDependency<? extends E>> key = (AssociationKey<ReadListenDependency<? extends E>>) AbstractPileBuilder.upperBound;
		return v.getAssociation(key);
	}
	/**
	 * Query the lower bound association of a value. May return null if there is no bound.
	 */
	public static <E> ReadListenDependency<? extends E> getLowerBound(HasAssociations v){
		@SuppressWarnings("unchecked")
		AssociationKey<ReadListenDependency<? extends E>> key = (AssociationKey<ReadListenDependency<? extends E>>) AbstractPileBuilder.lowerBound;
		return v.getAssociation(key);
	}

}
