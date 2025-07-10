//package pile.specialized_bool.combinations;
//
//import java.util.concurrent.ExecutorService;
//import java.util.function.BooleanSupplier;
//
//import pile.aspect.WriteElsewhere;
//import pile.aspect.combinations.ReadWriteDependency;
//import pile.aspect.combinations.WriteElsewherePile;
//import pile.specialized_bool.PileBool;
//
//public interface WriteElsewhereBool extends PileBool, WriteElsewhere {
//	public static WriteElsewhereBoolImpl make(
//            ReadWriteDependency<Boolean> wrapped, 
//            ExecutorService deferTo, 
//            BooleanSupplier wouldDefer) {
//		return new WriteElsewhereBoolImpl(wrapped, deferTo, wouldDefer);
//	}
//	static class WriteElsewhereBoolImpl extends WriteElsewherePile.WriteElsewherePileImpl<Boolean> implements WriteElsewhereBool {
//
//		public WriteElsewhereBoolImpl(
//				ReadWriteDependency<Boolean> wrapped, 
//				ExecutorService deferTo,
//				BooleanSupplier wouldDefer) {
//			super(wrapped, deferTo, wouldDefer);
//		}
//		@Override
//		public WriteElsewhereBoolImpl setNull() {
//			super.setNull();
//			return this;
//		}
//		@Override
//		public WriteElsewhereBoolImpl setName(String name) {
//			super.setName(name);
//			return this;
//		}
//		
//	}
//}