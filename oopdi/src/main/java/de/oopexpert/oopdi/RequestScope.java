package de.oopexpert.oopdi;

import static java.util.Collections.synchronizedMap;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import de.oopexpert.oopdi.exception.NoRequestScopeAvailable;

public abstract class RequestScope {

    private static Map<Thread, List<InstancesState>> requestInstanceMaps = synchronizedMap(new HashMap<>());
	
    private abstract static class FunctionRequest<PARAMETER, RESULT, OPERATION extends Function<PARAMETER, RESULT>> extends RequestScope {

    	protected abstract RESULT exec(Function<Context<?>, OPERATION> prepareOperation, PARAMETER parameter);

    }

    private abstract static class SupplierRequest<RESULT, OPERATION extends Supplier<RESULT>> extends RequestScope {
    	
    	protected abstract RESULT exec(Function<Context<?>, OPERATION> prepareOperation);

    }

    private abstract static class ConsumerRequest<PARAMETER, OPERATION extends Consumer<PARAMETER>> extends RequestScope {

    	protected abstract void exec(Function<Context<?>, OPERATION> prepareOperation, PARAMETER parameter);

    }

    private abstract static class RunnableRequest<OPERATION extends Runnable> extends RequestScope {

    	protected abstract void exec(Function<Context<?>, OPERATION> operation);

    }

	private static void init(RequestScope request) {
		List<InstancesState> list = requestInstanceMaps.get(Thread.currentThread());
		if (list == null) {
			list = new ArrayList<>();
			requestInstanceMaps.put(Thread.currentThread(), list);
		}
		list.add(0, new InstancesState());
	}
	

	public static  <RESULT, OPERATION extends Function<PARAMETER, RESULT>, PARAMETER> RESULT executeFunction(OOPDI<?> oopdi, Function<Context<?>, OPERATION> prepareOperation, PARAMETER parameter) {
		return new FunctionRequest<PARAMETER, RESULT, OPERATION>() {


			@Override
			protected RESULT exec(Function<Context<?>, OPERATION> prepareOperation, PARAMETER parameter) {
				
				try {
					
					init(this);
					
					return prepareOperation.apply(oopdi.createContext()).apply(parameter);
					
				} finally {
					cleanup();
				}			
				
			}

		}.exec(prepareOperation, parameter);
		
	}

	public static <RESULT, OPERATION extends Supplier<RESULT>> RESULT executeSupplier(OOPDI<?> oopdi, Function<Context<?>, OPERATION> prepareOperation) {
		
		return new SupplierRequest<RESULT, OPERATION>() {
			
			@Override
			protected RESULT exec(Function<Context<?>, OPERATION> prepareOperation) {
				
				try {
					
					init(this);
					
					OPERATION operation = prepareOperation.apply(oopdi.createContext());
					return operation.get();
					
				} finally {
					cleanup();
				}
				
			}

		}.exec(prepareOperation);

	}

	public static <PARAMETER, OPERATION extends Consumer<PARAMETER>> void executeConsumer(OOPDI<?> oopdi, Function<Context<?>, OPERATION> prepareOperation, PARAMETER parameter) {
		
		new ConsumerRequest<PARAMETER, OPERATION>() {
			
			@Override
			protected void exec(Function<Context<?>, OPERATION> prepareOperation, PARAMETER parameter) {

				try {
					
					init(this);
					
					prepareOperation.apply(oopdi.createContext()).accept(parameter);
					
				} finally {
					cleanup();
				}
				
			}

		}.exec(prepareOperation, parameter);

	}

	public static <OPERATION extends Runnable> void executeRunnable(OOPDI<?> oopdi, Function<Context<?>, OPERATION> prepareOperation) {
		new RunnableRequest<OPERATION>() {
			@Override
			protected void exec(Function<Context<?>, OPERATION> prepareOperation) {
				try {
					
					init(this);
					
					prepareOperation.apply(oopdi.createContext()).run();
					
				} finally {
					cleanup();
				}
				
			}
		}.exec(prepareOperation);
	}

	private static void cleanup() {
		List<InstancesState> list = requestInstanceMaps.get(Thread.currentThread());
		list.remove(0);
		if (list.isEmpty()) {
			requestInstanceMaps.remove(Thread.currentThread());
		}
	}

	public static Object getRequestScopeInstance(Class<?> clazz) {
		return getRequestScopedInstances().instances.get(clazz);
	}

	public static InstancesState getRequestScopedInstances() {
		List<InstancesState> list = requestInstanceMaps.get(Thread.currentThread());
		if (list == null) {
			throw new NoRequestScopeAvailable();
		}
		return list.get(0);
	}

	public static  <T> void getRequestScopeInstance(Class<T> clazz, T t) {
		getRequestScopedInstances().instances.put(clazz, t);
	}


	public static InstancesState getRequestInstances() {
		return getRequestScopedInstances();
	}

}
