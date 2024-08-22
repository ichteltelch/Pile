package pile.aspect.limitedresource;

import java.util.Comparator;
import java.util.concurrent.atomic.AtomicInteger;


public abstract class Supplicant implements Comparable<Supplicant>{
	public static final Comparator<? super Supplicant> BY_DEADLINE = (a, b)->Long.compare(a.deadline(), b.deadline());
	
	static final AtomicInteger INST_COUNTER = new AtomicInteger();
	private final int id = INST_COUNTER.incrementAndGet();
	private final int priority;
	private final long deadline;
	
	public Supplicant(int priority, long deadline) {
		this.priority = priority;
		this.deadline = deadline;
	}
	
	public long deadline() {
		return deadline;
	}
	@Override
	public int compareTo(Supplicant o) {
		int c = Integer.compare(priority, o.priority);
		if(c!=0) return c;
		c = Long.compare(deadline, o.deadline);
		if(c!=0) return c;
		return Integer.compare(id, o.id);
	}
}
