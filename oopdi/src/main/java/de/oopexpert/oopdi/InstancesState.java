package de.oopexpert.oopdi;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class InstancesState {

	private final Map<Class<?>, Object> instances = new HashMap<>();
	private final Set<Class<?>> constructorInjection = new HashSet<Class<?>>();
	private final ConcurrentHashMap<Class<?>, Object> classLocks = new ConcurrentHashMap<>();

	public Object getLockFor(Class<?> c) {
		return classLocks.computeIfAbsent(c, k -> new Object());
	}

	public boolean instanceExists(Class<?> c) {
		return this.instances.get(c) != null;
	}

	public <X> void put(Class<X> c, X instance) {
		this.instances.put(c, instance);
	}

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
	
	private int callDepth;
	
	public void incrementCallDepth() {
		callDepth++;
	}

	public void decrementCallDepth() {
		callDepth--;
	}

	public int getCallDepth() {
		return callDepth;
	}
	
}
