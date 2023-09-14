package pile.aspect.suppress;

/**
 * An implementation of {@link AutoCloseable} that does not throw checked exceptions.
 * @author bb
 *
 */
@FunctionalInterface
public interface SafeCloseable extends AutoCloseable{
	public static final SafeCloseable NOP = ()->{};
	@Override
	void close();
}
