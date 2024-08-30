package pile.utils.defer;

import java.util.function.Supplier;

import pile.aspect.suppress.Suppressor;

public class ThreadLocalDeferrer implements Deferrer {
	public final ThreadLocal<Deferrer> current;
	public ThreadLocalDeferrer(Supplier<? extends Deferrer> make) {
        current = ThreadLocal.withInitial(make);
    }
	@Override
    public void run(Runnable r) {
        current.get().run(r);
    }
	@Override
	public Suppressor suppressRunningImmediately() {
		return current.get().suppressRunningImmediately();
	}
	@Override
	public boolean isRunningImmediately() {
		return current.get().isRunningImmediately();
	}
	@Override
	public boolean isDeferring() {
		return current.get().isDeferring();
	}
	@Override
	public boolean hasStartedRunningDeferred() {
		return current.get().hasStartedRunningDeferred();
	}
	@Override
	public Deferrer makeSynchronized(Object monitor) {
		return this;
	}
	@Override
	public void __incrementSuppressors() {
		current.get().__incrementSuppressors();
	}
	@Override
    public void __decrementSuppressors() {
        current.get().__decrementSuppressors();
    }
}