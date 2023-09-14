package pile.specialized_bool.combinations;

import pile.aspect.combinations.ReadListenValue;
import pile.builder.IndependentBuilder;
import pile.builder.SealPileBuilder;
import pile.specialized_bool.IndependentBool;
import pile.specialized_bool.PileBool;
import pile.specialized_bool.SealBool;

public interface ReadListenValueBool extends ReadValueBool, ReadListenValue<Boolean>{
	

	


	@Override public default IndependentBool validBuffer_memo(){return readOnlyValidBuffer_memo();}
	@Override public default IndependentBool readOnlyValidBuffer_memo(){return (IndependentBool) READ_ONLY_VALID_BUFFER_CACHE.apply(this);}

	
	@Override public default IndependentBool validBuffer(){return readOnlyValidBuffer();}
	@Override public default IndependentBuilder<IndependentBool, Boolean> validBufferBuilder() {return readOnlyValidBufferBuilder();}
	@Override public default IndependentBool readOnlyValidBuffer(){return readOnlyValidBufferBuilder().build();}
	@Override public default IndependentBuilder<IndependentBool, Boolean> readOnlyValidBufferBuilder() {return PileBool.ib().setupValidBuffer(this);}

	
	@Override public default SealBool buffer(){return readOnlyBuffer();}
	@Override public default SealPileBuilder<SealBool, Boolean> bufferBuilder() {return readOnlyBufferBuilder();}
	@Override public default SealBool readOnlyBuffer(){return readOnlyBufferBuilder().build();}
	@Override public default SealPileBuilder<SealBool, Boolean> readOnlyBufferBuilder() {return PileBool.sb().setupBuffer(this);}

	@Override public default SealBool rateLimited(long coldStartTime, long coolDownTime){return readOnlyRateLimited(coldStartTime, coolDownTime);}
	@Override public default SealPileBuilder<SealBool, Boolean> rateLimitedBuilder(long coldStartTime, long coolDownTime) {return readOnlyRateLimitedBuilder(coldStartTime, coolDownTime);}
	@Override public default SealBool readOnlyRateLimited(long coldStartTime, long coolDownTime){return readOnlyRateLimitedBuilder(coldStartTime, coolDownTime).build();}
	@Override public default SealPileBuilder<SealBool, Boolean> readOnlyRateLimitedBuilder(long coldStartTime, long coolDownTime) {return PileBool.sb().setupRateLimited(this, coldStartTime, coolDownTime);}

}
