package de.oopexpert.oopdi;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class InstancesState {

	public final Map<Class<?>, Object> instances = new HashMap<>();
	public final Set<Class<?>> constructorInjection = new HashSet<Class<?>>();

	public boolean instanceExists(Class<?> c) {
		return this.instances.get(c) != null;
	}

	public <X> void put(Class<X> c, X instance) {
		this.instances.put(c, instance);
	}

	public <X> X get(Class<X> c) {
		return (X) this.instances.get(c);
	}

}
