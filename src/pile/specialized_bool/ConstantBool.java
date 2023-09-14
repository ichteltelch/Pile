package pile.specialized_bool;

import pile.impl.Constant;
import pile.impl.Piles;
import pile.specialized_bool.combinations.ReadWriteListenDependencyBool;

public class ConstantBool extends Constant<Boolean> implements ReadWriteListenDependencyBool {
	public ConstantBool(Boolean init) {super(init);}
	@Override public ConstantBool setNull() {return this;}
	public ReadWriteListenDependencyBool not() {
		return threeWay(Piles.FALSE, Piles.TRUE, Piles.NULL_B);
	}
	@Override
	public ConstantBool setName(String s) {
		return this;
	}

}