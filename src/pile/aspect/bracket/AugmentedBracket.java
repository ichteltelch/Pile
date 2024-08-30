package pile.aspect.bracket;

import java.util.function.BiConsumer;
import java.util.function.Consumer;

public class AugmentedBracket<E, O> implements ValueBracket<E, O> {

	BiConsumer<? super E, ? super O> preOpen;
	BiConsumer<? super E, ? super O> postClose;
	ValueBracket<E, O> back;
	
    public AugmentedBracket(BiConsumer<? super E,? super O> preOpen, BiConsumer<? super E,? super O> postClose, ValueBracket<E, O> back) {
    	this.preOpen = preOpen;
        this.postClose = postClose;
        this.back = back;
    }
    @Override
    public boolean close(E value, O owner) {
        boolean r = back.close(value, owner);
        if(postClose!=null)
        	postClose.accept(value, owner);
		return r;
    }
    @Override
    public boolean open(E value, O owner) {
    	if(preOpen!=null)
    		preOpen.accept(value, owner);
        return back.open(value, owner);
    }
    @Override
    public boolean isInheritable() {
        return back.isInheritable();
    }
    @Override
    public boolean openIsNop() {
        return back.openIsNop() && preOpen == null;
    }
    @Override
    public boolean closeIsNop() {
        return back.closeIsNop() && postClose == null;
    }
    @Override
    public boolean canBecomeObsolete() {
        return back.canBecomeObsolete();
    }
    @Override
    public ValueBracket<E, O> filtersFirst() {
    	ValueBracket<E, O> tback = back.filtersFirst();
    	if(tback==back)
    		return this;
        if(!(tback instanceof FilteredBracket<?, ?>))
        	return new AugmentedBracket<>(preOpen, postClose, tback);
        FilteredBracket<E, O> cast = (FilteredBracket<E, O>)tback;
        return cast.sameFilters(new AugmentedBracket<E, O>(preOpen, postClose, cast.getWrapped()));
    }
	public static class ValueOnly<V> extends AugmentedBracket<V, Object> implements ValueOnlyBracket<V> {

	

	    public ValueOnly(BiConsumer<? super V, ? super Object> preOpen, BiConsumer<? super V, ? super Object> postClose,
				ValueOnlyBracket<V> back) {
			super(preOpen, postClose, back);
		}
		public ValueOnly(Consumer<? super V> preOpen, Consumer<? super V> postClose,
				ValueOnlyBracket<V> back) {
			super(preOpen==null?null:(v, o)->preOpen.accept(v), postClose==null?null:(v, o)->postClose.accept(v), back);
		}
		@Override
	    public ValueOnlyBracket<V> filtersFirst() {
	    	ValueOnlyBracket<V> tback = ((ValueOnlyBracket<V>) back).filtersFirst();
	    	if(tback==back)
	    		return this;
	        if(!(tback instanceof FilteredBracket.ValueOnly<?>))
	        	return new AugmentedBracket.ValueOnly<>(preOpen, postClose, tback);
	        FilteredBracket.ValueOnly<V> cast = (FilteredBracket.ValueOnly<V>)tback;
	        return cast.sameFilters(new AugmentedBracket.ValueOnly<V>(preOpen, postClose, cast.getWrapped()));
	    }
	}
}
