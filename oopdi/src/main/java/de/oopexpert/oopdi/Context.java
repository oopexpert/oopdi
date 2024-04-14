package de.oopexpert.oopdi;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import de.oopexpert.oopdi.exception.CannotInject;
import de.oopexpert.oopdi.exception.MultipleClassesLeftAfterFiltering;
import de.oopexpert.oopdi.exception.MultipleConstructors;
import de.oopexpert.oopdi.exception.NoClassesLeftAfterFiltering;
import net.sf.cglib.proxy.Enhancer;
import net.sf.cglib.proxy.MethodInterceptor;

public class Context<T> {
	
	private Map<Class<?>, Set<Class<?>>> componentSets  = new HashMap<>();
    
    private InstancesState globalInstances;
    private InstancesState threadInstances;
	private Map<Class<?>, Object> proxiedObjectsByClass;

	private String[] profiles;
	private OOPDI<T> oopdi;
	
	private DerivedClassesResolver derivedClassesResolver = new DerivedClassesResolver();

	private Map<Class<?>, Class<?>> proxyClasses;

	public Context(OOPDI<T> oopdi, Class<T> rootClazz, InstancesState globalInstances, InstancesState threadInstances, Map<Class<?>, Object> proxies, String[] profiles, Map<Class<?>, Class<?>> proxyClasses) {
		this.oopdi = oopdi;
		this.globalInstances = globalInstances;
		this.threadInstances = threadInstances;
		this.proxiedObjectsByClass = proxies;
		this.proxyClasses = proxyClasses;
		this.profiles = profiles;
		try {
			T rootObject = this.getOrCreate(rootClazz);
			proxies.put(rootClazz, proxyIfNotExists(rootObject));
		} catch (ClassNotFoundException | InstantiationException | IllegalAccessException
				| InvocationTargetException | NoSuchMethodException | IllegalArgumentException | IOException
				| URISyntaxException e) {
			throw new RuntimeException(e);
		}
	}

	private Object processFields(Object instance) throws IllegalArgumentException, IllegalAccessException, ClassNotFoundException, IOException, URISyntaxException, InstantiationException, InvocationTargetException, NoSuchMethodException {
        for (Field field : getDeclaredFields(instance)) {
		    processField(instance, field);
        }
        return instance;
    }

	private Field[] getDeclaredFields(Object instance) {
		return instance.getClass().getDeclaredFields();
	}

	private void processField(Object instance, Field field) throws IllegalAccessException, ClassNotFoundException, InstantiationException, InvocationTargetException, NoSuchMethodException, IOException, URISyntaxException {
		field.setAccessible(true);
		if (field.get(instance) == null) {
		    if (field.isAnnotationPresent(InjectInstance.class)) {
				inject(instance, field);
		    } else if (field.isAnnotationPresent(InjectSet.class)) {
		    	injectSet(instance, field);
		    }
		}
	}

	private void injectSet(Object instance, Field field) throws IllegalAccessException, ClassNotFoundException, IOException, URISyntaxException, InstantiationException, InvocationTargetException, NoSuchMethodException, IllegalArgumentException {
		InjectSet annotation = field.getAnnotation(InjectSet.class);
		if (componentSets.get(annotation.hint()) == null) {
			registerComponents(annotation.hint());
		}
		
		field.set(instance, getOrCreateInstances((Class<?>) annotation.hint()));
	}

	private Set<Object> getOrCreateInstances(Class<?> hint) throws ClassNotFoundException, IOException, URISyntaxException, InstantiationException, IllegalAccessException, InvocationTargetException, NoSuchMethodException {
		
		Set<Object> components = new HashSet<>();
		
		for (Class<?> clazz : componentSets.get(hint)) {
			Object object = getOrCreateSetInstance(clazz);
			components.add(proxyIfNotExists(object));
		}
		
		return components;
	}

	void inject(Object instance, Field field) throws IllegalArgumentException, IllegalAccessException, ClassNotFoundException, InstantiationException, InvocationTargetException, NoSuchMethodException, IOException, URISyntaxException {
		try {
			proxyIfNotExists(instance);
			field.set(instance, proxyIfNotExists(getOrCreate(field.getType())));
			field.set(proxyIfNotExists(instance), proxyIfNotExists(getOrCreate(field.getType())));
		} catch (NoClassesLeftAfterFiltering | MultipleClassesLeftAfterFiltering e) {
			throw new CannotInject("Cannot inject object of type '" + field.getType().getName() + "' into field '" + field.getName() + "' of type '" + field.getDeclaringClass().getName() + "'", e);
		}
	}

