package pile.interop.preferences;

import java.util.Objects;
import java.util.function.BiPredicate;
import java.util.function.Function;
import java.util.prefs.PreferenceChangeListener;
import java.util.prefs.Preferences;

import pile.aspect.LastValueRememberer;
import pile.aspect.combinations.ReadListenDependency;
import pile.builder.IndependentBuilder;
import pile.impl.Independent;
import pile.impl.Piles;
import pile.specialized_String.IndependentString;
import pile.specialized_String.combinations.LastValueRemembererString;
import pile.specialized_bool.IndependentBool;
import pile.specialized_bool.combinations.LastValueRemembererBool;
import pile.specialized_double.IndependentDouble;
import pile.specialized_double.combinations.LastValueRemembererDouble;
import pile.specialized_int.IndependentInt;
import pile.specialized_int.combinations.LastValueRemembererInt;
import pile.utils.WeakCleanupWithRunnable;

/**
 * Connection between the value package and java's {@link Preferences} API.
 * @author bb
 *
 */
public class PrefInterop {
	/**
	 * What should be done when a <code>null</code> is written to a value representing a preference
	 * that can or should not be <code>null</code>, for example because it is of a primitive type.
	 * @author bb
	 *
	 */
	public static enum NullBehavior{
		/**
		 * Delete the key
		 */
		DELETE, 
		/**
		 * Do not modify the storage (the {@link Independent} itself will remain <code>null</code>)
		 */
		IGNORE,
		/**
		 * Attempt to store a <code>null</code> reference anyway. This is not supported currently.
		 */
		STORE_NULL,
		/**
		 * Write a default value to the store
		 */
		STORE_DEFAULT
	}
	/**
	 * Equivalence relation for the string representations of objects.
	 * Two objects are equivalent if they are identical or if they both are not <code>null</code>
	 * and their {@link Object#toString()} methods return equal values.
	 */
	private static final BiPredicate<? super Double, ? super Double> STRING_EQUIVALENCE = (a, b)->a==b?true:(a==null|b==null)?false:Objects.equals(a.toString(), b.toString());
	/**
	 * Make an {@link IndependentBool} representing a boolean value stored in a {@link Preferences} node
	 * @param node The node
	 * @param key The Key under which the node is stored
	 * @param defaultValue The default value to use when the key does not exist
	 * @param nb What to do it a <code>null</code> is written to the {@link IndependentBool}
	 * @return
	 */
	public static IndependentBool boolPreference(Preferences node, String key, boolean defaultValue, NullBehavior _nb){
		NullBehavior nb = _nb==null?NullBehavior.IGNORE:_nb;
		if(nb == NullBehavior.STORE_NULL) {
			throw new IllegalArgumentException("The NullBehavior for a boolean preference cannot be STORE_NULL");
		}
		ThreadLocal<Boolean> inChange=new ThreadLocal<>();
		IndependentBool ret = new IndependentBool(null);
		WeakCleanupWithRunnable<IndependentBool> weakRet = new WeakCleanupWithRunnable<>(ret, null);
		PreferenceChangeListener pcl = e->{
			if(key.equals(e.getKey())) {
				IndependentBool strongRet = weakRet.get();
				if(strongRet==null)
					return;
				Boolean changing = inChange.get();
				if(Boolean.TRUE.equals(changing))
					return;
				try {
					inChange.set(true);
					strongRet.set(node.getBoolean(key, defaultValue));
				}finally {
					inChange.set(changing);
				}
			}
		};
		node.addPreferenceChangeListener(pcl);
		weakRet.setCleanupAction(()->node.removePreferenceChangeListener(pcl));
		ret.set(node.getBoolean(key, defaultValue));
		ret.addValueListener(e->{
			Boolean changing = inChange.get();
			if(Boolean.TRUE.equals(changing))
				return;
			try {
				Boolean value = ret.get();
				if(value==null) {
					switch(nb) {
					case DELETE:
						node.remove(key);
						return;
					case IGNORE:
						return;
					case STORE_DEFAULT:
						value = defaultValue;
						break;
					case STORE_NULL:
						assert false;
						break;
					}
				}
				node.putBoolean(key, value);
			}finally {
				inChange.set(changing);
			}
		});
		return ret;
	}
	/**
	 * Make an {@link IndependentDouble} representing a double precision floating point value 
	 * stored in a {@link Preferences} node
	 * @param node The node
	 * @param key The Key under which the node is stored
	 * @param defaultValue The default value to use when the key does not exist
	 * @param nb What to do it a <code>null</code> is written to the {@link IndependentDouble}
	 * @return
	 */
	public static IndependentDouble doublePreference(Preferences node, String key, double defaultValue, NullBehavior _nb){
		NullBehavior nb = _nb==null?NullBehavior.IGNORE:_nb;
		return doublePreferenceBuilder(node, key, defaultValue, nb).build();
	}
	/**
	 * Make an {@link IndependentDouble} representing a double precision floating point value 
	 * stored in a {@link Preferences} node
	 * @param node The node
	 * @param key The Key under which the node is stored
	 * @param defaultValue The default value to use when the key does not exist
	 * @param nb What to do it a <code>null</code> is written to the {@link IndependentDouble}
	 * @return a builder for whose {@link IndependentBuilder#build()} method will return the
	 * configured value. You can use other methods of the builder before you do that, for example to configure
	 * bounds.
	 */
	public static IndependentBuilder<IndependentDouble, Double> doublePreferenceBuilder(Preferences node, String key, double defaultValue, NullBehavior _nb){
		NullBehavior nb = _nb==null?NullBehavior.IGNORE:_nb;
		if(nb == NullBehavior.STORE_NULL) {
			throw new IllegalArgumentException("The NullBehavior for a double preference cannot be STORE_NULL");
		}
		ThreadLocal<Boolean> inChange=new ThreadLocal<>();
		IndependentBuilder<IndependentDouble, Double> builder = Piles.independent(node.getDouble(key, defaultValue));
		IndependentDouble ret = builder.valueBeingBuilt();
		builder.neverNull();
		ret._setEquivalence(STRING_EQUIVALENCE);
		WeakCleanupWithRunnable<IndependentDouble> weakRet = new WeakCleanupWithRunnable<>(ret, null);
		PreferenceChangeListener pcl = e->{
			if(key.equals(e.getKey())) {
				IndependentDouble strongRet = weakRet.get();
				if(strongRet==null)
					return;
				Boolean changing = inChange.get();
				if(Boolean.TRUE.equals(changing))
					return;
				try {
					inChange.set(true);
					strongRet.set(node.getDouble(key, defaultValue));
				}finally {
					inChange.set(changing);
				}
			}
		};
		node.addPreferenceChangeListener(pcl);
		weakRet.setCleanupAction(()->node.removePreferenceChangeListener(pcl));
		
		ret.addValueListener(e->{
			Boolean changing = inChange.get();
			if(Boolean.TRUE.equals(changing))
				return;
			try {
				Double value = ret.get();
				if(value==null) {
					switch(nb) {
					case DELETE:
						node.remove(key);
						return;
					case IGNORE:
						return;
					case STORE_DEFAULT:
						value = defaultValue;
						break;
					case STORE_NULL:
						assert false;
						break;
					}
				}
				node.putDouble(key, value);
			}finally {
				inChange.set(changing);
			}
		});
		return builder;
	}
	/**
	 * Make an {@link IndependentInt} representing a integer value stored in a {@link Preferences} node
	 * @param node The node
	 * @param key The Key under which the node is stored
	 * @param defaultValue The default value to use when the key does not exist
	 * @param nb What to do it a <code>null</code> is written to the {@link IndependentInt}
	 * @return
	 */
	public static IndependentInt intPreference(Preferences node, String key, int defaultValue, NullBehavior _nb){
		NullBehavior nb = _nb==null?NullBehavior.IGNORE:_nb;
		return intPreferenceBuilder(node, key, defaultValue, nb).build();
	}
	/**
	 * Make an {@link IndependentInt} representing a integer
	 * value stored in a {@link Preferences} node
	 * @param node The node
	 * @param key The Key under which the node is stored
	 * @param defaultValue The default value to use when the key does not exist
	 * @param nb What to do it a <code>null</code> is written to the {@link IndependentInt}
	 * @return a builder for whose {@link IndependentBuilder#build()} method will return the
	 * configured value. You can use other methods of the builder before you do that, for example to configure
	 */
	public static IndependentBuilder<IndependentInt, Integer> intPreferenceBuilder(Preferences node, String key,
			int defaultValue, NullBehavior _nb){
		NullBehavior nb = _nb==null?NullBehavior.IGNORE:_nb;
		if(nb == NullBehavior.STORE_NULL) {
			throw new IllegalArgumentException("The NullBehavior for an integer preference cannot be STORE_NULL");
		}
		ThreadLocal<Boolean> inChange=new ThreadLocal<>();
		IndependentBuilder<IndependentInt, Integer> builder = Piles.independent(node.getInt(key, defaultValue));
		builder.neverNull();
		IndependentInt ret = builder.valueBeingBuilt();
		WeakCleanupWithRunnable<IndependentInt> weakRet = new WeakCleanupWithRunnable<>(ret, null);
		PreferenceChangeListener pcl = e->{
			if(key.equals(e.getKey())) {
				IndependentInt strongRet = weakRet.get();
				if(strongRet==null)
					return;
				Boolean changing = inChange.get();
				if(Boolean.TRUE.equals(changing))
					return;
				try {
					inChange.set(true);
					strongRet.set(node.getInt(key, defaultValue));
				}finally {
					inChange.set(changing);
				}
			}
		};
		node.addPreferenceChangeListener(pcl);
		weakRet.setCleanupAction(()->node.removePreferenceChangeListener(pcl));
		ret.set(node.getInt(key, defaultValue));
		ret.addValueListener(e->{
			Boolean changing = inChange.get();
			if(Boolean.TRUE.equals(changing))
				return;
			try {
				Integer value = ret.get();
				if(value==null) {
					switch(nb) {
					case DELETE:
						node.remove(key);
						return;
					case IGNORE:
						return;
					case STORE_DEFAULT:
						value = defaultValue;
						break;
					case STORE_NULL:
						assert false;
						break;
					}
				}
				node.putInt(key, value);
			}finally {
				inChange.set(changing);
			}
		});
		return builder;
	}
	/**
	 * Make an {@link IndependentString} representing a {@link String} value stored in a {@link Preferences} node
	 * @param node The node
	 * @param key The Key under which the node is stored
	 * @param defaultValue The default value to use when the key does not exist
	 * @param nb What to do it a <code>null</code> is written to the {@link IndependentString}
	 * @return
	 */
	public static IndependentString stringPreference(Preferences node, String key, String defaultValue, NullBehavior _nb){
		NullBehavior nb = _nb==null?NullBehavior.IGNORE:_nb;
		if(nb == NullBehavior.STORE_NULL) {
			throw new IllegalArgumentException("The NullBehavior for a String preference cannot be STORE_NULL");
		}
		ThreadLocal<Boolean> inChange=new ThreadLocal<>();
		IndependentString ret = new IndependentString(null);
		WeakCleanupWithRunnable<IndependentString> weakRet = new WeakCleanupWithRunnable<>(ret, null);
		PreferenceChangeListener pcl = e->{
			if(key.equals(e.getKey())) {
				IndependentString strongRet = weakRet.get();
				if(strongRet==null)
					return;
				Boolean changing = inChange.get();
				if(Boolean.TRUE.equals(changing))
					return;
				try {
					inChange.set(true);
					strongRet.set(node.get(key, defaultValue));
				}finally {
					inChange.set(changing);
				}
			}
		};
		node.addPreferenceChangeListener(pcl);
		weakRet.setCleanupAction(()->node.removePreferenceChangeListener(pcl));
		ret.set(node.get(key, defaultValue));
		ret.addValueListener(e->{
			Boolean changing = inChange.get();
			if(Boolean.TRUE.equals(changing))
				return;
			try {
				String value = ret.get();
				if(value==null) {
					switch(nb) {
					case DELETE:
						node.remove(key);
						return;
					case IGNORE:
						return;
					case STORE_DEFAULT:
						value = defaultValue;
						break;
					case STORE_NULL:
						break;
					}
				}
				node.put(key, value);
			}finally {
				inChange.set(changing);
			}
		});
		return ret;
	}
	
