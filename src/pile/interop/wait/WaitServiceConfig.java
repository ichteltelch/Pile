package pile.interop.wait;

/**
 * Package private class for handling the injection of the {@link WaitService} dependency  
 * @author bb
 *
 */
final class WaitServiceConfig{
	static WaitService globalDefault = WaitService.DEBUGGABLE_NATIVE;
	static volatile ThreadLocal<WaitService> current;
	static ThreadLocal<WaitService> current(){
		ThreadLocal<WaitService> local = current;
		if(local==null) {
			synchronized (WaitServiceConfig.class) {
				local = current;
				if(local==null) {
					current = local = new InheritableThreadLocal<>();
				}
			}
		}
		return local;
	}
	static ThreadLocal<WaitService> current(WaitService notIfThis){
		ThreadLocal<WaitService> local = current;
		if(local==null) {
			synchronized (WaitServiceConfig.class) {
				local = current;
				if(local==null) {
					if(globalDefault == notIfThis)
						return null;
					current = local = new InheritableThreadLocal<>();
				}
			}
		}
		return local;
	}
	private WaitServiceConfig() {}
}