	private <B> B proxyIfNotExists(B instance) {
		Class<?> nonProxyClass = proxyClasses.get(instance.getClass());
		if (nonProxyClass == null) {
			nonProxyClass = instance.getClass();
		}
		if (!proxiedObjectsByClass.containsKey(nonProxyClass)) {
			proxiedObjectsByClass.put(nonProxyClass, proxy(nonProxyClass));
		}
		return (B) proxiedObjectsByClass.get(nonProxyClass);
		
	}
	
	private void registerComponents(Class<?> clazz) throws ClassNotFoundException, IOException, URISyntaxException, InstantiationException, IllegalAccessException, InvocationTargetException, NoSuchMethodException, IllegalArgumentException {
		if (!this.componentSets.containsKey(clazz)) {
			Set<Class<?>> componentClasses = new HashSet<>();
			this.componentSets.put(clazz, componentClasses);
			Set<Class<?>> classes = nonAbstractClasses(withProfiles(injectables(derivedClassesResolver.getDerivedClasses(clazz, clazz.getPackageName()))));
			for (Class<?> c : classes) {
				componentClasses.add(c);
			}
		}
	}


	private <A> A getOrCreate(Class<A> c) throws ClassNotFoundException, IOException, URISyntaxException, InstantiationException, IllegalAccessException, InvocationTargetException, NoSuchMethodException, IllegalArgumentException {
	    if (c.isAnnotationPresent(Injectable.class) || Modifier.isAbstract(c.getModifiers())) {
	    	if (c.isAnnotationPresent(Injectable.class) && Modifier.isAbstract(c.getModifiers())) {
	    		throw new CannotInject("Class '" + c.getName() + "' is annotated with '@Injectable' but it is abstract and therefore cannot be injected.");
	    	}
			return getOrCreateInjectable(c);
	    } else {
        	throw new RuntimeException("Cannot inject Class " + c.getName() + " is not annotated as 'Injectable' or it isn't abstract!");
	    }
	}

	private Object getOrCreateSetInstance(Class<?> c) throws ClassNotFoundException, IOException, URISyntaxException, InstantiationException, IllegalAccessException, InvocationTargetException, NoSuchMethodException, IllegalArgumentException {
	    if (c.isAnnotationPresent(Injectable.class) || Modifier.isAbstract(c.getModifiers())) {
	    	if (c.isAnnotationPresent(Injectable.class) && Modifier.isAbstract(c.getModifiers())) {
	    		throw new CannotInject("Class '" + c.getName() + "' is annotated with '@Injectable' but it is abstract and therefore cannot be injected.");
	    	}
			return createInjectableInstance(c);
	    } else {
        	throw new RuntimeException("Cannot inject Class " + c.getName() + " is not annotated as 'Injectable' or it isn't abstract!");
	    }
	}

	private <X> X createInjectableInstance(Class<X> x) throws InstantiationException, IllegalAccessException, InvocationTargetException, NoSuchMethodException, IllegalArgumentException, ClassNotFoundException, IOException, URISyntaxException {
		Class<?> c = determineRelevantClass(x);
		X instance = createInstance(c);
		processFields(instance);
		executePostConstructMethod(instance);
		return instance;
	}

	private <X> X getOrCreateInjectable(Class<X> x) throws InstantiationException, IllegalAccessException, InvocationTargetException, NoSuchMethodException, IllegalArgumentException, ClassNotFoundException, IOException, URISyntaxException {
		Class<?> c = determineRelevantClass(x);
		InstancesState scopedMap = getScopedInstancesState(scopeOf(c));
		X instance;
		if (scopedMap.instances.get(c) == null) {
			instance = createInstance(c);
			scopedMap.instances.put(c, instance);
			processFields(instance);
			executePostConstructMethod(instance);
		} else {
			instance = (X) scopedMap.instances.get(c);
		}
		return instance;
	}

