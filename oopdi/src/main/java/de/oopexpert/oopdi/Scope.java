package de.oopexpert.oopdi;

import java.util.Map;

public enum Scope {

	GLOBAL {
		@Override
		Map<Class<?>, Object> select(Map<Class<?>, Object> globalInstances, Map<Class<?>, Object> threadInstances,
				Map<Class<?>, Object> localInstances) {
			return globalInstances;
		}
	},
	THREAD {
		@Override
		Map<Class<?>, Object> select(Map<Class<?>, Object> globalInstances, Map<Class<?>, Object> threadInstances,
				Map<Class<?>, Object> localInstances) {
			return threadInstances;
		}
	},
	LOCAL {
		@Override
		Map<Class<?>, Object> select(Map<Class<?>, Object> globalInstances, Map<Class<?>, Object> threadInstances,
				Map<Class<?>, Object> localInstances) {
			return localInstances;
		}
	},
	REQUEST {

		@Override
		Map<Class<?>, Object> select(Map<Class<?>, Object> globalInstances, Map<Class<?>, Object> threadInstances,
				Map<Class<?>, Object> localInstances) {
			return RequestScope.getRequestScopedInstances();
		}
		
	};

	abstract Map<Class<?>, Object> select(Map<Class<?>, Object> globalInstances, Map<Class<?>, Object> threadInstances,
			Map<Class<?>, Object> localInstances);
	
}
