package pile.utils;
/**
 * An Exception that reports a custom stack trace
 */
public class StackTraceWrapper extends Exception{
	public StackTraceWrapper(String message, StackTraceElement[] stackTrace) {
		super(message);
		setStackTrace(stackTrace);
	}
	public StackTraceWrapper(StackTraceElement[] stackTrace) {
		super();
		setStackTrace(stackTrace);
	}
	@Override
	public synchronized Throwable fillInStackTrace() {
		return this;
	}
}