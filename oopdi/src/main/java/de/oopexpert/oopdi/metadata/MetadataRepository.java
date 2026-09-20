package de.oopexpert.oopdi.metadata;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import de.oopexpert.oopdi.annotation.PostConstruct;
import de.oopexpert.oopdi.annotation.PreDestroy;

public class MetadataRepository {

	public static final String CACHE_SYSTEM_PROPERTY = "oopdi.cache.metadata";

	private final boolean cacheEnabled;
	private final ConcurrentHashMap<Class<?>, ClassMetadata> cache = new ConcurrentHashMap<>();

	public MetadataRepository() {
		this(Boolean.getBoolean(CACHE_SYSTEM_PROPERTY));
	}

	public MetadataRepository(boolean cacheEnabled) {
		this.cacheEnabled = cacheEnabled;
	}

	public boolean isCacheEnabled() {
		return cacheEnabled;
	}

	public ClassMetadata getMetadata(Class<?> clazz) {
		if (!cacheEnabled) {
			return inspect(clazz);
		}
		return cache.computeIfAbsent(clazz, this::inspect);
	}

	private ClassMetadata inspect(Class<?> clazz) {
		Constructor<?> primaryConstructor = clazz.getDeclaredConstructors().length > 0 
				? clazz.getDeclaredConstructors()[0] 
				: null;

		List<Field> fields = collectFields(clazz);
		Set<Method> postConstruct = collectMethodsAnnotatedWith(clazz, PostConstruct.class);
		Set<Method> preDestroy = collectMethodsAnnotatedWith(clazz, PreDestroy.class);

		return new ClassMetadata(clazz, primaryConstructor, fields, postConstruct, preDestroy);
	}

	private List<Field> collectFields(Class<?> clazz) {
		List<Field> fields = new ArrayList<>();
		Class<?> current = clazz;
		while (current != null && current != Object.class) {
			fields.addAll(Arrays.asList(current.getDeclaredFields()));
			current = current.getSuperclass();
		}
		return fields;
	}

	private Set<Method> collectMethodsAnnotatedWith(Class<?> clazz, Class<? extends java.lang.annotation.Annotation> annotation) {
		Set<Method> methods = new HashSet<>();
		Class<?> current = clazz;
		while (current != null && current != Object.class) {
			Arrays.stream(current.getDeclaredMethods())
					.filter(m -> m.isAnnotationPresent(annotation))
					.forEach(methods::add);
			current = current.getSuperclass();
		}
		return methods;
	}
}