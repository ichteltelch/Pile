package pile.tests;


import java.io.PrintStream;
import java.util.Optional;
import java.util.Random;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import javax.swing.JButton;

import pile.aspect.ReadValue.InvalidValueException;
import pile.aspect.combinations.Pile;
import pile.aspect.listen.ListenValue;
import pile.aspect.listen.ValueListener;
import pile.aspect.recompute.Recomputation;
import pile.aspect.suppress.Suppressor;
import pile.impl.Independent;
import pile.impl.PileImpl;
import pile.impl.Piles;
import pile.impl.SealPile;
import pile.interop.exec.StandardExecutors;
import pile.specialized_String.PileString;
import pile.specialized_bool.PileBool;

public class Tests {
	public static void main(String[] args) throws InterruptedException {
		basicDependencyTest();
		//statusTests();
		//validBufferTest();
		//		logicTest();
		//		choiceTest();
	}
	static void statusTests() throws InterruptedException {
		PileString v = Piles.init("I")
				.recompute(()->"A")
//				.delay(2000)
				.name("v")
				.build();
		ValueListener obs = e->{
			System.out.println(e.getSource());
		};
		ListenValue[] statuus={
				v,
				v.validity(),
				v.computing(),
				v.nullOrInvalid(),
				v.validNonNull(),
				v.validNull(),
		};
		for(ListenValue s: statuus) {
			s.addValueListener(obs);
			s.fireValueChange();
		}

		System.out.println("\npermaInvalidate");
		v.permaInvalidate();
		System.out.println("\nset");
		v.set("B");
		System.out.println("\nset null");
		v.set(null);
		System.out.println("\nset");
		v.set("C");
		System.out.println("\nrevalidate");
		v.revalidate();
		System.out.println("\nsleep 1000");
		Thread.sleep(1000);
		System.out.println("\nsleep 1000");
		Thread.sleep(1000);
		System.out.println("\nsleep 1000");
		Thread.sleep(1000);
		System.out.println("\nend");
	}

	static void choiceTest() {
		PrintStream o = System.out;
		PileImpl<Integer> ifTrue = Piles.init(1).name("ifTrue").build();		
		PileImpl<Integer> ifFalse = Piles.init(2).name("ifFalse").build();		
		PileImpl<Integer> ifNull = Piles.init(0).name("ifNull").build();
		PileBool chooser = Piles.init(true).name("chooser").build();
		SealPile<Integer> choice = PileBool.chooseWritable(chooser, ifTrue, ifFalse, ifNull);

		o.println(chooser); o.println(ifTrue); o.println(ifFalse); o.println(ifNull); o.println(choice);
		System.out.println("\nchooser <- false");
		chooser.set(false);
		o.println(chooser); o.println(ifTrue); o.println(ifFalse); o.println(ifNull); o.println(choice);
		System.out.println("\nifTrue <- 10");
		ifTrue.set(10);
		o.println(chooser); o.println(ifTrue); o.println(ifFalse); o.println(ifNull); o.println(choice);
		System.out.println("\nifFalse <- 20");
		ifFalse.set(20);
		o.println(chooser); o.println(ifTrue); o.println(ifFalse); o.println(ifNull); o.println(choice);
		System.out.println("\ninvalidate ifTrue");
		ifTrue.permaInvalidate();
		o.println(chooser); o.println(ifTrue); o.println(ifFalse); o.println(ifNull); o.println(choice);
		System.out.println("\ninvalidate ifFalse");
		ifFalse.permaInvalidate();
		o.println(chooser); o.println(ifTrue); o.println(ifFalse); o.println(ifNull); o.println(choice);
		System.out.println("\nchooser <- null");
		chooser.set(null);
		o.println(chooser); o.println(ifTrue); o.println(ifFalse); o.println(ifNull); o.println(choice);
		System.out.println("\ninvalidate chooser");
		chooser.permaInvalidate();
		o.println(chooser); o.println(ifTrue); o.println(ifFalse); o.println(ifNull); o.println(choice);
		System.out.println("\nifTrue <- 100");
		ifTrue.set(10);
		o.println(chooser); o.println(ifTrue); o.println(ifFalse); o.println(ifNull); o.println(choice);
		System.out.println("\nchooser <- true");
		chooser.set(true);
		o.println(chooser); o.println(ifTrue); o.println(ifFalse); o.println(ifNull); o.println(choice);
		System.out.println("\nchoice <- 42");
		choice.set(42);
		o.println(chooser); o.println(ifTrue); o.println(ifFalse); o.println(ifNull); o.println(choice);
		System.out.println("\nchooser <- false");
		chooser.set(false);
		o.println(chooser); o.println(ifTrue); o.println(ifFalse); o.println(ifNull); o.println(choice);
		System.out.println("\nchoice <- 9801");
		choice.set(9801);
		o.println(chooser); o.println(ifTrue); o.println(ifFalse); o.println(ifNull); o.println(choice);
		System.out.println("\ninvalidate chooser");
		chooser.permaInvalidate();
		o.println(chooser); o.println(ifTrue); o.println(ifFalse); o.println(ifNull); o.println(choice);
		System.out.println("\nchoice <- 7");
		choice.set(9801);
		o.println(chooser); o.println(ifTrue); o.println(ifFalse); o.println(ifNull); o.println(choice);

	}

