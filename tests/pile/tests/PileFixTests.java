package pile.tests;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.prefs.Preferences;

import pile.aspect.LastValueRememberer;
import pile.impl.Piles;
import pile.interop.preferences.PrefInterop;
import pile.interop.preferences.PrefInterop.NullBehavior;
import pile.specialized_String.IndependentString;
import pile.specialized_String.combinations.LastValueRemembererString;
import pile.specialized_double.IndependentDouble;
import pile.specialized_double.PileDouble;
import pile.specialized_double.SealDouble;
import pile.specialized_int.IndependentInt;
import pile.specialized_int.PileInt;
import pile.specialized_int.SealInt;
import pile.utils.SequentialQueue;

/**
 * Regression checks for the bugs fixed in {@code possible-bugs.md} (Tier A/B/C). One assertion
 * per behaviour, runnable with {@code java -cp <out> pile.tests.PileFixTests}.
 */
public class PileFixTests {
	enum Color { RED, GREEN, BLUE }

	public static void main(String[] args) {
		Check.run("PB-38", PileFixTests::pb38_intAddDropsOperand);
		Check.run("PB-39", PileFixTests::pb39_doubleInverse);
		Check.run("PB-40", PileFixTests::pb40_doubleDivideRW);
		Check.run("PB-33", PileFixTests::pb33_sequentialQueueWorkerThread);
		Check.run("PB-31/32", PileFixTests::pb31_32_prefStoreNull);
		Check.run("\\0 escape", PileFixTests::prefNulEscape);
		System.exit(Check.summary("PileFixTests"));
	}

	/** PB-38: PileInt.add(readDep, const) must be op+const, not the constant. */
	static void pb38_intAddDropsOperand() {
		Check.section("PB-38 PileInt.add read-only operand");
		IndependentInt x = Piles.independent(5).build();
		SealInt sum = PileInt.add(x, 3);
		Check.eq("5+3", 8, sum.get());
		x.set(10);
		Check.eq("tracks operand: 10+3", 13, sum.get());
		SealInt diff = PileInt.subtract(x, 4);
		Check.eq("10-4 (propagates through addRO)", 6, diff.get());
	}

	/** PB-39: PileDouble.inverse must be the reciprocal 1/v (was negation). */
	static void pb39_doubleInverse() {
		Check.section("PB-39 PileDouble.inverse reciprocal");
		IndependentDouble x = Piles.independent(4.0).build();
		SealDouble inv = PileDouble.inverse(x);
		Check.eqD("1/4", 0.25, inv.get());
		x.set(2.0);
		Check.eqD("tracks: 1/2", 0.5, inv.get());
		inv.set(0.1); // write-back: x = 1/0.1
		Check.eqD("RW write-back: x = 1/0.1", 10.0, x.get());
	}

	/** PB-40: PileDouble.divideRW must redirect writes to the operand. */
	static void pb40_doubleDivideRW() {
		Check.section("PB-40 PileDouble.divideRW write-back");
		IndependentDouble x = Piles.independent(8.0).build();
		SealDouble half = PileDouble.divideRW(x, 2.0);
		Check.eqD("8/2", 4.0, half.get());
		half.set(5.0); // write-back: x = 5*2
		Check.eqD("RW write-back: x = 5*2", 10.0, x.get());
	}

	/** PB-33: isQueueWorkerThread must compare the worker Thread, not the Future. */
	static void pb33_sequentialQueueWorkerThread() throws InterruptedException {
		Check.section("PB-33 SequentialQueue.isQueueWorkerThread");
		SequentialQueue q = new SequentialQueue("pile-test-queue");
		boolean[] onWorker = { false };
		CountDownLatch latch = new CountDownLatch(1);
		q.enqueue(() -> { onWorker[0] = q.isQueueWorkerThread(); latch.countDown(); });
		Check.that("task ran within 5s", latch.await(5, TimeUnit.SECONDS));
		Check.that("true inside the worker", onWorker[0]);
		Check.that("false on the main thread", !q.isQueueWorkerThread());
	}

