package de.oopexpert.oopdi;

import java.util.function.Function;

public class ContextExecution {

	private OOPDI<?> oopdi;

	public ContextExecution(OOPDI<?> oopdi) {
		this.oopdi = oopdi;
	}

	private <T, X, Y> Function<Context<?>, Function<X, Y>> getFunctionWithContext(Class<T> clazz, Function<T, Function<X, Y>> f) {
		return (context) -> f.apply(context.getOrCreateInstance(clazz))::apply;
	}


	private <T> Function<Context<?>, Runnable> getRunnableWithContext(Class<T> clazz, Function<T, Runnable> f) {
		return (context) -> f.apply(context.getOrCreateInstance(clazz))::run;
	}
	
}
