//package pile.aspect.combinations;
//
//import java.util.concurrent.ExecutorService;
//import java.util.function.BooleanSupplier;
//
//import pile.aspect.WriteElsewhere;
//import pile.aspect.listen.ListenValue;
//import pile.aspect.listen.ValueEvent;
//import pile.aspect.listen.ValueListener;
//import pile.builder.PileBuilder;
//import pile.impl.PileImpl;
//import pile.specialized_bool.combinations.ReadListenDependencyBool;
//
///**
// * Run writes in a different {@link Thread} or at a later time, maybe.
// * How, exactly, is controlled using an {@link ExecutorService} and a {@link BooleanSupplier}
// * @param <E>
// */
//public interface WriteElsewherePile<E> extends Pile<E>, WriteElsewhere {
//	public static <T> WriteElsewherePileImpl<T> make(
//			ReadWriteDependency<T> wrapped, 
//			ExecutorService deferTo, 
//			BooleanSupplier wouldDefer) {
//		return new WriteElsewherePileImpl<>(wrapped, deferTo, wouldDefer);
//	}
//
//	static class WriteElsewherePileImpl<T> extends PileImpl<T> implements WriteElsewherePile<T> {
//
//		final ReadWriteDependency<T> wrapped;
//		final ExecutorService deferTo;
//		final BooleanSupplier wouldDefer;
//
//		public WriteElsewherePileImpl(
//				ReadWriteDependency<T> wrapped, 
//				ExecutorService deferTo, 
//				BooleanSupplier wouldDefer) {
//			this.wrapped = wrapped;
//			this.deferTo = deferTo;
//
//			this.wouldDefer = wouldDefer;
//			new PileBuilder<WriteElsewherePileImpl<T>, T>(this)
//			.name("write deferring " + wrapped.dependencyName())
//			.recompute(()->{
//				T v = wrapped.get();
//				if(v==null)
//					System.out.print("");
//				return v;
//			})
//			.equivalence(wrapped._getEquivalence())
//			.whenChanged(wrapped);
//		}
//		@Override
//		public boolean wouldDefer() {
//			return wouldDefer.getAsBoolean();
//		}
//		@Override
//		public T applyCorrection(T value) {
//			super.applyCorrection(value);
//			return wrapped.applyCorrection(value);
//		}
//		@Override
//		public T set(T val) {
//			ListenValue.DEFER.__incrementSuppressors();
//			try {
//				ValueListener psvl = pendingSet;
//				if(psvl!=null) {
//					wrapped.validity().removeValueListener(psvl);
//				}
//				if(val==null)
//					System.out.print("");
//				if(wouldDefer()) {
//					if(!isInTransaction()) {
//
//						super.set(val);
//						val = wrapped.applyCorrection(val);
//						T fval=val;
//						deferTo.execute(() -> super.set(wrapped.set(fval)));
//					}else {
//						T fval=val;
//						ReadListenDependencyBool itv = wrapped.inTransactionValue();
//						itv.addValueListener(new ValueListener() {
//							public void valueChanged(ValueEvent e) {
//								if(itv.isFalse()) {
//									itv.removeValueListener(this);
//									if(!wrapped._getEquivalence().test(fval, getAsync()))
//										set(fval);
//								}
//							};
//						});
//					}
//					return null;
//				}
//
//				val = wrapped.applyCorrection(val);
//				wrapped.set(val);
//				psvl = pendingSet;
//				if(psvl!=null) {
//					wrapped.validity().removeValueListener(psvl);
//				}
//				if(wrapped.isValid()) {
//					try {
//						T wval = wrapped.getValidOrThrow();
//						super.set(wval);
//						return wval;
//					}catch(InvalidValueException e) {
//					}
//				}
//				super.revalidate();
//				//			pendingSet = wrapped.doOnceWhenValid(v->{
//				//				if(v==null)
//				//					System.out.print("");
//				//				super.set(v);	
//				//			});
//				return null;
//			}finally {
//				ListenValue.DEFER.__decrementSuppressors();
//			}
//		}
//		volatile ValueListener pendingSet;
//		@Override
//		protected void informLongTermInvalid() {
//			if(wouldDefer()) {
//				deferTo.execute(super::informLongTermInvalid);
//			}
//			super.informLongTermInvalid();
//		}
//	}
//
//}