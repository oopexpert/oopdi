package de.oopexpert.teststructure;

import de.oopexpert.oopdi.VariableSource;
import de.oopexpert.oopdi.annotation.InjectVariable;
import de.oopexpert.oopdi.annotation.Injectable;

@Injectable
public class ClassVariableMatrix {

    @InjectVariable(key = "matrix.long", source = VariableSource.PARAMETER)
    private long longValue;

    @InjectVariable(key = "matrix.long.boxed", source = VariableSource.PARAMETER)
    private Long longBoxedValue;

    @InjectVariable(key = "matrix.short", source = VariableSource.PARAMETER)
    private short shortValue;

    @InjectVariable(key = "matrix.short.boxed", source = VariableSource.PARAMETER)
    private Short shortBoxedValue;

    @InjectVariable(key = "matrix.float", source = VariableSource.PARAMETER)
    private float floatValue;

    @InjectVariable(key = "matrix.float.boxed", source = VariableSource.PARAMETER)
    private Float floatBoxedValue;

    @InjectVariable(key = "matrix.double", source = VariableSource.PARAMETER)
    private double doubleValue;

    @InjectVariable(key = "matrix.double.boxed", source = VariableSource.PARAMETER)
    private Double doubleBoxedValue;

    public long getLongValue() {
        return longValue;
    }

    public Long getLongBoxedValue() {
        return longBoxedValue;
    }

    public short getShortValue() {
        return shortValue;
    }

    public Short getShortBoxedValue() {
        return shortBoxedValue;
    }

    public float getFloatValue() {
        return floatValue;
    }

    public Float getFloatBoxedValue() {
        return floatBoxedValue;
    }

    public double getDoubleValue() {
        return doubleValue;
    }

    public Double getDoubleBoxedValue() {
        return doubleBoxedValue;
    }

}
