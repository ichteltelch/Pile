package pile.aspect.bracket;

import pile.aspect.suppress.MockBlock;
import pile.utils.Nonreentrant;

public class NonreentrantBracket<E, O>
implements ValueBracket<E, O>{
	final Nonreentrant nr;
	final ValueBracket<E, O> back;
	final boolean nopOpen;
	final boolean nopClose;
	
    public NonreentrantBracket(Nonreentrant nr, ValueBracket<E, O> back) {
    	this.nr = nr;
        this.back = back;
        this.nopOpen = back.openIsNop();
        this.nopClose = back.closeIsNop();
    }
    @Override
    public boolean close(E value, O owner) {
    	if(nopClose)
    		return false;
    	try(MockBlock mb = nr.block_noThrow()){
    		if(mb!=MockBlock.NOP) {
    			return back.close(value, owner);
    		}
    	}
    	return false;
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
            return false;
        try(MockBlock mb = nr.block_noThrow()){
            if(mb!=MockBlock.NOP) {
                return back.open(value, owner);
            }
        }
        return true;
    }
    @Override
    public boolean openIsNop() {
        return nopOpen;
    }
    public static class ValueOnly<V> extends NonreentrantBracket<V, Object> implements ValueOnlyBracket<V>{

		public ValueOnly(Nonreentrant nr, ValueOnlyBracket<V> back) {
			super(nr, back);
		}

	    @Override
	    public ValueOnlyBracket<V> filtersFirst() {
	    	ValueOnlyBracket<V> tback = ((ValueOnlyBracket<V>)back).filtersFirst();
	    	if(tback==back)
	    		return this;
	        if(!(tback instanceof FilteredBracket.ValueOnly<?>)) {
	        	return new NonreentrantBracket.ValueOnly<>(nr, tback);
	        }
	        FilteredBracket.ValueOnly<V> cast = (FilteredBracket.ValueOnly<V>) tback;
	        return new FilteredBracket.ValueOnly<V>(new NonreentrantBracket.ValueOnly<V>(nr, cast.getWrapped()), cast.openFilter, cast.closeFilter);
	    }

		
    }
    @Override
    public ValueBracket<E, O> filtersFirst() {
    	ValueBracket<E, O> tback = back.filtersFirst();
    	if(tback==back)
    		return this;
        if(!(tback instanceof FilteredBracket<?,?>)) {
        	return new NonreentrantBracket<E, O>(nr, tback);
        }
        FilteredBracket<E, O> cast = (FilteredBracket<E, O>) tback;
        return new FilteredBracket<E, O>(new NonreentrantBracket<>(nr, cast.getWrapped()), cast.openFilter, cast.closeFilter);
    }

    @Override
    public boolean canBecomeObsolete() {
    	return back.canBecomeObsolete();
    }
}
