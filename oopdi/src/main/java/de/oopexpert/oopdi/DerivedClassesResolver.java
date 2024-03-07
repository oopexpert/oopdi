package de.oopexpert.oopdi;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

public class DerivedClassesResolver {

	private static final char PACKAGE_SEPARATOR = '.';
	private static final char PATH_SEPARATOR = '/';
	private static final String SUFFIX_CLASS = ".class";
    private static final String SUFFIX_JAR = ".jar";

	public Set<Class<?>> getDerivedClasses(Class<?> parentClass, String packageName) throws ClassNotFoundException, IOException, URISyntaxException {
	    
		Set<Class<?>> classes = new HashSet<>();
	    
	    for (String classpathEntry : getClassPathEntries()) {
	        classes.addAll(getDerivedClassesInPath(parentClass, packageName, classpathEntry));
	    }
	    
	    return classes;
	}

	private Set<Class<?>> getDerivedClassesInPath(Class<?> parentClass, String packageName, String classpathEntry) throws ClassNotFoundException, IOException, URISyntaxException {
		Set<Class<?>> classes = new HashSet<>();
		if (classpathEntry.endsWith(SUFFIX_JAR)) {
			classes.addAll(getDerivedClassesFromJar(parentClass, toPathName(packageName), classpathEntry));
		} else {
		    File directory = new File(classpathEntry, toPathName(packageName));
		    if (directory.exists()) {
		        classes.addAll(getDerivedClassesFromDirectory(parentClass, packageName, directory));
		    }
		}
		return classes;
	}

	private String[] getClassPathEntries() {
		return System.getProperty("java.class.path").split(File.pathSeparator);
	}

	private Set<Class<?>> getDerivedClassesFromJar(Class<?> parentClass, String path, String classpathEntry) throws ClassNotFoundException, IOException {
	    Set<Class<?>> classes = new HashSet<>();
		try (JarFile jarFile = new JarFile(classpathEntry)) {
		    Enumeration<JarEntry> entries = jarFile.entries();
		    while (entries.hasMoreElements()) {
		        Class<?> classInJarEntry = findAssignableClassInJarEntry(parentClass, path, entries.nextElement());
		        if (classInJarEntry != null) {
		        	classes.add(classInJarEntry);
		        }
		    }
		}
		return classes;
	}


	private Class<?> findAssignableClassInJarEntry(Class<?> parentClass, String path, JarEntry jarEntry) throws ClassNotFoundException {
        String jarEntryName = jarEntry.getName();
		if (jarEntryName.startsWith(path) && jarEntryName.endsWith(SUFFIX_CLASS)) {
		    String className = toClassName(jarEntryName);
		    Class<?> clazz = Class.forName(className);
		    if (parentClass.isAssignableFrom(clazz)) {
		        return clazz;
		    }
		}
		return null;
	}

	private String toClassName(String pathName) {
		return pathName.substring(0, pathName.length() - SUFFIX_CLASS.length()).replace(PATH_SEPARATOR, PACKAGE_SEPARATOR);
	}

	private String toPathName(String packageName) {
		return packageName.replace(PACKAGE_SEPARATOR, PATH_SEPARATOR);
	}

	private Set<Class<?>> getDerivedClassesFromDirectory(Class<?> parentClass, String packageName, File directory) throws ClassNotFoundException, IOException, URISyntaxException {
        Set<Class<?>> classes = new HashSet<>();
		for (File file : directory.listFiles()) {
			classes.addAll(getDerivedClassesFromDirectoryOrFile(parentClass, packageName, file));
		}
		return classes;
	}

	private Set<Class<?>> getDerivedClassesFromDirectoryOrFile(Class<?> parentClass, String packageName, File file) throws ClassNotFoundException, IOException, URISyntaxException {
        Set<Class<?>> classes = new HashSet<>();
		if (file.isDirectory()) {
		    classes.addAll(getDerivedClasses(parentClass, packageName + PACKAGE_SEPARATOR + file.getName()));
		} else if (file.getName().endsWith(SUFFIX_CLASS)) {
		    String className = packageName + PACKAGE_SEPARATOR + file.getName().substring(0, file.getName().length() - SUFFIX_CLASS.length());
		    Class<?> clazz = Class.forName(className);
		    if (parentClass.isAssignableFrom(clazz) && !parentClass.equals(clazz)) {
		        classes.add(clazz);
		    }
		}
		return classes;
	}

}
