package de.oopexpert.oopdi;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;

import de.oopexpert.oopdi.annotation.Injectable;
import de.oopexpert.oopdi.exception.CannotInject;
import net.sf.cglib.proxy.Enhancer;
import net.sf.cglib.proxy.MethodInterceptor;
import net.sf.cglib.proxy.MethodProxy;

public class ProxyManager {

    private Map<Class<?>, Object> proxies = new HashMap<>();
    private Map<Class<?>, Class<?>> proxyClasses = new HashMap<>();

	public <B> B proxyIfNotExists(B instance) {
		return (B) proxyIfNotExists((Class<B>)instance.getClass(), c -> instance);
	}

	public <T> T proxyIfNotExists(Class<T> clazz, Function<Class<T>, T> realObjectCreator) {
		synchronized (proxies) {
			Class<T> nonProxyClass = nonProxyClazz(clazz);
			if (!proxies.containsKey(nonProxyClass)) {
				proxies.put(nonProxyClass, proxy(nonProxyClass, realObjectCreator));
			}
			return (T) proxies.get(nonProxyClass);
		}
		
	}

	private <T> T proxy(Class<T> clazz, Function<Class<T>, T> realObjectCreator) {
		
		System.out.print("Create proxy of " + clazz.getName() + "...");
		
		java.lang.reflect.Constructor<?>[] constructors = clazz.getDeclaredConstructors();

		T proxiedObject;
				
        if (constructors.length == 0) {
            // No constructors defined, use default constructor if available
            proxiedObject = createProxyWithDefaultConstructor(clazz, realObjectCreator);
            proxyClasses.put(proxiedObject.getClass(), clazz);
        } else if (constructors.length == 1) {
            // One constructor is defined
            proxiedObject = createProxyWithSingleConstructor(clazz, constructors[0], realObjectCreator);
            proxyClasses.put(proxiedObject.getClass(), clazz);
        } else {
            // More than one constructor defined, which is not allowed
            throw new CannotInject("Multiple constructors found in class: " + clazz.getName());
        }

		System.out.println("ok");

		return proxiedObject;
	}

	private <T> T createProxyWithDefaultConstructor(Class<T> clazz, Function<Class<T>, T> realObjectCreator) {
        return (T) createEnhancer(clazz, realObjectCreator).create();
    }

    private static final ThreadLocal<List<InstancesState>> requestScope = new ThreadLocal<>();

	private <T> Enhancer createEnhancer(Class<T> clazz, Function<Class<T>, T> realObjectCreator) {
		
		Enhancer enhancer = new Enhancer();
        enhancer.setSuperclass(clazz);
        
        Supplier<T> realObjectSupplier;
        
        if (isImmediateInstantiationRequested(clazz) && Scope.isImmediateInstantiationPossible(clazz)) {
			T realObject = realObjectCreator.apply(clazz);
        	realObjectSupplier = () -> realObject;
        } else {
        	realObjectSupplier = () -> realObjectCreator.apply(clazz);
        }
        
        enhancer.setCallback((MethodInterceptor) (obj, method, args, proxy) -> intercept(realObjectSupplier, method, args, proxy, realObjectSupplier));
        
		return enhancer;
	}
	
	private <T> Object intercept(Object obj, java.lang.reflect.Method method, Object[] args, MethodProxy proxy, Supplier<T> realObjectSupplier) throws Throwable {
		
        List<InstancesState> list = requestScope.get();
        
		if (list == null) {
            list = new ArrayList<InstancesState>();
			requestScope.set(list);
        }
		
		list.add(0, new InstancesState());
		
        try {
        	return method.invoke(realObjectSupplier.get(), args);
        } finally {
    		list.remove(0);
    		if (list.isEmpty()) {
    			requestScope.remove();
    		}
        }
		
	}

    private <T> T createProxyWithSingleConstructor(Class<T> clazz, java.lang.reflect.Constructor<?> constructor, Function<Class<T>, T> realObjectCreator) {
       	return (T) createEnhancer(clazz, realObjectCreator).create(constructor.getParameterTypes(), argsForConstructor(constructor));
    }
    
    private Object[] argsForConstructor(java.lang.reflect.Constructor<?> constructor) {
        int paramCount = constructor.getParameterCount();
        Object[] args = new Object[paramCount];
        for (int i = 0; i < paramCount; i++) {
            args[i] = null;
        }
        return args;
    }
    

	private <A> Class<A> nonProxyClazz(Class<A> clazz) {
		Class<A> nonProxyClass = (Class<A>) proxyClasses.get(clazz);
		if (nonProxyClass == null) {
			nonProxyClass = clazz;
		}
		return nonProxyClass;
	}
	
	public static boolean isImmediateInstantiationRequested(Class<?> c) {
		return c.getAnnotation(Injectable.class).immediate();
	}

}
