package de.oopexpert.oopdi;

import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

public class OOPDI<T> {

	private ScopedInstances scopedInstances;
	
	private Class<T> rootClazz;
	private ContextExecution contextExecution = new ContextExecution(this);

	private ProxyManager proxyManager = new ProxyManager();
	private ClassesResolver classesResolver;

	private Context<T> context;
	
    public OOPDI(Class<T> rootClazz, String... profiles) {
    	this.scopedInstances = new ScopedInstances();
    	this.classesResolver = new ClassesResolver(profiles);
    	this.rootClazz = rootClazz;
	}

    Context<T> getContext() {
    	if (this.context == null) {
        	this.context = new Context<T>(this, rootClazz, scopedInstances, proxyManager, classesResolver);
    	}
    	return this.context;
    }

	public <T1> void execRunnable(Class<T1> clazz, Function<T1, Runnable> f) {
		contextExecution.execRunnable(clazz, f);
	}
	
	public <T1, Y> Y execSupplier(Class<T1> clazz, Function<T1, Supplier<Y>> f) {
		return contextExecution.execSupplier(clazz, f);
	}

	public <T1, X, Y> Y execFunction(Class<T1> clazz, Function<T1, Function<X, Y>> f, X x) {
		return contextExecution.execFunction(clazz, f, x);
	}

	public <T1, X> void execConsumer(Class<T1> clazz, Function<T1, Consumer<X>> f, X x) {
		contextExecution.execConsumer(clazz, f, x);
	}

	public <T> T getInstance(Class<T> clazz) {
		System.out.print("Create entry proxy for class " + clazz.getName() + "...");
//        Enhancer enhancer = new Enhancer();
//        enhancer.setSuperclass(clazz);
//        enhancer.setCallback(new RequestScopeInterceptor<>(createContext(), clazz));
//        T create = (T) enhancer.create();
		T create = getContext().getOrCreateInstance(clazz);
		System.out.println("ok");
		return create;
    }
	
}
