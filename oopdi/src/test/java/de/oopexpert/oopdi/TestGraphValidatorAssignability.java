package de.oopexpert.oopdi;

import java.util.UUID;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Verifies the exact assignability matrix of {@link GraphValidator#isAssignableToField}:
 * every primitive accepts only its own boxed wrapper (mirroring {@code Field.set}),
 * foreign boxed types fail, {@code null} holds only for reference fields.
 */
class TestGraphValidatorAssignability {

	@Test
	void testPrimitivesAcceptOnlyTheirOwnWrapper() {
		Assertions.assertTrue(GraphValidator.isAssignableToField(Boolean.TRUE, boolean.class));
		Assertions.assertTrue(GraphValidator.isAssignableToField((byte) 1, byte.class));
		Assertions.assertTrue(GraphValidator.isAssignableToField('x', char.class));
		Assertions.assertTrue(GraphValidator.isAssignableToField((short) 1, short.class));
		Assertions.assertTrue(GraphValidator.isAssignableToField(1, int.class));
		Assertions.assertTrue(GraphValidator.isAssignableToField(1L, long.class));
		Assertions.assertTrue(GraphValidator.isAssignableToField(1.0f, float.class));
		Assertions.assertTrue(GraphValidator.isAssignableToField(1.0d, double.class));
	}

	@Test
	void testPrimitivesRejectForeignBoxedTypes() {
		Assertions.assertFalse(GraphValidator.isAssignableToField(1L, int.class),
			"A Long value must not validate for an int field, mirroring Field.set");
		Assertions.assertFalse(GraphValidator.isAssignableToField(1, long.class),
			"An Integer value must not validate for a long field, mirroring Field.set");
		Assertions.assertFalse(GraphValidator.isAssignableToField("1", int.class),
			"A String value must not validate for an int field, mirroring Field.set");
		Assertions.assertFalse(GraphValidator.isAssignableToField(Boolean.TRUE, int.class),
			"A Boolean value must not validate for an int field, mirroring Field.set");
	}

	@Test
	void testNullHoldsOnlyForReferenceFields() {
		Assertions.assertFalse(GraphValidator.isAssignableToField(null, int.class),
			"null must not validate for a primitive field");
		Assertions.assertFalse(GraphValidator.isAssignableToField(null, boolean.class),
			"null must not validate for a primitive field");
		Assertions.assertTrue(GraphValidator.isAssignableToField(null, String.class),
			"null must validate for a reference field");
		Assertions.assertTrue(GraphValidator.isAssignableToField(null, Object.class),
			"null must validate for a reference field");
	}

	@Test
	void testReferenceTypesFollowInstanceof() {
		Assertions.assertTrue(GraphValidator.isAssignableToField("value", String.class));
		Assertions.assertTrue(GraphValidator.isAssignableToField("value", Object.class));
		Assertions.assertTrue(GraphValidator.isAssignableToField(Integer.valueOf(1), Number.class));
		Assertions.assertFalse(GraphValidator.isAssignableToField("value", UUID.class),
			"A String value must not validate for a UUID field, mirroring Field.set");
		Assertions.assertFalse(GraphValidator.isAssignableToField(Integer.valueOf(1), Long.class),
			"An Integer value must not validate for a Long field, mirroring Field.set");
	}

}
