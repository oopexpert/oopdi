package de.oopexpert.oopdi;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import de.oopexpert.teststructure.ClassA;
import de.oopexpert.teststructure.ClassMissingVar;
import de.oopexpert.teststructure.ClassOptionalVar;
import de.oopexpert.teststructure.ClassRoot;
import de.oopexpert.teststructure.ClassVariableInvalidFormat;
import de.oopexpert.teststructure.ClassVariableMatrix;

class TestVariableInjection {

    @Test
    void testInjectSystemEnvironmentVariable() {

        OOPDI<ClassRoot> oopdi = new OOPDI<ClassRoot>(ClassRoot.class);

        String EXPECTED = "jdbc://mysql:userdb";

        ClassA instance = oopdi.getInstance(ClassA.class);

        Assertions.assertEquals(EXPECTED, instance.getDbURL());

    }

    @Test
    void testInjectParameterVariable() {

        OOPDI<ClassRoot> oopdi = new OOPDI<ClassRoot>(ClassRoot.class);

        String EXPECTED = "dbUser1";

        ClassA instance = oopdi.getInstance(ClassA.class);

        Assertions.assertEquals(EXPECTED, instance.getDbUsername());

    }

    @Test
    void testCommonTypeConversionToInt() {

        OOPDI<ClassRoot> oopdi = new OOPDI<ClassRoot>(ClassRoot.class);

        int EXPECTED = 4;

        ClassA instance = oopdi.getInstance(ClassA.class);

        Assertions.assertEquals(EXPECTED, instance.getCounter());

    }

    @Test
    void testInjectVariableMissingKeyThrowsDescriptiveError() {

        OOPDI<ClassMissingVar> oopdi = new OOPDI<>(ClassMissingVar.class);

        ClassMissingVar instance = oopdi.getInstance(ClassMissingVar.class);

        RuntimeException ex = Assertions.assertThrows(RuntimeException.class, instance::getMissingValue);

        Assertions.assertTrue(
            ex.getMessage().contains("definitelyNotSetKey_12345"),
            "Exception message should contain the missing key name, but was: " + ex.getMessage()
        );

    }

    @Test
    void testInjectVariableOptionalYieldsNull() {

        OOPDI<ClassOptionalVar> oopdi = new OOPDI<>(ClassOptionalVar.class);

        ClassOptionalVar instance = oopdi.getInstance(ClassOptionalVar.class);

        Assertions.assertNull(instance.getOptionalValue(),
            "optional=true with missing key should inject null");

    }

    @Test
    void testInjectVariableDefaultValueUsedWhenKeyMissing() {

        OOPDI<ClassOptionalVar> oopdi = new OOPDI<>(ClassOptionalVar.class);

        ClassOptionalVar instance = oopdi.getInstance(ClassOptionalVar.class);

        Assertions.assertEquals("fallback", instance.getDefaultValue(),
            "defaultValue should be used when the key is not found");

    }

    @Test
    void testInjectVariableDefaultValueParsedForPrimitive() {

        OOPDI<ClassOptionalVar> oopdi = new OOPDI<>(ClassOptionalVar.class);

        ClassOptionalVar instance = oopdi.getInstance(ClassOptionalVar.class);

        Assertions.assertEquals(42, instance.getDefaultInt(),
            "defaultValue should be parsed to the field's primitive type");

    }

    @Test
    void testInjectVariableParsesExtendedNumericTypes() {

        System.setProperty("matrix.long", "9000000000");
        System.setProperty("matrix.long.boxed", "9000000001");
        System.setProperty("matrix.short", "32000");
        System.setProperty("matrix.short.boxed", "31999");
        System.setProperty("matrix.float", "3.5");
        System.setProperty("matrix.float.boxed", "4.5");
        System.setProperty("matrix.double", "7.25");
        System.setProperty("matrix.double.boxed", "8.25");

        try {
            OOPDI<ClassVariableMatrix> oopdi = new OOPDI<>(ClassVariableMatrix.class);
            ClassVariableMatrix instance = oopdi.getInstance(ClassVariableMatrix.class);

            Assertions.assertEquals(9000000000L, instance.getLongValue());
            Assertions.assertEquals(Long.valueOf(9000000001L), instance.getLongBoxedValue());
            Assertions.assertEquals((short) 32000, instance.getShortValue());
            Assertions.assertEquals(Short.valueOf((short) 31999), instance.getShortBoxedValue());
            Assertions.assertEquals(3.5f, instance.getFloatValue());
            Assertions.assertEquals(Float.valueOf(4.5f), instance.getFloatBoxedValue());
            Assertions.assertEquals(7.25d, instance.getDoubleValue());
            Assertions.assertEquals(Double.valueOf(8.25d), instance.getDoubleBoxedValue());
        } finally {
            System.clearProperty("matrix.long");
            System.clearProperty("matrix.long.boxed");
            System.clearProperty("matrix.short");
            System.clearProperty("matrix.short.boxed");
            System.clearProperty("matrix.float");
            System.clearProperty("matrix.float.boxed");
            System.clearProperty("matrix.double");
            System.clearProperty("matrix.double.boxed");
        }

    }

    @Test
    void testInjectVariableInvalidNumericFormatThrows() {

        System.setProperty("matrix.invalid.int", "notANumber");

        try {
            OOPDI<ClassVariableInvalidFormat> oopdi = new OOPDI<>(ClassVariableInvalidFormat.class);
            ClassVariableInvalidFormat instance = oopdi.getInstance(ClassVariableInvalidFormat.class);

            RuntimeException ex = Assertions.assertThrows(RuntimeException.class, instance::getInvalidInt,
                "Invalid numeric values should fail during injection");
            Assertions.assertTrue(ex.getCause() instanceof NumberFormatException,
                "NumberFormatException should be preserved as the cause");
        } finally {
            System.clearProperty("matrix.invalid.int");
        }

    }

}
