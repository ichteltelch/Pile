package pile.relation;

import java.util.Objects;
import java.util.function.Consumer;

import pile.aspect.ReadValue.InvalidValueException;
import pile.aspect.combinations.ReadListenValue;
import pile.aspect.listen.ValueListener;
import pile.aspect.suppress.Suppressor;
import pile.impl.Piles;
import pile.specialized_bool.IndependentBool;
import pile.specialized_bool.combinations.ReadListenDependencyBool;

/**
 * This class implements most of the logic from making relations switchable. It is meant to be used
 * as a field of a concrete {@link SwitchableRelation} that it can forward the relevant method calls to.
 * @author bb
 *
 */
public class ImplSwitchableRelation implements SwitchableRelation<ReadListenValue<Boolean>>{

	protected final Object mutex = new Object();
	int suppressors;
	protected ReadListenValue<Boolean> shouldBeEnabled = Piles.TRUE;

	private Consumer<? super Boolean> setEnabled;
	boolean onlyOnChanges;
	
	public SwitchableRelation<ReadListenValue<Boolean>> onlyOnChanges(boolean onlyOnChanges) {
		this.onlyOnChanges = onlyOnChanges;
		if(!onlyOnChanges) {
			isEnabled.fireValueChange();
		}
        return this;
	}
	public SwitchableRelation<ReadListenValue<Boolean>> onlyOnChanges() {
		return onlyOnChanges(true);
	}
	
	protected final IndependentBool isEnabled = Piles.independent(true)
			.giveSetter(s->setEnabled=s)
			.seal()
			.build(); 

	@Override
	public Suppressor disable() {
		Boolean v;
		Suppressor ret = Suppressor.wrap(()->{
			Boolean v2;
			synchronized (mutex) {
				--suppressors;
				if(suppressors==0) {
					if(shouldBeEnabled.isValid()) {

						try {
							v2 = shouldBeEnabled.getValidOrThrow();
						} catch (InvalidValueException e) {
							v2 = false;
						}
						if(v2==null)
							v2=false;
					}else {
						v2=false;
					}
				}else {
					v2=false;
				}
			}
			setEnabled.accept(v2);
		});
		synchronized (mutex) {

			++suppressors;
			try {
				ret=ret.wrapWeak();
			}catch(Error|RuntimeException e) {
				--suppressors;
				throw e;
			}
			if(suppressors==1) {
				if(shouldBeEnabled.isValid()) {

					try {
						v = shouldBeEnabled.getValidOrThrow();
					} catch (InvalidValueException e) {
						v = false;
					}
					if(v==null)
						v=false;
				}else {
					v=false;
				}
				
			}else {
				v=null;
			}
		}
		if(v!=null)
			setEnabled.accept(v);
		return ret;

	}

	@Override
	public ReadListenDependencyBool isEnabled() {
		return isEnabled;
	}
	
	public boolean shouldActOnlyOnOperandChanges() {
		return onlyOnChanges;
	}
	
	@Override
	public ReadListenValue<Boolean> shouldBeEnabled() {
		return shouldBeEnabled;
	}
	
	final ValueListener sbeChanged = e->{
		Boolean v;
		synchronized (mutex) {
			if(suppressors>0) {
				v=false;
			}else if(!shouldBeEnabled.isValid()) {
				v=false;
			}else {
				try {
					v=shouldBeEnabled.getValidOrThrow();
					if(v==null)
						v=false;
				} catch (InvalidValueException e1) {
					v=false;
				}
			}
		}
		setEnabled.accept(v);
	};
	ValueListener removeHandle;

	@Override
	public void setShouldBeEnabled(ReadListenValue<Boolean> sbe) {
		Objects.requireNonNull(sbe);
		synchronized (mutex) {
			if(sbe==shouldBeEnabled)
				return;
			if(shouldBeEnabled!=null && removeHandle!=null)
				shouldBeEnabled.removeValueListener(removeHandle);
			removeHandle=null;
			shouldBeEnabled=sbe;
			if(sbe!=null && !sbe.willNeverChange()) {
				removeHandle=sbe.addWeakValueListener(sbeChanged);
			}
		}
		sbeChanged.valueChanged(null);
	}
}
