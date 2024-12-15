package de.oopexpert.teststructure;

import java.util.function.Consumer;

import de.oopexpert.oopdi.Scope;
import de.oopexpert.oopdi.annotation.Injectable;

@Injectable(scope=Scope.REQUEST)
public class ClassD {

	private int i;
	
	public ClassD() {
	}
	
	public int getI() {
		return i;
	}

	public void setI(int i) {
		this.i = i;
	}
	
	public void execute(Consumer<ClassD> consumerClassD) {
		consumerClassD.accept(this);
	}
	
}
