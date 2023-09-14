package pile.tests;


import javax.swing.JLabel;

import pile.aspect.listen.ValueListener;
import pile.impl.MutRef;
import pile.impl.PileImpl;
import pile.impl.Piles;
import pile.specialized_int.PileInt;

public class WeakTest {
	public static void main(String[] args) {
		MutRef<ValueListener> lis=new MutRef<>();
		//MutRef<ValueListener> wrap=new MutRef<>();
		PileInt v1 = Piles.init(0)
			.onChange_weak_f(v->lis.val=e->System.out.println(v))
				.onChange(e->System.out.println("s: "+e.getSource()))
				.name("v1")
				.build();
		@SuppressWarnings("unused")
		PileImpl<?> v2 = Piles.computeS(JLabel::new).onChange(e->{
			System.out.println(e.getSource());
		})		
				.name("v2")
				.whenChanged(v1);

		v2=null;
		v1.set(1);
		gc();
		v1.set(2);
		lis.setNull();
		v1.set(3);
		gc();
		v1.set(4);
		gc();
		v1.set(5);

		System.out.println();

	}

	private static void gc() {
		for(int i=0; i<50; ++i)
			System.gc();
	}
}