	/**
	 * Make an {@link IndependentString} representing a {@link String} value stored in a {@link Preferences} node
	 * @param node The node
	 * @param key The Key under which the node is stored
	 * @param defaultValue The default value to use when the key does not exist
	 * @param nb What to do it a <code>null</code> is written to the {@link IndependentString}
	 * @return
	 */
	public static <E extends Enum<E>> Independent<E> enumPreference(Preferences node, String key, E defaultValue, NullBehavior _nb, 
			Function<? super String, ? extends E> resolver){
		NullBehavior nb = _nb==null?NullBehavior.IGNORE:_nb;
		if(nb == NullBehavior.STORE_NULL) {
			throw new IllegalArgumentException("The NullBehavior for an Enum preference cannot be STORE_NULL");
		}
		ThreadLocal<Boolean> inChange=new ThreadLocal<>();
		Independent<E> ret = new Independent<E>(null);
		WeakCleanupWithRunnable<Independent<E>> weakRet = new WeakCleanupWithRunnable<>(ret, null);
		PreferenceChangeListener pcl = e->{
			if(key.equals(e.getKey())) {
				Independent<E> strongRet = weakRet.get();
				if(strongRet==null)
					return;
				Boolean changing = inChange.get();
				if(Boolean.TRUE.equals(changing))
					return;
				try {
					inChange.set(true);
					strongRet.set(resolver.apply(node.get(key, defaultValue.name())));
				}finally {
					inChange.set(changing);
				}
			}
		};
		node.addPreferenceChangeListener(pcl);
		weakRet.setCleanupAction(()->node.removePreferenceChangeListener(pcl));
		ret.set(resolver.apply(node.get(key, defaultValue.name())));
		ret.addValueListener(e->{
			Boolean changing = inChange.get();
			if(Boolean.TRUE.equals(changing))
				return;
			try {
				E value = ret.get();
				if(value==null) {
					switch(nb) {
					case DELETE:
						node.remove(key);
						return;
					case IGNORE:
						return;
					case STORE_DEFAULT:
						value = defaultValue;
						break;
					case STORE_NULL:
						break;
					}
				}
				node.put(key, value.name());
			}finally {
				inChange.set(changing);
			}
		});
		return ret;
	}
	
	
	/**
	 * Make a {@link LastValueRememberer} for booleans, backed by an entry in a {@link Preferences} node
	 * @param node The node
	 * @param key The Key under which the node is stored
	 * @param defaultValue The default value to use when the key does not exist
	 * @param nb
	 * @return
	 */
	public static LastValueRemembererBool rememberBool(
			Preferences node, 
			String key, 
			boolean defaultValue,
			NullBehavior _nb){
		NullBehavior nb = _nb==null?NullBehavior.IGNORE:_nb;
		if(nb == NullBehavior.STORE_NULL) {
			throw new IllegalArgumentException("The NullBehavior for a boolean preference cannot be STORE_NULL");
		}
		return new LastValueRemembererBool() {

			@Override
			public void storeLastValue(Boolean e) {
				if (e==null) {
					switch(nb) {
					case DELETE:
						node.remove(key);
						return;
					case IGNORE:
						return;
					case STORE_DEFAULT:
						e=defaultValue;
						break;
					case STORE_NULL:
						assert false;
						return;
					}
				}
				node.putBoolean(key, e);
			}

			@Override
			public Boolean recallLastValue() {
				return node.getBoolean(key, defaultValue);
			}

		};

	}
	/**
	 * Make a {@link LastValueRememberer} for booleans, backed by an entry in a {@link Preferences} node
	 * @param node The node
	 * @param key The Key under which the node is stored
	 * @param defaultValue The default value to use when the key does not exist
	 * @param nb
	 * @return
	 */
	public static LastValueRemembererBool rememberBool(
			ReadListenDependency<? extends Preferences> nodeV, 
			String key, 
			ReadListenDependency<? extends Boolean> defaultValue,
			NullBehavior _nb){
		NullBehavior nb = _nb==null?NullBehavior.IGNORE:_nb;
		if(nb == NullBehavior.STORE_NULL) {
			throw new IllegalArgumentException("The NullBehavior for a boolean preference cannot be STORE_NULL");
		}
		return new LastValueRemembererBool() {

			@Override
			public void storeLastValue(Boolean e) {
				Preferences node = nodeV.get();
				if (e==null) {
					switch(nb) {
					case DELETE:
						node.remove(key);
						return;
					case IGNORE:
						return;
					case STORE_DEFAULT:
						e=defaultValue.get();
						break;
					case STORE_NULL:
						assert false;
						return;
					}
				}
				node.putBoolean(key, e);
			}

			@Override
			public Boolean recallLastValue() {
				Preferences node = nodeV.get();
				return node.getBoolean(key, defaultValue.get());
			}

		};

	}
	/**
	 * Make a {@link LastValueRememberer} for doubles, backed by an entry in a {@link Preferences} node
	 * @param node The node
	 * @param key The Key under which the node is stored
	 * @param defaultValue The default value to use when the key does not exist
	 * @param nb
	 * @return
	 */
	public static LastValueRemembererDouble rememberDouble(
			Preferences node, 
			String key, 
			double defaultValue,
			NullBehavior _nb){
		NullBehavior nb = _nb==null?NullBehavior.IGNORE:_nb;
		if(nb == NullBehavior.STORE_NULL) {
			throw new IllegalArgumentException("The NullBehavior for a double preference cannot be STORE_NULL");
		}
		return new LastValueRemembererDouble() {

			@Override
			public void storeLastValue(Double e) {
				if(e==null) {
					switch(nb) {
					case DELETE:
						node.remove(key);
						return;
					case IGNORE:
						return;
					case STORE_DEFAULT:
						e=defaultValue;
						break;
					case STORE_NULL:
						assert false;
						return;
					}
				}
				node.putDouble(key, e);
			}

			@Override
			public Double recallLastValue() {
				return node.getDouble(key, defaultValue);
			}

		};

	}
	/**
	 * Make a {@link LastValueRememberer} for integers, backed by an entry in a {@link Preferences} node
	 * @param node The node
	 * @param key The Key under which the node is stored
	 * @param defaultValue The default value to use when the key does not exist
	 * @param nb
	 * @return
	 */
	public static LastValueRemembererInt rememberInt(
			Preferences node, 
			String key, 
			int defaultValue,
			NullBehavior _nb){
		NullBehavior nb = _nb==null?NullBehavior.IGNORE:_nb;
		if(nb == NullBehavior.STORE_NULL) {
			throw new IllegalArgumentException("The NullBehavior for an integer preference cannot be STORE_NULL");
		}
		return new LastValueRemembererInt() {

			@Override
			public void storeLastValue(Integer e) {
				if(e==null) {
					switch(nb) {
					case DELETE:
						node.remove(key);
						return;
					case IGNORE:
						return;
					case STORE_DEFAULT:
						e=defaultValue;
						break;
					case STORE_NULL:
						assert false;
						return;
					}
				}
				node.putInt(key, e);
			}

			@Override
			public Integer recallLastValue() {
				return node.getInt(key, defaultValue);
			}

		};

	}
	/**
	 * Make a {@link LastValueRememberer} for strings, backed by an entry in a {@link Preferences} node
	 * @param node The node
	 * @param key The Key under which the node is stored
	 * @param defaultValue The default value to use when the key does not exist
	 * @param nb
	 * @return
	 */
	public static LastValueRemembererString rememberString(
			Preferences node, 
			String key, 
			String defaultValue,
			NullBehavior _nb){
		NullBehavior nb = _nb==null?NullBehavior.IGNORE:_nb;

		return new LastValueRemembererString() {

			@Override
			public void storeLastValue(String e) {
				if(e==null) {
					switch(nb) {
					case DELETE:
						node.remove(key);
						return;
					case IGNORE:
						return;
					case STORE_DEFAULT:
						e=defaultValue;
						break;
					case STORE_NULL:
						assert false;
						return;
					}
				}
				node.put(key, e);
			}

			@Override
			public String recallLastValue() {
				return node.get(key, defaultValue);
			}

		};

	}
	/**
	 * Make a {@link LastValueRememberer} for enums, backed by an entry in a {@link Preferences} node.
	 * Requests to store <code>null</code> will be ignored.
	 * @param node The node
	 * @param key The Key under which the node is stored
	 * @param defaultValue The default value to use when the key does not exist
	 * @param resolver A function that takes a string and returns an enum value. If it throws an {@link IllegalArgumentException}, the {@code defaultValue} will be used.
	 * @return
	 */
	public static <E extends Enum<?>> LastValueRememberer<E> rememberEnum(
			Preferences node, 
			String key, 
			E defaultValue,
			Function<? super String, ? extends E> resolver
			){
		return rememberEnum(node, key, defaultValue, resolver, NullBehavior.IGNORE);
	}
	/**
	 * Make a {@link LastValueRememberer} for enums, backed by an entry in a {@link Preferences} node
	 * @param node The node
	 * @param key The Key under which the node is stored
	 * @param defaultValue The default value to use when the key does not exist
	 * @param resolver A function that takes a string and returns an enum value. If it throws an {@link IllegalArgumentException}, the {@code defaultValue} will be used.
	 * @param nb
	 * @return
	 */
	public static <E extends Enum<?>> LastValueRememberer<E> rememberEnum(
			Preferences node, 
			String key, 
			E defaultValue,
			Function<? super String, ? extends E> resolver,
			NullBehavior _nb){
		NullBehavior nb = _nb==null?NullBehavior.IGNORE:_nb;

		return new LastValueRememberer<E>() {

			@Override
			public void storeLastValue(E e) {
				String s=null;
				if(e==null) {
					switch(nb) {
					case DELETE:
						node.remove(key);
						return;
					case IGNORE:
						return;
					case STORE_DEFAULT:
						s=defaultValue==null?"":defaultValue.name();
						break;
					case STORE_NULL:
						s = "";
						return;
					}
				}else {
					s = e.name();
				}
				node.put(key, s);
			}

			@Override
			public E recallLastValue() {
				String s = node.get(key, defaultValue==null?"":defaultValue.name());
				if(s.length()==0)
					return null;
				try {
					return resolver.apply(s);
				}catch(IllegalArgumentException x) {
					return defaultValue;
				}
			}

		};

	}

