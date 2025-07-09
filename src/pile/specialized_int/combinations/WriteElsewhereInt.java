package pile.specialized_int.combinations;

import java.util.concurrent.ExecutorService;
import java.util.function.BooleanSupplier;

import pile.aspect.WriteElsewhere;
import pile.aspect.combinations.ReadWriteDependency;
import pile.aspect.combinations.WriteElsewherePile;
import pile.specialized_int.PileInt;

public interface WriteElsewhereInt extends PileInt, WriteElsewhere {
	public static WriteElsewhereIntImpl make(
            ReadWriteDependency<Integer> wrapped, 
            ExecutorService deferTo, 
            BooleanSupplier wouldDefer) {
		return new WriteElsewhereIntImpl(wrapped, deferTo, wouldDefer);
	}
	static class WriteElsewhereIntImpl extends WriteElsewherePile.WriteElsewherePileImpl<Integer> implements WriteElsewhereInt {

		public WriteElsewhereIntImpl(
				ReadWriteDependency<Integer> wrapped, 
				ExecutorService deferTo,
				BooleanSupplier wouldDefer) {
			super(wrapped, deferTo, wouldDefer);
		}
		@Override
		public WriteElsewhereIntImpl setNull() {
			super.setNull();
			return this;
		}
		
	}
}