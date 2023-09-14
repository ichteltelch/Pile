package pile.specialized_bool.combinations;

import pile.aspect.combinations.ReadWriteListenValue;
import pile.builder.IndependentBuilder;
import pile.builder.SealPileBuilder;
import pile.specialized_bool.IndependentBool;
import pile.specialized_bool.PileBool;
import pile.specialized_bool.SealBool;

public interface ReadWriteListenValueBool extends 
ReadListenValueBool, 
ReadWriteValueBool,
ReadWriteListenValue<Boolean>
{
	
	@Override default ReadWriteListenValueBool setNull() {
		set(null);
		return this;
	}
	
	@Override public default IndependentBool validBuffer_memo(){return writableValidBuffer_memo();}
	@Override public default IndependentBool writableValidBuffer_memo(){return (IndependentBool) WRITABLE_VALID_BUFFER_CACHE.apply(this);}

	@Override public default IndependentBool validBuffer(){return writableValidBuffer();}
	@Override public default IndependentBuilder<IndependentBool, Boolean> validBufferBuilder() {return writableValidBufferBuilder();}
	@Override public default IndependentBool writableValidBuffer() {return writableValidBufferBuilder().build();}
	@Override public default IndependentBuilder<IndependentBool, Boolean> writableValidBufferBuilder() {return PileBool.ib().setupWritableValidBuffer(this);}

	
	@Override public default SealBool buffer(){return writableBuffer();}
	@Override public default SealPileBuilder<SealBool, Boolean> bufferBuilder(){return writableBufferBuilder();}
	@Override public default SealBool writableBuffer() {return writableBufferBuilder().build();}
	@Override public default SealPileBuilder<SealBool, Boolean> writableBufferBuilder() {return PileBool.sb().setupWritableBuffer(this);}

	@Override public default SealBool rateLimited(long coldStartTime, long coolDownTime){return writableRateLimited(coldStartTime, coolDownTime);}
	@Override public default SealPileBuilder<SealBool, Boolean> rateLimitedBuilder(long coldStartTime, long coolDownTime){return writableRateLimitedBuilder(coldStartTime, coolDownTime);}
	@Override public default SealBool writableRateLimited(long coldStartTime, long coolDownTime) {return writableRateLimitedBuilder(coldStartTime, coolDownTime).build();}
	@Override public default SealPileBuilder<SealBool, Boolean> writableRateLimitedBuilder(long coldStartTime, long coolDownTime) {return PileBool.sb().setupWritableRateLimited(this, coldStartTime, coolDownTime);}

}
