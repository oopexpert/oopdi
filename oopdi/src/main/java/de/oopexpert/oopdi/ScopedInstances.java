package de.oopexpert.oopdi;

import static java.lang.Thread.currentThread;
import static java.util.Collections.synchronizedMap;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.WeakHashMap;

import de.oopexpert.oopdi.proxy.RequestScopeManager;

public class ScopedInstances {

    private final InstancesState globalInstances = new InstancesState();
    private final Map<Thread, InstancesState> threadInstanceMaps = synchronizedMap(new WeakHashMap<>());
    private final RequestScopeManager requestScopeManager;

	public ScopedInstances(RequestScopeManager requestScopeManager) {
		this.requestScopeManager = Objects.requireNonNull(requestScopeManager, "requestScopeManager must not be null");
	}

	public InstancesState getScopedInstancesState(Scope scope) {
		return scope.select(globalInstances, getThreadInstancesMap(), requestScopeManager);
	}

	private synchronized InstancesState getThreadInstancesMap() {
		
		if (threadInstanceMaps.get(currentThread()) == null) {
			threadInstanceMaps.put(currentThread(), new InstancesState());
		}
		
		return threadInstanceMaps.get(currentThread());
	}

	public Collection<InstancesState> allInstanceStates() {
		Collection<InstancesState> all = new ArrayList<>();
		all.add(globalInstances);
		synchronized (threadInstanceMaps) {
			all.addAll(threadInstanceMaps.values());
		}
		return all;
	}

	/**
	 * Drops all per-thread instance states, releasing beans pinned by pooled threads beyond
	 * their container's shutdown. Called once at the end of {@code Context.shutdown()}, after
	 * the drain loop destroyed everything (global state intentionally stays: already-resolved
	 * beans remain readable from the scope cache after shutdown).
	 */
	public void clearThreadStates() {
		synchronized (threadInstanceMaps) {
			threadInstanceMaps.clear();
		}
	}

}
