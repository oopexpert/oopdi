package de.oopexpert.oopdi;

import java.lang.reflect.Method;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
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
		Traversal traversal = new Traversal(instanceFactory, classesResolver, metadataRepository);
		visit(rootClazz, traversal);
		if (!traversal.problems.isEmpty()) {
			CannotInject aggregated = new CannotInject("Startup validation failed with %d problem(s):\n- %s".formatted(
					traversal.problems.size(), String.join("\n- ", traversal.problems)));
			traversal.causes.forEach(aggregated::addSuppressed);
			throw aggregated;
		}
	}

	private void visit(Class<?> requested, Traversal traversal) {
		Optional<Class<?>> relevant = traversal.resolve(requested);
		if (relevant.isPresent() && traversal.enter(relevant.get())) {
			try {
				inspect(relevant.get(), traversal);
			} finally {
				traversal.leave(relevant.get());
			}
		}
	}

	private void inspect(Class<?> relevant, Traversal traversal) {
		Optional<ClassMetadata> metadata = traversal.inspectMetadata(relevant);
		if (metadata.isPresent()) {
			ClassMetadata inspected = metadata.get();
			traversal.checkLifecycle(inspected);
			inspectConstructor(inspected, traversal);
			inspectFields(inspected, traversal);
			inspectPostConstruct(inspected, traversal);
		}
	}

	private void inspectConstructor(ClassMetadata metadata, Traversal traversal) {
		var primaryConstructor = metadata.getPrimaryConstructor();
		if (primaryConstructor != null) {
			for (Class<?> parameterType : primaryConstructor.getParameterTypes()) {
				traverseDependency(parameterType, traversal);
			}
		}
	}

	private void inspectFields(ClassMetadata metadata, Traversal traversal) {
		for (InjectionPoint point : metadata.getFieldInjectionPoints()) {
			if (resolverPipeline.supports(point)) {
				traversal.inspectField(metadata.getTargetClass(), point)
						.ifPresent(dependency -> visit(dependency, traversal));
			}
		}
	}

	private void inspectPostConstruct(ClassMetadata metadata, Traversal traversal) {
		for (Method postConstruct : metadata.getPostConstructMethods()) {
			for (Class<?> parameterType : postConstruct.getParameterTypes()) {
				traverseDependency(parameterType, traversal);
			}
		}
	}

	private void traverseDependency(Class<?> dependency, Traversal traversal) {
		// No special-casing for primitives, Strings or arrays: the runtime resolves every
		// constructor parameter through getOrCreate (which rejects anything non-eligible), so
		// the validator must report them the same way instead of silently skipping them.
		// Only the container itself is directly injectable without being a bean.
		if (!OOPDI.class.isAssignableFrom(dependency)) {
			visit(dependency, traversal);
		}
	}

	/**
	 * Bundles the mutable traversal state (chain, finished set, collected problems) that would
	 * otherwise be threaded through every method as parameters.
	 */
	private static final class Traversal {

		private final InstanceFactory instanceFactory;
		private final ClassesResolver classesResolver;
		private final MetadataRepository metadataRepository;

		private final Deque<Class<?>> path = new ArrayDeque<>();
		private final Set<Class<?>> done = new HashSet<>();
		private final List<String> problems = new ArrayList<>();
		private final List<Throwable> causes = new ArrayList<>();

		Traversal(InstanceFactory instanceFactory, ClassesResolver classesResolver,
				MetadataRepository metadataRepository) {
			this.instanceFactory = instanceFactory;
			this.classesResolver = classesResolver;
			this.metadataRepository = metadataRepository;
		}

		/**
		 * Runs eligibility and relevant-class resolution for a requested type, recording any
		 * failure. Empty means the type contributes nothing further to the traversal. A single
		 * try/catch covers both steps in runtime order (eligibility of the requested type
		 * first): whichever step fails, the failure is recorded and resolution stops there,
		 * exactly as the runtime would fail at the same step.
		 */
		Optional<Class<?>> resolve(Class<?> requested) {
			Class<?> relevant = null;
			try {
				// Mirrors Context.getOrCreate first: eligibility of the requested type.
				instanceFactory.validateEligible(requested);
				// Mirrors InstanceFactory.getOrCreateInjectable first step: resolve the relevant
				// concrete class (profile filtering, hierarchy scan). This is what actually gets built.
				relevant = classesResolver.determineRelevantClass(requested);
			} catch (RuntimeException e) {
				problem(requested, e);
				done.add(requested);
			}
			return Optional.ofNullable(relevant);
		}

		/**
		 * Enters a resolved class into the current chain. Returns false when there is nothing
		 * to inspect: either already fully validated, or part of a dependency cycle (which is
		 * reported with its path instead).
		 */
		boolean enter(Class<?> relevant) {
			boolean fresh = !done.contains(relevant);
			if (fresh && path.contains(relevant)) {
				problem(relevant.getName(), "dependency cycle detected: %s.".formatted(describeCycle(relevant)));
				fresh = false;
			}
			if (fresh) {
				path.push(relevant);
			}
			return fresh;
		}

		void leave(Class<?> relevant) {
			path.pop();
			done.add(relevant);
		}

		/**
		 * Inspects reflective metadata, recording structural violations. Empty means the class
		 * contributes nothing further to the traversal.
		 */
		Optional<ClassMetadata> inspectMetadata(Class<?> relevant) {
			ClassMetadata metadata = null;
			try {
				metadata = metadataRepository.getMetadata(relevant);
			} catch (RuntimeException e) {
				problem(relevant, e);
			}
			return Optional.ofNullable(metadata);
		}

		void checkLifecycle(ClassMetadata metadata) {
			Class<?> target = metadata.getTargetClass();
			if (metadata.getPostConstructMethods().size() > 1) {
				problem(target.getName(), "multiple @PostConstruct methods found in class hierarchy; only one is allowed.");
			}
			if (metadata.getPreDestroyMethods().size() > 1) {
				problem(target.getName(), "multiple @PreDestroy methods found in class hierarchy; only one is allowed.");
			}
			for (Method preDestroy : metadata.getPreDestroyMethods()) {
				if (preDestroy.getParameterCount() > 0) {
					problem(target.getName(), "@PreDestroy method '%s' takes parameters; none are allowed.".formatted(preDestroy.getName()));
				}
			}
		}

		/**
		 * Checks a single supported field injection point inline (variable presence, set
		 * resolvability) and returns the bean type to traverse further, if any. Problems are
		 * attributed to the owning bean, matching runtime resolution messages.
		 */
		Optional<Class<?>> inspectField(Class<?> owner, InjectionPoint point) {
			Optional<Class<?>> dependency = Optional.empty();
			if (point.hasAnnotation(InjectVariable.class)) {
				try {
					VariableDependencyResolver.requireVariableValue(point);
				} catch (CannotInject e) {
					problem(owner, e);
				}
			} else if (point.hasAnnotation(InjectSet.class)) {
				Class<?> hint = point.findAnnotation(InjectSet.class).orElseThrow().hint();
				try {
					classesResolver.getSet(hint);
				} catch (RuntimeException e) {
					problem(owner, e);
				}
			} else {
				dependency = Optional.of(point.getType());
			}
			return dependency;
		}

		private String describeCycle(Class<?> relevant) {
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
			return String.join(" -> ", cycle);
		}

		private void problem(Class<?> owner, RuntimeException e) {
			problem(owner.getName(), e.getMessage());
			causes.add(e);
		}

		private void problem(String ownerName, String detail) {
			problems.add("'%s': %s".formatted(ownerName, detail));
		}
	}
}