	static void logicTest() {
		PileBool a = Piles.init(true).name("a").build();
		PileBool b = Piles.init(true).name("b").build();
		//		ValueBool notA = a.not();
		//		ValueBool notB = b.not();
		PileBool aAndB = PileBool.and2(a, b);
		PrintStream o = System.out;
		//		o.println(a); o.println(b); o.println(notA); o.println(notB); o.println(aAndB);
		//		o.println("\nb <- false"); b.set(false);
		//		o.println(a); o.println(b); o.println(notA); o.println(notB); o.println(aAndB);
		//		o.println("\ninvalidate a <- false"); a.invalidate();
		//		o.println(a); o.println(b); o.println(notA); o.println(notB); o.println(aAndB);
		//		o.println("\nb <- true"); b.set(true);
		//		o.println(a); o.println(b); o.println(notA); o.println(notB); o.println(aAndB);

		Random rnd = new Random(123);
		for(int i=0; i<100000; ++i) {
			boolean which = rnd.nextBoolean();
			int action = rnd.nextInt(4);
			PileBool chosen = which?a:b;
			o.println();
			o.println("Test "+i);
			if(i==16)
				System.out.println();
			switch(action) {
			case 0:
				o.println(chosen.dependencyName()+" <- null");
				chosen.set(null);
				break;
			case 1:
				o.println(chosen.dependencyName()+" <- true");
				chosen.set(true);
				break;
			case 2:
				o.println(chosen.dependencyName()+" <- false");
				chosen.set(false);
				break;
			case 3:
				o.println("invalidate "+chosen.dependencyName());
				chosen.permaInvalidate();
				break;
			}
			o.println(a); o.println(b); o.println(aAndB);
			System.out.println("depends on a: "+aAndB.dependsOn(a));
			System.out.println("depends on b: "+aAndB.dependsOn(b));
			Optional<Boolean> aVal = getWithValidity(a);
			Optional<Boolean> bVal = getWithValidity(b);
			Optional<Boolean> aAndBVal = getWithValidity(aAndB);
			checkAnd2(aVal, bVal, aAndBVal);
			//			aVal.branch(av->{bVal.branch(bv->{
			//
			//			}, ()->{
			//
			//			});}, ()-> {bVal.branch(bv->{
			//
			//			}, ()->{
			//
			//			});});

		}
	}

	private static <E> Optional<E> getWithValidity(Pile<E> a) {
		if(a.isValid())
			try {
				return Optional.of(a.getValidOrThrow());
			} catch (InvalidValueException e) {
			}
		return Optional.empty();
	}
	static void checkAnd3(Optional<Boolean> aVal, Optional<Boolean> bVal, Optional<Boolean> aAndBVal) {
		int aRank = and3Rank(aVal);
		int bRank = and3Rank(bVal);
		int aAndBRank = and3Rank(aAndBVal);
		if(aAndBRank!=Math.min(aRank, bRank))
			throw new IllegalStateException();
	}

	private static int and3Rank(Optional<Boolean> val) {
		if(!val.isPresent())
			return 2;
		Boolean b = val.get();
		if(b==null)
			return 0;
		return b?3:1;
	}
	static void checkAnd2(Optional<Boolean> aVal, Optional<Boolean> bVal, Optional<Boolean> aAndBVal) {
		int aRank = and2Rank(aVal);
		int bRank = and2Rank(bVal);
		int aAndBRank = and2Rank(aAndBVal);
		if(aAndBRank!=Math.min(aRank, bRank))
			throw new IllegalStateException();
	}

	private static int and2Rank(Optional<Boolean> val) {
		if(!val.isPresent())
			return 0;
		Boolean b = val.get();
		if(b==null)
			return 2;
		return b?3:1;
	}

