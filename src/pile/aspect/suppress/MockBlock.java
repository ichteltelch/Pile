package pile.aspect.suppress;

/**
 * A workaround for Java's lack of user-defined block constructs. Use like this:
 * <code>
 * <pre>
 * try(MockBlock _igonred = someMethod()){
 * 	content();
 * }
 * </pre>
 * </code>
 * A {@link MockBlock} must be {@link #close() close}d in the same thread that opened it.
 * 
 * @author bb
 *
 */
public abstract class MockBlock implements SafeCloseable {

	/**
	 * A {@link MockBlock} that does nothing. 
	 */
	public static final MockBlock NOP = new MockBlock() {
		{myThread=null;}
		@Override protected void open() {}
		@Override protected void close_impl() {}
		public CancelClose cancellableClose() {return CancelClose.NOP;}
	};
	Thread myThread;
	/**
	 * Remembers the current thread, and {@link #open() open}s the block.
	 */
	public MockBlock() {
		myThread = Thread.currentThread();
		open();
	}
	/**
	 * Remembers the current thread, and {@link #open() open}s the block.
	 * Also calls the given {@link Runnable}, which is considered to be part of the opening process.
	 */
	public MockBlock(Runnable alsoOpen) {
		myThread = Thread.currentThread();
		open();
		if(alsoOpen!=null)
			alsoOpen.run();
	}
	/**
	 * Make a {@link MockBlock} with trivial opening logic and the given {@link Runnable} for closing.
	 * @param r
	 * @return
	 */
	public static MockBlock closeOnly(Runnable r) {
		return new MockBlock() {
			@Override protected void open() {}
			@Override protected void close_impl() {r.run();}
		};
	}
	/**
	 * Called when entering the block.
	 */
	protected abstract void open();

	/**
	 * Called when leaving the block.
	 */
	protected abstract void close_impl();
	@Override
	public void close() {
		if(myThread==null)
			return;
		if(Thread.currentThread()!=myThread)
			throw new IllegalStateException("MockBlock closed in wrong thread");
		myThread=null;
		close_impl();
	}
	/**
	 * I can't think of any use cases for this, but if you need to move the closing 
	 * of the block outside the try-with-resources block, you can use this.
	 * 
	 * If an exception is thrown, the block will be closed.
	 * @return
	 */
	public CancelClose cancellableClose() {
		CancelClose r = null;
		try {
			r = new CancelClose() {
				boolean canceled;
				@Override
				public void close() {
					if(!canceled)
						MockBlock.this.close();
				}

				@Override
				public void cancel() {
					canceled = true;
				}
			};
		}finally {
			if(r==null)
				close();
		}
		return r;
	}
}
