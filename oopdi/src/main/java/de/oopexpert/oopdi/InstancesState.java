package de.oopexpert.oopdi;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class InstancesState {

	private final Map<Class<?>, Object> instances = new LinkedHashMap<>();
	private final Set<Class<?>> constructorInjection = ConcurrentHashMap.newKeySet();
	private final ConcurrentHashMap<Class<?>, Object> classLocks = new ConcurrentHashMap<>();

	public Object getLockFor(Class<?> c) {
		return classLocks.computeIfAbsent(c, k -> new Object());
	}

	public boolean instanceExists(Class<?> c) {
		return this.instances.containsKey(c);
	}

	public <X> void put(Class<X> c, X instance) {
		this.instances.put(c, instance);
	}

	@SuppressWarnings("unchecked")
	public <X> X get(Class<X> c) {
		return (X) this.instances.get(c);
	}

	public boolean isUnderConstruction(Class<?> c) {
		return constructorInjection.contains(c);
	}

	public void markUnderConstruction(Class<?> c) {
		constructorInjection.add(c);
	}

	public void unmarkUnderConstruction(Class<?> c) {
		constructorInjection.remove(c);
	}

	public Collection<Object> allInstances() {
		return instances.values();
	}

	/**
	 * Returns all instances in reverse creation order, so that dependents
	 * are destroyed before the dependencies they were built on.
	 */
	public List<Object> allInstancesInReverseCreationOrder() {
		return new ArrayList<>(instances.values()).reversed();
	}
}