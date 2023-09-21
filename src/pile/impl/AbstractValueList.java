package pile.impl;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

import pile.aspect.ValueBracket;
import pile.aspect.combinations.Pile;
import pile.aspect.combinations.ReadDependency;
import pile.aspect.combinations.ReadListenDependency;
import pile.aspect.combinations.ReadWriteListenDependency;
import pile.aspect.suppress.Suppressor;
import pile.specialized_bool.PileBool;
import pile.specialized_bool.SealBool;
import pile.specialized_bool.combinations.ReadListenDependencyBool;
import pile.specialized_int.SealInt;
import pile.specialized_int.combinations.ReadListenDependencyInt;
import pile.utils.Functional;

/**
 * A list of {@linkplain ReadWriteListenDependency reactive values}
 * @author bb
 * 
 * @param <E>
 */
public abstract class 
AbstractValueList<Self extends AbstractValueList<Self, E>, E> 
extends PileCompound 
implements Iterable<E>{
	//	private final static Logger log=Logger.getLogger("ValueList");
	/**
	 * The elements
	 */
	ArrayList<ReadWriteListenDependency<E>> elems=new ArrayList<>();

	/**
	 * A name for debugging purposes
	 */
	String name;

	@Override
	public String autoCompundName() {
		return name==null?"A list":name;
	}
	/**
	 * 
	 * @param name  A name for debugging purposes. The {@link #head()} will be named after this. too.
	 */
	public AbstractValueList(String name) {
		this.name=name;
		head().setName(name+".head");
	}
	protected void subclassDestroy(){
		try(Suppressor s = head().suppressAutoValidation()){
			clear();
			head().destroy();
		}
	}
	/**
	 * Clear the list. If the list is nonempty, this removes all elements,
	 *  {@link ReadDependency#destroy() destroy}s 
	 * the values containing them, possibly fires an event and revalidates the
	 *  {@link PileCompound#head }
	 */
	public synchronized void clear() {
		int oldSize=elems.size();
		if(oldSize==0)
			return;
		try(Suppressor s = head().suppressAutoValidation()){
			head().permaInvalidate();
			for(int i=elems.size()-1; i>=0; --i){
				ReadDependency<E> v = elems.get(i);
				head().removeDependency(v);
				elems.remove(i);
				v.destroy();

			}
		}finally {
			head().autoValidate();
		}
		intervalRemoved(0, oldSize-1);
	}
	/**
	 * Clear the list. If the list is nonempty, this removes all elements,
	 *  {@link ReadDependency#destroy() destroy}s 
	 * the values containing them, possibly fires an event and revalidates the
	 *  {@link PileCompound#head }
	 */
	public synchronized void removeIf(Predicate<? super E> filter) {
		int oldSize=elems.size();
		if(oldSize==0)
			return;
		int firstRemoved = -1;
		int removedCount = 0;
		try(Suppressor s = head().suppressAutoValidation()){
			head().permaInvalidate();
			for(int i=0; i<elems.size(); ++i){
				ReadWriteListenDependency<E> v = elems.get(i);
				if(filter.test(v.get())) {
					head().removeDependency(v);
					elems.set(i, null);
					v.destroy();
					++ removedCount;
					if(firstRemoved==-1)
						firstRemoved = i;
				}else if(removedCount!=0) {
					elems.set(i-removedCount, v);
				}
			}
			for(int i=0; i<removedCount; ++i) {
				elems.remove(elems.size()-1);
			}
		}finally {
			head().autoValidate();
		}
		if(firstRemoved>=0) {
			contentsChanged(firstRemoved, elems.size()+removedCount);
		}
	}
	/**
	 * Called after an interval of consecutive indices was removed. 
	 * Both indices are inclusive and refer to the situation before
	 * the interval was removed.
	 * @param index0
	 * @param index1
	 */
	protected void intervalRemoved(int begin, int end) {}
	/**
	 * Called after an interval of consecutive indices was added. 
	 * Both indices are inclusive and refer to the situation after the interval was added.
	 * @param index0
	 * @param index1
	 */
	protected  void intervalAdded(int begin, int end) {}
	/**
	 * Called after an interval of consecutive indices was changed in a complex way. 
	 * Both indices are inclusive and refer to the situation after the interval was added.
	 * @param index0
	 * @param index1
	 */
	protected  void contentsChanged(int begin, int end) {}
	/**
	 * Add an element at the end of the list. 
	 * It is wrapped in a {@link ReadDependency} by the {@link #wrap(Object)} method.
	 * @param e
	 */
	public synchronized void add(E e){
		add(size(), e);
	}
	/**
	 * Insert an element at the specified index. 
	 * It is wrapped in an {@link ReadDependency} by the {@link #wrap(Object)} method.
	 * @param index
	 * @param e
	 */
	public synchronized void add(int index, E e){
		addV(index, wrap(e));
	}

	/**
	 * Override this to provide a standard way for wrapping simple values into observable values
	 * @param e
	 * @return
	 */
	protected ReadWriteListenDependency<E> wrap(E e) {
		throw new UnsupportedOperationException("AbstractValueList::wrap has to be overriden to be used");
	}
	
	/**
	 * Brackets that might be installed on all elements of this list.
	 * Whether and how this field is used is up to the concrete subclass.
	 * 
	 */
	protected ArrayList<ValueBracket<? super E, ? super ReadListenDependency<? extends E>>> brackets;
	
	/**
	 * Add a {@link ValueBracket} that might be installed on all elements of this list.
	 * Whether and how this wil be used is up to the concrete subclass.
	 * @see #brackets
	 * 
	 */
	public void addBracket(ValueBracket<? super E, ? super ReadListenDependency<? extends E>> br) {
		if(brackets==null)
			brackets=new ArrayList<>();
		brackets.add(br);
	}
	/**
	 * Add an element at the end of the list, wrapped in the given {@link ReadWriteListenDependency}.
	 * Note that the {@link ReadWriteListenDependency} will be 
	 * {@link ReadDependency#destroy() destroy}ed when the element is removed.
	 * @param e
	 */
	public synchronized void addV(ReadWriteListenDependency<E> e){
		addV(size(), e);
	}
	

	/**
	 * Insert an element at the specified index, wrapped in the given {@link ReadWriteListenDependency}.
	 * Note that the {@link ReadWriteListenDependency} will be 
	 * {@link ReadDependency#destroy() destroy}ed when the element is removed.
	 * @param index
	 * @param e
	 */
	public synchronized void addV(int index, ReadWriteListenDependency<E> e){
		try(Suppressor s = head().suppressAutoValidation()){
			head().addDependency(e);
			elems.add(index, e);
		}
		intervalAdded(index, index);
	}

	/**
	 * Remove an element by index.
	 * @param i
	 */
	public synchronized void remove(int i){
		try(Suppressor s = head().suppressAutoValidation()){
			ReadDependency<E> v = elems.get(i);
			head().removeDependency(v);
			elems.remove(i);
			v.destroy();
		}
		intervalRemoved(i, i);
	}
	/**
	 * Get the {@link ReadWriteListenDependency} wrapping the element at the specified index.
	 * @param index
	 * @return
	 */
	public ReadWriteListenDependency<E> get(int index){
		return elems.get(index);
	}
	/**
	 * Get the number of elements
	 * @return
	 */
	public int size(){
		return elems.size();
	}
	/**
	 * This field caches the result of {@link #autoIterable()}
	 */
	private Iterable<ReadWriteListenDependency<E>> autoIterable;
	/**
	 * View this as an {@link Iterable} that iterates on the values wrapping the elements instead of over the elements
	 * themselves.
	 * @return
	 */
	public final Iterable<ReadWriteListenDependency<E>> autoIterable(){
		if(autoIterable==null) {
			autoIterable= new Iterable<ReadWriteListenDependency<E>>() {
				@Override
				public Iterator<ReadWriteListenDependency<E>> iterator() {
					return autoIterator();
				}
			};
		}
		return autoIterable;
	};
	/**
	 * Make an {@link Iterator} that iterates on the values wrapping the elements instead of over the elements
	 * themselves. The iterator supports the {@link Iterator#remove()} method.
	 * @return
	 */
	public Iterator<ReadWriteListenDependency<E>> autoIterator() {
		return new Iterator<ReadWriteListenDependency<E>>() {
			Iterator<ReadWriteListenDependency<E>> b=elems.iterator();
			ReadWriteListenDependency<E> last=null;
			int i=-1;
			@Override
			public boolean hasNext() {
				return b.hasNext();
			}

			@Override
			public ReadWriteListenDependency<E> next() {
				++i;
				return (last=b.next());
			}

			@Override
			public void remove() {
				synchronized(AbstractValueList.this) {
					try(Suppressor s = head().suppressAutoValidation()){
						head().removeDependency(last);
						b.remove();
						last.destroy();
						last=null;
					}
					intervalRemoved(i, i);
				}
				--i;
			}
		};
	}
	/**
	 * Remove the first element that {@link #equals(Object)} the given value.
	 * @param e
	 */
	public void removeFirst(E e) {
		if(e==null)
			removeFirst(Functional.IS_NULL);
		else
			removeFirst(e::equals);
	}
	/**
	 * Remove the first element that fulfills the given {@link Predicate}
	 * @param e
	 */
	public void removeFirst(Predicate<? super E> e) {
		for(Iterator<E> it=iterator(); it.hasNext(); ) {
			if(e.test(it.next())) {
				it.remove();
				return;
			}
		}
	}
	/**
	 * Remove the first element that fulfills the given {@link Predicate}
	 * @param e
	 */
	public int indexOfFirst(Predicate<? super E> e) {
		int index = 0;
		for(Iterator<E> it=iterator(); it.hasNext(); ) {
			if(e.test(it.next())) {
				return index;
			}
			++index;
		}
		return -1;
	}
	/**
	 * Remove the first element that fulfills the given {@link Predicate}
	 * @param e
	 */
	public E getFirst(Predicate<? super E> e) {
		for(Iterator<E> it=iterator(); it.hasNext(); ) {
			E v = it.next();
			if(e.test(v)) {
				return v;
			}
		}
		return null;
	}
	/**
	 * Make an Iterator that iterates over the values in this list. {@link Iterator#remove()} is supported.
	 */
	@Override
	public Iterator<E> iterator() {
		return new Iterator<E>() {
			Iterator<ReadWriteListenDependency<E>> b=elems.iterator();
			ReadDependency<E> last=null;
			int i=-1;
			@Override
			public boolean hasNext() {
				return b.hasNext();
			}

			@Override
			public E next() {
				++i;
				return (last=b.next()).get();
			}

			@Override
			public void remove() {
				synchronized(AbstractValueList.this) {
					try(Suppressor s = head().suppressAutoValidation()){
						head().removeDependency(last);
						b.remove();
						last.destroy();
						last=null;
					}
					intervalRemoved(i, i);

				}

				--i;
			}
		};
	}
	/**
	 * 
	 * @return {@link #size()}
	 */
	public int getSize() {
		return size();
	}
	/**
	 * Get the unwrapped element at the given index
	 * @param index
	 * @throws IndexOutOfBoundsException
	 * @return
	 */
	public E getElementAt(int index) {
		return get(index).get();
	}

	/**
	 * Test if this List is empty
	 * @return
	 */
	public boolean isEmpty() {
		return size()==0;
	}
	/**
	 * Convert this {@link AbstractValueList} to an {@link ArrayList} containing the current unwrapped elements.
	 * @return
	 */
	public ArrayList<E> toArrayList() {
		ArrayList<E> ret=new ArrayList<>();
		return toArrayList(this, ret);
	}
	/**
	 * Convert this {@link AbstractValueList} to an {@link ArrayList} containing 
	 * the current unwrapped elements that pass the {@link Predicate}
	 * @return
	 */
	public ArrayList<E> toArrayList(Predicate<? super E> filter) {
		ArrayList<E> ret=new ArrayList<>();
		return toArrayList(this, ret, filter);
	}
	/**
	 * Convert this {@link AbstractValueList} to an {@link ArrayList} 
	 * containing the result of mapping
	 * the current unwrapped elements that pass the {@link Predicate}
	 * through the given {@link Function}.
	 * @return
	 */
	public <R> ArrayList<R> toArrayList(Predicate<? super E> filter, Function<? super E, ? extends R> map) {
		ArrayList<R> ret=new ArrayList<>();
		return toArrayList(this, ret, filter, map);
	}
	/**
	 * Convert an {@link AbstractValueList} to an {@link ArrayList} containing its current unwrapped  elements,
	 * 
	 * @param <F> The element type of the {@link ArrayList}
	 * @param <E> The element type of the {@link AbstractValueList}
	 * @param l
	 * @param ret
	 * @return
	 */
	public static <F, E extends F> ArrayList<F> toArrayList(AbstractValueList<?, E> l, ArrayList<F> ret) {
		synchronized (l) {
			for(E e: l)
				ret.add(e);			
		}
		return ret;
	}
	/**
	 * Convert an {@link AbstractValueList} to an {@link ArrayList} containing its current unwrapped elements
	 * that pass the {@link Predicate}
	 * 
	 * @param <F> The element type of the {@link ArrayList}
	 * @param <E> The element type of the {@link AbstractValueList}
	 * @param l
	 * @param ret
	 * @param filter
	 * @return
	 */
	public static <F, E extends F> ArrayList<F> toArrayList(AbstractValueList<?, E> l, ArrayList<F> ret, Predicate<? super E> filter) {
		synchronized (l) {
			for(E e: l)
				if(filter.test(e))
					ret.add(e);			
		}
		return ret;
	}
	/**
	 * Convert an {@link AbstractValueList} to an {@link ArrayList} the result of mapping
	 * the current unwrapped elements that pass the {@link Predicate}
	 * through the given {@link Function}.
	 * 
	 * @param <F> The element type of the {@link ArrayList}
	 * @param <E> The element type of the {@link AbstractValueList}
	 * @param l
	 * @param ret
	 * @param filter
	 * @param map
	 * @return
	 */
	public static <E, R> ArrayList<R> toArrayList(AbstractValueList<?, E> l, ArrayList<R> ret, Predicate<? super E> filter, Function<? super E, ? extends R> map) {
		synchronized (l) {
			for(E e: l)
				if(filter.test(e))
					ret.add(map.apply(e));			
		}
		return ret;
	}
	/**
	 * Find the first index where an element equal to the given one is contained, or -1 if there is none.
	 * @param sel
	 * @return
	 */
	public int indexOf(E sel) {
		int ret=0;
		for(E e: this) {
			if(Objects.equals(sel, e))
				return ret;
			++ret;
		}
		return -1;
	}
	/**
	 * This field caches the result of {@link #sizeR()}
	 */
	volatile SealInt sizeR=null;
	/**
	 * Used to set {@link #sizeR()}
	 * @return
	 */
	Consumer<? super Boolean> setSizeR=Functional.NOP;

	/**
	 * Get a lazily initialized value that follows the size of this list.
	 * @return
	 */
	public ReadListenDependencyInt sizeR() {
		SealInt localRef = sizeR;
		if (localRef == null) {
			synchronized (this) {
				localRef = sizeR;
				if (localRef == null) {
					localRef = Piles.sealed(size()).recompute(()->{
						return size();
					})
							.name((name==null?"?":name)+".size")
							.parent(this)
							.whenChanged(head());
					sizeR = localRef;
				}
			}
		}
		return localRef;

	}
	/**
	 * Give a name to this {@link AbstractValueList} for debugging purposes.
	 * @param string
	 * @return
	 */
	public Self setName(String string) {
		name=string;
		return self();
	}
	/**
	 * 
	 * @return {@code this}
	 */
	public abstract Self self();

	volatile private ReadListenDependencyBool emptyV;
	/**
	 * Lazily create a value that follows the emptiness status of this list.
	 * @return
	 */
	public ReadListenDependencyBool isEmptyR() {
		ReadListenDependencyBool local = emptyV;
		if(local==null) {
			synchronized (this) {
				local = emptyV;
				if(local==null) {
					SealBool ret = PileBool.equal(sizeR(), 0);
					ret.setName((name==null?"?":name)+" empty?");
					ret.owner=this;
					local = ret.validBuffer();
					emptyV = local;
				}
			}
		}
		return local;
		
	}

	@Override
	public String toString() {
		if(isEmpty())
			return "[]";
		StringBuilder sb = new StringBuilder();
		sb.append("[");
		Iterator<E> elems = iterator();
		sb.append(elems.next());
		while(elems.hasNext())
			sb.append(", ").append(elems.next());
		
		return sb.append("]").toString();
	}

	public Pile<Object> head() {return super.head();}
	volatile boolean destroyed;

	/**
	 * 
	 * @return whether this object has been destroyed and should not be used anymore.
	 */
	public boolean isDestroyed() {
		return destroyed;
	}

	@Override
	public final void destroy() {
		if(destroyed)
			return;
		destroyed=true;
		subclassDestroy();

	}

}
