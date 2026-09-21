package de.oopexpert.oopdi.proxy;

import java.util.function.Supplier;

/**
 * Per-proxy interception state held by generated proxy instances (see
 * {@link ProxiedBeanCarrier}): the owning container's request-scope manager and the supplier
 * that resolves the real object for the proxy's scope.
 *
 * <p>Public only because generated proxy classes (loaded into the bean's class loader, i.e. a
 * different runtime package) must be able to reference it. Framework-internal plumbing, not
 * part of the public API contract.</p>
 *
 * @param requestScopeManager the owning container's request-scope manager
 * @param realObjectSupplier resolves the real object for the proxy's scope
 */
public record ProxyTarget(RequestScopeManager requestScopeManager, Supplier<?> realObjectSupplier) {
}
