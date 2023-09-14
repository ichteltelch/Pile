package pile.relation;

import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;

import pile.aspect.WriteValue;
import pile.aspect.combinations.ReadWriteListenValue;
import pile.aspect.listen.ValueEvent;
import pile.aspect.listen.ValueListener;


/**
 * Manage a set of boolean {@link ReadWriteListenValue}s so that at least one of them is ultimately selected
 * @author bb
 *
 * This class is not thread-safe.
 */
public class BooleanGroup_Min1 {
	/**
	 * The {@link ReadWriteListenValue} items together with the {@link ValueListener}s that react to their changes
	 */
	IdentityHashMap<ReadWriteListenValue<Boolean>, ValueListener> items=new IdentityHashMap<>();

	/**
	 * The currently active item
	 */
	Set<ReadWriteListenValue<Boolean>> active=new HashSet<>();

	boolean invertWhenLastIsDeselected;
	
	public BooleanGroup_Min1() {
	}
	/**
	 * Specify whether the selection pattern should be inverted when the last item is deselected, that is,
	 * the other items get selected.
	 * @param v
	 * @return
	 */
	public BooleanGroup_Min1 setInvertWhenLastDeselected(boolean v) {
		invertWhenLastIsDeselected = v;
		return this;
	}
	@SafeVarargs
	public BooleanGroup_Min1(ReadWriteListenValue<Boolean>... members) {
		if(members!=null)
			for(ReadWriteListenValue<Boolean> m: members)
				add(m);
	}
	public BooleanGroup_Min1(Iterable<? extends ReadWriteListenValue<Boolean>> members) {
		if(members!=null)
			members.forEach(this::add);
	}
	/**
	 * Add an item to the group.
	 * @param elem
	 */	
	public void add(ReadWriteListenValue<Boolean> elem) {
		ValueListener cl = items.get(elem);
		if(cl==null) {
			cl = e -> {
				if(Boolean.TRUE.equals(elem.get())) {
					active.add(elem);
				}else {
					active.remove(elem);
					if(active.isEmpty()) {
						if(invertWhenLastIsDeselected && items.size()>=2) {
							for(WriteValue<Boolean> v: items.keySet()) {
								if(v!=elem)
									v.set(true);
							}
						}else {
							elem.set(true);
						}
					}
				}
				if(callback!=null)
					callback.run();
			};
			items.put(elem,  cl);
			elem.addValueListener(cl);
		};
		if(Boolean.TRUE.equals(elem.get()))
			active.add(elem);
	}
	/**
	 * Remove an item. If it was <code>true</code>, i.e. the active item of the group,
	 * the group will be without an active item until one of the remaining items is
	 * set to <code>true</code> 
	 * @param elem
	 */
	public void remove(ReadWriteListenValue<Boolean> elem) {
		if(active.contains(elem)) {
			active.remove(elem);
		}
		ValueListener cl=items.get(elem);
		if(cl!=null) {
			elem.removeValueListener(cl);
			items.remove(elem);
		}
	}
	/**
	 * Same as {@link #clear()}
	 */
	public void destroy() {
		clear();
	}
	/**
	 * Remove all items from the group
	 */	
	public void clear() {
		active.clear();
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
	public BooleanGroup_Min1 afterChange(Runnable callback) {
		this.callback = callback;
		return this;
	}
}
