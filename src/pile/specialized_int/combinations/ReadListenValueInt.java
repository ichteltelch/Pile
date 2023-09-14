package pile.specialized_int.combinations;

import pile.builder.IndependentBuilder;
import pile.builder.SealPileBuilder;
import pile.specialized_Comparable.combinations.ReadListenValueComparable;
import pile.specialized_int.IndependentInt;
import pile.specialized_int.PileInt;
import pile.specialized_int.SealInt;

public interface ReadListenValueInt extends ReadValueInt, ReadListenValueComparable<Integer>{

	@Override public default IndependentInt validBuffer_memo(){return readOnlyValidBuffer_memo();}
	@Override public default IndependentInt readOnlyValidBuffer_memo(){return (IndependentInt) READ_ONLY_VALID_BUFFER_CACHE.apply(this);}
	
	@Override public default IndependentInt validBuffer(){return readOnlyValidBuffer();}
	@Override public default IndependentBuilder<IndependentInt, Integer> validBufferBuilder() {return readOnlyValidBufferBuilder();}
	@Override public default IndependentInt readOnlyValidBuffer(){return readOnlyValidBufferBuilder().build();}
	@Override public default IndependentBuilder<IndependentInt, Integer> readOnlyValidBufferBuilder() {return PileInt.ib().setupValidBuffer(this);}

	
	
	@Override public default SealInt buffer(){return readOnlyBuffer();}
	@Override public default SealPileBuilder<SealInt, Integer> bufferBuilder() {return readOnlyBufferBuilder();}
	@Override public default SealInt readOnlyBuffer(){return readOnlyBufferBuilder().build();}
	@Override public default SealPileBuilder<SealInt, Integer> readOnlyBufferBuilder() {return PileInt.sb().setupBuffer(this);}
	
	@Override public default SealInt rateLimited(long coldStartTime, long coolDownTime){return readOnlyRateLimited(coldStartTime, coolDownTime);}
	@Override public default SealPileBuilder<SealInt, Integer> rateLimitedBuilder(long coldStartTime, long coolDownTime) {return readOnlyRateLimitedBuilder(coldStartTime, coolDownTime);}
	@Override public default SealInt readOnlyRateLimited(long coldStartTime, long coolDownTime){return readOnlyRateLimitedBuilder(coldStartTime, coolDownTime).build();}
	@Override public default SealPileBuilder<SealInt, Integer> readOnlyRateLimitedBuilder(long coldStartTime, long coolDownTime) {return PileInt.sb().setupRateLimited(this, coldStartTime, coolDownTime);}

}
