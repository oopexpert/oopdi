package de.oopexpert.oopdi;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class InstancesState {

	public final Map<Class<?>, Object> instances = new HashMap<>();
	public final Set<Class<?>> constructorInjection = new HashSet<Class<?>>();


}
