package de.oopexpert.oopdi;

public enum VariableSource {

	SYSTEM {
		@Override
		public String getValueByKey(String key) {
			return System.getenv(key);
		}
	},
	PARAMETER {
		@Override
		public String getValueByKey(String key) {
			return System.getProperty(key);
		}
	};

	public abstract String getValueByKey(String key);
	
}