	private <X> X createInstance(Class<?> c) throws InstantiationException, IllegalAccessException, InvocationTargetException, ClassNotFoundException, IOException, URISyntaxException, NoSuchMethodException {
		Set<Class<?>> constructorInjection = getScopedInstancesState(scopeOf(c)).constructorInjection;
		synchronized (constructorInjection) {
			X instance;
			if (constructorInjection.contains(c)) {
				throw new UnderConstruction(c.getName() + " is still under construction.");
			}
			constructorInjection.add(c);
			try {
				instance = (X) instanciateWith((Constructor<?>) getConstructor(c));
			} catch (UnderConstruction cd) {
				throw new CannotInject("Cycle in dependencies detected while performing constructor injection on " + c.getName(), cd);
			} finally {
				constructorInjection.remove(c);
			}
			
			return instance;
		}
	}
	
	private  Object proxy(Class<?> clazz) {
		
		System.out.println("Create proxy of " + clazz.getName() + ".");
		
		java.lang.reflect.Constructor<?>[] constructors = clazz.getDeclaredConstructors();

        if (constructors.length == 0) {
            // No constructors defined, use default constructor if available
            Object proxiedObject = createProxyWithDefaultConstructor(clazz);
            proxyClasses.put(proxiedObject.getClass(), clazz);
			return proxiedObject;
        } else if (constructors.length == 1) {
            // One constructor is defined
            Object proxiedObject = createProxyWithSingleConstructor(clazz, constructors[0]);
            proxyClasses.put(proxiedObject.getClass(), clazz);
			return proxiedObject;
        } else {
            // More than one constructor defined, which is not allowed
            throw new CannotInject("Multiple constructors found in class: " + clazz.getName());
        }
	}

	
	private <T> T createProxyWithDefaultConstructor(Class<T> clazz) {
        return (T) createEnhancer(clazz).create();
    }

	private <T> Enhancer createEnhancer(Class<T> clazz) {
		Enhancer enhancer = new Enhancer();
        enhancer.setSuperclass(clazz);
        enhancer.setCallback((MethodInterceptor) (obj, method, args, proxy) -> {
        	Object object = getOrCreate(clazz);
            return method.invoke(object, args);
        });
		return enhancer;
	}

    private <T> T createProxyWithSingleConstructor(Class<T> clazz, java.lang.reflect.Constructor<?> constructor) {
       	return (T) createEnhancer(clazz).create(constructor.getParameterTypes(), argsForConstructor(constructor));
    }
    
    private Object[] argsForConstructor(java.lang.reflect.Constructor<?> constructor) {
        int paramCount = constructor.getParameterCount();
        Object[] args = new Object[paramCount];
        for (int i = 0; i < paramCount; i++) {
            args[i] = null;
        }
        return args;
    }
    
	private void executePostConstructMethod(Object instance) throws IllegalAccessException, IllegalArgumentException, InvocationTargetException, ClassNotFoundException, InstantiationException, NoSuchMethodException, IOException, URISyntaxException {
		
		Set<Method> postConstructMethods = Arrays.asList(instance.getClass().getDeclaredMethods()).stream().filter(method -> method.isAnnotationPresent(PostConstruct.class)).collect(Collectors.toSet());

		if (postConstructMethods.size() > 1) {
			throw new MultiplePostConstructMethods(instance.getClass());
		}
		
		if (postConstructMethods.iterator().hasNext()) {
			Method method = postConstructMethods.iterator().next();
			Class<?>[] parameterTypes = method.getParameterTypes();
			method.setAccessible(true);
			method.invoke(instance, getOrCreateParametersBy(parameterTypes));
		}
		
	}

	private <X> X instanciateWith(Constructor<?> constructor) throws InstantiationException, IllegalAccessException, InvocationTargetException, ClassNotFoundException, IOException, URISyntaxException, NoSuchMethodException {
		return (X) constructor.newInstance(getOrCreateParametersBy(constructor.getParameterTypes()));
	}

	private Object[] getOrCreateParametersBy(Class<?>[] parameterTypes) throws ClassNotFoundException, IOException, URISyntaxException, InstantiationException, IllegalAccessException, InvocationTargetException, NoSuchMethodException {
		List<Object> parameters = new ArrayList<>();
		
		for (Class<?> parameterType : parameterTypes) {
			if (OOPDI.class.isAssignableFrom(parameterType)) {
				parameters.add(this.oopdi);
			} else {
				parameters.add(getOrCreate(parameterType));
			}
		}
		return parameters.toArray(new Object[parameters.size()]);
	}

