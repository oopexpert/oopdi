package de.oopexpert.oopdi;

import static java.lang.Thread.currentThread;
import static java.util.Collections.synchronizedMap;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

public class OOPDI<T> {
	
    private InstancesState globalInstances = new InstancesState();
    private Map<Class<?>, Object> proxies = new HashMap<>();

    private Map<Thread, InstancesState> threadInstanceMaps = synchronizedMap(new HashMap<>());

	private Class<T> rootClazz;
	private ContextExecution contextExecution;
	
    public OOPDI(Class<T> rootClazz) {
    	this.rootClazz = rootClazz;
    	this.contextExecution = new ContextExecution(this);
	}

    Context<T> createContext(String... profiles) {
    	return new Context<T>(this, rootClazz, globalInstances, getThreadInstancesMap(), proxies, profiles);
    }

	private synchronized InstancesState getThreadInstancesMap() {
		
		if (threadInstanceMaps.get(currentThread()) == null) {
			threadInstanceMaps.put(currentThread(), new InstancesState());
		}
		
		return threadInstanceMaps.get(currentThread());
	}

	public <T1, X, Y> Y execFunction(Class<T1> clazz, Function<T1, Function<X, Y>> f, X x) {
		return contextExecution.execFunction(clazz, f, x);
	}

	public <T1, Y> Y execSupplier(Class<T1> clazz, Function<T1, Supplier<Y>> f) {
		return contextExecution.execSupplier(clazz, f);
	}

	public <T1, X> void execConsumer(Class<T1> clazz, Function<T1, Consumer<X>> f, X x) {
		contextExecution.execConsumer(clazz, f, x);
	}

	public <T1> void execRunnable(Class<T1> clazz, Function<T1, Runnable> f) {
		contextExecution.execRunnable(clazz, f);
	}
	
}
