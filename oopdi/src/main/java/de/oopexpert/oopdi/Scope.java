package de.oopexpert.oopdi;

public enum Scope {

	GLOBAL {
		@Override
		InstancesState select(InstancesState globalInstances, InstancesState threadInstances) {
			return globalInstances;
		}
	},
	THREAD {
		@Override
		InstancesState select(InstancesState globalInstances, InstancesState threadInstances) {
			return threadInstances;
		}
	},
	LOCAL {
		@Override
		InstancesState select(InstancesState globalInstances, InstancesState threadInstances) {
			return new InstancesState();
		}
	},
	REQUEST {
		@Override
		InstancesState select(InstancesState globalInstances, InstancesState threadInstances) {
			return RequestScope.getRequestInstances();
		}
	};

	abstract InstancesState select(InstancesState globalInstances, InstancesState threadInstances);
	
}
