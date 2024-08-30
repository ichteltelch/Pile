package pile.utils.defer;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;

public interface DeferrerQueue {
	public boolean isQueueEmpty();
	public Runnable pollQueue();
	public void enqueue(Runnable r);
	
	
	public class FiFo implements DeferrerQueue {
		ArrayDeque<Runnable> queue = new ArrayDeque<>();
		public void enqueue(Runnable r) {
            queue.addLast(r);
        }
		public Runnable pollQueue() {
            return queue.pollFirst();
        }
		public boolean isQueueEmpty() {
            return queue.isEmpty();
        }
	}
	public class LiFo implements DeferrerQueue {
		ArrayList<Runnable> queue = new ArrayList<>();
		public void enqueue(Runnable r) {
            queue.add(r);
        }
		public Runnable pollQueue() {
            return queue.isEmpty()?null:queue.remove(queue.size()-1);
        }
		public boolean isQueueEmpty() {
            return queue.isEmpty();
        }
	}
	public class Dedup implements DeferrerQueue{
		public static interface StayMoveToEnd{
			boolean stayDontMoveToEnd();
		}
		boolean stayDontMoveToEnd;
		
		public Dedup(boolean stayDontMoveToEnd){
			this.stayDontMoveToEnd = stayDontMoveToEnd;
		}
		static class Entry{
			Entry prev;
			Entry next;
			Runnable r;
			public Entry(Runnable r) {
				this.r=r;
			}
			public void remove() {
				prev.next = next;
				next.prev = prev;
				prev = null;
				next = null;
			}
			public void insertAfter(Entry newPrev) {
				prev = newPrev;
				next = newPrev.next;
				next.prev = this;
				prev.next = this;
			}
			public void insertBefore(Entry newNext) {
				prev = newNext.prev;
				next = newNext;
				next.prev = this;
				prev.next = this;
			}
		}

		Entry sentinel = new Entry(null);{
			sentinel.next = sentinel;
            sentinel.prev = sentinel;
		}
		
		HashMap<Runnable, Entry> queue = new HashMap<>();
		public void enqueue(Runnable r) {
			Entry present = queue.get(r);
			if(present==null) {
				present = new Entry(r);
				present.insertBefore(sentinel);
				queue.put(r, present);
				return;
			}
			boolean stay = r instanceof StayMoveToEnd ? ((StayMoveToEnd)r).stayDontMoveToEnd() :  stayDontMoveToEnd;
			if(!stay) {
				present.remove();
                present.insertBefore(sentinel);
			}
            
        }
		public Runnable pollQueue() {
			Entry first = sentinel.next;
			if(first==sentinel)
				return null;
			first.remove();
			return first.r;
        }
		public boolean isQueueEmpty() {
            return sentinel.next==sentinel;
        }
		
	}
}
