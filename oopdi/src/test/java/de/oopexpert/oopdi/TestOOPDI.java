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
		
		oopdi.execRunnable(ClassRoot.class, r -> r::executeRunnable);
		oopdi.execRunnable(ClassRoot.class, r -> r::executeRunnable);
		
		Set<ClassB> classesB = oopdi.execSupplier(ClassRoot.class, r -> r::getClassesB);
		
		Integer increment = oopdi.execFunction(classesB.iterator().next().getClass(), c -> c::executeFunction, 2);
		
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
