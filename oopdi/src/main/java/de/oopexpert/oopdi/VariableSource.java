package de.oopexpert.oopdi;

public enum VariableSource {

	SYSTEM {
		@Override
		String getValueByKey(String key) {
			return System.getenv(key);
		}
	},
	PARAMETER {
		@Override
		String getValueByKey(String key) {
			return System.getProperty(key);
		}
	};

	abstract String getValueByKey(String key);
	
}
