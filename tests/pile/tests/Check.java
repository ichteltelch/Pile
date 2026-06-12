package pile.tests;

/**
 * A tiny zero-dependency test harness for the Pile test suite: assertion helpers plus a
 * pass/fail tally. No external libraries — just a {@code main} that runs checks and prints a
 * summary; {@link #summary(String)} returns a non-zero code on failure so a runner can
 * {@link System#exit} on it.
 *
 * <p>Usage:
 * <pre>
 *   Check.section("int arithmetic");
 *   Check.eq("x+3", 8, y.get());
 *   Check.that("valid", v.isValid());
 *   System.exit(Check.summary("PileFixTests"));
 * </pre>
 */
public class Check {
	private static int passed, failed;
	private static String current = "?";

	/** Label the group of checks that follow (shown in failure messages). */
	public static void section(String name) {
		current = name;
		System.out.println("-- " + name);
	}

	/** Assert {@code cond} is true. */
	public static void that(String what, boolean cond) {
		if (cond) {
			passed++;
		} else {
			failed++;
			System.out.println("  FAIL [" + current + "] " + what);
		}
	}

	/** Assert {@code expected.equals(actual)} (null-safe). Use boxed values for primitives. */
	public static void eq(String what, Object expected, Object actual) {
		boolean ok = expected == null ? actual == null : expected.equals(actual);
		if (ok) {
			passed++;
		} else {
			failed++;
			System.out.println("  FAIL [" + current + "] " + what
					+ ": expected <" + expected + "> but was <" + actual + ">");
		}
	}

	/** Assert two doubles are equal within a small tolerance (and NaN==NaN). */
	public static void eqD(String what, double expected, double actual) {
		boolean ok = Double.compare(expected, actual) == 0
				|| Math.abs(expected - actual) <= 1e-9;
		if (ok) {
			passed++;
		} else {
			failed++;
			System.out.println("  FAIL [" + current + "] " + what
					+ ": expected <" + expected + "> but was <" + actual + ">");
		}
	}

	/** A throwing block, for {@link #throwsX}. */
	public interface Block {
		void run() throws Throwable;
	}

	/** Assert running {@code b} throws an instance of {@code ex}. */
	public static void throwsX(String what, Class<? extends Throwable> ex, Block b) {
		try {
			b.run();
			failed++;
			System.out.println("  FAIL [" + current + "] " + what
					+ ": expected " + ex.getSimpleName() + " but nothing was thrown");
		} catch (Throwable t) {
			if (ex.isInstance(t)) {
				passed++;
			} else {
				failed++;
				System.out.println("  FAIL [" + current + "] " + what
						+ ": expected " + ex.getSimpleName() + " but got " + t);
			}
		}
	}

	/** Run a section, turning any uncaught throwable into a recorded failure (so the suite continues). */
	public static void run(String name, Block b) {
		try {
			b.run();
		} catch (Throwable t) {
			failed++;
			System.out.println("  FAIL [" + name + "] threw " + t);
		}
	}

	/** Print the tally and return an exit code (0 = all passed). */
	public static int summary(String suite) {
		System.out.println(suite + ": " + passed + " passed, " + failed + " failed");
		return failed == 0 ? 0 : 1;
	}
}
