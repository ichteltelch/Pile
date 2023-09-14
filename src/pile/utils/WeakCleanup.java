package pile.utils;

import java.io.IOException;
import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.function.Supplier;



/**
 * A {@link WeakReference} that has a {@link #run()} method that will be called in a daemon thread when the reference becomes enqueued, 
 * usually for cleanup and detection of loose ends.
 * @author bb
 * @param <E>
 */
public abstract class WeakCleanup<E> extends WeakReference<E> implements Runnable{

	public WeakCleanup(E referent, Supplier<? extends ReferenceQueue<? super Object>> rm) {
		super(referent, rm.get());
	}
	public WeakCleanup(E referent) {
		this(referent, AbstractReferenceManager.Std());
	}
	//	@Override
	//	public void clear() {
	//		super.clear();
	//		ReferenceManager.safeRun(this);
	//	}

	static ArrayList<Bucket> buckets=new ArrayList<>();
	static int bucketsCap = 0;
	/**
	 * Debugging method
	 */
	public static void deb() {
		@SuppressWarnings("unused")
		ArrayList<Bucket> bs = buckets;
		@SuppressWarnings("unused")
		Supplier<? extends ReferenceQueue<? super Object>> am = AbstractReferenceManager.Std();
		try {
			System.in.read();
			System.in.read();
			System.in.read();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	static private class Bucket extends WeakCleanup<Object>{
		int position=-1;
		Runnable runThis;
		public Bucket(Object referent, Runnable handle, Supplier<? extends ReferenceQueue<? super Object>> rm) {
			super(referent,rm);
			runThis=handle;
		}
		void remove(){
			synchronized(buckets) {
				int wls = buckets.size();
				if(position == -1 || position>=wls || buckets.get(position)!=this)
					return;
				if(wls==position+1) {
					buckets.remove(position);
				}else {
					Bucket last=buckets.remove(wls-1);
					last.position=position;
					buckets.set(position, last);
				}
				position=-1;
				if(buckets.size()<<1 < bucketsCap) {
					buckets.trimToSize();
					bucketsCap = buckets.size();
				}
				
			}
		}

		@Override
		public void run() {
			remove();
			runThis.run();
		}

	}

	/**
	 * Run the given {@link Runnable} if the given {@link Object}
	 * becomes weakly reachable. Uses the {@link AbstractReferenceManager#Std()
	 * standard ReferenceManager} to detect the reachability change and to run the handler.
	 * @param o
	 * @param run
	 */
	public static void runIfWeak(Object o, Runnable run) {
		runIfWeak(o, run, AbstractReferenceManager.Std());
	}
	/**
	 * Run the given {@link Runnable} if the given {@link Object}
	 * becomes weakly reachable. 
	 * @param o
	 * @param run
	 * @param rm Use this {@link ReferenceQueue} to detect the reachability change 
	 * and to run the handler.
	 */
	public static void runIfWeak(Object o, Runnable run, Supplier<? extends ReferenceQueue<? super Object>> rm) {
		Bucket n=new Bucket(o, run, rm);
		synchronized (buckets) {
			n.position=buckets.size();
			buckets.add(n);		
			bucketsCap = Math.max(buckets.size(), bucketsCap);
		}

	}


}
