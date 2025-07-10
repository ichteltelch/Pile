//package pile.specialized_double.combinations;
//
//import java.util.concurrent.ExecutorService;
//import java.util.function.BooleanSupplier;
//
//import pile.aspect.WriteElsewhere;
//import pile.aspect.combinations.ReadWriteDependency;
//import pile.aspect.combinations.WriteElsewherePile;
//import pile.specialized_double.PileDouble;
//
//public interface WriteElsewhereDouble extends PileDouble, WriteElsewhere {
//	public static WriteElsewhereDoubleImpl make(
//            ReadWriteDependency<Double> wrapped, 
//            ExecutorService deferTo, 
//            BooleanSupplier wouldDefer) {
//		return new WriteElsewhereDoubleImpl(wrapped, deferTo, wouldDefer);
//	}
//	static class WriteElsewhereDoubleImpl extends WriteElsewherePile.WriteElsewherePileImpl<Double> implements WriteElsewhereDouble {
//
//		public WriteElsewhereDoubleImpl(
//				ReadWriteDependency<Double> wrapped, 
//				ExecutorService deferTo,
//				BooleanSupplier wouldDefer) {
//			super(wrapped, deferTo, wouldDefer);
//		}
//		@Override
//		public WriteElsewhereDoubleImpl setNull() {
//			super.setNull();
//			return this;
//		}
//		
//	}
//}