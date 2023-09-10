package de.oopexpert.oopdi;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.stream.Collectors;

import de.oopexpert.oopdi.exception.CannotInject;
import de.oopexpert.oopdi.exception.MultipleClassesLeftAfterFiltering;
import de.oopexpert.oopdi.exception.MultipleConstructors;
import de.oopexpert.oopdi.exception.NoClassesLeftAfterFiltering;

public class Context<T> {
	
    private Map<Class<?>, Set<Class<?>>> componentSets  = new HashMap<>();
    private Map<Class<?>, Object>      globalInstances;
    private Map<Class<?>, Object>      threadInstances;
    private Map<Class<?>, Object>      localInstances = new HashMap<>();

	private String[] profiles;
	private OOPDI<T> oopdi;
    
	public Context(OOPDI<T> oopdi, Class<T> rootClazz, Map<Class<?>, Object> globalInstances, Map<Class<?>, Object> threadInstances, String[] profiles) {
		this.oopdi = oopdi;
		this.globalInstances = globalInstances;
		this.threadInstances = threadInstances;
		this.profiles = profiles;
		try {
			this.getOrCreate(rootClazz);
		} catch (ClassNotFoundException | InstantiationException | IllegalAccessException
				| InvocationTargetException | NoSuchMethodException | IllegalArgumentException | IOException
				| URISyntaxException e) {
			throw new RuntimeException(e);
		}
	}

	private Object processFields(Object instance) throws IllegalArgumentException, IllegalAccessException, ClassNotFoundException, IOException, URISyntaxException, InstantiationException, InvocationTargetException, NoSuchMethodException {
        for (Field field : instance.getClass().getDeclaredFields()) {
		    field.setAccessible(true);
        	if (field.get(instance) == null) {
	            if (field.isAnnotationPresent(InjectInstance.class)) {
	        		inject(instance, field);
	            } else if (field.isAnnotationPresent(InjectSet.class)) {
	            	injectSet(instance, field);
	            }
        	}
        }
        return instance;
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
		
		Set<Class<?>> set = componentSets.get(hint);
		
		for (Class<?> class1 : set) {
			components.add(getOrCreate(class1));
		}
		return components;
	}

	private void inject(Object instance, Field field) throws IllegalArgumentException, IllegalAccessException, ClassNotFoundException, InstantiationException, InvocationTargetException, NoSuchMethodException, IOException, URISyntaxException {
		try {
			field.set(instance, getOrCreate(field.getType()));
		} catch (NoClassesLeftAfterFiltering | MultipleClassesLeftAfterFiltering e) {
			throw new CannotInject("Cannot inject object of type '" + field.getType().getName() + "' into field '" + field.getName() + "' of type '" + field.getDeclaringClass().getName() + "'", e);
		}
	}
	
	private Set<Class<?>> getDerivedClasses(Class<?> parentClass, String packageName) throws ClassNotFoundException, IOException, URISyntaxException {
	    Set<Class<?>> classes = new HashSet<>();
	    String path = getPath(packageName);
	    String[] classpathEntries = System.getProperty("java.class.path").split(File.pathSeparator);
	    for (String classpathEntry : classpathEntries) {
	        if (classpathEntry.endsWith(".jar")) {
	            try (JarFile jarFile = new JarFile(classpathEntry)) {
	                Enumeration<JarEntry> entries = jarFile.entries();
	                while (entries.hasMoreElements()) {
	                    JarEntry entry = entries.nextElement();
	                    String name = entry.getName();
	                    if (name.endsWith(".class") && name.startsWith(path)) {
	                        String className = name.substring(0, name.length() - ".class".length()).replace("/", ".");
	                        Class<?> clazz = Class.forName(className);
	                        if (parentClass.isAssignableFrom(clazz)) {
	                            classes.add(clazz);
	                        }
	                    }
	                }
	            }
	        } else {
	            File directory = new File(classpathEntry, packageName.replace(".", "/"));
	            if (directory.exists()) {
	                classes.addAll(getDerivedClassesFromDirectory(parentClass, packageName, directory));
	            }
	        }
	    }
	    return classes;
	}

	private String getPath(String packageName) {
		return packageName.replace('.', '/');
	}