	/**
	 * Make an {@link IndependentBool} representing a boolean value stored in a {@link Preferences} node.
	 * If you get a compiler problem due to overloading, you can call 
	 * {@link #boolPreference(Preferences, String, boolean, NullBehavior)} instead.
	 * @param node The node
	 * @param key The Key under which the node is stored
	 * @param defaultValue The default value to use when the key does not exist
	 * @param nb What to do it a <code>null</code> is written to the {@link IndependentBool}. Defaults to {@link NullBehavior#IGNORE}
	 * @return
	 */
	public static IndependentBool preference(Preferences node, String key, boolean defaultValue, NullBehavior _nb){
		NullBehavior nb = _nb==null?NullBehavior.IGNORE:_nb;

		return boolPreference(node, key, defaultValue, nb);
	}
	/**
	 * Make an {@link IndependentDouble} representing a double precision floating point value 
	 * stored in a {@link Preferences} node.
	 * If you get a compiler problem due to overloading, you can call 
	 * {@link #doublePreference(Preferences, String, double, NullBehavior)} instead.
	 * @param node The node
	 * @param key The Key under which the node is stored
	 * @param defaultValue The default value to use when the key does not exist
	 * @param nb What to do it a <code>null</code> is written to the {@link IndependentDouble}. Defaults to {@link NullBehavior#IGNORE}
	 * @return
	 */
	public static IndependentDouble preference(Preferences node, String key, double defaultValue, NullBehavior _nb){
		NullBehavior nb = _nb==null?NullBehavior.IGNORE:_nb;
		return doublePreference(node, key, defaultValue, nb);
	}
	/**
	 * Make an {@link IndependentInt} representing a integer value stored in a {@link Preferences} node.
	 * If you get a compiler problem due to overloading, you can call 
	 * {@link #intPreference(Preferences, String, int, NullBehavior)} instead.
	 * @param node The node
	 * @param key The Key under which the node is stored
	 * @param defaultValue The default value to use when the key does not exist
	 * @param nb What to do it a <code>null</code> is written to the {@link IndependentInt}. Defaults to {@link NullBehavior#IGNORE}
	 * @return
	 */
	public static IndependentInt preference(Preferences node, String key, int defaultValue, NullBehavior _nb){
		NullBehavior nb = _nb==null?NullBehavior.IGNORE:_nb;
		return intPreference(node, key, defaultValue, nb);
	}
	/**
	 * Make an {@link IndependentInt} representing a integer value stored in a {@link Preferences} node.
	 * If you get a compiler problem due to overloading, you can call 
	 * {@link #stringPreference(Preferences, String, String, NullBehavior)} instead.
	 * @param node The node
	 * @param key The Key under which the node is stored
	 * @param defaultValue The default value to use when the key does not exist
	 * @param nb What to do it a <code>null</code> is written to the {@link IndependentString}. Defaults to {@link NullBehavior#IGNORE}
	 * @return
	 */
	public static IndependentString preference(Preferences node, String key, String defaultValue, NullBehavior _nb){
		NullBehavior nb = _nb==null?NullBehavior.IGNORE:_nb;
		return stringPreference(node, key, defaultValue, nb);
	}
	/**
	 * Make a {@link LastValueRememberer} for booleans, backed by an entry in a {@link Preferences} node.
	 * If you get a compiler problem due to overloading, you can call 
	 * {@link #rememberBool(Preferences, String, boolean, NullBehavior)} instead.
	 * @param node The node
	 * @param key The Key under which the node is stored
	 * @param defaultValue The default value to use when the key does not exist
	 * @param nb What to do it a <code>null</code> is written to the {@link IndependentString}. Defaults to {@link NullBehavior#IGNORE}
	 * 
	 * @return
	 */
	public static LastValueRemembererBool remember(
			Preferences node, 
			String key, 
			boolean defaultValue,
			NullBehavior _nb){
		NullBehavior nb = _nb==null?NullBehavior.IGNORE:_nb;
		return rememberBool(node, key, defaultValue, nb);
	}
	/**
	 * Make a {@link LastValueRememberer} for doubles, backed by an entry in a {@link Preferences} node.
	 * If you get a compiler problem due to overloading, you can call 
	 * {@link #rememberDouble(Preferences, String, double, NullBehavior)} instead.
	 * @param node The node
	 * @param key The Key under which the node is stored
	 * @param defaultValue The default value to use when the key does not exist
	 * @param nb What to do it a <code>null</code> is written to the {@link IndependentString}. Defaults to {@link NullBehavior#IGNORE}
	 * @return
	 */
	public static LastValueRemembererDouble remember(
			Preferences node, 
			String key, 
			double defaultValue,
			NullBehavior _nb){
		NullBehavior nb = _nb==null?NullBehavior.IGNORE:_nb;
		return rememberDouble(node, key, defaultValue, nb);
	}
	/**
	 * Make a {@link LastValueRememberer} for integers, backed by an entry in a {@link Preferences} node.
	 * If you get a compiler problem due to overloading, you can call 
	 * {@link #rememberInt(Preferences, String, int, NullBehavior)} instead.
	 * @param node The node
	 * @param key The Key under which the node is stored
	 * @param defaultValue The default value to use when the key does not exist
	 * @param nb What to do it a <code>null</code> is written to the {@link IndependentString}. Defaults to {@link NullBehavior#IGNORE}
	 * @return
	 */
	public static LastValueRemembererInt remember(
			Preferences node, 
			String key, 
			int defaultValue,
			NullBehavior _nb){
		NullBehavior nb = _nb==null?NullBehavior.IGNORE:_nb;
		return rememberInt(node, key, defaultValue, nb);
	}
	/**
	 * Make a {@link LastValueRememberer} for Strings, backed by an entry in a {@link Preferences} node.
	 * If you get a compiler problem due to overloading, you can call 
	 * {@link #rememberString(Preferences, String, String, NullBehavior)} instead.
	 * @param node The node
	 * @param key The Key under which the node is stored
	 * @param defaultValue The default value to use when the key does not exist
	 * @param nb What to do it a <code>null</code> is written to the {@link IndependentString}. Defaults to {@link NullBehavior#IGNORE}
	 * @return
	 */
	public static LastValueRemembererString remember(
			Preferences node, 
			String key, 
			String defaultValue,
			NullBehavior _nb){
		NullBehavior nb = _nb==null?NullBehavior.IGNORE:_nb;
		return rememberString(node, key, defaultValue, nb);
	}
	/**
	 * Make a {@link LastValueRememberer} for booleans, backed by an entry in a {@link Preferences} node.
	 * If an attempt is made to remember a <code>null</code> instead of a proper value, the {@link Preferences} node is not modified.
	 * If you get a compiler problem due to overloading, you can call 
	 * {@link #rememberBool(Preferences, String, boolean, NullBehavior)} instead.
	 * Requests to store <code>null</code> will be ignored.
	 * @param node The node
	 * @param key The Key under which the node is stored
	 * @param defaultValue The default value to use when the key does not exist
	 * 
	 * @return
	 */
	public static LastValueRemembererBool remember(
			Preferences node, 
			String key, 
			boolean defaultValue){
		return remember(node, key, defaultValue, NullBehavior.IGNORE);
	}
	/**
	 * Make a {@link LastValueRememberer} for doubles, backed by an entry in a {@link Preferences} node.
	 * If an attempt is made to remember a <code>null</code> instead of a proper value, the {@link Preferences} node is not modified.
	 * If you get a compiler problem due to overloading, you can call 
	 * Requests to store <code>null</code> will be ignored.
	 * {@link #rememberDouble(Preferences, String, double, NullBehavior)} instead.
	 * @param node The node
	 * @param key The Key under which the node is stored
	 * @param defaultValue The default value to use when the key does not exist
	 * @return
	 */
	public static LastValueRemembererDouble remember(
			Preferences node, 
			String key, 
			double defaultValue){
		return remember(node, key, defaultValue, NullBehavior.IGNORE);
	}
	/**
	 * Make a {@link LastValueRememberer} for integers, backed by an entry in a {@link Preferences} node.
	 * If an attempt is made to remember a <code>null</code> instead of a proper value, the {@link Preferences} node is not modified.
	 * If you get a compiler problem due to overloading, you can call 
	 * {@link #rememberInt(Preferences, String, int, NullBehavior)} instead.
	 * Requests to store <code>null</code> will be ignored.
	 * @param node The node
	 * @param key The Key under which the node is stored
	 * @param defaultValue The default value to use when the key does not exist
	 * @return
	 */
	public static LastValueRemembererInt remember(
			Preferences node, 
			String key, 
			int defaultValue){
		return remember(node, key, defaultValue, NullBehavior.IGNORE);
	}
	/**
	 * Make a {@link LastValueRememberer} for Strings, backed by an entry in a {@link Preferences} node.
	 * If an attempt is made to remember a <code>null</code> instead of a proper value, the {@link Preferences} node is not modified.
	 * If you get a compiler problem due to overloading, you can call 
	 * {@link #rememberString(Preferences, String, String, NullBehavior)} instead.
	 * Requests to store <code>null</code> will be ignored.
	 * @param node The node
	 * @param key The Key under which the node is stored
	 * @param defaultValue The default value to use when the key does not exist
	 * @return
	 */
	public static LastValueRemembererString remember(
			Preferences node, 
			String key, 
			String defaultValue){
		return remember(node, key, defaultValue, NullBehavior.IGNORE);
	}
}
	

