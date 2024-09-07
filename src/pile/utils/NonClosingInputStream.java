package pile.utils;

import java.io.IOException;
import java.io.InputStream;

/**
 * A Wrapper for another {@link InputStream} whose {@link #close()}-method
 * does <b>not</b> call the close method of the wrapped stream.
 */
public class NonClosingInputStream extends InputStream {
	final InputStream wrapped;
	int closeAttempts = 0;
	public NonClosingInputStream(InputStream is) {
		wrapped = is;
	}

	@Override
	public int read() throws IOException {
		return wrapped.read();
	}
	@Override
	public int read(byte[] b) throws IOException {
		return wrapped.read(b);
	}
	@Override
	public int read(byte[] b, int off, int len) throws IOException {
		return wrapped.read(b, off, len);
	}
	@Override
	public void reset() throws IOException {
		wrapped.reset();
	}
	@Override
	public int available() throws IOException {
		return wrapped.available();
	}
	@Override
	public void mark(int readlimit) {
		wrapped.mark(readlimit);
	}
	@Override
	public boolean markSupported() {
		return wrapped.markSupported();
	}
	@Override
	public long skip(long n) throws IOException {
		return wrapped.skip(n);
	}
	@Override
	public synchronized void close() {
		closeAttempts++;
	}
	public int getCloseAttempts() {
		return closeAttempts;
	}
	
	
	

}
