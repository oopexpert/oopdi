package de.oopexpert.oopdi.proxy;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
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

	/**
	 * Destroys request-scoped beans when their call chain ends. Set once by the owning
	 * {@code Context} after its lifecycle processor exists (same pattern as the
	 * post-processor {@code Consumer} in {@code InstanceFactory}); {@code null} means no
	 * request-end destruction (behavioral fallback, used in isolation).
	 */
	private volatile Consumer<Object> requestEndDestroyer;

	private static final class RequestContext {
		final InstancesState state = new InstancesState();
		int callDepth = 0;
	}

	public void setRequestEndDestroyer(Consumer<Object> requestEndDestroyer) {
		this.requestEndDestroyer = requestEndDestroyer;
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

		Throwable mainFailure = null;
		Object result = null;
		try {
			result = method.invoke(realObjectSupplier.get(), args);
		} catch (Throwable t) {
			mainFailure = t;
		}

		List<Throwable> cleanupFailures = destroyRequestStateIfOutermost(context);

		if (mainFailure != null) {
			cleanupFailures.forEach(mainFailure::addSuppressed);
			throw mainFailure;
		}
		if (!cleanupFailures.isEmpty()) {
			RuntimeException aggregated = new RuntimeException("Request chain completed with "
					+ cleanupFailures.size() + " failing @PreDestroy invocation(s); "
					+ "all remaining request-scoped instances were still destroyed best-effort.");
			cleanupFailures.forEach(aggregated::addSuppressed);
			throw aggregated;
		}
		return result;
	}

	/**
	 * Decrements the call depth and, when the outermost call of the chain ends, destroys every
	 * request-scoped bean of that chain in reverse creation order, best-effort. Snapshots are
	 * copied before the {@code ThreadLocal} is cleared so that cleanup starting new chains gets
	 * a fresh context instead of polluting the ending one. Returns the collected cleanup
	 * failures (empty when nothing failed or no destroyer is wired).
	 */
	private List<Throwable> destroyRequestStateIfOutermost(RequestContext context) {
		List<Throwable> failures = new ArrayList<>();
		context.callDepth--;
		if (context.callDepth != 0) {
			return failures;
		}
		List<Object> toDestroy = context.state.allInstancesInReverseCreationOrder();
		requestContext.remove();
		Consumer<Object> destroyer = requestEndDestroyer;
		if (destroyer != null) {
			for (Object instance : toDestroy) {
				try {
					destroyer.accept(instance);
				} catch (RuntimeException e) {
					failures.add(e);
				}
			}
		}
		return failures;
	}
}