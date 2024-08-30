package pile.specialized_int;

import java.util.concurrent.ExecutorService;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.logging.Level;
import java.util.logging.Logger;

import pile.aspect.bracket.ValueBracket;
import pile.aspect.bracket.ValueOnlyBracket;
import pile.aspect.combinations.Pile;
import pile.aspect.suppress.Suppressor;
import pile.interop.debug.DebugEnabled;
import pile.interop.exec.StandardExecutors;
import pile.utils.SequentialQueue;
import pile.utils.WeakCleanup;

public class SuppressInt extends IndependentInt{



	private final static Logger log=Logger.getLogger("SuppressInt");

	public static Function<? super SuppressInt, ? extends Suppressor> METHOD = SuppressInt::suppress;
	int suppressors=0;
	private Consumer<? super Integer> setter=makeSetter();
	public SuppressInt() {
		super(0);
		seal();
	}
	SequentialQueue asyncChange;
	ExecutorService asyncRelease;
	/**
	 * Configure this {@link SuppressInt} so that any changes to the {@link Pile}
	 * are performed in a dedicated {@link SequentialQueue} 
	 * @return this
	 */
	public SuppressInt asyncChange() {
		asyncChange=new SequentialQueue("asyncRelease");
		return this;
	}
	/**
	 * Configure this {@link SuppressInt} so that any changes to the {@link Pile}
	 * are performed on the given {@link SequentialQueue}.
	 * Pass <code>null</code> or do not call this method to have the changes performed
	 * in the thread calling {@link #suppress()} or {@link Suppressor#release()}.
	 * In that case, an internal monitor will be locked while the {@link Pile} is changed.
	 * 
	 * @return this
	 */
	public SuppressInt asyncChange(SequentialQueue s) {
		asyncChange=s;
		return this;
	}
	/**
	 * If you specify an {@link ExecutorService} using this method,
	 * it will be passed to the {@link Suppressor}s' 
	 * {@link Suppressor#wrapAsync(ExecutorService)} method so that 
	 * the {@link Suppressor}s returned by {@link #suppress()} are 
	 * released in a job executed by the {@link ExecutorService}.
	 * @param s
	 * @return
	 */
	public SuppressInt asyncRelease(ExecutorService s) {
		asyncRelease=s;
		return this;
	}

	public Suppressor suppress() {
		Suppressor s = Suppressor.wrap(this::decrement);
		increment(s);
		if(asyncRelease!=null)
			return s.wrapAsync(asyncRelease);
		return s;
	}
	private void decrement() {
			
		if(asyncChange!=null) {
			synchronized (setter) {
				--suppressors;
			}
			asyncChange.enqueue(()->{
				int nv;
				synchronized(setter) {
					nv = suppressors;
				}
				setter.accept(nv);	
			});
		} else {
			synchronized (setter) {
				--suppressors;
				setter.accept(suppressors);
			}
		}
	}
	private void increment(Suppressor ss) {
		Suppressor s=null;

		try {
			if(asyncChange!=null) {
				synchronized (setter) {
					++suppressors;
				}
				s = ss;
				try {
					asyncChange.syncEnqueue(()->{
						int nv;
						synchronized(setter) {
							nv = suppressors;
						}
						setter.accept(nv);	
					}); //TODO: is this really exception safe?
				} catch (InterruptedException e) {
					log.log(Level.WARNING, "suppress interrupted", e);
					StandardExecutors.interruptSelf();
				}
			}else {				
				synchronized (setter) {
					++suppressors;
					s = ss;
					setter.accept(suppressors);
				}
			}
			s=null;
		}finally {
			if(s!=null)
				s.release();
		}
	}
	@Override
	public SuppressInt setName(String name) {
		super.setName(name);
		return this;
	}

	/**
	 * Make a bracket that suppresses the {@link SuppressInt} as long as the value fulfills the given criterion.
	 * The Bracket may be used on multiple objects. If it becomes weakly reachable,
	 * but has not been closed on all values where it was applied, it is closed
	 * @param <T>
	 * @param inheritable
	 * @param crit: Important: This must predicate always evaluate to the same result for the same object! 
	 * @return
	 */
	public <T> ValueBracket<T, Object> suppressBracket(boolean inheritable, Predicate<? super T> crit) {
		MutInt openCount = new MutInt(); 
		ValueBracket<T, Object> ret = new ValueOnlyBracket<T>() {
			@Override
			public boolean isInheritable() {
				return inheritable;
			}
			@Override
			public boolean open(T value, Object owner) {
				if(crit.test(value)) {
					boolean success = false;
					try {
						
						increment(null);
						success = true;
						synchronized (openCount) {
							++openCount.val;
						}
					}finally {
						if(!success)
							decrement();
					}
				}
				return true;
			}
			@Override
			public boolean close(T value, Object owner) {
				if(crit.test(value)) {
					synchronized (openCount) {
						--openCount.val;
					}
					decrement();
				}
				return false;
			}
			@Override
			public boolean openIsNop() {
				return false;
			}
			@Override
			public boolean closeIsNop() {
				return false;
			}
			@Override
			public boolean canBecomeObsolete() {
				return false;
			}
		};
		WeakCleanup.runIfWeak(ret, ()->{
			int times = 0;
			synchronized (openCount) {
				times = openCount.val;
				openCount.val = 0;
			}
			if(times>0) {
				try {
					while(times>0) {
						times--;
						decrement();
					}
				}finally {
					if(times>0) {
						synchronized (openCount) {
							openCount.val += times;
						}
						log.warning("leaked suppressBracket no completely closed!");

					}
					log.warning("suppressBracket leaked!");
				}
			}
			
		});
		if(DebugEnabled.DETECT_STUCK_BRACKETS)
			ret=ret.detectStuck();
		return ret;
	}
}
