package de.oopexpert.teststructure;

import de.oopexpert.oopdi.VariableSource;
import de.oopexpert.oopdi.annotation.InjectVariable;
import de.oopexpert.oopdi.annotation.Injectable;

@Injectable
public class ClassA {
	
	private int i;

	@InjectVariable(key = "dbUrl", source = VariableSource.SYSTEM)
	private String dbURL;

	@InjectVariable(key = "dbUsername", source = VariableSource.PARAMETER)
	private String dbUsername;

	@InjectVariable(key = "counter", source = VariableSource.PARAMETER)
	private int counter;

	public String getDbURL() {
		return dbURL;
	}

	public String getDbUsername() {
		return dbUsername;
	}

	public int getCounter() {
		return counter;
	}

	public int getI() {
		return i;
	}

	public void setI(int i) {
		this.i = i;
	}
	
}
