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
import java.util.function.Function;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.oopexpert.oopdi.annotation.InjectInstance;
import de.oopexpert.oopdi.annotation.InjectSet;
import de.oopexpert.oopdi.annotation.InjectVariable;
import de.oopexpert.oopdi.annotation.Injectable;
import de.oopexpert.oopdi.annotation.PostConstruct;
import de.oopexpert.oopdi.annotation.PreDestroy;
import de.oopexpert.oopdi.exception.CannotInject;
import de.oopexpert.oopdi.exception.MultipleClassesLeftAfterFiltering;
import de.oopexpert.oopdi.exception.MultipleConstructors;
import de.oopexpert.oopdi.exception.MultiplePostConstructMethods;
import de.oopexpert.oopdi.exception.NoClassesLeftAfterFiltering;
import de.oopexpert.oopdi.exception.UnderConstruction;

public class Context<T> {

	private static final Logger log = LoggerFactory.getLogger(Context.class);

	private ScopedInstances scopedInstances;

	private OOPDI<T> oopdi;
	
	private ClassesResolver classesResolver;
	private ProxyManager proxyManager;

	public Context(OOPDI<T> oopdi, Class<T> rootClazz, ScopedInstances scopedInstances, ProxyManager proxyManager, ClassesResolver classesResolver) {
		this.oopdi = oopdi;
		this.scopedInstances = scopedInstances;
		this.classesResolver = classesResolver;
		this.proxyManager = proxyManager;
		this.proxyManager.proxyIfNotExists(rootClazz, this::getOrCreate);
	}

	private Object processFields(Object instance) throws IllegalArgumentException, IllegalAccessException, ClassNotFoundException, IOException, URISyntaxException, InstantiationException, InvocationTargetException, NoSuchMethodException {
        Class<? extends Object> clazz = instance.getClass();
		processFields(instance, clazz);
        return instance;
    }

	private void processFields(Object instance, Class<? extends Object> clazz) throws IllegalAccessException, ClassNotFoundException, InstantiationException, InvocationTargetException, NoSuchMethodException, IOException, URISyntaxException {
		for (Field field : clazz.getDeclaredFields()) {
		    processField(instance, field);
        }
		if (clazz.getSuperclass() != null) {
			processFields(instance, clazz.getSuperclass());
		}
	}

	private void processField(Object instance, Field field) throws IllegalAccessException, ClassNotFoundException, InstantiationException, InvocationTargetException, NoSuchMethodException, IOException, URISyntaxException {
		field.setAccessible(true);
	    if (field.isAnnotationPresent(InjectInstance.class)) {
			inject(instance, field);
	    } else if (field.isAnnotationPresent(InjectSet.class)) {
	    	injectSet(instance, field);
	    } else if (field.isAnnotationPresent(InjectVariable.class)) {
	    	injectVariable(instance, field);
	    }
	}

    private static final Map<Class<?>, Function<String, Object>> typeParsers = new HashMap<>();

    static {
        // Populate the map with parsers for each supported type
        typeParsers.put(Integer.class, Integer::valueOf);
        typeParsers.put(int.class, Integer::parseInt);
        typeParsers.put(Long.class, Long::valueOf);
        typeParsers.put(long.class, Long::parseLong);
        typeParsers.put(Short.class, Short::valueOf);
        typeParsers.put(short.class, Short::parseShort);
        typeParsers.put(Float.class, Float::valueOf);
        typeParsers.put(float.class, Float::parseFloat);
        typeParsers.put(Double.class, Double::valueOf);
        typeParsers.put(double.class, Double::parseDouble);
        // Add additional types as needed
    }

	private void injectVariable(Object instance, Field field) throws IllegalArgumentException, IllegalAccessException {

		InjectVariable annotation = field.getAnnotation(InjectVariable.class);
		VariableSource source = annotation.source();
		String key = annotation.key();
		String valueByKey = source.getValueByKey(key);

		if (valueByKey == null) {
			if (!annotation.defaultValue().isEmpty()) {
				valueByKey = annotation.defaultValue();
			} else if (annotation.optional()) {
				// inject null for reference types, zero/false for primitives
				if (!field.getType().isPrimitive()) {
					field.set(instance, null);
				}
				// primitives already hold their zero value from construction — nothing to set
				return;
			} else {
				throw new RuntimeException("Cannot inject variable: key '" + key + "' not found in source " + source.name() + " for field '" + field.getName() + "' in " + field.getDeclaringClass().getName());
			}
		}

		Function<String, Object> parser = typeParsers.get(field.getType());
        if (parser != null) {
            field.set(instance, parser.apply(valueByKey));
        } else {
        	field.set(instance, valueByKey);
        }

	}

	private void injectSet(Object instance, Field field) throws IllegalAccessException, ClassNotFoundException, IOException, URISyntaxException, InstantiationException, InvocationTargetException, NoSuchMethodException, IllegalArgumentException {
		field.set(instance, getOrCreateInstances((Class<?>) field.getAnnotation(InjectSet.class).hint()));
	}

	private Set<Object> getOrCreateInstances(Class<?> hint) throws ClassNotFoundException, IOException, URISyntaxException, InstantiationException, IllegalAccessException, InvocationTargetException, NoSuchMethodException {
		
		Set<Object> components = new HashSet<>();
		
		for (Class<?> clazz : classesResolver.getSet(hint)) {
			components.add(proxyManager.proxyIfNotExists(clazz, this::getOrCreate));
		}
		
		return components;
	}

	void inject(Object instance, Field field) throws IllegalArgumentException, IllegalAccessException, ClassNotFoundException, InstantiationException, InvocationTargetException, NoSuchMethodException, IOException, URISyntaxException {
		try {
			setField(instance, field, field.getType(), this::getOrCreate);
		} catch (NoClassesLeftAfterFiltering | MultipleClassesLeftAfterFiltering e) {
			throw new CannotInject("Cannot inject object of type '" + field.getType().getName() + "' into field '" + field.getName() + "' of type '" + field.getDeclaringClass().getName() + "'", e);
		}
	}

