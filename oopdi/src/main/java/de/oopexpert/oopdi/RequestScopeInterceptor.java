package de.oopexpert.oopdi;

import net.sf.cglib.proxy.MethodInterceptor;
import net.sf.cglib.proxy.MethodProxy;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

public class RequestScopeInterceptor<T> implements MethodInterceptor {
	
    private static final ThreadLocal<List<InstancesState>> requestScope = new ThreadLocal<>();
    
	private Context<T> context;
	private Class<?> clazz;

    public RequestScopeInterceptor(Context<T> context, Class<?> clazz) {
    	this.clazz = clazz;
		this.context = context;
	}

	@Override
    public Object intercept(Object obj, Method method, Object[] args, MethodProxy proxy) throws Throwable {
        List<InstancesState> list = requestScope.get();
		if (list == null) {
            list = new ArrayList<InstancesState>();
			requestScope.set(list);
			list.add(0, new InstancesState());
        }
        try {
            // Your custom logic before method invocation
            System.out.println("Starting method: " + method.getName());

            Object result = method.invoke(context.getOrCreateInstance(this.clazz), args);

            // Your custom logic after method invocation
            System.out.println("Completed method: " + method.getName());
            return result;
        } finally {
    		list.remove(0);
    		if (list.isEmpty()) {
    			requestScope.remove();
    		}
        }
    }

}