	static void checkAnd(Optional<Boolean> aVal, Optional<Boolean> bVal, Optional<Boolean> aAndBVal) {
		int aRank = andRank(aVal);
		int bRank = andRank(bVal);
		int aAndBRank = andRank(aAndBVal);
		if(aAndBRank!=Math.min(aRank, bRank))
			throw new IllegalStateException();
	}

	private static int andRank(Optional<Boolean> val) {
		if(val.isPresent())
			return 0;
		Boolean b = val.get();
		if(b==null)
			return 1;
		return b?3:2;
	}

	static void validBufferTest() {
		PileImpl<Integer> master = new PileImpl<>();
		Independent<Integer> slave = master.readOnlyValidBuffer();
		master.avName="master";
		slave.avName="slave";
		System.out.println(master); System.out.println(slave);
		System.out.println("master <- 1"); master.set(1);
		System.out.println(master); System.out.println(slave);
		System.out.println("master <- 2"); master.set(2);
		System.out.println(master); System.out.println(slave);
		System.out.println("invalidate master"); master.permaInvalidate();
		System.out.println(master); System.out.println(slave);
		System.out.println("master <- 3"); master.set(3);
		System.out.println(master); System.out.println(slave);
		System.out.println("slave <- 4"); slave.set(4);
		System.out.println(master); System.out.println(slave);
		System.out.println("invalidate master"); master.permaInvalidate();
		System.out.println(master); System.out.println(slave);
		System.out.println("slave <- 5"); slave.set(5);
		System.out.println(master); System.out.println(slave);



	}

	static void basicDependencyTest() throws InterruptedException {
		Independent<Integer> a = new Independent<>(2).setName("a");
		PileImpl<Integer> b = Piles.init(3).name("b").build();
		System.out.println(a);
		System.out.println(b);
		PileImpl<Integer> sum = Piles.compute(()->a.get()+b.get())
				.name("a+b")
				//				.delay(300)
				.whenChanged(a, b);
		PileImpl<Integer> prod = Piles.compute(()->a.get()*b.get())
				.name("a·b")
				//				.delay(200)
				.whenChanged(a, b);
		PileImpl<String> combined = Piles.compute(()->sum.get()+"; "+prod.get())
				.name("combined")
				.whenChanged(sum, prod);
		PileImpl<Integer> sum1000 = Piles.compute(()->sum.get()*1000)
				.name("(a+b)*1000")
				//				.delay(300)
				.whenChanged(sum);

		System.out.println(sum);
		System.out.println(prod);
		System.out.println(combined);




		ValueListener cl = e->{
			System.out.println("Changed: "+e.getSource());
			System.out.println();
		};
		a.addValueListener(cl);
		b.addValueListener(cl);
		sum.addValueListener(cl);
		sum1000.addValueListener(cl);
		prod.addValueListener(cl);
		combined.addValueListener(cl);
		//		for(int i=0; i<10; ++i) {
		//			System.out.println(sum);
		//			Thread.sleep(100);
		//		}


		if(!true) {
			b.__beginTransaction();
			//			b.permaInvalidate();
			sum.set(7);
			b.set(1);
			b.__endTransaction(true);
			return;
		}


		b.permaInvalidate();
		StandardExecutors.delayed().schedule(()->{
			try {
				b.set(4);
			}catch(Throwable e) {
				e.printStackTrace();
			}
		}, 300, TimeUnit.MILLISECONDS);
		a.set(200);
		StandardExecutors.delayed().schedule(()->{
			try {
				a.set(300);
			}catch(Throwable e) {
				e.printStackTrace();
			}
		}, 700, TimeUnit.MILLISECONDS);
		for(int i=0; i<20; ++i) {
			System.out.println();
			System.out.println("Time step: "+i);
			System.out.println(sum);
			System.out.println(prod);
			System.out.println(combined);
			Thread.sleep(100);
		}
		System.out.println("disabling autovalidation of combined");
		try(Suppressor sa = combined.suppressAutoValidation())
		{
			System.out.println("Invalidating combined");
			//combined.revalidate();
			sum.set(1);
			//combined.autoValidate();
			System.out.println(combined);
			System.out.println("Reenabling autovalidation");

		}
	}
	
	static Consumer<? super Recomputation<JButton>> recompute;
	static void simStartRecomp(){
		Recomputation<JButton> reco = null;
		recompute.accept(reco);
	}
	static void simSetRecomp(Consumer<? super Recomputation<JButton>> recomp){
		recompute=recomp;
	}
	{
		Consumer<Recomputation<? super JButton>> recomp=null;
		simSetRecomp(recomp);
		
		
		
		
	}
}
