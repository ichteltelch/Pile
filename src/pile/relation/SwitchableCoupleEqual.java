package pile.relation;

import pile.aspect.combinations.ReadListenValue;
import pile.aspect.combinations.ReadWriteListenValue;
import pile.aspect.suppress.Suppressor;
import pile.specialized_bool.combinations.ReadListenDependencyBool;

/**
 * Like a normal {@link CoupleEqual}, but it can be switched on or off based on a reactive boolean and {@link Suppressor}s.
 * @author bb
 *
 * @param <E>
 */
public class SwitchableCoupleEqual<E> 
extends CoupleEqual<E> 
implements SwitchableRelation<ReadListenValue<Boolean>> {
	SwitchableRelation<ReadListenValue<Boolean>> switcher;
	public SwitchableCoupleEqual(
			ReadWriteListenValue<E> op1,
			ReadWriteListenValue<E> op2, 
			ReadListenValue<Boolean> shouldBeEnabled,
			Mode mode) {
		super(op1, op2, false, mode);
		switcher = new ImplSwitchableRelation();
		switcher.setShouldBeEnabled(shouldBeEnabled);
		
		installEnabledListener();
		
	}



	@Override
	public Suppressor disable() {
		return switcher.disable();
	}

	@Override
	public ReadListenDependencyBool isEnabled() {
		return switcher.isEnabled();
	}

	@Override
	public ReadListenValue<Boolean> shouldBeEnabled() {
		return switcher.shouldBeEnabled();
	}

	@Override
	public void setShouldBeEnabled(ReadListenValue<Boolean> sbe) {
		switcher.setShouldBeEnabled(sbe);
	}
	@Override
	public boolean isEnabledPrim() {
		return switcher!=null && switcher.isEnabled().isTrue();
	}



	@Override
	public SwitchableCoupleEqual<E> onlyOnChanges(boolean onlyOnChanges) {
		switcher.onlyOnChanges(onlyOnChanges);
		return this;
	}
	public SwitchableCoupleEqual<E> onlyOnChanges() {
		return onlyOnChanges(true);
	}



	@Override
	public boolean shouldActOnlyOnOperandChanges() {
		return switcher.shouldActOnlyOnOperandChanges();
	}
}
