package pile.relation;


import java.util.IdentityHashMap;
import java.util.Map;

import pile.aspect.combinations.ReadWriteListenValue;
import pile.aspect.listen.ValueEvent;
import pile.aspect.listen.ValueListener;
import pile.interop.exec.StandardExecutors;

/**
 * Manage a set of boolean {@link ReadWriteListenValue}s so that exactly one of them is ultimately selected.
 * 
 * This class is not thread-safe.
 * @author bb
 *
 */
public class BooleanGroup_Exactly1 {
	/**
	 * The {@link ReadWriteListenValue} items together with the {@link ValueListener}s that react to their changes
	 */
	IdentityHashMap<ReadWriteListenValue<Boolean>, ValueListener> items=new IdentityHashMap<>();
	/**
	 * If an item gets set to <code>false</code>, another one must become <code>true</code>.
	 * This map is used to determine which one will be activated.
	 */
	IdentityHashMap<ReadWriteListenValue<Boolean>, ReadWriteListenValue<Boolean>> replacements=new IdentityHashMap<>();

	/**
	 * The currently active item
	 */
	ReadWriteListenValue<Boolean> active;
	
	public BooleanGroup_Exactly1() {
	}
	@SafeVarargs
	public BooleanGroup_Exactly1(ReadWriteListenValue<Boolean>... members) {
		if(members!=null)
			for(ReadWriteListenValue<Boolean> m: members)
				add(m);
	}
	public BooleanGroup_Exactly1(Iterable<? extends ReadWriteListenValue<Boolean>> members) {
		if(members!=null)
			members.forEach(this::add);
	}
	/**
	 * Set the replacement for an item. If the item was the only one active and was deselected,
	 * the replacement is selected instead.
	 * This does not work unless the replacement was will be also {@link #add(ReadWriteListenValue) add}ed as in item.
	 * @param elem
	 * @param repl
	 */
	public void setReplacement(ReadWriteListenValue<Boolean> elem, ReadWriteListenValue<Boolean> repl) {
		if(elem==repl)
			replacements.remove(elem);
		else
			replacements.put(elem, repl);
	}
	/**
	 * Add an item and define its replacement. 
	 * If its value is <code>true</code>, it will become the new active item of this group.
	 * @see #setReplacement(ReadWriteListenValue, ReadWriteListenValue)
	 * @param elem
	 * @param repl The replacement is not added by this method (unless identical to {@code elem})
	 */
	public void add(ReadWriteListenValue<Boolean> elem, ReadWriteListenValue<Boolean> repl) {
		setReplacement(elem, repl);
		add(elem);
	}
	/**
	 * Add an item. 
	 * If its value is <code>true</code>, it will become the new active item of this group.
	 * @param elem
	 */
	public void add(ReadWriteListenValue<Boolean> elem) {
		ValueListener cl = items.get(elem);
		if(cl==null) {
			cl = e -> {
				ReadWriteListenValue<Boolean> a = active;
				if(Boolean.TRUE.equals(elem.get())) {
					if(elem!=a) {
						active=elem;
						if(a!=null)
							a.set(false);
					}
				}else if(Boolean.FALSE.equals(elem.get())){
					if(elem==a) {
						active=null;
						ReadWriteListenValue<Boolean> r = replacements.get(elem);
						if(r==null || !items.containsKey(r))
							r = a;
						final ReadWriteListenValue<Boolean> fr = r;
						StandardExecutors.unlimited().execute(()->fr.set(true));
					}
				}
				if(callback!=null)
					callback.run();
			};

			items.put(elem,  cl);
			elem.addValueListener(cl);
		};
		cl.valueChanged(null);
	}
	/**
	 * Remove an item. If it was <code>true</code>, i.e. the active item of the group,
	 * the group will be without an active item until one of the remaining items is
	 * set to <code>true</code>, unless the defined replacement of the removed item can be
	 * set to <code>true</code>.
	 * @param elem
	 */
	public void remove(ReadWriteListenValue<Boolean> elem) {
		ReadWriteListenValue<Boolean> a = active;
		ReadWriteListenValue<Boolean> r;
		if(elem==a) {
			a=active=null;
			r = replacements.get(elem);
		}else {
			r = null;
		}
			

		
		ValueListener cl=items.get(elem);
		if(cl!=null) {
			elem.removeValueListener(cl);
			items.remove(elem);
		}
		if(r==null || !items.containsKey(r))
			;
		else
			StandardExecutors.unlimited().execute(()->r.set(true));

	}
	/**
	 * Set the active item to <code>false</code>. The group will be without an active item until one its items is
	 * set to <code>true</code> 
	 */
	public void deselectAll() {
		ReadWriteListenValue<Boolean> a = active;
		if(a!=null) {
			active = null;
			a.set(false);
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
	public BooleanGroup_Exactly1 afterChange(Runnable callback) {
		this.callback = callback;
		return this;
	}
}
