package de.oopexpert.oopdi;

import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.SequencedSet;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import de.oopexpert.oopdi.annotation.Injectable;

public class InjectableFilter implements Predicate<Class<?>> {

	private final Set<String> activeProfiles;

	public InjectableFilter(String... profiles) {
		this(Set.of(profiles));
	}

	public InjectableFilter(Set<String> activeProfiles) {
		this.activeProfiles = Set.copyOf(Objects.requireNonNull(activeProfiles));
	}

	@Override
	public boolean test(Class<?> clazz) {
		return clazz != null
				&& !Modifier.isAbstract(clazz.getModifiers())
				&& OOPDIReflection.isInjectable(clazz)
				&& matchesActiveProfiles(clazz);
	}

	/**
	 * Filtert eine Menge von Klassen und liefert ein geordnetes SequencedSet (Java 21) zurück.
	 */
	public <T> SequencedSet<Class<T>> filter(Set<Class<T>> classes) {
		return classes.stream()
				.filter(this)
				.collect(Collectors.toCollection(LinkedHashSet::new));
	}
	
	private boolean matchesActiveProfiles(Class<?> clazz) {
		var injectable = clazz.getAnnotation(Injectable.class);
		if (injectable == null) {
			return false;
		}
		var profiles = injectable.profiles();
		return profiles.length == 0 || Arrays.stream(profiles).anyMatch(activeProfiles::contains);
	}
}