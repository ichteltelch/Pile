package pile.tests;


import pile.aspect.Dependency;
import pile.aspect.ValueBracket;
import pile.aspect.combinations.ReadListenDependency;
import pile.aspect.listen.ValueListener;
import pile.impl.DebugCallback;
import pile.impl.Piles;
import pile.specialized_String.PileString;
import pile.specialized_bool.MutBool;
import pile.specialized_int.IndependentInt;
import pile.specialized_int.PileInt;

public class LoadSimTest {
	public static void main(String[] args) throws InterruptedException {
		long[] t0 = {System.currentTimeMillis()};

		MutBool loggerStarted=new MutBool(false);
		ValueListener logger=e->{
			if(loggerStarted.val)
				System.out.println((System.currentTimeMillis()-t0[0])+" | Changed: "+e.getSource());
		};
		ValueBracket<Object, ReadListenDependency<?>> loggerBracket = ValueBracket.make(false, (v, o)->{
			if(loggerStarted.val)
				System.out.println((System.currentTimeMillis()-t0[0])+" | Open bracket of "+o+" for value " +v);
		}, (v, o)->{
			if(loggerStarted.val)
				System.out.println((System.currentTimeMillis()-t0[0])+" | Close bracket of "+o+" for value " +v);
			return false;
		});
		DebugCallback debLogger = new DebugCallback() {
			@Override
			public void dependencyBeginsChanging(ReadListenDependency<?> source, Dependency d, boolean valid) {
				if(loggerStarted.val)
					System.out.println((System.currentTimeMillis()-t0[0])+" | Dependency begins changing: "+d+" -> "+source);
			}
			@Override
			public void dependencyEndsChanging(ReadListenDependency<?> source, Dependency d) {
				if(loggerStarted.val)
					System.out.println((System.currentTimeMillis()-t0[0])+" | Dependency ends changing: "+d+" -> "+source);
			}
			@Override
			public void beginTransactionCalled(ReadListenDependency<?> source) {
				if(loggerStarted.val)
					System.out.println((System.currentTimeMillis()-t0[0])+" | Begin transaction: "+source);
			}
			@Override
			public void endTransactionCalled(ReadListenDependency<?> source) {
				if(loggerStarted.val)
					System.out.println((System.currentTimeMillis()-t0[0])+" | End transaction: "+source);
			}
			@Override
			public void cancellingOngoingRecomputation(ReadListenDependency<?> source) {
				if(loggerStarted.val)
					System.out.println((System.currentTimeMillis()-t0[0])+" | cancelling ongoing recomputation: "+source);
			}
			@Override
			public void newlyScheduledRecomputation(ReadListenDependency<?> source) {
				if(loggerStarted.val)
					System.out.println((System.currentTimeMillis()-t0[0])+" | scheduling recopmutation: "+source);
			}
			@Override
			public void unschedulePendingRecomputation(ReadListenDependency<?> source) {
				if(loggerStarted.val)
					System.out.println((System.currentTimeMillis()-t0[0])+" | unscheduling pending recopmutation: "+source);
			}
			@Override
			public void startPendingRecomputation(ReadListenDependency<?> source) {
				if(loggerStarted.val)
					System.out.println((System.currentTimeMillis()-t0[0])+" | starting pending recopmutation: "+source);
			}
			@Override
			public void fireDeepRevalidate(ReadListenDependency<?> source) {
				if(loggerStarted.val)
					System.out.println((System.currentTimeMillis()-t0[0])+" | fire deep revalidate "+source);
			}
		};

		IndependentInt a = Piles.independent(0).name("a").onChange(logger).build();

		PileInt b = Piles.computeInt(()->{
			return a.get();
		}).delay(1000).name("b")
				.onChange(logger).debug(debLogger)//.bracket(loggerBracket)
				.whenChanged(a);
		PileInt c1 = Piles.computeInt(()->{
			return b.get();
		}).delay(1000).name("c1")
				.onChange(logger).debug(debLogger)//.bracket(loggerBracket)
				.whenChanged(b);
		PileInt d1 = Piles.computeInt(()->{
			return c1.get();
		}).delay(1000).name("d1")
				.onChange(logger).debug(debLogger)//.bracket(loggerBracket)
				.whenChanged(c1);
		PileInt c2 = Piles.computeInt(()->{
			return b.get();
		}).delay(1000).name("c2")
				.onChange(logger).debug(debLogger)//.bracket(loggerBracket)
				.whenChanged(b);
		PileInt d2 = Piles.computeInt(()->{
			return c2.get();
		}).delay(1000).name("d2")
				.onChange(logger).debug(debLogger)//.bracket(loggerBracket)
				.whenChanged(c2);
		PileString e = Piles.computeString(()->{
			return d1.get()+";"
					+d2.get()
					;
		}).delay(1000).name("e")
				.onChange(logger).debug(debLogger)
				.whenChanged(d1
						, d2
						);

		;		
		PileString f = Piles.computeString(()->{
			return e.get();
		}).delay(1000).name("f")
				.onChange(logger).debug(debLogger).bracket(loggerBracket)
				.whenChanged(e);
		a.set(1);
		b.revalidate();

		c1.revalidate();
		c1.set(10);
		d1.revalidate();

		c2.revalidate();
		d2.revalidate();

		//e.set("initial");
		f.set("initial");
		System.out.println("reset reference time"); t0[0]=System.currentTimeMillis();
		loggerStarted.set(true);

		Thread.sleep(100);
		a.set(2);
		c1.set(3);

		Thread.sleep(6000);
		System.out.println(f);



	}
}
