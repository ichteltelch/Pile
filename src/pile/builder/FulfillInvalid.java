package pile.builder;

import pile.aspect.recompute.Recomputation;

/**
 * Throw this from a recomputer code handed to a pile builder to indicate that 
 * Recomputation#fulfillInvalid() should be called on the current {@link Recomputation}
 * @author bb
 *
 */
public class FulfillInvalid extends RuntimeException{
	/**
	 * 
	 */
	private static final long serialVersionUID = -5232952595497167195L;
	public FulfillInvalid() {
	}
	public FulfillInvalid(String msg) {
		super(msg);
	}
	public FulfillInvalid(String msg, Throwable cause) {
		super(msg, cause);
	}
	public FulfillInvalid(Throwable cause) {
		super(cause);
	}
	/**
	 * @return never
	 * @throws FulfillInvalid
	 */
	public static FulfillInvalid r() throws FulfillInvalid{
		throw new FulfillInvalid();
	}
	/**
	 * @return never
	 * @throws FulfillInvalid
	 */
	public static FulfillInvalid r(String msg) throws FulfillInvalid{
		throw new FulfillInvalid(msg);
	}
	/**
	 * @return never
	 * @throws FulfillInvalid
	 */
	public static FulfillInvalid r(String msg, Throwable cause) throws FulfillInvalid{
		throw new FulfillInvalid(msg, cause);
	}
	/**
	 * @return never
	 * @throws FulfillInvalid
	 */
	public static FulfillInvalid r(Throwable cause) throws FulfillInvalid{
		throw new FulfillInvalid(cause);
	}

}
