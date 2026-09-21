package de.oopexpert.oopdi.resolver;

/**
 * Framework-internal extension of {@link DependencyResolutionContext}. Proxy issuance
 * ({@link #getOrCreateProxy(Class)}) is a framework-internal capability (used by the
 * {@code @InjectInstance}/{@code @InjectSet} resolvers and by {@code OOPDI} itself); external
 * consumers only ever need {@link DependencyResolutionContext#getOrCreate(Class)} and therefore
 * program against the base interface.
 *
 * <p>The single implementation is {@code de.oopexpert.oopdi.Context}, so the casts at the two
 * internal call sites always succeed.</p>
 */
public interface InternalResolutionContext extends DependencyResolutionContext {

	<A> A getOrCreateProxy(Class<A> clazz);

}
