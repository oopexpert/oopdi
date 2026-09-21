package de.oopexpert.teststructure;

import de.oopexpert.oopdi.annotation.Injectable;
import de.oopexpert.oopdi.annotation.PreDestroy;

/**
 * Declares a second {@code @PreDestroy} method on top of the inherited one from
 * {@link ClassPreDestroyBase}, violating the exactly-one-@PreDestroy-per-hierarchy rule.
 * Used to verify that shutdown fails with {@code MultiplePreDestroyMethods} (mirroring
 * {@code MultiplePostConstructMethods}) instead of a generic error.
 */
@Injectable
public class ClassMultiplePreDestroyChild extends ClassPreDestroyBase {

    @PreDestroy
    public void childCleanup() {
    }

}