	/** PB-31/32: STORE_NULL stores null as "" (enum/string) and escapes all-'\0' strings. */
	static void pb31_32_prefStoreNull() throws Exception {
		Check.section("PB-31/32 PrefInterop STORE_NULL round-trip");
		Preferences node = Preferences.userRoot().node("pile_test_" + System.nanoTime());
		try {
			LastValueRemembererString rs =
					PrefInterop.rememberString(node, "k", "def", NullBehavior.STORE_NULL);
			rs.storeLastValue(null);
			Check.eq("string null -> null", null, rs.recallLastValue());
			rs.storeLastValue("");
			Check.eq("string \"\" -> \"\" (verbatim, distinct from null)", "", rs.recallLastValue());
			rs.storeLastValue(" ");
			Check.eq("string \" \" (single space, verbatim)", " ", rs.recallLastValue());
			rs.storeLastValue("  leading");
			Check.eq("string \"  leading\" (verbatim)", "  leading", rs.recallLastValue());
			rs.storeLastValue("hello");
			Check.eq("string normal", "hello", rs.recallLastValue());

			Function<String, Color> resolver = Color::valueOf;
			LastValueRememberer<Color> re =
					PrefInterop.rememberEnum(node, "e", Color.RED, resolver, NullBehavior.STORE_NULL);
			re.storeLastValue(null);
			Check.eq("enum null -> null", null, re.recallLastValue());
			re.storeLastValue(Color.GREEN);
			Check.eq("enum normal", Color.GREEN, re.recallLastValue());
		} finally {
			node.removeNode();
		}
	}

	/** Preferences forbid U+0000: any '\0' (or backslash) in a stored string is escaped on store and recovered on recall. */
	static void prefNulEscape() throws Exception {
		Check.section("\\0 escape for Preferences-backed strings");
		Preferences node = Preferences.userRoot().node("pile_test_" + System.nanoTime());
		try {
			// rememberString, non-STORE_NULL mode: '\0' and backslash values round-trip (and don't throw)
			LastValueRemembererString ig = PrefInterop.rememberString(node, "ig", "def", NullBehavior.IGNORE);
			ig.storeLastValue("a\0b");
			Check.eq("IGNORE: a\\0b round-trips", "a\0b", ig.recallLastValue());
			ig.storeLastValue("c:\\path\\x");
			Check.eq("IGNORE: backslashes round-trip", "c:\\path\\x", ig.recallLastValue());
			ig.storeLastValue("plain");
			Check.eq("IGNORE: plain verbatim", "plain", ig.recallLastValue());

			// rememberString, STORE_NULL: '\0' values round-trip; null stays null; a real "" is not null
			LastValueRemembererString sn = PrefInterop.rememberString(node, "sn", "def", NullBehavior.STORE_NULL);
			sn.storeLastValue("x\0y");
			Check.eq("STORE_NULL: x\\0y round-trips", "x\0y", sn.recallLastValue());
			sn.storeLastValue(null);
			Check.eq("STORE_NULL: null stays null", null, sn.recallLastValue());
			sn.storeLastValue("");
			Check.eq("STORE_NULL: real \"\" is not null", "", sn.recallLastValue());

			// live stringPreference: setting a '\0' value must not throw and must persist through prefs
			IndependentString sp = PrefInterop.stringPreference(node, "sp", "def", NullBehavior.IGNORE);
			sp.set("p\0q");
			Check.eq("stringPreference: in-memory value", "p\0q", sp.get());
			IndependentString sp2 = PrefInterop.stringPreference(node, "sp", "def", NullBehavior.IGNORE);
			Check.eq("stringPreference: persisted + round-tripped via prefs", "p\0q", sp2.get());

			// invalid escape sequences (e.g. a hand-edited preferences store) are left verbatim, not corrupted
			node.put("raw1", "a\\xb"); // backslash-x: not a valid escape
			Check.eq("invalid escape \\x kept verbatim", "a\\xb",
					PrefInterop.rememberString(node, "raw1", "def", NullBehavior.IGNORE).recallLastValue());
			node.put("raw2", "ends\\"); // trailing backslash
			Check.eq("trailing backslash kept", "ends\\",
					PrefInterop.rememberString(node, "raw2", "def", NullBehavior.IGNORE).recallLastValue());
		} finally {
			node.removeNode();
		}
	}
}
