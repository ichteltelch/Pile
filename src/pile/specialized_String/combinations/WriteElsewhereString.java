package pile.specialized_String.combinations;

import java.util.concurrent.ExecutorService;
import java.util.function.BooleanSupplier;

import pile.aspect.WriteElsewhere;
import pile.aspect.combinations.ReadWriteDependency;
import pile.aspect.combinations.WriteElsewherePile;
import pile.specialized_String.PileString;

public interface WriteElsewhereString extends PileString, WriteElsewhere {
	public static WriteElsewhereStringImpl make(
            ReadWriteDependency<String> wrapped, 
            ExecutorService deferTo, 
            BooleanSupplier wouldDefer) {
		return new WriteElsewhereStringImpl(wrapped, deferTo, wouldDefer);
	}
	static class WriteElsewhereStringImpl extends WriteElsewherePile.WriteElsewherePileImpl<String> implements WriteElsewhereString {

		public WriteElsewhereStringImpl(
				ReadWriteDependency<String> wrapped, 
				ExecutorService deferTo,
				BooleanSupplier wouldDefer) {
			super(wrapped, deferTo, wouldDefer);
		}
		@Override
		public WriteElsewhereStringImpl setNull() {
			super.setNull();
			return this;
		}
		
	}
}