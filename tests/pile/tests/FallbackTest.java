package pile.tests;


import pile.aspect.Dependency;
import pile.aspect.combinations.ReadDependency;
import pile.aspect.combinations.ReadListenDependency;
import pile.aspect.listen.ValueListener;
import pile.impl.DebugCallback;
import pile.impl.MutRef;
import pile.impl.PileImpl;
import pile.impl.Piles;
import pile.impl.SealPile;
import pile.specialized_int.PileInt;

public class FallbackTest {
	public static void main(String[] args) throws InterruptedException {
//		fallbackTest();
		derefTest();
	}

	static void derefTest() {
		System.out.println();
		ValueListener vl = e->System.out.println(e.getSource());
		PileInt v1=Piles.init(1).name("v1").onChange(vl).build();
		PileInt v2=Piles.init(2).name("v2").onChange(vl).build();
		PileImpl<PileInt> indir = Piles.init(v1).onChange(vl).name("indir").build();
		PileInt deref = indir.writableFieldInt(e->{
			System.out.println("dereferencing…");
			return e;
		});
		@SuppressWarnings("unused")
		PileImpl<?> dep = Piles.compute(()->{System.out.println("dependency recomputes"); return null;}).whenChanged(deref);
		deref.setName("deref");
		deref.addValueListener(vl);
		deref.validity().addValueListener(vl);
		System.out.println("\nv1 <- 10");
		v1.set(10);
		System.out.println("\nv1 <- 11");
		v1.set(11);
		System.out.println("\nindir <- v2");
		indir.set(v2);
		System.out.println("\nv1 <- 100");
		v1.set(100);
		System.out.println("\nv2 <- 20");
		v2.set(20);
		System.out.println("\nv2 <- invalid");
		v2.permaInvalidate();
		System.out.println("\nindir <- v1");
		indir.set(v1);
		System.out.println("\nderef <- 3");
		deref.set(3);
		System.out.println("\nv2 <- 3");
		v2.set(3);
		System.out.println("\nindir <- v2");
		indir.set(v2);
		
	}

	static void fallbackTest() throws InterruptedException {
		PileImpl<Integer> v1a=Piles.init(1).name("v1a").build();
		PileImpl<Integer> v1b=Piles.init(10).name("v1b").build();
		PileImpl<Integer> v1ad = Piles.compute(v1a::get).delay(100).name("v1ad").whenChanged(v1a);
		PileImpl<Integer> v1bd = Piles.compute(v1b::get).delay(100).name("v1bd").whenChanged(v1b);
		PileImpl<Integer> v1sum = Piles.compute(()->v1ad.get()+v1bd.get()).name("v1sum").whenChanged(v1ad, v1bd);
		PileInt v2 = Piles.init(100).name("v2").build();
		PileInt v3 = Piles.init(1000).name("v3").build();
		Thread.sleep(200);

		PileInt choice = 
				Piles.firstValidInt(null, v1sum, v2, v3).setName("choice");
		SealPile<ReadDependency<Integer>> metaChoice=
				Piles.firstValidV(v1sum, v2, v3);
		metaChoice.setName("metaChoice");
		
		MutRef<Integer> counter = new MutRef<>(0);
		
		long t0 = System.currentTimeMillis();
		choice._setDebugCallback(new DebugCallback() {
			@Override
			public void dependencyBeginsChanging(ReadListenDependency<?> source, Dependency d, boolean valid) {
				long t1 = System.currentTimeMillis();
				System.out.println((t1-t0)+": "+"begin: "+d.dependencyName() +"->"+choice.dependencyName());
				counter.val++;
				if(counter.val==3)
					System.out.println();
			};
			@Override
			public void dependencyEndsChanging(ReadListenDependency<?> source, Dependency d) {
				long t1 = System.currentTimeMillis();
				System.out.println((t1-t0)+": "+"end: "+d.dependencyName() +"->"+choice.dependencyName());
			};
		});	
		metaChoice._setDebugCallback(new DebugCallback() {
			@Override
			public void dependencyBeginsChanging(ReadListenDependency<?> source, Dependency d, boolean valid) {
				long t1 = System.currentTimeMillis();
				System.out.println((t1-t0)+": "+"begin: "+d.dependencyName() +"->"+metaChoice.dependencyName());
			};
			@Override
			public void dependencyEndsChanging(ReadListenDependency<?> source, Dependency d) {
				long t1 = System.currentTimeMillis();
				System.out.println((t1-t0)+": "+"end: "+d.dependencyName() +"->"+metaChoice.dependencyName());
			};
		});	
		ValueListener cl = e->{
			long t1 = System.currentTimeMillis();
			System.out.println((t1-t0)+": "+e.getSource());
			if(e.getSource()==choice.validity())
				System.out.println();
		};
		choice.addValueListener(cl);
		choice.validity().addValueListener(cl);
		metaChoice.addValueListener(cl);
		metaChoice.validity().addValueListener(cl);
		v1a.addValueListener(cl);
		v1a.validity().addValueListener(cl);
		v1b.addValueListener(cl);
		v1b.validity().addValueListener(cl);
		v1ad.addValueListener(cl);
		v1ad.validity().addValueListener(cl);
		v1bd.addValueListener(cl);
		v1bd.validity().addValueListener(cl);
		v1sum.addValueListener(cl);
		v1sum.validity().addValueListener(cl);
		System.out.println();System.out.println();System.out.println();System.out.println();
		
		v1a.set(2);
		Thread.sleep(50);
		v1b.set(20);
		Thread.sleep(200);
		v1a.set(100);
		v1a.set(1000);

		
//		v2.set(20);
//		v1.set(10);
//		v1.invalidate();
//		v2.set(200);
//		v2.invalidate();
//		v1.set(100);
//		v3.invalidate();
//		v1.invalidate();
	}
}
