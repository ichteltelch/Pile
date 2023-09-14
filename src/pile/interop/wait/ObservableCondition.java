package pile.interop.wait;

import java.util.Iterator;
import java.util.Objects;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.locks.Condition;

import pile.utils.IdentityComparator;


/**
 * Wraps another {@link Condition} so that calls to its signal* methods can be observed
 * @author bb
 *
 */
public class ObservableCondition extends WrappedCondition implements Condition{
	public interface ConditionObserver{
		public void observe(boolean all);
		public boolean shouldRemove();
	}
	ConcurrentSkipListSet<ConditionObserver> listeners = new ConcurrentSkipListSet<>(IdentityComparator.INST);
	
	public ObservableCondition(Condition back) {
		super(back);
		Objects.requireNonNull(back);
	}
	public void fire(boolean all) {
		for(Iterator<ConditionObserver> i = listeners.iterator(); i.hasNext(); ) {
			ConditionObserver co = i.next();
			if(co.shouldRemove())
				i.remove();
			else
				co.observe(all);
		}
	}
	public void addObserver(ConditionObserver co) {
		listeners.add(co);
	}
	public void removeObserver(ConditionObserver co) {
		listeners.remove(co);
	}

	@Override
	public void signal(WaitService ws) {
		ws.signal(back);
		fire(false);
	}

	@Override
	public void signalAll(WaitService ws) {
		ws.signalAll(back);
		fire(true);
	}

}
