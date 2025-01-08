package pile.utils.defer;

import java.util.function.Supplier;

import pile.aspect.suppress.Suppressor;

public interface Deferrer {
	void run(Runnable r);
	Suppressor suppressRunningImmediately();
	boolean isRunningImmediately();
	boolean isDeferring();
	boolean hasStartedRunningDeferred();
	
	public void __incrementSuppressors();
	public void __decrementSuppressors();
	
	
	public Deferrer makeSynchronized(Object monitor);
	
	public static Supplier<Deferrer> wrap(Supplier<DeferrerQueue> qs){
		return ()->new DefererImpl(qs.get());
	}
	public static Deferrer makeThreadLocal(Supplier<DeferrerQueue> qs) {
		return new ThreadLocalDeferrer(wrap(qs));
	}
	
	
	Deferrer DONT = new Deferrer() {
		
		@Override
		public Suppressor suppressRunningImmediately() {
			return Suppressor.NOP;
		}
		
		@Override
		public void run(Runnable r) {
			r.run();
		}
		
		@Override
		public Deferrer makeSynchronized(Object monitor) {
			return this;
		}
		
		@Override
		public boolean isRunningImmediately() {
			return true;
		}
		
		@Override
		public boolean isDeferring() {
			return false;
		}
		
		@Override
		public boolean hasStartedRunningDeferred() {
			return false;
		}
		
		@Override
		public void __incrementSuppressors() {
		}
		
		@Override
		public void __decrementSuppressors() {
		}
	};

}
