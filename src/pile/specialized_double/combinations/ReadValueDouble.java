package pile.specialized_double.combinations;

import pile.specialized_Comparable.combinations.ReadValueComparable;

public interface ReadValueDouble extends ReadValueComparable<Double>{
	/**
	 * Get the held value as a boxed {@link Float}, preserving <code>null</code> references.
	 * @return
	 */
	public default Float getF() {
		Double ret = get();
		if(ret==null)
			return null;
		return ret.floatValue();
	}
}
