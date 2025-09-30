package pile.tests;


import pile.aspect.listen.RateLimitedValueListener;
import pile.aspect.listen.ValueListener;
import pile.impl.Piles;
import pile.specialized_int.IndependentInt;
import pile.specialized_int.PileInt;
import pile.specialized_int.SealInt;

public class RateLimitedTest {
	public static void main(String[] args) throws InterruptedException {
		//listenerTest();
		bufferTest();
	}
	static void bufferTest() throws InterruptedException {
		PileInt v1 = Piles.init(0).name("v1").build();
		IndependentInt validBuffer = v1.validBuffer().setName("valid");
		SealInt rateLimited = validBuffer.rateLimited(100, 2000).setName("rateLimited");
		Thread.sleep(3000);
		long t0 = System.currentTimeMillis();
		ValueListener reporter = e->System.out.println((System.currentTimeMillis()-t0)+": "+e.getSource());
		v1.addValueListener(reporter);
		validBuffer.addValueListener(reporter);
		rateLimited.addValueListener(reporter);
		
		for(int i=1; i<20; ++i) {
			v1.set(i);
			Thread.sleep(500);
			v1.set(i*10);
			Thread.sleep(500);
			v1.permaInvalidate();
			Thread.sleep(500);
		}
	}

	static void listenerTest() throws InterruptedException {
		PileInt v1 = Piles.init(0).build();
		PileInt v2 = Piles.init(0).build();
		ValueListener ml = RateLimitedValueListener.wrap(100, 300, !false, !false, me->{
			long start = System.currentTimeMillis();
			System.out.println(start+": "+me.isSource(v1)+", "+me.isSource(v2));
			System.out.println(start+": "+v1.get()*10+", "+v2.get()*10);
			try {
				Thread.sleep(200);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
			long stop = System.currentTimeMillis();
			System.out.println("Ran from "+start+" to "+stop);
		});
		v1.addValueListener(ml);
		v2.addValueListener(ml);
		v2.set(v2.get()+1);

		for(int i=0; i<300; ++i) {
			v1.set(v1.get()+1);
			//v2.set(v2.get()+1);
//			ml.runImmediately(true);
			Thread.sleep(10);
		}
	}
}
