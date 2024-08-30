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
}
