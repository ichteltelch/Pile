package pile.utils;

import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;



/**
 * Comparator that imposes an arbitrary, but consistent total ordering on all Objects
 * that induces the identity equivalence relation (and not the equality equivalence relation, 
 * in violation of the usual contract for Comparators)
 * 
 * 
 * @author bb
 *
 */
public class IdentityComparator implements Comparator<Object>{

	/**
	 * The singleton instance.
	 */
	public static final IdentityComparator INST=new IdentityComparator();


	private IdentityComparator(){}


	private ConcurrentHashMap<Integer, List<Ref>> map=new ConcurrentHashMap<Integer, List<Ref>>();

	private class Ref extends WeakCleanup<Object>{ //Or use Phantom?
		public Ref(Object referent, int hc) {
			super(referent);
			this.hc=hc;
		}
		int hc;
		@Override
		public void run() {
			Integer hco = hc;
			List<Ref> l=map.get(hco);
			if(l==null)
				return;
			synchronized (l) {
				l.remove(this);
				if(l.isEmpty())
					map.remove(hco);
			}
			clear();
		}
		public boolean equals(Object o) {
			return o==this;
		}
		@Override
		public int hashCode() {
			return System.identityHashCode(this);
		}

	}

	@Override
	public int compare(Object o1, Object o2) {
		//Identity check
		if(o1==o2){
			return 0;
		}

		//null check
		if(o1==null)
			return -1;
		if(o2==null)
			return 1;

		//IdentityHashCode comparison
		int h1=System.identityHashCode(o1);
		int h2=System.identityHashCode(o2);
		//System.out.println(h1+", "+h2);
//		h1=h2=0;
		if(h1<h2)
			return -1;
		if(h1>h2)
			return 1;

		//class name comparison
		String c1=o1.getClass().getName();
		String c2=o2.getClass().getName();
		int cd=c1.compareTo(c2);
		if(cd!=0)
			return cd;

		//get disambiguation list
		List<Ref> l=map.get(h1);

		//create list if absent
		if(l==null){
			l=new LinkedList<Ref>();
			l.add(new Ref(o1, h1));
			l.add(new Ref(o2, h2));
			List<Ref> nl = map.putIfAbsent(h1, l);
			if(nl!=null)
				//list was not actually absent at the critical moment
				l=nl;
			else
				return 1;
		}

		synchronized (l) {
			int i=0;
			int i1=-1, i2=-1;
			//serach list
			for(Iterator<Ref> it=l.listIterator(); it.hasNext(); i++){
				Ref ref=it.next();
				final Object x = ref.get();
				if(x==o1)
					i1=i;
				else if(x==o2)
					i2=i;
			}
			//add objects to list if necessary
			if(i1==-1){
				i1=i++;
				l.add(new Ref(o1, h1));
			}
			if(i2==-1){
				i2=i++;
				l.add(new Ref(o2, h2));
			}

			//compare indices
			return i2>i1?1:i1==i2?0:-1;
		}
	}


}
