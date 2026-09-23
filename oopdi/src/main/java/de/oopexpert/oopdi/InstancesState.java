package de.oopexpert.oopdi;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class InstancesState {

	/**
	 * Insertion-ordered instance cache. The per-class locks in {@code InstanceFactory} only
	 * serialize creation of the <em>same</em> bean — threads creating <em>different</em> beans in
	 * the same scope write to this map concurrently, so the map itself must be thread-safe.
	 * Insertion order is preserved (needed for reverse-creation-order destruction).
	 */
	private final Map<Class<?>, Object> instances = Collections.synchronizedMap(new LinkedHashMap<>());
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

	/**
	 * Removes a cached instance, used as compensation when post-creation processing (field
	 * injection, {@code @PostConstruct}) of a freshly constructed bean fails: the bean must not
	 * stay behind half-initialized. Runs under the map lock, like the snapshot methods.
	 */
	public void remove(Class<?> c) {
		synchronized (instances) {
			this.instances.remove(c);
		}
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

	/**
	 * @deprecated Production code only needs destruction order; use
	 *             {@link #allInstancesInReverseCreationOrder()} instead.
	 *             To be removed in 1.0.
	 */
	@Deprecated(forRemoval = true)
	public Collection<Object> allInstances() {
		synchronized (instances) {
			return List.copyOf(instances.values());
		}
	}

	/**
	 * Returns all instances in reverse creation order, so that dependents
	 * are destroyed before the dependencies they were built on.
	 */
	public List<Object> allInstancesInReverseCreationOrder() {
		synchronized (instances) {
			return new ArrayList<>(instances.values()).reversed();
		}
	}
}