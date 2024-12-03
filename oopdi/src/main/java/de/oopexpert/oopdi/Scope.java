package de.oopexpert.oopdi;

import de.oopexpert.oopdi.annotation.Injectable;

public enum Scope {

	GLOBAL {
		@Override
		InstancesState select(InstancesState globalInstances, InstancesState threadInstances) {
			return globalInstances;
		}

		@Override
		boolean isImmediateInstantiationPossible() {
			return true;
		}
	},
	THREAD {
		@Override
		InstancesState select(InstancesState globalInstances, InstancesState threadInstances) {
			return threadInstances;
		}
		@Override
		boolean isImmediateInstantiationPossible() {
			return true;
		}
	},
	LOCAL {
		@Override
		InstancesState select(InstancesState globalInstances, InstancesState threadInstances) {
			return new InstancesState();
		}
		@Override
		boolean isImmediateInstantiationPossible() {
			return false;
		}
	},
	REQUEST {
		@Override
		InstancesState select(InstancesState globalInstances, InstancesState threadInstances) {
			return ProxyManager.getRequestScopedInstances();
		}
		@Override
		boolean isImmediateInstantiationPossible() {
			return false;
		}
	};

	abstract InstancesState select(InstancesState globalInstances, InstancesState threadInstances);
	
	abstract boolean isImmediateInstantiationPossible();
	
	public static <X> Scope of(Class<X> c) {
		return c.getAnnotation(Injectable.class).scope();
	}
	
	public static boolean isImmediateInstantiationPossible(Class<?> clazz) {
		return of(clazz).isImmediateInstantiationPossible();
	}

}