	private <X> Constructor<?> getConstructor(Class<X> c) {
		Constructor<?>[] declaredConstructors = c.getDeclaredConstructors();
		
		if (declaredConstructors.length > 1) {
			throw new MultipleConstructors("Multiple constructors for class '" + c.getName() + "'. Cannot decide.");
		}
		
		Constructor<?> constructor = declaredConstructors[0];
		return constructor;
	}

	private Class<?> determineRelevantClass(Class<?> c) throws ClassNotFoundException, IOException, URISyntaxException {
		Set<Class<?>> derivedClasses = derivedClassesResolver.getDerivedClasses(c, c.getPackageName());
		
		Set<Class<?>> allClasses = new HashSet<>();
		allClasses.addAll(derivedClasses);
		allClasses.add(c);
		
		Set<Class<?>> filteredClasses = nonAbstractClasses(withProfiles(injectables(allClasses)));
		
		if (filteredClasses.isEmpty()) {
			throw new NoClassesLeftAfterFiltering("No classes left after profile/non-abstract filtering (" + c.getName() + ").");
		}
		
		if (filteredClasses.size() > 1) {
			throw new MultipleClassesLeftAfterFiltering("Multiple concrete classes left after profile/non-abstract filtering class hierarchy of class '" + c.getName() + "'. Cannot decide object instantiation.");
		}
		
		return filteredClasses.iterator().next();
	}

	private Set<Class<?>> injectables(Set<Class<?>> classes) {
		Set<Class<?>> filteredClasses = new HashSet<>();
		for (Class<?> clazz : classes) {
			if (clazz.isAnnotationPresent(Injectable.class)) {
				filteredClasses.add(clazz);
			}
		}
		return filteredClasses;
	}

	private Set<Class<?>> nonAbstractClasses(Set<Class<?>> classes) {
		Set<Class<?>> filteredClasses = new HashSet<>();
		for (Class<?> clazz : classes) {
			if (!Modifier.isAbstract(clazz.getModifiers())) {
				filteredClasses.add(clazz);
			}
		}
		return filteredClasses;
	}

	private <X> Set<Class<?>> withProfiles(Set<Class<?>> classes) {
		Set<Class<?>> filteredClasses = new HashSet<>();
		for (Class<?> derivedClass : classes) {
			filteredClasses.addAll(filterClassesByInjectableProfile(derivedClass));
		}
		return filteredClasses;
	}

	private Set<Class<?>> filterClassesByInjectableProfile(Class<?> derivedClass) {
		Set<Class<?>> filteredClasses = new HashSet<>();
		Injectable injectable = derivedClass.getAnnotation(Injectable.class);
		String[] derivedClassProfiles = injectable.profiles();
		if (derivedClassProfiles.length == 0) {
			filteredClasses.add(derivedClass);
		} else {
			filteredClasses.addAll(filterClassesByProfileMatch(derivedClass, derivedClassProfiles));
		}
		return filteredClasses;
	}

	private Set<Class<?>> filterClassesByProfileMatch(Class<?> derivedClass, String[] derivedClassProfiles) {
		
	    Set<Class<?>> filteredClasses = new HashSet<>();
	    
	    for (String activeProfile : this.profiles) {
	        if (isProfileMatch(derivedClassProfiles, activeProfile)) {
	            filteredClasses.add(derivedClass);
	        }
	    }

	    return filteredClasses;
	}
	
	private boolean isProfileMatch(String[] derivedClassProfiles, String activeProfile) {
	    return Arrays.stream(derivedClassProfiles).anyMatch(derivedProfile -> derivedProfile.equals(activeProfile));
	}
	
	private InstancesState getScopedInstancesState(Scope scope) {
		return scope.select(globalInstances, threadInstances);
	}

	private <X> Scope scopeOf(Class<X> c) {
		return c.getAnnotation(Injectable.class).scope();
	}

	public <A> A getOrCreateInstance(Class<A> clazz) {
		try {
			Class<A> nonProxyClass = (Class<A>) proxyClasses.get(clazz);
			if (nonProxyClass == null) {
				nonProxyClass = clazz;
			}
			return (A) proxyIfNotExists(getOrCreate(nonProxyClass));
		} catch (ClassNotFoundException | InstantiationException | IllegalAccessException | InvocationTargetException
				| NoSuchMethodException | IllegalArgumentException | IOException | URISyntaxException e) {
			throw new RuntimeException(e);
		}
	}
	
}
