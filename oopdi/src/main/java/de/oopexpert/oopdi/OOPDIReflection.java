package de.oopexpert.oopdi;

import de.oopexpert.oopdi.annotation.Injectable;

public class OOPDIReflection {

	public static <T> boolean isInjectable(Class<T> clazz) {
		return clazz.isAnnotationPresent(Injectable.class);
	}

}
