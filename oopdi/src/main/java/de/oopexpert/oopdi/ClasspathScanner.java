package de.oopexpert.oopdi;

import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

public class ClasspathScanner {

	private static final char PACKAGE_SEPARATOR = '.';
	private static final char PATH_SEPARATOR = '/';
	private static final String SUFFIX_CLASS = ".class";
	private static final String SUFFIX_JAR = ".jar";

	/**
	 * Scanned den Classpath nach allen Unterklassen oder Implementierungen der übergebenen Klasse
	 * innerhalb des angegebenen Pakets (inkl. Unterpaketen).
	 */
	public <T> Set<Class<T>> findDerivedClasses(Class<T> parentClass, String packageName) {
		try {
			var classes = new HashSet<Class<T>>();
			for (var classpathEntry : getClassPathEntries()) {
				classes.addAll(getDerivedClassesInClasspath(parentClass, packageName, classpathEntry));
			}
			return classes;
		} catch (ClassNotFoundException | IOException e) {
			throw new RuntimeException("Failed to scan classpath for subclasses of '" + parentClass.getName() + "' in package '" + packageName + "'", e);
		}
	}

	private <T> Set<Class<T>> getDerivedClassesInClasspath(Class<T> parentClass, String packageName, String classpathEntry) throws ClassNotFoundException, IOException {
		var classes = new HashSet<Class<T>>();

		if (classpathEntry.endsWith(SUFFIX_JAR)) {
			classes.addAll(getDerivedClassesFromJar(parentClass, toPathName(packageName), classpathEntry));
		} else {
			var entry = new File(classpathEntry);
			if (entry.isFile() && classpathEntry.endsWith(SUFFIX_CLASS)) {
				classes.addAll(getDerivedClassesFromDirectoryOrClassFile(parentClass, packageName, entry));
			} else {
				var directory = new File(classpathEntry, toPathName(packageName));
				if (directory.exists()) {
					classes.addAll(getDerivedClassesFromDirectory(parentClass, packageName, directory));
				}
			}
		}
		return classes;
	}

	private String[] getClassPathEntries() {
		return System.getProperty("java.class.path").split(File.pathSeparator);
	}

	private <T> Set<Class<T>> getDerivedClassesFromJar(Class<T> parentClass, String path, String classpathEntry) throws ClassNotFoundException, IOException {
		var classes = new HashSet<Class<T>>();
		try (var jarFile = new JarFile(classpathEntry)) {
			var entries = jarFile.entries();
			while (entries.hasMoreElements()) {
				var classInJarEntry = findAssignableClassInJarEntry(parentClass, path, entries.nextElement());
				if (classInJarEntry != null) {
					classes.add(classInJarEntry);
				}
			}
		}
		return classes;
	}

	private <T> Class<T> findAssignableClassInJarEntry(Class<T> parentClass, String path, JarEntry jarEntry) throws ClassNotFoundException {
		var jarEntryName = jarEntry.getName();
		if (jarEntryName.startsWith(path) && jarEntryName.endsWith(SUFFIX_CLASS)) {
			var className = toClassName(jarEntryName);
			var clazz = loadWithoutInitializing(className, parentClass);
			if (parentClass.isAssignableFrom(clazz) && !parentClass.equals(clazz)) {
				@SuppressWarnings("unchecked")
				Class<T> casted = (Class<T>) clazz;
				return casted;
			}
		}
		return null;
	}

	private <T> Set<Class<T>> getDerivedClassesFromDirectory(Class<T> parentClass, String packageName, File directory) throws ClassNotFoundException {
		var classes = new HashSet<Class<T>>();
		var files = directory.listFiles();
		if (files != null) {
			for (var file : files) {
				classes.addAll(getDerivedClassesFromDirectoryOrClassFile(parentClass, packageName, file));
			}
		}
		return classes;
	}

	private <T> Set<Class<T>> getDerivedClassesFromDirectoryOrClassFile(Class<T> parentClass, String packageName, File file) throws ClassNotFoundException {
		var classes = new HashSet<Class<T>>();
		if (file.isDirectory()) {
			classes.addAll(findDerivedClasses(parentClass, packageName + PACKAGE_SEPARATOR + file.getName()));
		} else {
			var clazz = findAssignableClassInFile(parentClass, packageName, file);
			if (clazz != null) {
				classes.add(clazz);
			}
		}
		return classes;
	}

	private <T> Class<T> findAssignableClassInFile(Class<T> parentClass, String packageName, File file) throws ClassNotFoundException {
		if (file.getName().endsWith(SUFFIX_CLASS)) {
			var className = toClassName(packageName, file);
			var clazz = loadWithoutInitializing(className, parentClass);
			if (parentClass.isAssignableFrom(clazz) && !parentClass.equals(clazz)) {
				@SuppressWarnings("unchecked")
				Class<T> casted = (Class<T>) clazz;
				return casted;
			}
		}
		return null;
	}

	/**
	 * Lädt eine Klasse ohne Ausführung ihrer statischen Initialisierer (initialize = false).
	 */
	private Class<?> loadWithoutInitializing(String className, Class<?> contextClass) throws ClassNotFoundException {
		return Class.forName(className, false, contextClass.getClassLoader());
	}

	private String toClassName(String pathName) {
		return pathName.substring(0, pathName.length() - SUFFIX_CLASS.length()).replace(PATH_SEPARATOR, PACKAGE_SEPARATOR);
	}

	private String toClassName(String packageName, File file) {
		return packageName + PACKAGE_SEPARATOR + file.getName().substring(0, file.getName().length() - SUFFIX_CLASS.length());
	}

	private String toPathName(String packageName) {
		return packageName.replace(PACKAGE_SEPARATOR, PATH_SEPARATOR);
	}
}