	private Set<Class<?>> getDerivedClassesFromDirectory(Class<?> parentClass, String packageName, File directory) throws ClassNotFoundException, IOException, URISyntaxException {
        Set<Class<?>> classes = new HashSet<>();
		for (File file : directory.listFiles()) {
			classes.addAll(getDerivedClassesFromFile(parentClass, packageName, file));
		}
		return classes;
	}

	private Set<Class<?>> getDerivedClassesFromFile(Class<?> parentClass, String packageName, File file) throws ClassNotFoundException, IOException, URISyntaxException {
        Set<Class<?>> classes = new HashSet<>();
		if (file.isDirectory()) {
		    classes.addAll(getDerivedClasses(parentClass, packageName + '.' + file.getName()));
		} else if (file.getName().endsWith(".class")) {
		    String className = packageName + '.' + file.getName().substring(0, file.getName().length() - 6);
		    Class<?> clazz = Class.forName(className);
		    if (parentClass.isAssignableFrom(clazz) && !parentClass.equals(clazz)) {
		        classes.add(clazz);
		    }
		}
		return classes;
	}

	private void registerComponents(Class<?> clazz) throws ClassNotFoundException, IOException, URISyntaxException, InstantiationException, IllegalAccessException, InvocationTargetException, NoSuchMethodException, IllegalArgumentException {
		if (!this.componentSets.containsKey(clazz)) {
			Set<Class<?>> componentClasses = new HashSet<>();
			this.componentSets.put(clazz, componentClasses);
			Set<Class<?>> classes = nonAbstractClasses(withProfiles(getDerivedClasses(clazz, clazz.getPackageName())));
			for (Class<?> c : classes) {
				componentClasses.add(c);
			}
		}
	}


	private Object getOrCreate(Class<?> c) throws ClassNotFoundException, IOException, URISyntaxException, InstantiationException, IllegalAccessException, InvocationTargetException, NoSuchMethodException, IllegalArgumentException {
	    if (c.isAnnotationPresent(Injectable.class) || Modifier.isAbstract(c.getModifiers())) {
	    	if (c.isAnnotationPresent(Injectable.class) && Modifier.isAbstract(c.getModifiers())) {
	    		throw new CannotInject("Class '" + c.getName() + "' is annotated with '@Injectable' but it is abstract and therefore cannot be injected.");
	    	}
			return getOrCreateInjectable(c);
	    } else {
        	throw new RuntimeException("Cannot inject Class " + c.getName() + " is not annotated as 'Injectable' or it isn't abstract!");
	    }
	}
	
	private <X> X getOrCreateInjectable(Class<X> x) throws InstantiationException, IllegalAccessException, InvocationTargetException, NoSuchMethodException, IllegalArgumentException, ClassNotFoundException, IOException, URISyntaxException {
		Class<?> c = determineRelevantClass(x);
		Map<Class<?>, Object> scopedMap = getScopedInstancesMapBy(scopeOf(c));
		X instance;
		if (scopedMap.get(c) == null) {
			instance = instanciateWith((Constructor<?>) getConstructor(c));
			scopedMap.put(c, instance);
			processFields(instance);
			executePostConstructMethod(instance);
		} else {
			instance = (X) scopedMap.get(c);
		}
		return instance;
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
		X instance;
		Class<?>[] parameterTypes = constructor.getParameterTypes();
		instance = (X) constructor.newInstance(getOrCreateParametersBy(parameterTypes));
		return instance;
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
		Set<Class<?>> derivedClasses = getDerivedClasses(c, c.getPackageName());
		
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
		for (String profile : this.profiles) {
			for (String derivedClassProfile : derivedClassProfiles) {
				if (profile.equals(derivedClassProfile)) {
					filteredClasses.add(derivedClass);
					break;
				}
			}
		}
		return filteredClasses;
	}

	
	private Map<Class<?>, Object> getScopedInstancesMapBy(Scope scope) {
		return scope.select(globalInstances, threadInstances, localInstances);
	}

	private <X> Scope scopeOf(Class<X> c) {
		return c.getAnnotation(Injectable.class).scope();
	}

	public <X> X getObject(Class<X> clazz) {
		try {
			Class<?> c = determineRelevantClass(clazz);
			Map<Class<?>, Object> scopedMap = getScopedInstancesMapBy(scopeOf(c));
			return (X) scopedMap.get(c);
		} catch (ClassNotFoundException | IOException | URISyntaxException e) {
			throw new RuntimeException(e);
		}
	}
	
}
