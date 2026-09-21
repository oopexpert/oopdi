package de.oopexpert.oopdi;

import java.io.File;
import java.io.IOException;
import java.lang.annotation.Annotation;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.oopexpert.oopdi.exception.ClasspathScanFailed;

public class ClasspathScanner {

	private static final Logger log = LoggerFactory.getLogger(ClasspathScanner.class);

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
			throw new ClasspathScanFailed("Failed to scan classpath for subclasses of '%s' in package '%s'.".formatted(parentClass.getName(), packageName), e);
		}
	}

	/**
	 * Scans the entire classpath (no parent-class/package restriction, starting from every
	 * classpath entry's root) for classes carrying the given annotation. Used for background
	 * metadata warmup, which needs to discover all {@code @Injectable} classes up front rather
	 * than lazily per already-known hint type (unlike {@link #findDerivedClasses}).
	 */
	public Set<Class<?>> findAllAnnotatedClasses(Class<? extends Annotation> annotation) {
		try {
			var classes = new HashSet<Class<?>>();
			for (var classpathEntry : getClassPathEntries()) {
				classes.addAll(getAnnotatedClassesInClasspath(annotation, classpathEntry));
			}
			return classes;
		} catch (IOException e) {
			throw new ClasspathScanFailed("Failed to scan classpath for classes annotated with '%s'.".formatted(annotation.getName()), e);
		}
	}

	private Set<Class<?>> getAnnotatedClassesInClasspath(Class<? extends Annotation> annotation, String classpathEntry) throws IOException {
		var classes = new HashSet<Class<?>>();

		if (classpathEntry.endsWith(SUFFIX_JAR)) {
			classes.addAll(getAnnotatedClassesFromJar(annotation, classpathEntry));
		} else {
			var root = new File(classpathEntry);
			if (root.isFile() && classpathEntry.endsWith(SUFFIX_CLASS)) {
				findAnnotatedClassInFile(annotation, "", root).ifPresent(classes::add);
			} else if (root.isDirectory()) {
				classes.addAll(getAnnotatedClassesFromDirectory(annotation, "", root));
			}
		}
		return classes;
	}

	private Set<Class<?>> getAnnotatedClassesFromJar(Class<? extends Annotation> annotation, String classpathEntry) throws IOException {
		var classes = new HashSet<Class<?>>();
		try (var jarFile = new JarFile(classpathEntry)) {
			var entries = jarFile.entries();
			while (entries.hasMoreElements()) {
				var jarEntry = entries.nextElement();
				var jarEntryName = jarEntry.getName();
				if (jarEntryName.endsWith(SUFFIX_CLASS)) {
					var className = toClassName(jarEntryName);
					tryLoadIfAnnotated(annotation, className).ifPresent(classes::add);
				}
			}
		}
		return classes;
	}

	private Set<Class<?>> getAnnotatedClassesFromDirectory(Class<? extends Annotation> annotation, String packageName, File directory) {
		var classes = new HashSet<Class<?>>();
		var files = directory.listFiles();
		if (files != null) {
			for (var file : files) {
				if (file.isDirectory()) {
					String childPackage = packageName.isEmpty() ? file.getName() : packageName + PACKAGE_SEPARATOR + file.getName();
					classes.addAll(getAnnotatedClassesFromDirectory(annotation, childPackage, file));
				} else {
					findAnnotatedClassInFile(annotation, packageName, file).ifPresent(classes::add);
				}
			}
		}
		return classes;
	}

	private Optional<Class<?>> findAnnotatedClassInFile(Class<? extends Annotation> annotation, String packageName, File file) {
		if (!file.getName().endsWith(SUFFIX_CLASS)) {
			return Optional.empty();
		}
		var simpleName = file.getName().substring(0, file.getName().length() - SUFFIX_CLASS.length());
		var className = packageName.isEmpty() ? simpleName : packageName + PACKAGE_SEPARATOR + simpleName;
		return tryLoadIfAnnotated(annotation, className);
	}

	/**
	 * Loads a candidate by name (non-initializing) and checks for the given annotation, treating
	 * any failure to even load the class file as "not a match" rather than aborting the whole
	 * scan. Some class-file entries encountered during a classpath-wide scan are not ordinary
	 * classes at all (for example {@code module-info.class}, which the JVM rejects with a
	 * {@link LinkageError} because it carries the {@code ACC_MODULE} access flag) — a single such
	 * entry must not crash the background warmup job.
	 */
	private Optional<Class<?>> tryLoadIfAnnotated(Class<? extends Annotation> annotation, String className) {
		try {
			var clazz = loadWithoutInitializing(className, getClass());
			return clazz.isAnnotationPresent(annotation) ? Optional.of(clazz) : Optional.empty();
		} catch (ClassNotFoundException | LinkageError e) {
			log.debug("Skipping unloadable classpath entry '{}' during annotation scan", className, e);
			return Optional.empty();
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