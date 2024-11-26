package de.oopexpert.teststructure;

import de.oopexpert.oopdi.Scope;
import de.oopexpert.oopdi.annotation.Injectable;

@Injectable(scope=Scope.LOCAL)
public class ClassC {

	public ClassC() {
	}
	
	public int executeFunction2(int i) {
		return i + 1;
	}	
	
}
