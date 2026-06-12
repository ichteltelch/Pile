package pile.tests;

import pile.aspect.listen.ValueListener;
import pile.aspect.suppress.Suppressor;
import pile.impl.Piles;
import pile.specialized_bool.IndependentBool;
import pile.specialized_bool.SealBool;
import pile.specialized_bool.combinations.ReadWriteListenDependencyBool;
import pile.specialized_int.IndependentInt;
import pile.specialized_int.PileInt;
import pile.specialized_int.SealInt;

/**
 * Characterization checks for core Pile behaviour (independent values, derivation/propagation,
 * validity, sealing/redirection, the boolean and integer operator algebra, listeners, buffers,
 * transactions). Zero-dependency; run with {@code java -cp <out> pile.tests.PileCoreTests}.
 */
public class PileCoreTests {
	public static void main(String[] args) {
		Check.run("independent", PileCoreTests::independent);
		Check.run("constant", PileCoreTests::constant);
		Check.run("derive+propagate", PileCoreTests::derive);
		Check.run("multi-dependency", PileCoreTests::multiDep);
		Check.run("bool not (redirect)", PileCoreTests::boolNot);
		Check.run("bool and/or", PileCoreTests::boolAndOr);
		Check.run("int arithmetic", PileCoreTests::intArith);
		Check.run("int comparison", PileCoreTests::intCompare);
		Check.run("listener fires", PileCoreTests::listener);
		Check.run("valid buffer", PileCoreTests::validBuffer);
		Check.run("transaction batches", PileCoreTests::transaction);
		System.exit(Check.summary("PileCoreTests"));
	}

	static void independent() {
		Check.section("independent value");
		IndependentInt x = Piles.independent(3).build();
		Check.eq("initial", 3, x.get());
		Check.that("valid", x.isValid());
		x.set(7);
		Check.eq("after set", 7, x.get());
	}

	static void constant() {
		Check.section("constant");
		Check.eq("constant(5)", 5, Piles.constant(5).get());
		Check.that("TRUE", Piles.TRUE.isTrue());
		Check.that("FALSE", Piles.FALSE.isFalse());
	}

	static void derive() throws InterruptedException {
		Check.section("derived value tracks its dependency");
		IndependentInt a = Piles.independent(4).build();
		PileInt b = Piles.computeInt(() -> a.get() * 10).whenChanged(a);
		Check.eq("4*10", 40, b.getValid());
		a.set(5);
		Check.eq("recomputes to 5*10", 50, b.getValid());
	}

	static void multiDep() throws InterruptedException {
		Check.section("value derived from two dependencies");
		IndependentInt a = Piles.independent(2).build();
		IndependentInt b = Piles.independent(3).build();
		PileInt sum = Piles.computeInt(() -> a.get() + b.get()).whenChanged(a, b);
		Check.eq("2+3", 5, sum.getValid());
		a.set(10);
		Check.eq("10+3", 13, sum.getValid());
		b.set(20);
		Check.eq("10+20", 30, sum.getValid());
	}

	static void boolNot() {
		Check.section("not() is a writable redirect");
		IndependentBool x = Piles.independent(true).build();
		ReadWriteListenDependencyBool nx = x.not();
		Check.eq("!true", false, nx.get());
		x.set(false);
		Check.eq("tracks: !false", true, nx.get());
		nx.set(false); // redirect: x = !false = true
		Check.eq("write-back: x", true, x.get());
	}

	static void boolAndOr() throws InterruptedException {
		Check.section("and / or");
		IndependentBool a = Piles.independent(true).build();
		IndependentBool b = Piles.independent(false).build();
		SealBool and = a.and(b);
		SealBool or = a.or(b);
		Check.eq("true&false", false, and.getValid());
		Check.eq("true|false", true, or.getValid());
		b.set(true);
		Check.eq("true&true", true, and.getValid());
	}

	static void intArith() throws InterruptedException {
		Check.section("plus / minus / times");
		IndependentInt a = Piles.independent(6).build();
		IndependentInt b = Piles.independent(4).build();
		Check.eq("6+4", 10, a.plus(b).getValid());
		Check.eq("6-4", 2, a.minus(b).getValid());
		Check.eq("6*4", 24, PileInt.multiply(a, b).getValid());
	}

	static void intCompare() throws InterruptedException {
		Check.section("comparison -> bool");
		IndependentInt a = Piles.independent(3).build();
		IndependentInt b = Piles.independent(5).build();
		Check.eq("3<5", true, a.lessThan(b, false).getValid());
		Check.eq("3>5", false, a.greaterThan(b, false).getValid());
		a.set(9);
		Check.eq("9<5", false, a.lessThan(b, false).getValid());
	}

	static void listener() {
		Check.section("listener fires on change");
		IndependentInt x = Piles.independent(0).build();
		int[] fires = { 0 };
		ValueListener l = e -> fires[0]++;
		x.addValueListener(l);
		x.set(1);
		x.set(2);
		Check.that("fired at least twice", fires[0] >= 2);
		int before = fires[0];
		x.removeValueListener(l);
		x.set(3);
		Check.eq("no fire after removal", (long) before, (long) fires[0]);
	}

	static void validBuffer() throws InterruptedException {
		Check.section("validBuffer keeps the last valid value");
		IndependentInt a = Piles.independent(1).build();
		PileInt d = Piles.computeInt(() -> a.get() + 100).whenChanged(a);
		IndependentInt vb = d.validBuffer();
		Check.eq("buffer = 101", 101, vb.getValid());
		a.set(2);
		Check.eq("buffer tracks valid: 102", 102, vb.getValid());
		d.permaInvalidate();
		Check.that("buffer stays valid while leader invalid", vb.isValid());
		Check.eq("buffer holds last valid 102", 102, vb.get());
	}

	static void transaction() throws InterruptedException {
		Check.section("transaction coalesces downstream recomputation");
		// baseline: three separate sets, observing the derived value after each
		IndependentInt a1 = Piles.independent(0).build();
		PileInt d1 = Piles.computeInt(() -> a1.get()).whenChanged(a1);
		int[] f1 = { 0 };
		d1.getValid();
		d1.addValueListener(e -> f1[0]++);
		a1.set(1); d1.getValid();
		a1.set(2); d1.getValid();
		a1.set(3); d1.getValid();
		int withoutTx = f1[0];

		// the same three sets, but inside one transaction
		IndependentInt a2 = Piles.independent(0).build();
		PileInt d2 = Piles.computeInt(() -> a2.get()).whenChanged(a2);
		int[] f2 = { 0 };
		d2.getValid();
		d2.addValueListener(e -> f2[0]++);
		try (Suppressor t = a2.transaction()) {
			a2.set(1);
			a2.set(2);
			a2.set(3);
		}
		int withTx = f2[0];

		Check.eq("final derived value", 3, d2.getValid());
		Check.that("coalesces: withTx=" + withTx + " <= withoutTx=" + withoutTx, withTx <= withoutTx);
	}
}
