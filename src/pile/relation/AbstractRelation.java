package pile.relation;

import pile.aspect.listen.ValueListener;
import pile.impl.Piles;
import pile.specialized_bool.combinations.ReadListenDependencyBool;

public abstract class AbstractRelation {
	
	abstract protected ValueListener getListener();
	
	protected void installEnabledListener() {
		ValueListener vl = getListener();
		if(isEnabled()!=Piles.TRUE) {
			isEnabled().addValueListener(e->{
				if(!shouldActOnlyOnOperandChanges())
					vl.runImmediately();
			});
		}
		if(isEnabled().isTrue() && !shouldActOnlyOnOperandChanges())
			vl.runImmediately();
	}
	
	
	protected boolean shouldActOnlyOnOperandChanges() {
		return true;
	}
	
	/**
	 * 
	 * @return Whether this coupling is active
	 */
	public boolean isEnabledPrim() {
		return true;
	}
	
	/**
	 * @return whether the relation is active, as a reactive boolean.
	 */
	public ReadListenDependencyBool isEnabled() {
		return Piles.TRUE;
	}

	public abstract void destroy();
}
