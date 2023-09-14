package pile.specialized_double.combinations;

import pile.builder.IndependentBuilder;
import pile.builder.SealPileBuilder;
import pile.specialized_Comparable.combinations.ReadListenValueComparable;
import pile.specialized_double.IndependentDouble;
import pile.specialized_double.PileDouble;
import pile.specialized_double.SealDouble;

public interface ReadListenValueDouble extends ReadValueDouble, ReadListenValueComparable<Double>{


	
	
	
	
	@Override public default IndependentDouble validBuffer_memo(){return readOnlyValidBuffer_memo();}
	@Override public default IndependentDouble readOnlyValidBuffer_memo(){return (IndependentDouble) READ_ONLY_VALID_BUFFER_CACHE.apply(this);}

	
	@Override public default IndependentDouble validBuffer(){return readOnlyValidBuffer();}
	@Override public default IndependentBuilder<IndependentDouble, Double> validBufferBuilder() {return readOnlyValidBufferBuilder();}
	@Override public default IndependentDouble readOnlyValidBuffer(){return readOnlyValidBufferBuilder().build();}
	@Override public default IndependentBuilder<IndependentDouble, Double> readOnlyValidBufferBuilder() {return PileDouble.ib().setupValidBuffer(this);}

	
	
	@Override public default SealDouble buffer(){return readOnlyBuffer();}
	@Override public default SealPileBuilder<SealDouble, Double> bufferBuilder() {return readOnlyBufferBuilder();}
	@Override public default SealDouble readOnlyBuffer(){return readOnlyBufferBuilder().build();}
	@Override public default SealPileBuilder<SealDouble, Double> readOnlyBufferBuilder() {return PileDouble.sb().setupBuffer(this);}
	
	@Override public default SealDouble rateLimited(long coldStartTime, long coolDownTime){return readOnlyRateLimited(coldStartTime, coolDownTime);}
	@Override public default SealPileBuilder<SealDouble, Double> rateLimitedBuilder(long coldStartTime, long coolDownTime) {return readOnlyRateLimitedBuilder(coldStartTime, coolDownTime);}
	@Override public default SealDouble readOnlyRateLimited(long coldStartTime, long coolDownTime){return readOnlyRateLimitedBuilder(coldStartTime, coolDownTime).build();}
	@Override public default SealPileBuilder<SealDouble, Double> readOnlyRateLimitedBuilder(long coldStartTime, long coolDownTime) {return PileDouble.sb().setupRateLimited(this, coldStartTime, coolDownTime);}

}
