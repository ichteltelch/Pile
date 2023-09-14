package pile.impl;

import pile.aspect.Depender;

/**
 * Superclass for subclasses of {@link AbstractReadListenDependency} that
 * are neither {@link Depender}s nor have a concept of recomputation; 
 * this class merely overrides some methods with empty implementations.
 * @author bb
 *
 * @param <E>
 */
public abstract class AbstractReadListenDependency_NoDepender<E> extends AbstractReadListenDependency<E> {
	{
		assert !(this instanceof Depender);
	}
	


	@Override public void autoValidate() {return ;}


//	@Override protected boolean scheduleRecomputationWithActivatedTransaction() {return false;}

	@Override protected boolean __shouldRemainInvalid() {return false;}

	@Override protected boolean cancelPendingRecomputation(boolean b) {return false;}
	@Override protected boolean cancelPendingRecomputation(boolean b, boolean nis) {return false;}

	@Override protected boolean isAutoValidating() {return false;}

	@Override protected void __startPendingRecompute(boolean force) {}

}
