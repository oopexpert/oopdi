package de.oopexpert.oopdi;

import static de.oopexpert.oopdi.RequestScope.execute;

import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

public class ContextExecution {

	private OOPDI<?> oopdi;

	public ContextExecution(OOPDI<?> oopdi) {
		this.oopdi = oopdi;
	}
	
	public <T, X,Y> Y execFunction(Class<T> clazz, Function<T, Function<X,Y>> f, X x) {
		return execute(clazz, oopdi, getFunctionWithContext(clazz, f)).apply(x);
	}

	private <T, X, Y> Function<Context<?>, Function<X, Y>> getFunctionWithContext(Class<T> clazz, Function<T, Function<X, Y>> f) {
		return (context) -> f.apply(context.getObject(clazz))::apply;
	}
	
	
	public <T, Y> Y execSupplier(Class<T> clazz, Function<T, Supplier<Y>> f) {
		return execute(clazz, oopdi, getSupplierWithContext(clazz, f)).get();
	}

	private <T, Y> Function<Context<?>, Supplier<Y>> getSupplierWithContext(Class<T> clazz, Function<T, Supplier<Y>> f) {
		return (context) -> f.apply(context.getObject(clazz))::get;
	}
	
	
	public <T, X> void execConsumer(Class<T> clazz, Function<T, Consumer<X>> f, X x) {
		execute(clazz, oopdi, getConsumerWithContext(clazz, f)).accept(x);
	}
	
	private <T, X> Function<Context<?>, Consumer<X>> getConsumerWithContext(Class<T> clazz, Function<T, Consumer<X>> f) {
		return (context) -> f.apply(context.getObject(clazz))::accept;
	}

	public <T> void execRunnable(Class<T> clazz, Function<T, Runnable> f) {
		execute(clazz, oopdi, getRunnableWithContext(clazz, f)).run();
	}

	private <T> Function<Context<?>, Runnable> getRunnableWithContext(Class<T> clazz, Function<T, Runnable> f) {
		return (context) -> f.apply(context.getObject(clazz))::run;
	}


	
}
