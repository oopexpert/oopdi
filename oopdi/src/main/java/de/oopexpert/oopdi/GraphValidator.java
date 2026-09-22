package de.oopexpert.oopdi;

import java.lang.reflect.Method;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import de.oopexpert.oopdi.annotation.InjectSet;
import de.oopexpert.oopdi.annotation.InjectVariable;
import de.oopexpert.oopdi.exception.CannotInject;
import de.oopexpert.oopdi.metadata.ClassMetadata;
import de.oopexpert.oopdi.metadata.MetadataRepository;
import de.oopexpert.oopdi.resolver.DependencyResolverPipeline;
import de.oopexpert.oopdi.resolver.InjectionPoint;
import de.oopexpert.oopdi.resolver.impl.VariableDependencyResolver;

/**
 * Dry-runs the resolvable bean graph starting from a root class without creating a single
 * instance: no constructor runs, no field is set, no lifecycle method fires. Every structural
 * problem found (eligibility, constructor rules, unresolvable dependencies, missing variables,
 * empty filtered sets, cycles, lifecycle cardinalities) is collected and reported together as
 * one {@link CannotInject}, so a single startup check surfaces the whole wiring state instead
 * of failing request-by-request at runtime.
 *
 * <p>Entry point for applications is {@link OOPDI#validate()}. All checks mirror what the
 * corresponding runtime path would do (same components, same messages), so validation can
 * neither pass something the runtime rejects nor reject something the runtime accepts.</p>
 */
public final class GraphValidator {

	private final InstanceFactory instanceFactory;
	private final ClassesResolver classesResolver;
	private final MetadataRepository metadataRepository;
	private final DependencyResolverPipeline resolverPipeline;

	public GraphValidator(InstanceFactory instanceFactory, ClassesResolver classesResolver,
			MetadataRepository metadataRepository, DependencyResolverPipeline resolverPipeline) {
		this.instanceFactory = Objects.requireNonNull(instanceFactory, "instanceFactory must not be null");
		this.classesResolver = Objects.requireNonNull(classesResolver, "classesResolver must not be null");
		this.metadataRepository = Objects.requireNonNull(metadataRepository, "metadataRepository must not be null");
		this.resolverPipeline = Objects.requireNonNull(resolverPipeline, "resolverPipeline must not be null");
	}

	public void validate(Class<?> rootClazz) {
		Objects.requireNonNull(rootClazz, "rootClazz must not be null");
		List<String> problems = new ArrayList<>();
		List<Throwable> causes = new ArrayList<>();
		visit(rootClazz, new ArrayDeque<>(), new HashSet<>(), problems, causes);
		if (!problems.isEmpty()) {
			CannotInject aggregated = new CannotInject("Startup validation failed with %d problem(s):\n- %s".formatted(
					problems.size(), String.join("\n- ", problems)));
			causes.forEach(aggregated::addSuppressed);
			throw aggregated;
		}
	}

	private void visit(Class<?> requested, Deque<Class<?>> path, Set<Class<?>> done,
			List<String> problems, List<Throwable> causes) {
		// Mirrors Context.getOrCreate first: eligibility of the requested type.
		try {
			instanceFactory.validateEligible(requested);
		} catch (RuntimeException e) {
			problems.add("'%s': %s".formatted(requested.getName(), e.getMessage()));
			causes.add(e);
			done.add(requested);
			return;
		}
		// Mirrors InstanceFactory.getOrCreateInjectable first step: resolve the relevant
		// concrete class (profile filtering, hierarchy scan). This is what actually gets built.
		Class<?> relevant;
		try {
			relevant = classesResolver.determineRelevantClass(requested);
		} catch (RuntimeException e) {
			problems.add("'%s': %s".formatted(requested.getName(), e.getMessage()));
			causes.add(e);
			done.add(requested);
			return;
		}
		if (done.contains(relevant)) {
			return;
		}
		if (path.contains(relevant)) {
			List<String> cycle = new ArrayList<>();
			boolean recording = false;
			var steps = path.descendingIterator();
			while (steps.hasNext()) {
				Class<?> step = steps.next();
				if (step.equals(relevant)) {
					recording = true;
				}
				if (recording) {
					cycle.add(step.getName());
				}
			}
			cycle.add(relevant.getName());
			problems.add("'%s': dependency cycle detected: %s.".formatted(
					relevant.getName(), String.join(" -> ", cycle)));
			return;
		}
		path.push(relevant);
		try {
			ClassMetadata metadata;
			try {
				metadata = metadataRepository.getMetadata(relevant);
			} catch (RuntimeException e) {
				problems.add("'%s': %s".formatted(relevant.getName(), e.getMessage()));
				causes.add(e);
				return;
			}
			if (metadata.getPostConstructMethods().size() > 1) {
				problems.add("'%s': multiple @PostConstruct methods found in class hierarchy; only one is allowed.".formatted(relevant.getName()));
			}
			if (metadata.getPreDestroyMethods().size() > 1) {
				problems.add("'%s': multiple @PreDestroy methods found in class hierarchy; only one is allowed.".formatted(relevant.getName()));
			}
			for (Method preDestroy : metadata.getPreDestroyMethods()) {
				if (preDestroy.getParameterCount() > 0) {
					problems.add("'%s': @PreDestroy method '%s' takes parameters; none are allowed.".formatted(relevant.getName(), preDestroy.getName()));
				}
			}
			var primaryConstructor = metadata.getPrimaryConstructor();
			if (primaryConstructor != null) {
				for (Class<?> parameterType : primaryConstructor.getParameterTypes()) {
					visitDependency(parameterType, path, done, problems, causes);
				}
			}
			for (InjectionPoint point : metadata.getFieldInjectionPoints()) {
				if (!resolverPipeline.supports(point)) {
					continue;
				}
				if (point.hasAnnotation(InjectVariable.class)) {
					try {
						VariableDependencyResolver.requireVariableValue(point);
					} catch (CannotInject e) {
						problems.add("'%s': %s".formatted(relevant.getName(), e.getMessage()));
						causes.add(e);
					}
				} else if (point.hasAnnotation(InjectSet.class)) {
					Class<?> hint = point.findAnnotation(InjectSet.class).orElseThrow().hint();
					try {
						classesResolver.getSet(hint);
					} catch (RuntimeException e) {
						problems.add("'%s': %s".formatted(relevant.getName(), e.getMessage()));
						causes.add(e);
					}
				} else {
					visitDependency(point.getType(), path, done, problems, causes);
				}
			}
			for (Method postConstruct : metadata.getPostConstructMethods()) {
				for (Class<?> parameterType : postConstruct.getParameterTypes()) {
					visitDependency(parameterType, path, done, problems, causes);
				}
			}
		} finally {
			path.pop();
			done.add(relevant);
		}
	}

	private void visitDependency(Class<?> dependency, Deque<Class<?>> path, Set<Class<?>> done,
			List<String> problems, List<Throwable> causes) {
		// No special-casing for primitives, Strings or arrays: the runtime resolves every
		// constructor parameter through getOrCreate (which rejects anything non-eligible), so
		// the validator must report them the same way instead of silently skipping them.
		// Only the container itself is directly injectable without being a bean.
		if (OOPDI.class.isAssignableFrom(dependency)) {
			return;
		}
		visit(dependency, path, done, problems, causes);
	}
}
