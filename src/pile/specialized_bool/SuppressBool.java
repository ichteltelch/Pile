package pile.specialized_bool;

import java.util.concurrent.ExecutorService;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.logging.Level;
import java.util.logging.Logger;

import pile.aspect.ValueBracket.ValueOnlyBracket;
import pile.aspect.combinations.Pile;
import pile.aspect.suppress.Suppressor;
import pile.interop.exec.StandardExecutors;
import pile.specialized_int.MutInt;
import pile.specialized_int.SuppressInt;
import pile.utils.SequentialQueue;
import pile.utils.WeakCleanup;

/**
 * A reactive boolean that usually <code>false</code>, but for as long as 
 * any {@link Suppressor}s created by the {@link #suppress()} method have not been
 * released, it is <code>true</code>. 
 * @author bb
 *
 */
public class SuppressBool extends IndependentBool{

	private final static Logger log=Logger.getLogger("SuppressBool");

	public static Function<? super SuppressBool, ? extends Suppressor> METHOD = SuppressBool::suppress;
	int suppressors=0;
	private Consumer<? super Boolean> setter=makeSetter();
	public SuppressBool() {
		super(false);
		seal();
	}
	SequentialQueue asyncChange;
	ExecutorService asyncRelease;
	/**
	 * Configure this {@link SuppressBool} so that any changes to the {@link Pile}
	 * are performed in a dedicated {@link SequentialQueue} 
	 * @return this
	 */
	public SuppressBool asyncChange() {
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
	public SuppressBool asyncChange(SequentialQueue s) {
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
	public SuppressBool asyncRelease(ExecutorService s) {
		asyncRelease=s;
		return this;
	}
	
	/**
	 * @return A Suppressor that causes this {@link SuppressBool} to hold the value
	 * <code>true</code> until it is released.
	 */
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
				boolean nv2;
				synchronized (setter) {
					--suppressors;
					nv2 = suppressors>0;
				}	
				setter.accept(nv2);
			});
		}else {
			synchronized (setter) {
				--suppressors;
				setter.accept(suppressors>0);
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
				s=ss;
				try {
					asyncChange.syncEnqueue(()->{
						boolean nv2;
						synchronized (setter) {
							--suppressors;
							nv2 = suppressors>0;
						}	
						setter.accept(nv2);
					}); //TODO: is this really exception safe?
				} catch (InterruptedException e) {
					log.log(Level.WARNING, "suppress interrupted", e);
					StandardExecutors.interruptSelf();
				}
			}else {
				synchronized (setter) {
					++suppressors;
					s=ss;
					setter.accept(suppressors>0);
				}
			}
			s=null;
		}finally {
			if(s!=null)
				s.release();
		}
	}
	@Override
	public SuppressBool setName(String name) {
		super.setName(name);
		return this;
	}

	/**
	 * Make a bracket that suppresses the {@link SuppressBool} as long as the value fulfills the given criterion.
	 * The Bracket may be used on multiple objects. If it becomes weakly reachable,
	 * but has not been closed on all values where it was applied, it is closed.
	 * @param <T>
	 * @param inheritable
	 * @param crit: Important: This must predicate always evaluate to the same result for the same object! 
	 * @return
	 */
	public <T> ValueOnlyBracket<T> suppressBracket(boolean inheritable, Predicate<? super T> crit) {
		MutInt openCount = new MutInt(); 
		ValueOnlyBracket<T> ret = new ValueOnlyBracket<T>() {
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
				return false;
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
			public boolean nopOpen() {
				return false;
			}
			@Override
			public boolean nopClose() {
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
		return ret;
	}
	/**
	 * This will fail, as {@link SuppressBool}s cannot be set directly
	 */
	@Deprecated
	@Override
	public Boolean set(Boolean val) {
		return super.set(val);
	}
	/**
	 * This will fail, as {@link SuppressBool}s cannot be set directly
	 */
	@Deprecated
	@Override
	public void setTrue() {
		super.setTrue();
	}
	/**
	 * This will fail, as {@link SuppressBool}s cannot be set directly
	 */
	@Deprecated
	@Override
	public void setFalse() {
		super.setFalse();
	}
	/**
	 * This will fail, as {@link SuppressBool}s cannot be set directly
	 */
	@Deprecated
	@Override
	public IndependentBool setNull() {
		return super.setNull();
	}
}
