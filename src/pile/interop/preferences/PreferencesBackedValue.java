package pile.interop.preferences;

import java.util.Objects;
import java.util.function.BiPredicate;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.prefs.PreferenceChangeEvent;
import java.util.prefs.PreferenceChangeListener;
import java.util.prefs.Preferences;

import pile.aspect.AlwaysValid;
import pile.aspect.Dependency;
import pile.aspect.combinations.ReadWriteDependency;
import pile.aspect.combinations.ReadWriteListenValue;
import pile.aspect.listen.ListenValue;
import pile.aspect.suppress.Suppressor;
import pile.impl.Independent;
import pile.impl.Piles;
import pile.interop.wait.WaitService;
import pile.specialized_bool.combinations.ReadListenDependencyBool;
import pile.utils.Bijection;
import pile.utils.WeakCleanupWithRunnable;

/**
 * A reactive value that is reflected in real time in a {@link Preferences} node.
 * 
 * This class does not implement {@link Dependency}; 
 * @param <T>
 */
public class PreferencesBackedValue<T> implements 
ReadWriteListenValue<T>, 
ListenValue.Managed, 
AlwaysValid<T>
{
	private static final Logger logger = Logger.getLogger("PreferencesBackedValue");
	final Preferences node;
	final String key;
	final Bijection<T, String> encode;
	final Bijection<String, T> decode;
	private Supplier<? extends T> defaultValue;
	private T currentValue;
	private String currentString;
	private BiPredicate<? super T, ? super T> equivalence = ReadWriteDependency.DEFAULT_BIJECT_EQUIVALENCE;
	
	/**
	 * Call this if you need to depend on the value, as it does not implement {@link Dependency}.
	 * 
	 * Convenience method; redirects to {@link #writableValidBuffer_memo()}.
	 * @return
	 */
	public Independent<T> asDependency(){
		return writableValidBuffer_memo();
	}
	
	volatile ListenValue.ListenerManager manager;
	public PreferencesBackedValue(Preferences node, String key, Bijection<T, String> codec, Supplier<? extends T> defaultValue) {
        this.node = node;
        this.key = key;
        this.encode = codec;
        this.decode = codec.inverse();
        this.defaultValue = defaultValue;
        WeakCleanupWithRunnable<PreferencesBackedValue<T>> belong=new WeakCleanupWithRunnable<>(this, null);
        node.addPreferenceChangeListener(new PreferenceChangeListener() {
        	{
        		belong.setCleanupAction(() -> node.removePreferenceChangeListener(this));
        	}
			@Override
			public void preferenceChange(PreferenceChangeEvent evt) {
				if(evt.getKey().equals(key)) {
					 PreferencesBackedValue<T> b = belong.get();
					 if(b!= null) {
						 b.read();
					 }else {
		        		node.removePreferenceChangeListener(this);		 
					 }
				}
			}
		});
        read();
        
    }
	@Override
	public T get() {
        return currentValue;
    }
	public T set(T value) {
        write(value, false);
        return value;
    }
	@Override
	public void accept(T t) {
		write(t, false);
	}
	public void reset() {
		node.remove(key);
	}
	
	private void write(T value, boolean force) {
		if(_write(value, force))
			fireValueChange();
	}
	private void read() {
        if(_read())
			fireValueChange();
    }
	private synchronized boolean _write(T value, boolean force) {
		if(!force && equivalence.test(value, currentValue))
			return false;
		currentValue = value;
		currentString = encode.apply(value);
		node.put(key, currentString);
		notifyAll();
		return true;
	}
	private synchronized boolean _read() {
		String newString = node.get(key, null);
        if(newString == null) {
            currentValue = defaultValue.get();
            currentString = encode.apply(currentValue);
    		notifyAll();
    		return true;
       }else {
        	if(Objects.equals(newString, currentString))
        		return false;
        	T oldValue = currentValue;
        	try {
        		currentValue = decode.apply(newString);
        		newString = currentString;
        	}catch(Exception e) {
        		logger.log(Level.WARNING, "Error decoding preferences value for key " + key, e);
        		currentValue = defaultValue.get();
                newString = encode.apply(currentValue);
        	}finally {
        	    notifyAll();
        	}
        	return !equivalence.test(oldValue, currentValue);
       }
	}
	@Override
	public boolean willNeverChange() {
		return false;
	}
	@Override
	public String dependencyName() {
		return node.absolutePath() + "/" + key;
	}
	@Override
	public void await(WaitService ws, BooleanSupplier c) throws InterruptedException {
		while(!c.getAsBoolean()) {
			synchronized (this) {
				ws.wait(this, 1000);
			}
		}
	}
	@Override
	public boolean await(WaitService ws, BooleanSupplier c, long millis) throws InterruptedException {
		long t0 = System.currentTimeMillis();
		while(!c.getAsBoolean()) {
			synchronized (this) {
				long left = millis - (System.currentTimeMillis()-t0);
				if(left<=0)
					return false;
				ws.wait(this, Math.min(1000, left));
			}
		}
		return true;
	}

	@Override
	public BiPredicate<? super T, ? super T> _getEquivalence() {
		return equivalence;
	}
	@Override
	public boolean isInTransaction() {
		return false;
	}
	@Override
	public ReadListenDependencyBool inTransactionValue() {
		return Piles.FALSE;
	}
	@Override
	public boolean isDestroyed() {
		return false;
	}
	@Override
	public void __beginTransaction(boolean invalidate) {
	}
	@Override
	public void permaInvalidate() {
	}
	@Override
	public void valueMutated() {
		write(currentValue, true);
	}
	@Override
	public void _setEquivalence(BiPredicate<? super T, ? super T> equivalence) {
		this.equivalence = equivalence;
	}
	@Override
	public void revalidate() {		
	}
	@Override
	public void __endTransaction(boolean changedIfOldInvalid) {
	}
	@Override
	public T applyCorrection(T v) {
		return decode.apply(encode.apply(v));
	}
	@Override
	public boolean remembersLastValue() {
		return false;
	}
	@Override
	public void storeLastValueNow() {		
	}
	@Override
	public void resetToLastValue() {		
	}

	@Override
	public ListenerManager _getListenerManager() {
		if(manager == null) {
			synchronized (this) {
				if(manager == null)
					manager = new ListenerManager(this);
			}
			
		}
		return manager;
	}

	@Override
	public Suppressor suppressRememberLastValue() {
		return Suppressor.NOP;
	}
	public void fireValueChange() {
		if(manager!=null)
			manager.fireValueChange();
	}

	@Override
	public String toString() {
		return key+"="+currentString;
	}

	
}
