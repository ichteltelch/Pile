package pile.relation;

import pile.aspect.Sealable;
import pile.aspect.combinations.ReadWriteListenValue;
import pile.aspect.listen.ValueListener;
import pile.impl.Piles;

/**
 * Objects of this class maintain an implication between two reactive booleans.
 * @author bb
 *
 */
public class Implication extends AbstractRelation{
	final ReadWriteListenValue<Boolean> premise; 
	final ReadWriteListenValue<Boolean> conclusion;
	final Boolean onConflictKeepPremise;
	final ValueListener vl;
	final ValueListener removeFromPremise;
	final ValueListener removeFromConclusion;

	/**
	 * 
	 * @param premise
	 * @param conclusion
	 * @param onConflictKeepPremise If the relation becomes active and there is a conflict,
	 * that is the premise is <code>true</code> but the conclusion is <code>false</code>, 
	 * then if this parameter is <code>true</code>, an attempt is made to change the conclusion
	 * to <code>true</code>, and if it's <code>false</code>, an attempt is made to  
	 * chance the premise to <code>false</code>. The attempt may fail due to corrections
	 * or {@link Sealable sealing}, so the other direction is tried too. 
	 * This parameter can be <code>null</code>, in which case no attempt is made to
	 * resolve the conflict.
	 * 
	 */
	public Implication(
			ReadWriteListenValue<Boolean> premise, 
			ReadWriteListenValue<Boolean> conclusion,
			Boolean onConflictKeepPremise) {
		this.premise=premise;
		this.conclusion=conclusion;
		this.onConflictKeepPremise=onConflictKeepPremise;
		vl = e -> {
			if(isEnabledPrim()) {
				Object src = e==null?null:e.getSource();
				if(src==null) {
					if(onConflictKeepPremise==null)
						return;
					if(onConflictKeepPremise) {
						forceConclusion();
						forcePremise();
					}else {
						forcePremise();
						forceConclusion();
					}
				}else if(src == this.premise) {
					forceConclusion();
				}else if(src == this.conclusion) {
					forcePremise();
				}
			}
		};
		removeFromPremise = premise.addWeakValueListener(vl);
		removeFromConclusion = conclusion.addWeakValueListener(vl);
		if(isEnabled()!=Piles.TRUE) {
			isEnabled().addValueListener(e->{
				if(!shouldActOnlyOnOperandChanges())
					vl.runImmediately(true);	
			});
		}
		vl.runImmediately(true);
	}
	@Override
	public void destroy() {
		premise.removeValueListener(removeFromPremise);
		conclusion.removeValueListener(removeFromConclusion);
	}





	private void forcePremise() {
		Boolean v = conclusion.get();
		if(Boolean.FALSE.equals(v))
			premise.set(false);
	}


	@Override
	protected ValueListener getListener() {
		return vl;
	}



	private void forceConclusion() {
		Boolean v = premise.get();
		if(Boolean.TRUE.equals(v))
			conclusion.set(true);
	}





}