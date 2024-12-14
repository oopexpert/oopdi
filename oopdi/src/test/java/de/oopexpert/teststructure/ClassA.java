package de.oopexpert.teststructure;

import de.oopexpert.oopdi.VariableSource;
import de.oopexpert.oopdi.annotation.InjectVariable;
import de.oopexpert.oopdi.annotation.Injectable;

@Injectable
public class ClassA {

	@InjectVariable(key = "dbUrl", source = VariableSource.SYSTEM)
	private String dbURL;

	@InjectVariable(key = "dbUsername", source = VariableSource.PARAMETER)
	private String dbUsername;

	public String getDbURL() {
		return dbURL;
	}

	public String getDbUsername() {
		return dbUsername;
	}
	
}
