package de.oopexpert.oopdi;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

import de.oopexpert.oopdi.exception.CannotInject;
import net.sf.cglib.proxy.Enhancer;
import net.sf.cglib.proxy.MethodInterceptor;

public class ProxyManager {

    private Map<Class<?>, Object> proxies = new HashMap<>();
    private Map<Class<?>, Class<?>> proxyClasses = new HashMap<>();

	public <B> B proxyIfNotExists(B instance) {
		Class<B> nonProxyClass = (Class<B>) proxyClasses.get(instance.getClass());
		if (nonProxyClass == null) {
			nonProxyClass = (Class<B>) instance.getClass();
		}
		if (!proxies.containsKey(nonProxyClass)) {
			proxies.put(nonProxyClass, proxy(nonProxyClass, () -> instance));
		}
		return (B) proxies.get(nonProxyClass);
		
	}

	private <T> T proxy(Class<T> clazz, Supplier<T> realObjectCreator) {
		
		System.out.println("Create proxy of " + clazz.getName() + ".");
		
		java.lang.reflect.Constructor<?>[] constructors = clazz.getDeclaredConstructors();

        if (constructors.length == 0) {
            // No constructors defined, use default constructor if available
            T proxiedObject = createProxyWithDefaultConstructor(clazz, realObjectCreator);
            proxyClasses.put(proxiedObject.getClass(), clazz);
			return proxiedObject;
        } else if (constructors.length == 1) {
            // One constructor is defined
            T proxiedObject = createProxyWithSingleConstructor(clazz, constructors[0], realObjectCreator);
            proxyClasses.put(proxiedObject.getClass(), clazz);
			return proxiedObject;
        } else {
            // More than one constructor defined, which is not allowed
            throw new CannotInject("Multiple constructors found in class: " + clazz.getName());
        }
	}

	private <T> T createProxyWithDefaultConstructor(Class<T> clazz, Supplier<T> realObjectCreator) {
        return (T) createEnhancer(clazz, realObjectCreator).create();
    }

	private <T> Enhancer createEnhancer(Class<T> clazz, Supplier<T> realObjectCreator) {
		Enhancer enhancer = new Enhancer();
        enhancer.setSuperclass(clazz);
        enhancer.setCallback((MethodInterceptor) (obj, method, args, proxy) -> {
        	return method.invoke(realObjectCreator.get(), args);
        });
		return enhancer;
	}

    private <T> T createProxyWithSingleConstructor(Class<T> clazz, java.lang.reflect.Constructor<?> constructor, Supplier<T> realObjectCreator) {
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
    

	public <A> Class<A> nonProxyClazz(Class<A> clazz) {
		Class<A> nonProxyClass = (Class<A>) proxyClasses.get(clazz);
		if (nonProxyClass == null) {
			nonProxyClass = clazz;
		}
		return nonProxyClass;
	}
	
}
