package de.oopexpert.oopdi.proxy;

import java.lang.reflect.Method;
import java.util.function.Supplier;

import de.oopexpert.oopdi.InstancesState;
import de.oopexpert.oopdi.exception.NoRequestScopeAvailable;

public class RequestScopeManager {

	/**
	 * Request state is intentionally instance-scoped, not static: each {@code OOPDI} container
	 * owns one {@code RequestScopeManager} (wired through {@code ProxyManager} for call
	 * interception and through {@code ScopedInstances} for instance selection), so REQUEST-scoped
	 * beans and call-depth counters never leak across container boundaries on the same thread.
	 */
	private final ThreadLocal<RequestContext> requestContext = new ThreadLocal<>();

	private static final class RequestContext {
		final InstancesState state = new InstancesState();
		int callDepth = 0;
	}

	public InstancesState getRequestScopedInstances() {
		RequestContext context = requestContext.get();
		if (context == null) {
			throw new NoRequestScopeAvailable();
		}
		return context.state;
	}

	public <T> Object intercept(Method method, Object[] args, Supplier<T> realObjectSupplier) throws Throwable {
		RequestContext context = requestContext.get();

		if (context == null) {
			context = new RequestContext();
			requestContext.set(context);
		}

		context.callDepth++;

		try {
			return method.invoke(realObjectSupplier.get(), args);
		} finally {
			context.callDepth--;
			if (context.callDepth == 0) {
				requestContext.remove();
			}
		}
	}
}