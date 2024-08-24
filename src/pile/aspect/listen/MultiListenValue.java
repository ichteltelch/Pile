package pile.aspect.listen;

public interface MultiListenValue extends ListenValue{
	
	/**
	 * Create a new {@link MultiListenValue}
	 * The events will be forwarded individually and specify the original source.
	 */
	public static MultiListenValue make() {
		return new ConcreteMultiListenValue();
	}

	
	/**
	 * Create a new {@link MultiListenValue}. The events will be collected in a
	 * {@link RateLimitedValueListener} before being forwarded at a limited rate.
	 * @param coldStartTime 
	 * @param coolDownTime
	 * @param startCoolingBefore
	 * @param careAboutSources if <code>true</code>, the source will be the set of sources collected
	 * by the {@link RateLimitedValueListener}. If <code>false</code>, the source
	 * will be this {@link ConcreteMultiListenValue}
	 */
	public static MultiListenValue rateLimited(
			long coldStartTime, 
			long coolDownTime, 
			boolean startCoolingBefore,
			boolean careAboutSources) {
		return new ConcreteMultiListenValue(
				coldStartTime, coolDownTime, startCoolingBefore, careAboutSources
				);
	}
	
	/**
	 * Start collecting events from a {@link ListenValue}.
	 * @param v the value. will hold no strong references to this {@link ConcreteMultiListenValue}.
	 * @return <code>true</code> iff the value was not already collected from
	 */
	public boolean add(ListenValue v);
	/**
	 * Stop collecting Events from a {@link ListenValue}
	 * @param v 
	 * @return <code>true</code> iff the value was previously collected from
	 */
	public boolean remove(ListenValue v);
	/**
	 * @param v
	 * @return Whether {@link ValueEvent}s from the given {@link ListenValue} 
	 * are currently collected
	 */
	public boolean collectsFrom(ListenValue v);
	
	public default int addCount(ListenValue...listenValues) {
		int count = 0;
		for(ListenValue v: listenValues)
			if(add(v))
                ++count;
        return count;
	}
    public default int addCount(Iterable<? extends ListenValue> listenValues) {
    	int count = 0;
        for(ListenValue v: listenValues)
            if(add(v))
                ++count;
        return count;
    }
    public default int removeCount(ListenValue...listenValues) {
    	int count = 0;
        for(ListenValue v: listenValues)
            if(remove(v))
                ++count;
        return count;
    }
    public default int removeCounting(Iterable<? extends ListenValue> listenValues) {
    	int count = 0;
        for(ListenValue v: listenValues)
            if(remove(v))
                ++count;
        return count;
    }
    public default MultiListenValue add(ListenValue...listenValues) {
    	for(ListenValue v: listenValues)
            add(v);
        return this;
    }
    public default MultiListenValue add(Iterable<? extends ListenValue> listenValues) {
        for(ListenValue v: listenValues)
            add(v);
        return this;
    }
    public default MultiListenValue remove(ListenValue...listenValues) {
        for(ListenValue v: listenValues)
            remove(v);
        return this;
    }
    public default MultiListenValue remove(Iterable<? extends ListenValue> listenValues) {
        for(ListenValue v: listenValues)
            remove(v);
        return this;
    }



    
	
	
	
	
}
