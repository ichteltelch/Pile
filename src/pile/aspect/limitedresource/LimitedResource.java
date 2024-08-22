package pile.aspect.limitedresource;

import java.util.concurrent.locks.ReentrantLock;

import pile.interop.wait.GuardedCondition;
import pile.interop.wait.ObservableCondition;

public class LimitedResource {
	static final ReentrantLock LR_LOCK = new ReentrantLock(true); 
	public final String name;
	private long max;
	private long used;
	public final ObservableCondition available = new ObservableCondition(new GuardedCondition(LR_LOCK.newCondition(), ()->used<max));
	public LimitedResource(String name, long max) {
		this.name = name;
		this.max = max;
		used=0;
	}
	
}
