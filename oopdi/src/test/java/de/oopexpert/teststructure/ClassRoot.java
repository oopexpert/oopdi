package de.oopexpert.teststructure;

import java.util.Set;
import java.util.function.Consumer;

import de.oopexpert.oopdi.Scope;
import de.oopexpert.oopdi.annotation.InjectInstance;
import de.oopexpert.oopdi.annotation.InjectSet;
import de.oopexpert.oopdi.annotation.Injectable;

@Injectable(scope = Scope.LOCAL)
public class ClassRoot {

	public static final int TEST_I_CLASS_D = 5;

	@InjectInstance
	private ClassA classA;

	@InjectInstance
	private ClassD classD;

	@InjectSet(hint = ClassB.class)
	private Set<ClassB> classesB;
	
	public void executeRunnable() {
	}
	
	public Set<ClassB> getClassesB() {
		return classesB;
	}

	public void execute(Consumer<ClassD> consumerClassD) {
		classD.setI(TEST_I_CLASS_D);
		classD.execute(consumerClassD);
	}
	
}
