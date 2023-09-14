package pile.specialized_bool.combinations;

import pile.aspect.combinations.ReadDependency;
import pile.aspect.combinations.ReadWriteDependency;
import pile.specialized_bool.PileBool;
import pile.specialized_bool.SealBool;

public interface ReadWriteDependencyBool extends 
ReadWriteValueBool, 
ReadDependencyBool,
ReadWriteDependency<Boolean>
{
	/**
	 * Delegates to {@link PileBool#notRW(PileBool)}
	 */
	default public ReadWriteListenDependencyBool not() {
		return PileBool.notRW(this);
	}
	/**
	 * Delegates to {@link PileBool#notRW(PileBool)}
	 */
	default public ReadWriteListenDependencyBool notRW() {
		return PileBool.notRW(this);
	}
	public default ReadWriteDependencyBool setNull() {
		set(null);
		return this;
	}
	/**
	 * Controlled not operation. Writing to the result will attempt to write to {@code this}
	 * appropriately.
     * Delegates to {@link PileBool#cNot(ReadWriteDependency, ReadDependency)}
     */
	public default SealBool invertIf(ReadDependency<? extends Boolean> control) {
		return PileBool.cNot(this, control);
	}
}