	private <A> void setField(Object instance, Field field, Class<A> fieldClazz, Function<Class<A>, A> creator) throws IllegalAccessException {
		field.set(instance, proxyManager.proxyIfNotExists(fieldClazz, creator));
	}

	private <A> A getOrCreate(Class<A> c)  {
		
	    checkInjectableAnnotated(c);
	    checkNonAbstract(c);
	    checkImmediateInstantiationConfiguration(c);

		return getOrCreateInjectable(c);

	}

	private <A> void checkImmediateInstantiationConfiguration(Class<A> c) {
		if (ProxyManager.isImmediateInstantiationRequested(c) && !Scope.isImmediateInstantiationPossible(c)) {
        	throw new RuntimeException("Missconfiguration of Class " + c.getName() + ". It is demanded to be intantiated immediately but this is only possible with scopes of GLOBAL and THREAD.");
	    }
	}

	private <A> void checkNonAbstract(Class<A> c) {
		if (Modifier.isAbstract(c.getModifiers())) {
        	throw new RuntimeException("Cannot instantiate Class " + c.getName() + ". It is abstract!");
	    }
	}

	private <A> void checkInjectableAnnotated(Class<A> c) {
		if (!c.isAnnotationPresent(Injectable.class)) {
        	throw new RuntimeException("Will not instantiate Class " + c.getName() + ". It is not annotated as 'Injectable'!");
	    }
	}

	private <X> X getOrCreateInjectable(Class<X> x) {
		try {
			Class<X> c = (Class<X>) classesResolver.determineRelevantClass(x);
			InstancesState scopedMap = scopedInstances.getScopedInstancesState(Scope.of(c));
			X instance;
			synchronized (scopedMap.getLockFor(c)) {
				if (!scopedMap.instanceExists(c)) {
					instance = createInstance(c);
					scopedMap.put(c, instance);
					log.debug("Created instance of {}", c.getName());
					processFields(instance);
					executePostConstructMethod(instance);
				} else {
					instance = (X) scopedMap.get(c);
				}
			}
			return instance;
		} catch (ClassNotFoundException | InstantiationException | IllegalAccessException | InvocationTargetException
				| NoSuchMethodException | IllegalArgumentException | IOException | URISyntaxException e) {
			throw new RuntimeException(e);
		}

	}

	private <X> X createInstance(Class<?> c) throws InstantiationException, IllegalAccessException, InvocationTargetException, ClassNotFoundException, IOException, URISyntaxException, NoSuchMethodException {
		InstancesState scopedMap = scopedInstances.getScopedInstancesState(Scope.of(c));
		synchronized (scopedMap.getLockFor(c)) {
			X instance;
			if (scopedMap.isUnderConstruction(c)) {
				throw new UnderConstruction(c.getName() + " is still under construction.");
			}
			scopedMap.markUnderConstruction(c);
			try {
				instance = instanciateWith((Constructor<?>) getConstructor(c));
			} catch (UnderConstruction cd) {
				throw new CannotInject("Cycle in dependencies detected while performing constructor injection on " + c.getName(), cd);
			} finally {
				scopedMap.unmarkUnderConstruction(c);
			}
			
			return instance;
		}
	}
	
	private void executePostConstructMethod(Object instance) throws IllegalAccessException, IllegalArgumentException, InvocationTargetException, ClassNotFoundException, InstantiationException, NoSuchMethodException, IOException, URISyntaxException {

		Set<Method> postConstructMethods = collectPostConstructMethods(instance.getClass());

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

	private Set<Method> collectPostConstructMethods(Class<?> clazz) {
		Set<Method> methods = new HashSet<>();
		if (clazz == null) {
			return methods;
		}
		Arrays.stream(clazz.getDeclaredMethods())
			.filter(m -> m.isAnnotationPresent(PostConstruct.class))
			.forEach(methods::add);
		methods.addAll(collectPostConstructMethods(clazz.getSuperclass()));
		return methods;
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

	public <A> A getOrCreateInstance(Class<A> clazz) {
		return (A) proxyManager.proxyIfNotExists(clazz, this::getOrCreate);
	}

	public void shutdown() {
		for (InstancesState state : scopedInstances.allInstanceStates()) {
			for (Object instance : state.allInstances()) {
				invokePreDestroyMethod(instance);
			}
		}
	}

	private void invokePreDestroyMethod(Object instance) {
		Set<Method> preDestroyMethods = collectPreDestroyMethods(instance.getClass());
		if (preDestroyMethods.size() > 1) {
			throw new RuntimeException("Multiple @PreDestroy methods found in class hierarchy of " + instance.getClass().getName() + ". Only one is allowed.");
		}
		if (preDestroyMethods.iterator().hasNext()) {
			Method method = preDestroyMethods.iterator().next();
			method.setAccessible(true);
			try {
				method.invoke(instance);
			} catch (IllegalAccessException | InvocationTargetException e) {
				throw new RuntimeException("Failed to invoke @PreDestroy method '" + method.getName() + "' on " + instance.getClass().getName(), e);
			}
		}
	}

	private Set<Method> collectPreDestroyMethods(Class<?> clazz) {
		Set<Method> methods = new HashSet<>();
		if (clazz == null) {
			return methods;
		}
		Arrays.stream(clazz.getDeclaredMethods())
			.filter(m -> m.isAnnotationPresent(PreDestroy.class))
			.forEach(methods::add);
		methods.addAll(collectPreDestroyMethods(clazz.getSuperclass()));
		return methods;
	}

}
