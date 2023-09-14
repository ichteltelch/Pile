package pile.specialized_String.combinations;

import pile.builder.IndependentBuilder;
import pile.builder.SealPileBuilder;
import pile.specialized_Comparable.combinations.ReadWriteListenValueComparable;
import pile.specialized_String.IndependentString;
import pile.specialized_String.PileString;
import pile.specialized_String.SealString;

public interface ReadWriteListenValueString extends 
ReadListenValueString, 
ReadWriteValueString,
ReadWriteListenValueComparable<String>
{


	@Override public default ReadWriteListenValueString setNull() {
		set(null);
		return this;
	}
	
	@Override public default IndependentString validBuffer_memo(){return writableValidBuffer_memo();}
	@Override public default IndependentString writableValidBuffer_memo(){return (IndependentString) WRITABLE_VALID_BUFFER_CACHE.apply(this);}

	
	@Override public default IndependentString validBuffer(){return writableValidBuffer();}
	@Override public default IndependentBuilder<IndependentString, String> validBufferBuilder() {return writableValidBufferBuilder();}
	@Override public default IndependentString writableValidBuffer() {return writableValidBufferBuilder().build();}
	@Override public default IndependentBuilder<IndependentString, String> writableValidBufferBuilder() {return PileString.ib().setupWritableValidBuffer(this);}


	@Override public default SealString buffer(){return writableBuffer();}
	@Override public default SealPileBuilder<SealString, String> bufferBuilder(){return writableBufferBuilder();}
	@Override public default SealString writableBuffer() {return writableBufferBuilder().build();}
	@Override public default SealPileBuilder<SealString, String> writableBufferBuilder() {return PileString.sb().setupWritableBuffer(this);}
	
	@Override public default SealString rateLimited(long coldStartTime, long coolDownTime){return writableRateLimited(coldStartTime, coolDownTime);}
	@Override public default SealPileBuilder<SealString, String> rateLimitedBuilder(long coldStartTime, long coolDownTime){return writableRateLimitedBuilder(coldStartTime, coolDownTime);}
	@Override public default SealString writableRateLimited(long coldStartTime, long coolDownTime) {return writableRateLimitedBuilder(coldStartTime, coolDownTime).build();}
	@Override public default SealPileBuilder<SealString, String> writableRateLimitedBuilder(long coldStartTime, long coolDownTime) {return PileString.sb().setupWritableRateLimited(this, coldStartTime, coolDownTime);}

}
