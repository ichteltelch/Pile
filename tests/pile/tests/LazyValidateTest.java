package pile.tests;


import pile.aspect.listen.ValueListener;
import pile.aspect.suppress.Suppressor;
import pile.impl.PileImpl;
import pile.impl.Piles;

public class LazyValidateTest {
	public static void main(String[] args) throws InterruptedException {
		PileImpl<Integer> v1 = Piles.init(4).name("v1").build(); 
		PileImpl<Integer> v2 = Piles.compute(v1::get).name("v2").whenChanged(v1);
		PileImpl<Integer> v3 = Piles.compute(v2::get).name("v3").whenChanged(v2);
//		v2.setLazyValidating(true);
		@SuppressWarnings("unused")
		Suppressor s = v3.suppressAutoValidation().wrapWeak("This is intentional");
		v2.setLazyValidating(true);
		ValueListener vl = e->{System.out.println(e.getSource());};
		v1.addValueListener(vl);
		v2.addValueListener(vl);
		v3.addValueListener(vl);
		System.out.println("v1 <- 2");
		v1.set(2);
//		System.out.println("v2.couldBeValid: "+v2.couldBeValid());
		System.out.println("get v2");
		System.out.println(v2.get());
		
		System.out.println("v1 <- invalid");
		v1.permaInvalidate();
//		v1.couldBeValid();
//		System.out.println("v2.couldBeValid: "+v2.couldBeValid());
		System.out.println("get v2");
		System.out.println(v2.get());
		System.out.println("v1 <- 3");
		v1.set(3);
//		System.out.println("v3.couldBeValid: "+v3.couldBeValid());
		System.out.println("get v3");
		System.out.println(v3.get());
		System.out.println("get.lazyValidating <- false");
		v2.setLazyValidating(false);
		System.out.println("get v2");
		System.out.println(v2.get());
		s=null;
		for(int i=0; i<10; ++i) {
			System.out.println("gc");
			System.gc();
			Thread.sleep(100);
		}
		
	}
}
