package de.oopexpert.oopdi.proxy;

import java.lang.reflect.Method;
import java.util.function.Supplier;

import de.oopexpert.oopdi.InstancesState;
import de.oopexpert.oopdi.exception.NoRequestScopeAvailable;

public class RequestScopeManager {

	private static final ThreadLocal<RequestContext> REQUEST_CONTEXT = new ThreadLocal<>();

	private static final class RequestContext {
		final InstancesState state = new InstancesState();
		int callDepth = 0;
	}

	public static InstancesState getRequestScopedInstances() {
		RequestContext context = REQUEST_CONTEXT.get();
		if (context == null) {
			throw new NoRequestScopeAvailable();
		}
		return context.state;
	}

	public <T> Object intercept(Method method, Object[] args, Supplier<T> realObjectSupplier) throws Throwable {
		RequestContext context = REQUEST_CONTEXT.get();

		if (context == null) {
			context = new RequestContext();
			REQUEST_CONTEXT.set(context);
		}

		context.callDepth++;

		try {
			return method.invoke(realObjectSupplier.get(), args);
		} finally {
			context.callDepth--;
			if (context.callDepth == 0) {
				REQUEST_CONTEXT.remove();
			}
		}
	}
}