package de.oopexpert.teststructure;

import de.oopexpert.oopdi.Scope;
import de.oopexpert.oopdi.annotation.Injectable;

@Injectable(scope=Scope.THREAD)
public class ClassC {

	private int i;
	
	public ClassC() {
	}
	
	public int executeFunction2(int i) {
		return i + 1;
	}

	public int getI() {
		return i;
	}

	public void setI(int i) {
		this.i = i;
	}
	
}
