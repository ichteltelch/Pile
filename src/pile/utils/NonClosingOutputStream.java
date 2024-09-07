package pile.utils;

import java.io.IOException;
import java.io.OutputStream;

/**
 * A Wrapper for another {@link OutputStream} whose {@link #close()}-method
 * does <b>not</b> call the close method of the wrapped stream.
 */
public class NonClosingOutputStream extends OutputStream {

	final OutputStream wrapped;
	int closeAttempts = 0;
	public NonClosingOutputStream(OutputStream fos) {
		wrapped=fos;
	}

	@Override
	public void write(int b) throws IOException {
		wrapped.write(b);
	}
	@Override
	public void write(byte[] b) throws IOException {
		wrapped.write(b);
	}
	@Override
	public void write(byte[] b, int off, int len) throws IOException {
		wrapped.write(b, off, len);
	}
	@Override
	public void flush() throws IOException {
		wrapped.flush();
	}
	@Override
	public void close() throws IOException {
		closeAttempts++;
		wrapped.flush();
	}
	public int getCloseAttempts() {
		return closeAttempts;
	}
}
