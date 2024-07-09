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
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import de.oopexpert.oopdi.annotation.InjectInstance;
import de.oopexpert.oopdi.annotation.InjectSet;
import de.oopexpert.oopdi.annotation.Injectable;
import de.oopexpert.oopdi.annotation.PostConstruct;
import de.oopexpert.oopdi.exception.CannotInject;
import de.oopexpert.oopdi.exception.MultipleClassesLeftAfterFiltering;
import de.oopexpert.oopdi.exception.MultipleConstructors;
import de.oopexpert.oopdi.exception.NoClassesLeftAfterFiltering;

public class Context<T> {

	private ScopedInstances scopedInstances;

	private OOPDI<T> oopdi;
	
	private ClassesResolver classesResolver;
	private ProxyManager proxyManager;

	public Context(OOPDI<T> oopdi, Class<T> rootClazz, ScopedInstances scopedInstances, ProxyManager proxyManager, ClassesResolver classesResolver) {
		this.oopdi = oopdi;
		this.scopedInstances = scopedInstances;
		this.classesResolver = classesResolver;
		this.proxyManager = proxyManager;
		try {
			proxyManager.proxyIfNotExists(this.getOrCreate(rootClazz));
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
		field.set(instance, getOrCreateInstances((Class<?>) field.getAnnotation(InjectSet.class).hint()));
	}

	private Set<Object> getOrCreateInstances(Class<?> hint) throws ClassNotFoundException, IOException, URISyntaxException, InstantiationException, IllegalAccessException, InvocationTargetException, NoSuchMethodException {
		
		Set<Object> components = new HashSet<>();
		
		for (Class<?> clazz : classesResolver.getSet(hint)) {
			Object object = getOrCreateSetInstance(clazz);
			components.add(proxyManager.proxyIfNotExists(object));
		}
		
		return components;
	}

	void inject(Object instance, Field field) throws IllegalArgumentException, IllegalAccessException, ClassNotFoundException, InstantiationException, InvocationTargetException, NoSuchMethodException, IOException, URISyntaxException {
		try {
			field.set(instance, proxyManager.proxyIfNotExists(getOrCreate(field.getType())));
			field.set(proxyManager.proxyIfNotExists(instance), proxyManager.proxyIfNotExists(getOrCreate(field.getType())));
		} catch (NoClassesLeftAfterFiltering | MultipleClassesLeftAfterFiltering e) {
			throw new CannotInject("Cannot inject object of type '" + field.getType().getName() + "' into field '" + field.getName() + "' of type '" + field.getDeclaringClass().getName() + "'", e);
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
		Class<?> c = classesResolver.determineRelevantClass(x);
		X instance = createInstance(c);
		processFields(instance);
		executePostConstructMethod(instance);
		return instance;
	}

	private <X> X getOrCreateInjectable(Class<X> x) throws InstantiationException, IllegalAccessException, InvocationTargetException, NoSuchMethodException, IllegalArgumentException, ClassNotFoundException, IOException, URISyntaxException {
		Class<X> c = (Class<X>) classesResolver.determineRelevantClass(x);
		InstancesState scopedMap = scopedInstances.getScopedInstancesState(scopeOf(c));
		X instance;
		if (!scopedMap.instanceExists(c)) {
			instance = createInstance(c);
			scopedMap.put(c,  instance);
			processFields(instance);
			executePostConstructMethod(instance);
		} else {
			instance = (X) scopedMap.get(c);
		}
		return instance;
	}

	private <X> X createInstance(Class<?> c) throws InstantiationException, IllegalAccessException, InvocationTargetException, ClassNotFoundException, IOException, URISyntaxException, NoSuchMethodException {
		Set<Class<?>> constructorInjection = scopedInstances.getScopedInstancesState(scopeOf(c)).constructorInjection;
		synchronized (constructorInjection) {
			X instance;
			if (constructorInjection.contains(c)) {
				throw new UnderConstruction(c.getName() + " is still under construction.");
			}
			constructorInjection.add(c);
			try {
				instance = instanciateWith((Constructor<?>) getConstructor(c));
			} catch (UnderConstruction cd) {
				throw new CannotInject("Cycle in dependencies detected while performing constructor injection on " + c.getName(), cd);
			} finally {
				constructorInjection.remove(c);
			}
			
			return instance;
		}
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
		
		return declaredConstructors[0];
	}

	private <X> Scope scopeOf(Class<X> c) {
		return c.getAnnotation(Injectable.class).scope();
	}

	public <A> A getOrCreateInstance(Class<A> clazz) {
		try {
			return (A) proxyManager.proxyIfNotExists(getOrCreate(proxyManager.nonProxyClazz(clazz)));
		} catch (ClassNotFoundException | InstantiationException | IllegalAccessException | InvocationTargetException
				| NoSuchMethodException | IllegalArgumentException | IOException | URISyntaxException e) {
			throw new RuntimeException(e);
		}
	}

}
