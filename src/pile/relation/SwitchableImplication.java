package pile.relation;

import pile.aspect.combinations.ReadListenValue;
import pile.aspect.combinations.ReadWriteListenValue;
import pile.aspect.suppress.Suppressor;
import pile.specialized_bool.combinations.ReadListenDependencyBool;

/**
 * Like a normal {@link Implication}, but it can be switched on or off based on a reactive boolean and {@link Suppressor}s.
 * @author bb
 *
 */
public class SwitchableImplication extends Implication implements SwitchableRelation<ReadListenValue<Boolean>>{
	final SwitchableRelation<ReadListenValue<Boolean>> switcher;
	public SwitchableImplication(
			ReadWriteListenValue<Boolean> premise, 
			ReadWriteListenValue<Boolean> conclusion,
			Boolean onConflictKeepPremise, 
			ReadListenValue<Boolean> shouldBeEnabled) {
		super(premise, conclusion, onConflictKeepPremise);
		switcher = new ImplSwitchableRelation();
		switcher.setShouldBeEnabled(shouldBeEnabled);
		if(switcher.isEnabled().isTrue())
			vl.valueChanged(null);
		switcher.isEnabled().addValueListener(e->vl.valueChanged(null));
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
	public boolean isEnabledPrim() {
		return switcher.isEnabled().isTrue();
	}
	@Override
	public ReadListenValue<Boolean> shouldBeEnabled() {
		return switcher.shouldBeEnabled();
	}
	@Override
	public void setShouldBeEnabled(ReadListenValue<Boolean> sbe) {
		switcher.setShouldBeEnabled(sbe);
	}


}