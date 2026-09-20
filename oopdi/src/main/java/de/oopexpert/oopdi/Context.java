package de.oopexpert.oopdi;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Objects;

import de.oopexpert.oopdi.exception.CannotInject;
import de.oopexpert.oopdi.metadata.ClassMetadata;
import de.oopexpert.oopdi.metadata.MetadataRepository;
import de.oopexpert.oopdi.resolver.DependencyResolutionContext;
import de.oopexpert.oopdi.resolver.DependencyResolverPipeline;
import de.oopexpert.oopdi.resolver.FieldInjectionPoint;
import de.oopexpert.oopdi.resolver.InjectionPoint;
import de.oopexpert.oopdi.resolver.impl.InstanceDependencyResolver;
import de.oopexpert.oopdi.resolver.impl.SetDependencyResolver;
import de.oopexpert.oopdi.resolver.impl.VariableDependencyResolver;

public class Context<T> implements DependencyResolutionContext {

	private final ScopedInstances scopedInstances;
	private final ProxyManager proxyManager;
	private final DependencyResolverPipeline resolverPipeline;
	private final InstanceFactory instanceFactory;
	private final LifecycleProcessor lifecycleProcessor;
	private final MetadataRepository metadataRepository;

	private final ThreadLocal<Boolean> directConstructionPhase = ThreadLocal.withInitial(() -> false);

	public Context(OOPDI<T> oopdi, Class<T> rootClazz, ScopedInstances scopedInstances,
			ProxyManager proxyManager, ClassesResolver classesResolver, MetadataRepository metadataRepository) {
		this.scopedInstances = Objects.requireNonNull(scopedInstances);
		this.proxyManager = Objects.requireNonNull(proxyManager);
		this.metadataRepository = Objects.requireNonNull(metadataRepository, "metadataRepository must not be null");

		this.resolverPipeline = new DependencyResolverPipeline(List.of(
				new VariableDependencyResolver(),
				new SetDependencyResolver(classesResolver),
				new InstanceDependencyResolver()
		));

		this.lifecycleProcessor = new LifecycleProcessor(this, oopdi, metadataRepository);
		this.instanceFactory = new InstanceFactory(this, classesResolver, oopdi, metadataRepository);

		this.proxyManager.proxyIfNotExists(rootClazz, instanceFactory::validateEligible, this::getOrCreate);
	}

	@Override
	public <A> A getOrCreate(Class<A> clazz) {
		instanceFactory.validateEligible(clazz);
		instanceFactory.checkImmediateInstantiationConfiguration(clazz);
		return instanceFactory.getOrCreateInjectable(clazz, scopedInstances, instance -> {
			injectFields(instance);
			lifecycleProcessor.executePostConstruct(instance);
		}, directConstructionPhase);
	}


	@Override
	public <A> A getOrCreateProxy(Class<A> clazz) {
		return proxyManager.proxyIfNotExists(clazz, instanceFactory::validateEligible, this::getOrCreate);
	}

	@Override
	public boolean isDirectConstructionPhase() {
		return Boolean.TRUE.equals(directConstructionPhase.get());
	}

	public void injectFields(Object instance) {
		ClassMetadata metadata = metadataRepository.getMetadata(instance.getClass());
		for (InjectionPoint point : metadata.getFieldInjectionPoints()) {
			if (resolverPipeline.supports(point)) {
				Field field = ((FieldInjectionPoint) point).field();
				field.setAccessible(true);
				Object resolvedValue = resolverPipeline.resolve(point, this);
				try {
					field.set(instance, resolvedValue);
				} catch (IllegalAccessException e) {
					throw new CannotInject("Feldinjektion fehlgeschlagen: " + field.getName(), e);
				}
			}
		}
	}

	public void shutdown() {
		for (InstancesState state : scopedInstances.allInstanceStates()) {
			for (Object instance : state.allInstancesInReverseCreationOrder()) {
				lifecycleProcessor.invokePreDestroy(instance);
			}
		}
	}
}