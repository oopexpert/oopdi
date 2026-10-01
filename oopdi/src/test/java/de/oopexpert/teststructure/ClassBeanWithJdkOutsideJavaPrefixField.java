package de.oopexpert.teststructure;

import javax.swing.Timer;

import de.oopexpert.oopdi.annotation.Injectable;

/**
 * An {@code @Injectable} bean with a {@code final}, UNANNOTATED field of type
 * {@code javax.swing.Timer}, set only in the constructor. {@code javax.swing.Timer} needs no
 * display/AWT toolkit to construct and is safe to instantiate headless; it only serves here to
 * demonstrate that a JDK type OUTSIDE the "java." package prefix (i.e. "javax.") was
 * previously misdetected as an injection point by the constructor-parameter type-name
 * fallback in {@code InstanceDependencyResolver}, causing {@code CannotInject} ("it is not
 * annotated as 'Injectable'") for an unannotated field.
 */
@Injectable
public class ClassBeanWithJdkOutsideJavaPrefixField {

	private final Timer timer;

	public ClassBeanWithJdkOutsideJavaPrefixField() {
		this.timer = new Timer(100, e -> { });
	}

	public Timer getTimer() {
		return timer;
	}

}
