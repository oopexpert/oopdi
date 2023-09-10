package de.oopexpert.oopdi;

import static java.util.Collections.synchronizedMap;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;

import de.oopexpert.oopdi.exception.NoRequestScopeAvailable;

public abstract class RequestScope {

    private static Map<Thread, List<Map<Class<?>, Object>>> requestInstanceMaps = synchronizedMap(new HashMap<>());
	
    private abstract static class FunctionRequest extends RequestScope {

    	protected abstract <X,Y>  Y exec(BiFunction<Context<?>, X,Y> f, X x);

    }

    private abstract static class SupplierRequest extends RequestScope {
    	
    	protected abstract <Y>  Y exec(Function<Context<?>, Y> f);

    }

    private abstract static class ConsumerRequest extends RequestScope {

    	protected abstract <X> void exec(BiConsumer<Context<?>,X> f, X x);

    }

    private abstract static class RunnableRequest extends RequestScope {

    	protected abstract void exec(Consumer<Context<?>> f);

    }

	private static void init(RequestScope request) {
		List<Map<Class<?>, Object>> list = requestInstanceMaps.get(Thread.currentThread());
		if (list == null) {
			list = new ArrayList<>();
			requestInstanceMaps.put(Thread.currentThread(), list);
		}
		list.add(0, new HashMap<>());
	}
	

	public static  <X,Y> Y execute(Class<?> clazz, OOPDI<?> oopdi, BiFunction<Context<?>, X,Y> f, X x) {
		return new FunctionRequest() {
			
			@Override
			protected <A, B> B exec(BiFunction<Context<?>, A, B> f, A x) {
				
				try {
					
					init(this);
					
					return f.apply(oopdi.createContext(), x);
					
				} finally {
					cleanup();
				}
				
			}

		}.exec(f, x);
		
	}

	public static <Y>  Y execute(Class<?> clazz, OOPDI<?> oopdi, Function<Context<?>, Y> f) {
		
		return new SupplierRequest() {
			
			@Override
			protected <B> B exec(Function<Context<?>, B> f) {
				
				try {
					
					init(this);
					
					return f.apply(oopdi.createContext());
					
				} finally {
					cleanup();
				}
				
			}

		}.exec(f);

	}

	public static <X> void execute(Class<?> clazz, OOPDI<?> oopdi, BiConsumer<Context<?>, X> f, X x) {
		
		new ConsumerRequest() {
			
			@Override
			protected <A> void exec(BiConsumer<Context<?>, A> f, A x) {
				
				try {
					
					init(this);
					
					f.accept(oopdi.createContext(), x);
					
				} finally {
					cleanup();
				}
				
			}

		}.exec(f, x);

	}

	public static void execute(Class<?> clazz, OOPDI<?> oopdi, Consumer<Context<?>> r) {
		new RunnableRequest() {
			@Override
			protected void exec(Consumer<Context<?>> f) {
				try {
					
					init(this);
					
					f.accept(oopdi.createContext());
					
				} finally {
					cleanup();
				}
				
			}
		}.exec(r);
	}

	private static void cleanup() {
		List<Map<Class<?>, Object>> list = requestInstanceMaps.get(Thread.currentThread());
		list.remove(0);
		if (list.isEmpty()) {
			requestInstanceMaps.remove(Thread.currentThread());
		}
	}

	public static Object getRequestScopeInstance(Class<?> clazz) {
		return getRequestScopedInstances().get(clazz);
	}

	public static Map<Class<?>, Object> getRequestScopedInstances() {
		List<Map<Class<?>, Object>> list = requestInstanceMaps.get(Thread.currentThread());
		if (list == null) throw new NoRequestScopeAvailable();
		return list.get(0);
	}

	public static  <T> void getRequestScopeInstance(Class<T> clazz, T t) {
		getRequestScopedInstances().put(clazz, t);
	}

}
