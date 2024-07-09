package de.oopexpert.teststructure;

import java.util.Set;

import de.oopexpert.oopdi.Scope;
import de.oopexpert.oopdi.annotation.InjectInstance;
import de.oopexpert.oopdi.annotation.InjectSet;
import de.oopexpert.oopdi.annotation.Injectable;

@Injectable(scope = Scope.LOCAL)
public class ClassRoot {

	@InjectInstance
	private ClassA classA;

	@InjectSet(hint = ClassB.class)
	private Set<ClassB> classesB;
	
	public void executeRunnable() {
		System.out.println(this.toString());
	}
	
	public Set<ClassB> getClassesB() {
		return classesB;
	}
	
}
