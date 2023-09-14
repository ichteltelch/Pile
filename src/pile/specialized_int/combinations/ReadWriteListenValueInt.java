package pile.specialized_int.combinations;

import pile.builder.IndependentBuilder;
import pile.builder.SealPileBuilder;
import pile.specialized_Comparable.combinations.ReadWriteListenValueComparable;
import pile.specialized_int.IndependentInt;
import pile.specialized_int.PileInt;
import pile.specialized_int.SealInt;

public interface ReadWriteListenValueInt extends 
ReadListenValueInt, 
ReadWriteValueInt,
ReadWriteListenValueComparable<Integer>
{
	@Override public default ReadWriteListenValueInt setNull() {
		set(null);
		return this;
	}
	
	@Override public default IndependentInt validBuffer_memo(){return writableValidBuffer_memo();}
	@Override public default IndependentInt writableValidBuffer_memo(){return (IndependentInt) WRITABLE_VALID_BUFFER_CACHE.apply(this);}

	
	@Override public default IndependentInt validBuffer(){return writableValidBuffer();}
	@Override public default IndependentBuilder<IndependentInt, Integer> validBufferBuilder() {return writableValidBufferBuilder();}
	@Override public default IndependentInt writableValidBuffer() {return writableValidBufferBuilder().build();}
	@Override public default IndependentBuilder<IndependentInt, Integer> writableValidBufferBuilder() {return PileInt.ib().setupWritableValidBuffer(this);}

	
	@Override public default SealInt buffer(){return writableBuffer();}
	@Override public default SealPileBuilder<SealInt, Integer> bufferBuilder(){return writableBufferBuilder();}
	@Override public default SealInt writableBuffer() {return writableBufferBuilder().build();}
	@Override public default SealPileBuilder<SealInt, Integer> writableBufferBuilder() {return PileInt.sb().setupWritableBuffer(this);}

	@Override public default SealInt rateLimited(long coldStartTime, long coolDownTime){return writableRateLimited(coldStartTime, coolDownTime);}
	@Override public default SealPileBuilder<SealInt, Integer> rateLimitedBuilder(long coldStartTime, long coolDownTime){return writableRateLimitedBuilder(coldStartTime, coolDownTime);}
	@Override public default SealInt writableRateLimited(long coldStartTime, long coolDownTime) {return writableRateLimitedBuilder(coldStartTime, coolDownTime).build();}
	@Override public default SealPileBuilder<SealInt, Integer> writableRateLimitedBuilder(long coldStartTime, long coolDownTime) {return PileInt.sb().setupWritableRateLimited(this, coldStartTime, coolDownTime);}

}
