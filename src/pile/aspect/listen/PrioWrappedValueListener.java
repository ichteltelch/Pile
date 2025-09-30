package pile.aspect.listen;

/**
 * A {@link ValueListener} with a non-standard {@link #priority() priority}.
 * @author bb
 *
 */
final class PrioWrappedValueListener implements ValueListener {
	private final int prio;
	private final ValueListener back;
	/**
	 * Make a {@link ValueListener} that works like the given one, but with a different
	 * {@link #priority() priority}
	 * @param back
	 * @param prio
	 */
	public PrioWrappedValueListener(ValueListener back, int prio) {
		this.back=back;
		this.prio=prio;
	}


	@Override
	public void valueChanged(ValueEvent e) {
		back.valueChanged(e);
	}

	@Override
	public int priority() {
		return prio;
	}

	@Override
	public void runImmediately() {
		back.runImmediately();
	}
	
	@Override
	public void runImmediately(boolean inThisThread) {
		back.runImmediately(inThisThread);
	}
	
	@Override
	public ValueListener withPrio(int prio) {
		return back.withPrio(prio);
	}
}