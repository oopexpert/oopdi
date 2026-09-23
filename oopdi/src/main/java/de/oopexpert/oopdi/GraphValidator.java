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
import de.oopexpert.oopdi.exception.MultiplePostConstructMethods;
import de.oopexpert.oopdi.exception.MultiplePreDestroyMethods;
import de.oopexpert.oopdi.metadata.ClassMetadata;
import de.oopexpert.oopdi.metadata.MetadataRepository;
import de.oopexpert.oopdi.parser.TypeParserRegistry;
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
	private final LifecycleProcessor lifecycleProcessor;

	public GraphValidator(InstanceFactory instanceFactory, ClassesResolver classesResolver,
			MetadataRepository metadataRepository, DependencyResolverPipeline resolverPipeline,
			LifecycleProcessor lifecycleProcessor) {
		this.instanceFactory = Objects.requireNonNull(instanceFactory, "instanceFactory must not be null");
		this.classesResolver = Objects.requireNonNull(classesResolver, "classesResolver must not be null");
		this.metadataRepository = Objects.requireNonNull(metadataRepository, "metadataRepository must not be null");
		this.resolverPipeline = Objects.requireNonNull(resolverPipeline, "resolverPipeline must not be null");
		this.lifecycleProcessor = Objects.requireNonNull(lifecycleProcessor, "lifecycleProcessor must not be null");
	}

	public void validate(Class<?> rootClazz) {
		Objects.requireNonNull(rootClazz, "rootClazz must not be null");
		Traversal traversal = new Traversal(instanceFactory, classesResolver, metadataRepository, lifecycleProcessor);
		visit(rootClazz, traversal);
		if (!traversal.problems.isEmpty()) {
			CannotInject aggregated = new CannotInject("Startup validation failed with %d problem(s):\n- %s".formatted(
					traversal.problems.size(), String.join("\n- ", traversal.problems)));
			traversal.causes.forEach(aggregated::addSuppressed);
			throw aggregated;
		}
	}

	private void visit(Class<?> requested, Traversal traversal) {
		visitEdge(requested, true, traversal);
	}

	private void visitEdge(Class<?> requested, boolean viaConstructor, Traversal traversal) {
		Optional<Class<?>> relevant = traversal.resolve(requested);
		if (relevant.isPresent() && traversal.enter(relevant.get(), viaConstructor)) {
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
				traverseDependency(parameterType, true, traversal);
			}
		}
	}

	private void inspectFields(ClassMetadata metadata, Traversal traversal) {
		for (InjectionPoint point : metadata.getFieldInjectionPoints()) {
			if (resolverPipeline.supports(point)) {
				for (Class<?> dependency : traversal.inspectField(metadata.getTargetClass(), point)) {
					visitEdge(dependency, false, traversal);
				}
			}
		}
	}

	private void inspectPostConstruct(ClassMetadata metadata, Traversal traversal) {
		for (Method postConstruct : metadata.getPostConstructMethods()) {
			for (Class<?> parameterType : postConstruct.getParameterTypes()) {
				traverseDependency(parameterType, false, traversal);
			}
		}
	}

	private void traverseDependency(Class<?> dependency, boolean viaConstructor, Traversal traversal) {
		// No special-casing for primitives, Strings or arrays: the runtime resolves every
		// constructor parameter through getOrCreate (which rejects anything non-eligible), so
		// the validator must report them the same way instead of silently skipping them.
		// Only the container itself is directly injectable without being a bean.
		if (!OOPDI.class.isAssignableFrom(dependency)) {
			visitEdge(dependency, viaConstructor, traversal);
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
		private final LifecycleProcessor lifecycleProcessor;
		// Same defaults the runtime resolvers use (Context wires no custom registry anywhere);
		// trial parsing is side-effect free for these built-in parsers.
		private final TypeParserRegistry typeParserRegistry = new TypeParserRegistry();

		private final Deque<Step> path = new ArrayDeque<>();
		private final Set<Class<?>> done = new HashSet<>();
		private final List<String> problems = new ArrayList<>();
		private final List<Throwable> causes = new ArrayList<>();

		/**
		 * One chain link: the bean type plus how the chain reached it. Only constructor edges
		 * can deadlock the runtime (the {@code UnderConstruction} mark lives exclusively in the
		 * construction phase), so only loops consisting solely of constructor edges are
		 * reported as cycles — field, set and lifecycle edges resolve against already-cached
		 * instances at runtime and must stay silent here as well.
		 */
		private record Step(Class<?> type, boolean viaConstructor) {
		}

		Traversal(InstanceFactory instanceFactory, ClassesResolver classesResolver,
				MetadataRepository metadataRepository, LifecycleProcessor lifecycleProcessor) {
			this.instanceFactory = instanceFactory;
			this.classesResolver = classesResolver;
			this.metadataRepository = metadataRepository;
			this.lifecycleProcessor = lifecycleProcessor;
		}

		/**
		 * Runs eligibility, immediate-configuration and relevant-class resolution for a
		 * requested type in runtime order, recording any failure. An immediate-configuration
		 * failure is recorded but traversal continues (the class itself is fine, only the flag
		 * is wrong); the other failures stop this branch. Empty means the type contributes
		 * nothing further to the traversal.
		 */
		Optional<Class<?>> resolve(Class<?> requested) {
			Class<?> relevant = null;
			try {
				// Mirrors Context.getOrCreate first: eligibility of the requested type.
				instanceFactory.validateEligible(requested);
				try {
					// Mirrors Context.getOrCreate second: immediate-configuration check.
					instanceFactory.checkImmediateInstantiationConfiguration(requested);
				} catch (CannotInject e) {
					problem(requested, e);
				}
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
		 * to inspect: either already fully validated, or revisiting a class already on the
		 * chain. A revisit is only reported as a cycle when every edge of the loop is a
		 * constructor edge; other loops resolve at runtime and stay silent (while still
		 * terminating this branch).
		 */
		boolean enter(Class<?> relevant, boolean viaConstructor) {
			boolean fresh = !done.contains(relevant);
			if (fresh && onPath(relevant)) {
				if (isConstructorLoop(relevant, viaConstructor)) {
					problem(relevant.getName(), "dependency cycle detected: %s.".formatted(describeCycle(relevant)));
				}
				fresh = false;
			}
			if (fresh) {
				path.push(new Step(relevant, viaConstructor));
			}
			return fresh;
		}

		private boolean onPath(Class<?> relevant) {
			for (Step step : path) {
				if (step.type().equals(relevant)) {
					return true;
				}
			}
			return false;
		}

		/**
		 * True when every edge of the loop back to {@code relevant} — the stored edges after
		 * its first occurrence plus the incoming one — is a constructor edge. Only such loops
		 * deadlock the runtime; all others resolve against cached instances.
		 */
		private boolean isConstructorLoop(Class<?> relevant, boolean viaConstructor) {
			if (!viaConstructor) {
				return false;
			}
			boolean recording = false;
			var steps = path.descendingIterator();
			while (steps.hasNext()) {
				Step step = steps.next();
				if (!recording) {
					if (step.type().equals(relevant)) {
						recording = true;
					}
					continue;
				}
				if (!step.viaConstructor()) {
					return false;
				}
			}
			return true;
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
			try {
				lifecycleProcessor.findPostConstructMethod(metadata);
			} catch (MultiplePostConstructMethods e) {
				problem(target, e);
			}
			try {
				lifecycleProcessor.findPreDestroyMethod(metadata);
			} catch (MultiplePreDestroyMethods e) {
				problem(target, e);
			}
			for (Method preDestroy : metadata.getPreDestroyMethods()) {
				if (preDestroy.getParameterCount() > 0) {
					problem(target.getName(), "@PreDestroy method '%s' takes parameters; none are allowed.".formatted(preDestroy.getName()));
				}
			}
		}

		/**
		 * Checks a single supported field injection point inline and returns the bean types
		 * to traverse further, if any. Variable presence is verified with the same helper
		 * the runtime uses (plus a trial parse, since the runtime would fail on unparsable
		 * values as well); set hints must resolve without error, and every element class is
		 * traversed like a field dependency. Problems are attributed to the owning bean,
		 * matching runtime resolution messages.
		 */
		List<Class<?>> inspectField(Class<?> owner, InjectionPoint point) {
			List<Class<?>> dependencies = new ArrayList<>();
			if (point.hasAnnotation(InjectVariable.class)) {
				try {
					String value = VariableDependencyResolver.requireVariableValue(point);
					if (value != null) {
				try {
					typeParserRegistry.parse(value, point.getType());
				} catch (IllegalArgumentException e) {
					problem(owner.getName(), "Cannot inject variable: invalid format for key '%s' in source %s for field in '%s' (value '%s').".formatted(
							point.findAnnotation(InjectVariable.class).orElseThrow().key(),
							point.findAnnotation(InjectVariable.class).orElseThrow().source().name(),
							owner.getName(), value), e);
				}
					}
				} catch (CannotInject e) {
					problem(owner, e);
				}
			} else if (point.hasAnnotation(InjectSet.class)) {
				Class<?> hint = point.findAnnotation(InjectSet.class).orElseThrow().hint();
				try {
					dependencies.addAll(classesResolver.getSet(hint));
				} catch (RuntimeException e) {
					problem(owner, e);
				}
			} else {
				dependencies.add(point.getType());
			}
			return dependencies;
		}

		private String describeCycle(Class<?> relevant) {
			List<String> cycle = new ArrayList<>();
			boolean recording = false;
			var steps = path.descendingIterator();
			while (steps.hasNext()) {
				Step step = steps.next();
				if (step.type().equals(relevant)) {
					recording = true;
				}
				if (recording) {
					cycle.add(step.type().getName());
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

		private void problem(String ownerName, String detail, Throwable cause) {
			problem(ownerName, detail);
			causes.add(cause);
		}
	}
}
