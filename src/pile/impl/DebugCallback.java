package pile.impl;

import pile.aspect.Dependency;
import pile.aspect.combinations.ReadListenDependency;
import pile.aspect.suppress.Suppressor;

/**
 * An interface for hooking into the inner workings of {@link AbstractReadListenDependency} and {@link PileImpl}
 * for debugging. Look where the methods are called to understand how to use them.
 * @author bb
 *
 */
public interface DebugCallback {
//	public static final DebugCallback NOP = new DebugCallback(){ };

	default void dependencyBeginsChanging(ReadListenDependency<?> source, Dependency d, boolean valid){};
	default void dependencyEndsChanging(ReadListenDependency<?> source, Dependency d){}
	default void fireValueChange(ReadListenDependency<?> source) {}
	default void endTransactionCalled(ReadListenDependency<?> source) {}
	default void beginTransactionCalled(ReadListenDependency<?> source) {}
	default void set(ReadListenDependency<?> source, Object val) {}
	default void fulfill(ReadListenDependency<?> source, Object val) {}
	default void fulfillInvalid(ReadListenDependency<?> source) {}
	default void explicitlyInvalidate(ReadListenDependency<?> source, boolean fromFulfill) {}
	default void newlyScheduledRecomputation(ReadListenDependency<?> source) {}
	default void cancellingOngoingRecomputation(ReadListenDependency<?> source) {}
	default void unschedulePendingRecomputation(ReadListenDependency<?> source) {}
	default void startPendingRecomputation(ReadListenDependency<?> source) {}
	default void fireDeepRevalidate(ReadListenDependency<?> source){}
	default void revalidateCalled(ReadListenDependency<?> source){}
	default void autoValidationSuppressorCreated(ReadListenDependency<?> source, Suppressor s) {}
	default void autoValidationSuppressorReleased(ReadListenDependency<?> source, Suppressor s) {}
	default void trace() {
		System.err.println("Trace: ");
		for(StackTraceElement e: Thread.currentThread().getStackTrace())
			System.err.println(e);
	}

}
