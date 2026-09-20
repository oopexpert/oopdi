package de.oopexpert.oopdi.metadata;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import de.oopexpert.oopdi.annotation.PostConstruct;
import de.oopexpert.oopdi.annotation.PreDestroy;
import de.oopexpert.oopdi.exception.MultipleConstructors;

/**
 * Single source of truth for reflective metadata of managed classes (primary constructor,
 * field injection points, lifecycle methods). Every consumer that needs to know "which
 * constructor to use" (real object instantiation as well as proxy stub generation) goes
 * through {@link #getMetadata(Class)} instead of re-deriving that information itself, so the
 * "exactly one constructor" invariant is validated in exactly one place.
 */
public class MetadataRepository {

	private final boolean cacheEnabled;
	private final ConcurrentHashMap<Class<?>, ClassMetadata> cache = new ConcurrentHashMap<>();

	public MetadataRepository() {
		this(MetadataMode.fromSystemProperty());
	}

	public MetadataRepository(MetadataMode mode) {
		this.cacheEnabled = Objects.requireNonNull(mode, "mode must not be null").isCacheEnabled();
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
		Constructor<?> primaryConstructor = determinePrimaryConstructor(clazz);

		List<Field> fields = collectFields(clazz);
		Set<Method> postConstruct = collectMethodsAnnotatedWith(clazz, PostConstruct.class);
		Set<Method> preDestroy = collectMethodsAnnotatedWith(clazz, PreDestroy.class);

		return new ClassMetadata(clazz, primaryConstructor, fields, postConstruct, preDestroy);
	}

	private Constructor<?> determinePrimaryConstructor(Class<?> clazz) {
		Constructor<?>[] declaredConstructors = clazz.getDeclaredConstructors();
		if (declaredConstructors.length > 1) {
			throw new MultipleConstructors("Multiple constructors for class '" + clazz.getName() + "'. Cannot decide.");
		}
		return declaredConstructors.length == 1 ? declaredConstructors[0] : null;
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