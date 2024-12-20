package de.oopexpert.teststructure;

import de.oopexpert.oopdi.annotation.InjectInstance;

public abstract class ClassB {

	@InjectInstance
	private ClassC classC;
	
	private int i;
	
	public int executeFunction(int i) {
		return i + 1;
	}
	
	public int executeFunction2(int i) {
		return classC.executeFunction2(i);
	}

	public int getI() {
		return i;
	}

	public void setI(int i) {
		this.i = i;
	}
	
}
