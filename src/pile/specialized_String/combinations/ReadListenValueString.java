package pile.specialized_String.combinations;

import pile.builder.IndependentBuilder;
import pile.builder.SealPileBuilder;
import pile.specialized_Comparable.combinations.ReadListenValueComparable;
import pile.specialized_String.IndependentString;
import pile.specialized_String.PileString;
import pile.specialized_String.SealString;

public interface ReadListenValueString extends ReadValueString, ReadListenValueComparable<String>{


	@Override public default IndependentString validBuffer_memo(){return readOnlyValidBuffer_memo();}
	@Override public default IndependentString readOnlyValidBuffer_memo(){return (IndependentString) READ_ONLY_VALID_BUFFER_CACHE.apply(this);}

	
	@Override public default IndependentString validBuffer(){return readOnlyValidBuffer();}
	@Override public default IndependentBuilder<IndependentString, String> validBufferBuilder() {return readOnlyValidBufferBuilder();}
	@Override public default IndependentString readOnlyValidBuffer(){return readOnlyValidBufferBuilder().build();}
	@Override public default IndependentBuilder<IndependentString, String> readOnlyValidBufferBuilder() {return PileString	.ib().setupValidBuffer(this);}

	
	@Override public default SealString buffer(){return readOnlyBuffer();}
	@Override default SealPileBuilder<? extends SealString, String> bufferBuilder() {return readOnlyBufferBuilder();}
	@Override public default SealString readOnlyBuffer(){return readOnlyBufferBuilder().build();}
	@Override public default SealPileBuilder<SealString, String> readOnlyBufferBuilder() {return PileString.sb().setupBuffer(this);}
	
	@Override public default SealString rateLimited(long coldStartTime, long coolDownTime){return readOnlyRateLimited(coldStartTime, coolDownTime);}
	@Override public default SealPileBuilder<SealString, String> rateLimitedBuilder(long coldStartTime, long coolDownTime) {return readOnlyRateLimitedBuilder(coldStartTime, coolDownTime);}
	@Override public default SealString readOnlyRateLimited(long coldStartTime, long coolDownTime){return readOnlyRateLimitedBuilder(coldStartTime, coolDownTime).build();}
	@Override public default SealPileBuilder<SealString, String> readOnlyRateLimitedBuilder(long coldStartTime, long coolDownTime) {return PileString.sb().setupRateLimited(this, coldStartTime, coolDownTime);}

}
