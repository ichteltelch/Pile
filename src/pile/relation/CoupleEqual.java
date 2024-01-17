package pile.relation;


import java.util.Objects;

import pile.aspect.combinations.ReadWriteListenValue;
import pile.aspect.listen.ValueEvent;
import pile.aspect.listen.ValueListener;
import pile.utils.Functional;
import pile.utils.Nonreentrant;

/**
 * A Relation that cause two {@link ReadWriteListenValue}s to be equal.
 * The values will on their own hold no strong references to the {@link CoupleEqual} object, 
 * so you need to store the reference somehow else if you don't want the values to become decoupled
 * randomly.
 * @author bb
 * @see SwitchableCoupleEqual Here's a subclass that lets you switch the coupling on and off
 *
 * @param <E>
 */
public class CoupleEqual<E> extends AbstractRelation{
	public enum Mode{
		/**
		 * The default mode: The coupling is bidirectional.
		 * When the coupling becomes active, the first {@link ReadWriteListenValue} takes on the value of the second.
		 */
		BIDI_2_TO_1,
		/**
		 * The coupling is bidirectional.
		 * When the coupling becomes active, the second {@link ReadWriteListenValue} takes on the value of the first.
		 */
		BIDI_1_TO_2,
		/**
		 * The first value is set from the second value, never in the other direction
		 */
		ONLY_1_TO_2,
		/**
		 * The second value is set from the first value, never in the other direction
		 */
		ONLY_2_TO_1,

	}
	ReadWriteListenValue<E> op1;
	ReadWriteListenValue<E> op2;
	final Mode mode;
	Nonreentrant nr = new Nonreentrant();
	protected final ValueListener vl=nr.<ValueEvent>fixed(this::sync, Functional.NOP)::accept;

	private void sync(ValueEvent e) {
		if(!isEnabledPrim())
			return;
		if(e==null) {
			switch(mode) {
			case ONLY_1_TO_2:
			case BIDI_1_TO_2:
				op2.transferFrom(op1, false);			
				break;
			case ONLY_2_TO_1:
			case BIDI_2_TO_1:
				op1.transferFrom(op2, false);
				break;
			}
		}else if(e.getSource()==op2) {
			switch(mode) {
			case ONLY_1_TO_2:
				break;
			case ONLY_2_TO_1:
			case BIDI_2_TO_1:
			case BIDI_1_TO_2:
				op1.transferFrom(op2, false);
				break;
			}
		}else if(e.getSource()==op1) {
			switch(mode) {
			case ONLY_1_TO_2:
			case BIDI_2_TO_1:
			case BIDI_1_TO_2:
				op2.transferFrom(op1, false);			
				break;
			case ONLY_2_TO_1:
				break;
			}
		}
	}
	ValueListener removeFromOp1;
	ValueListener removeFromOp2;
	/**
	 * @param op1
	 * @param op2
	 * @param mode which value is leader and which is follower? Defaults to {@link Mode#BIDI_2_TO_1}
	 */
	public CoupleEqual(ReadWriteListenValue<E> op1, ReadWriteListenValue<E> op2, Mode mode) {
		this(op1, op2, true, mode);
	}
	/**
	 * When the coupling becomes active, the first {@link ReadWriteListenValue} takes on the value of
	 * the second.
	 * @param op1
	 * @param op2
	 * @param initSync whether to synchronizes the values in the constructor.
	 * @param mode which value is leader and which is follower? Defaults to {@link Mode#BIDI_2_TO_1}
	 */
	protected CoupleEqual(ReadWriteListenValue<E> op1, ReadWriteListenValue<E> op2, boolean initSync, Mode mode) {
		Objects.requireNonNull(op1);
		Objects.requireNonNull(op2);
		this.mode=mode==null?Mode.BIDI_2_TO_1:mode;
		this.op1=op1;
		this.op2=op2;
		removeFromOp1=op1.addWeakValueListener(vl);
		removeFromOp2=op2.addWeakValueListener(vl);

		if(initSync)
			vl.valueChanged(null);
	}
	
	@Override
	protected ValueListener getListener() {
		return vl;
	}
	
}
