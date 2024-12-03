package de.oopexpert.oopdi;

import java.util.Set;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import de.oopexpert.teststructure.ClassB;
import de.oopexpert.teststructure.ClassB1;
import de.oopexpert.teststructure.ClassRoot;

class TestOOPDI {
	
	@Test
	void test() {
		
		OOPDI<ClassRoot> oopdi = new OOPDI<ClassRoot>(ClassRoot.class);
		
		oopdi.getInstance(ClassRoot.class).executeRunnable();
		oopdi.getInstance(ClassRoot.class).executeRunnable();
		
		Set<ClassB> classesB = oopdi.getInstance(ClassRoot.class).getClassesB();
		
		Integer increment = oopdi.getInstance(classesB.iterator().next().getClass()).executeFunction(2);
		
		Assertions.assertEquals(3, increment);
		
	}

	@Test
	void test2() {
		
		OOPDI<ClassRoot> oopdi = new OOPDI<ClassRoot>(ClassRoot.class);
		
		ClassB1 instance = oopdi.getInstance(ClassB1.class);

		Assertions.assertEquals(3, instance.executeFunction(2));
		Assertions.assertEquals(3, instance.executeFunction2(2));
		Assertions.assertEquals(3, instance.executeFunction2(2));
		
	}

}
