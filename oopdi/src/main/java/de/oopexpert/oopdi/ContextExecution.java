package de.oopexpert.oopdi;

import static de.oopexpert.oopdi.RequestScope.executeConsumer;
import static de.oopexpert.oopdi.RequestScope.executeSupplier;
import static de.oopexpert.oopdi.RequestScope.executeFunction;
import static de.oopexpert.oopdi.RequestScope.executeRunnable;

import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

public class ContextExecution {

	private OOPDI<?> oopdi;

	public ContextExecution(OOPDI<?> oopdi) {
		this.oopdi = oopdi;
	}
	
	public <T, X,Y> Y execFunction(Class<T> clazz, Function<T, Function<X,Y>> f, X x) {
		return executeFunction(oopdi, getFunctionWithContext(clazz, f), x);
	}

	private <T, X, Y> Function<Context<?>, Function<X, Y>> getFunctionWithContext(Class<T> clazz, Function<T, Function<X, Y>> f) {
		return (context) -> f.apply(context.getOrCreateInstance(clazz))::apply;
	}
	
	
	public <T, Y> Y execSupplier(Class<T> clazz, Function<T, Supplier<Y>> f) {
		return executeSupplier(oopdi, getSupplierWithContext(clazz, f));
	}

	private <T, Y> Function<Context<?>, Supplier<Y>> getSupplierWithContext(Class<T> clazz, Function<T, Supplier<Y>> f) {
		return (context) -> f.apply(context.getOrCreateInstance(clazz))::get;
	}
	
	
	public <T, X> void execConsumer(Class<T> clazz, Function<T, Consumer<X>> f, X x) {
		executeConsumer(oopdi, getConsumerWithContext(clazz, f), x);
	}
	
	private <T, X> Function<Context<?>, Consumer<X>> getConsumerWithContext(Class<T> clazz, Function<T, Consumer<X>> f) {
		return (context) -> f.apply(context.getOrCreateInstance(clazz))::accept;
	}

	public <T> void execRunnable(Class<T> clazz, Function<T, Runnable> f) {
		executeRunnable(oopdi, getRunnableWithContext(clazz, f));
	}

	private <T> Function<Context<?>, Runnable> getRunnableWithContext(Class<T> clazz, Function<T, Runnable> f) {
		return (context) -> f.apply(context.getOrCreateInstance(clazz))::run;
	}
	
}
