package pile.aspect.bracket;

import java.util.function.Predicate;

import pile.utils.Functional;

public class FilteredBracket<E, O>
implements ValueBracket<E, O>
{
	private final ValueBracket<E, O> back;
	final public Predicate<? super E> openFilter;
	final public Predicate<? super E> closeFilter;
	
	final boolean nopOpen;
	final boolean nopClose;
	public FilteredBracket(ValueBracket<E, O> back, Predicate<? super E> openFilter, Predicate<? super E> closeFilter) {
		this.back = back;
		this.openFilter = openFilter;
		this.closeFilter = closeFilter;
        this.nopOpen = back.openIsNop();
        this.nopClose = back.closeIsNop();
	}
	public ValueBracket<E, O> getWrapped(){
		return back;
	}
	@Override
    public boolean close(E value, O owner) {
		if(nopClose)
			return false;
	    if(closeFilter!=null &&!openFilter.test(value))
	        return true;
        return back.close(value, owner);
    }
    @Override
    public boolean isInheritable() {
        return back.isInheritable();
    }
    @Override
    public boolean closeIsNop() {
        return nopClose;
    }
    @Override
    public boolean open(E value, O owner) {
        if(nopOpen)
            return true;
        if(openFilter!=null &&!openFilter.test(value))
            return true;
        return back.open(value, owner);
	}
	@Override
	public boolean openIsNop() {
		return nopOpen;
	}
	public static class ValueOnly<V> extends FilteredBracket<V, Object> implements ValueOnlyBracket<V>{
		public ValueOnly(ValueOnlyBracket<V> back, Predicate<? super V> openFilter, Predicate<? super V> closeFilter) {
            super(back, openFilter, closeFilter);
        }
		@Override
		public ValueOnlyBracket<V> filtersFirst() {
			ValueOnlyBracket<V> cback = getWrapped();
			ValueOnlyBracket<V> tback = cback.filtersFirst();
			if(tback==cback)
				return this;
			if(!(tback instanceof FilteredBracket.ValueOnly<?>)) {
				return new FilteredBracket.ValueOnly<>(tback, openFilter, closeFilter);
			}
			FilteredBracket.ValueOnly<V> cast = (FilteredBracket.ValueOnly<V>) tback;
			Predicate<? super V> combinedOpenFilter = openFilter == null? cast.openFilter : cast.openFilter==null? openFilter :
				Functional.<V>conjunction(openFilter, cast.openFilter);
			Predicate<? super V> combinedCloseFilter = closeFilter == null? cast.closeFilter : cast.closeFilter==null? closeFilter :
				openFilter==closeFilter && cast.openFilter==cast.closeFilter ? combinedOpenFilter : 
				Functional.<V>conjunction(closeFilter, cast.closeFilter);
			return new FilteredBracket.ValueOnly<>(tback, combinedOpenFilter, combinedCloseFilter);
		}
		@Override
		public ValueOnlyBracket<V> getWrapped() {
			return (ValueOnlyBracket<V>) super.getWrapped();
		}
		
		public ValueOnlyBracket<V> sameFilters(ValueOnlyBracket<V> newBack) {
			return new FilteredBracket.ValueOnly<>(newBack, openFilter, closeFilter);
		}
	}
	@Override
	public ValueBracket<E, O> filtersFirst() {
		ValueBracket<E, O> tback = back.filtersFirst();
		if(tback==back)
			return this;
		if(!(tback instanceof FilteredBracket<?, ?>)) {
			return new FilteredBracket<>(tback, openFilter, closeFilter);
		}
		FilteredBracket<E, O> cast = (FilteredBracket<E, O>) tback;
		Predicate<? super E> combinedOpenFilter = openFilter == null? cast.openFilter : cast.openFilter==null? openFilter :
			Functional.<E>conjunction(openFilter, cast.openFilter);
		Predicate<? super E> combinedCloseFilter = closeFilter == null? cast.closeFilter : cast.closeFilter==null? closeFilter :
			openFilter==closeFilter && cast.openFilter==cast.closeFilter ? combinedOpenFilter : 
			Functional.<E>conjunction(closeFilter, cast.closeFilter);
		return new FilteredBracket<>(tback, combinedOpenFilter, combinedCloseFilter);
	}
    @Override
    public boolean canBecomeObsolete() {
    	return back.canBecomeObsolete();
    }
	public ValueBracket<E, O> sameFilters(ValueBracket<E, O> newBack) {
		return new FilteredBracket<>(newBack, openFilter, closeFilter);
	}
}
