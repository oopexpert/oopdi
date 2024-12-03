package de.oopexpert.oopdi;

public class OOPDI<T> {

	private ScopedInstances scopedInstances;
	
	private Class<T> rootClazz;

	private ProxyManager proxyManager = new ProxyManager();
	private ClassesResolver classesResolver;

	private Context<T> context;
	
    public OOPDI(Class<T> rootClazz, String... profiles) {
    	this.scopedInstances = new ScopedInstances();
    	this.classesResolver = new ClassesResolver(profiles);
    	this.rootClazz = rootClazz;
	}

    synchronized Context<T> getContext() {
    	if (this.context == null) {
        	this.context = new Context<T>(this, rootClazz, scopedInstances, proxyManager, classesResolver);
    	}
    	return this.context;
    }

	public <T> T getInstance(Class<T> clazz) {
		return getContext().getOrCreateInstance(clazz);
    }
	
}
