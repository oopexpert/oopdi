package de.oopexpert.oopdi.parser;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

public class TypeParserRegistry {

	private final Map<Class<?>, Function<String, Object>> typeParsers = new HashMap<>();

	public TypeParserRegistry() {
		registerDefaultParsers();
	}

	private void registerDefaultParsers() {
		typeParsers.put(Integer.class, Integer::valueOf);
		typeParsers.put(int.class, Integer::parseInt);
		typeParsers.put(Long.class, Long::valueOf);
		typeParsers.put(long.class, Long::parseLong);
		typeParsers.put(Short.class, Short::valueOf);
		typeParsers.put(short.class, Short::parseShort);
		typeParsers.put(Float.class, Float::valueOf);
		typeParsers.put(float.class, Float::parseFloat);
		typeParsers.put(Double.class, Double::valueOf);
		typeParsers.put(double.class, Double::parseDouble);
		typeParsers.put(Boolean.class, Boolean::valueOf);
		typeParsers.put(boolean.class, Boolean::parseBoolean);
		typeParsers.put(Byte.class, Byte::valueOf);
		typeParsers.put(byte.class, Byte::parseByte);
		typeParsers.put(Character.class, s -> s.charAt(0));
		typeParsers.put(char.class, s -> s.charAt(0));
	}

	public Object parse(String value, Class<?> targetType) {
		Function<String, Object> parser = typeParsers.get(targetType);
		return parser != null ? parser.apply(value) : value;
	}

	public void registerParser(Class<?> type, Function<String, Object> parser) {
		typeParsers.put(type, parser);
	}
}