package de.oopexpert.oopdi;

import static java.lang.Thread.currentThread;
import static java.util.Collections.synchronizedMap;

import java.util.HashMap;
import java.util.Map;

public class ScopedInstances {

    private final InstancesState globalInstances = new InstancesState();
    private final Map<Thread, InstancesState> threadInstanceMaps = synchronizedMap(new HashMap<>());

	public InstancesState getScopedInstancesState(Scope scope) {
		return scope.select(globalInstances, getThreadInstancesMap());
	}

	private synchronized InstancesState getThreadInstancesMap() {
		
		if (threadInstanceMaps.get(currentThread()) == null) {
			threadInstanceMaps.put(currentThread(), new InstancesState());
		}
		
		return threadInstanceMaps.get(currentThread());
	}

}
