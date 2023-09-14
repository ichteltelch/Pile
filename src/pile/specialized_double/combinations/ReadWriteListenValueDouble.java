package pile.specialized_double.combinations;

import pile.builder.IndependentBuilder;
import pile.builder.SealPileBuilder;
import pile.specialized_Comparable.combinations.ReadWriteListenValueComparable;
import pile.specialized_double.IndependentDouble;
import pile.specialized_double.PileDouble;
import pile.specialized_double.SealDouble;

public interface ReadWriteListenValueDouble extends 
ReadListenValueDouble, 
ReadWriteValueDouble,
ReadWriteListenValueComparable<Double>
{
	
	@Override default ReadWriteListenValueDouble setNull() {
		set(null);
		return this;
	}
	
	@Override public default IndependentDouble validBuffer_memo(){return writableValidBuffer_memo();}
	@Override public default IndependentDouble writableValidBuffer_memo(){return (IndependentDouble) WRITABLE_VALID_BUFFER_CACHE.apply(this);}

	
	@Override public default IndependentDouble validBuffer(){return writableValidBuffer();}
	@Override public default IndependentBuilder<IndependentDouble, Double> validBufferBuilder() {return writableValidBufferBuilder();}
	@Override public default IndependentDouble writableValidBuffer() {return writableValidBufferBuilder().build();}
	@Override public default IndependentBuilder<IndependentDouble, Double> writableValidBufferBuilder() {return PileDouble.ib().setupWritableValidBuffer(this);}

	

	@Override public default SealDouble buffer(){return writableBuffer();}
	@Override public default SealPileBuilder<SealDouble, Double> bufferBuilder(){return writableBufferBuilder();}
	@Override public default SealDouble writableBuffer() {return writableBufferBuilder().build();}
	@Override public default SealPileBuilder<SealDouble, Double> writableBufferBuilder() {return PileDouble.sb().setupWritableBuffer(this);}
	
	@Override public default SealDouble rateLimited(long coldStartTime, long coolDownTime){return writableRateLimited(coldStartTime, coolDownTime);}
	@Override public default SealPileBuilder<SealDouble, Double> rateLimitedBuilder(long coldStartTime, long coolDownTime){return writableRateLimitedBuilder(coldStartTime, coolDownTime);}
	@Override public default SealDouble writableRateLimited(long coldStartTime, long coolDownTime) {return writableRateLimitedBuilder(coldStartTime, coolDownTime).build();}
	@Override public default SealPileBuilder<SealDouble, Double> writableRateLimitedBuilder(long coldStartTime, long coolDownTime) {return PileDouble.sb().setupWritableRateLimited(this, coldStartTime, coolDownTime);}


}
