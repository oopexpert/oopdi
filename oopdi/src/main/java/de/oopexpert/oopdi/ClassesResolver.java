package de.oopexpert.oopdi;

import java.util.Map;
import java.util.Objects;
import java.util.SequencedSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import de.oopexpert.oopdi.exception.MultipleClassesLeftAfterFiltering;
import de.oopexpert.oopdi.exception.NoClassesLeftAfterFiltering;

public class ClassesResolver {

	private final ClasspathScanner scanner;
	private final InjectableFilter filter;

	private final Map<Class<?>, Set<Class<?>>> componentSets = new ConcurrentHashMap<>();
	private final Map<Class<?>, Class<?>> relevantClassCache = new ConcurrentHashMap<>();

	public ClassesResolver(String... profiles) {
		this(new ClasspathScanner(), new InjectableFilter(profiles));
	}

	public ClassesResolver(ClasspathScanner scanner, InjectableFilter filter) {
		this.scanner = Objects.requireNonNull(scanner, "ClasspathScanner must not be null");
		this.filter = Objects.requireNonNull(filter, "InjectableFilter must not be null");
	}

	@SuppressWarnings("unchecked")
	public <T> Class<T> determineRelevantClass(Class<T> c) {
		// Atomic fill: racing threads for the same uncached key must share one scan instead
		// of each scanning the classpath. A mapping-function failure propagates without
		// recording anything, so the next call transparently retries (same as before).
		return (Class<T>) relevantClassCache.computeIfAbsent(c, key -> resolveRelevantClass((Class<T>) key));
	}

	private <T> Class<T> resolveRelevantClass(Class<T> c) {
		var allClasses = scanner.findDerivedClasses(c, c.getPackageName());
		allClasses.add(c);

		SequencedSet<Class<T>> filteredClasses = filter.filter(allClasses);

		if (filteredClasses.isEmpty()) {
			throw new NoClassesLeftAfterFiltering("No classes left after profile/non-abstract filtering ('%s').".formatted(c.getName()));
		}

		if (filteredClasses.size() > 1) {
			throw new MultipleClassesLeftAfterFiltering("Multiple concrete classes left after profile/non-abstract filtering class hierarchy of class '%s'. Cannot decide object instantiation.".formatted(c.getName()));
		}

		return filteredClasses.getFirst();
	}

	public Set<Class<?>> getSet(Class<?> hint) {
		return componentSets.computeIfAbsent(hint, this::findAndFilterComponents);
	}

	private <T> Set<Class<?>> findAndFilterComponents(Class<T> hint) {
		Set<Class<T>> derivedClasses = scanner.findDerivedClasses(hint, hint.getPackageName());
		return filter.filter(derivedClasses)
				.stream()
				.map(c -> (Class<?>) c)
				.collect(Collectors.toUnmodifiableSet());
	}
}