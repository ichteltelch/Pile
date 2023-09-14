package pile.relation;

import java.util.IdentityHashMap;
import java.util.Map;

import pile.aspect.combinations.ReadWriteListenValue;
import pile.aspect.listen.ValueEvent;
import pile.aspect.listen.ValueListener;

/**
 * Manage a set of boolean {@link ReadWriteListenValue}s so that at most one of them is ultimately selected
 * @author bb
 *
 * This class is not thread-safe.
 */
public class BooleanGroup_Max1 {
	/**
	 * The {@link ReadWriteListenValue} items together with the {@link ValueListener}s that react to their changes
	 */
	IdentityHashMap<ReadWriteListenValue<Boolean>, ValueListener> items=new IdentityHashMap<>();
	/**
	 * The currently active item
	 */
	ReadWriteListenValue<Boolean> active;
	public BooleanGroup_Max1() {
	}
	@SafeVarargs
	public BooleanGroup_Max1(ReadWriteListenValue<Boolean>... members) {
		if(members!=null)
			for(ReadWriteListenValue<Boolean> m: members)
				add(m);
	}
	public BooleanGroup_Max1(Iterable<? extends ReadWriteListenValue<Boolean>> members) {
		if(members!=null)
			members.forEach(this::add);
	}
	/**
	 * Add an item. If its value is <code>true</code>, it will become the new active item of this group.
	 * @param elem
	 */
	public void add(ReadWriteListenValue<Boolean> elem) {
		ValueListener cl = items.get(elem);
		if(cl==null) {
			cl = e -> {
				if(Boolean.TRUE.equals(elem.get())) {
					if(elem!=active) {
						if(active!=null)
							active.set(false);
						active=elem;
					}
				}else {
					if(elem==active) {
						active=null;
					}
				}
			};
			items.put(elem,  cl);
			elem.addValueListener(cl);
			if(callback!=null)
				callback.run();
		};
		cl.valueChanged(null);
	}
	/**
	 * Remove an item. If it was <code>true</code>, i.e. the active item of the group,
	 * the group will be without an active item until one of the remaining items is
	 * set to <code>true</code> 
	 * @param elem
	 */
	public void remove(ReadWriteListenValue<Boolean> elem) {
		if(elem==active) {
			active=null;
		}
		ValueListener cl=items.get(elem);
		if(cl!=null) {
			elem.removeValueListener(cl);
			items.remove(elem);
		}
	}
	/**
	 * Set the active item to <code>false</code>. The group will be without an active item until one its items is
	 * set to <code>true</code> 
	 */
	public void deselectAll() {
		if(active!=null) {
			active.set(false);
		}
	}
	/**
	 * Same as {@link #clear()}
	 */
	public void destroy() {
		clear();
	}
	/**
	 * Get the supposedly only {@link ReadWriteListenValue} item of the group that is <code>true</code>, or <code>null</code> if there is none
	 * @return
	 */
	public ReadWriteListenValue<Boolean> getSelected(){
		return active;
	}
	/**
	 * Remove all items from the group
	 */
	public void clear() {
		active=null;
		for(Map.Entry<ReadWriteListenValue<Boolean>, ValueListener> e: items.entrySet())
			e.getKey().removeValueListener(e.getValue());
		items.clear();
	}
	Runnable callback;
	/**
	 * Set a callback that is invoked when a {@link ValueEvent} happens on one of the group's items.
	 * The callback is run after the {@link ValueEvent} has been handled.
	 * @param callback
	 * @return
	 */
	public BooleanGroup_Max1 afterChange(Runnable callback) {
		this.callback = callback;
		return this;
	}
}
