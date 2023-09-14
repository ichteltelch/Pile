package pile.tests;


import pile.impl.PileCompound;
import pile.impl.PileImpl;
import pile.impl.Piles;

public class CompoundTest {
	static class Dodo extends PileCompound{
		PileImpl<Integer> do1=Piles.init(1).name("do1").build();
		PileImpl<Integer> do2=Piles.init(2).name("do2").build();
		{
			head().addDependency(do1, do2);
		}
		public Dodo self() {
			return this;
		}
		@Override
		public void destroy() {
			System.out.println("destroy Dodo!");
			do1.destroy();
			do2.destroy();
		}
		@Override
		public String autoCompundName() {
			return "A dodo";
		}
		@Override
		public String toString() {
			return "Dodo: "+do1+" ,  "+do2;
		}

	}
//	public static void main(String[] args) {
//		ValueList<Dodo> dodos=new ValueList<>("dodos");
//		dodos.head().addValueListener(e->{
//			System.out.println("Dodos changed: "+dodos);
//			System.out.println("source: "+e.getSource());
//		});
//		Dodo dodo1 = new Dodo();
//		Dodo dodo2 = new Dodo();
//		dodos.addV(dodo1);
//		dodos.addV(dodo2);
//		dodo1.do1.set(100);
//	}
}
