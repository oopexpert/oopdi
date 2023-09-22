package de.oopexpert.oopdi;

public enum Scope {

	GLOBAL {
		@Override
		InstancesState select(InstancesState globalInstances, InstancesState threadInstances,
				InstancesState localInstances) {
			return globalInstances;
		}
	},
	THREAD {
		@Override
		InstancesState select(InstancesState globalInstances, InstancesState threadInstances,
				InstancesState localInstances) {
			return threadInstances;
		}
	},
	LOCAL {
		@Override
		InstancesState select(InstancesState globalInstances, InstancesState threadInstances,
				InstancesState localInstances) {
			return localInstances;
		}
	};
	
	abstract InstancesState select(InstancesState globalInstances, InstancesState threadInstances,
			InstancesState localInstances);
	
}
