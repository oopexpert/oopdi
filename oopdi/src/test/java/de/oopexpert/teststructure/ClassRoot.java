package de.oopexpert.teststructure;

import java.util.Set;

import de.oopexpert.oopdi.InjectInstance;
import de.oopexpert.oopdi.InjectSet;
import de.oopexpert.oopdi.Injectable;
import de.oopexpert.oopdi.Scope;

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
