package de.oopexpert.oopdi;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Modifier;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import de.oopexpert.oopdi.annotation.Injectable;
import de.oopexpert.oopdi.exception.MultipleClassesLeftAfterFiltering;
import de.oopexpert.oopdi.exception.NoClassesLeftAfterFiltering;

public class ClassesResolver {

	private static final char PACKAGE_SEPARATOR = '.';
	private static final char PATH_SEPARATOR = '/';
	private static final String SUFFIX_CLASS = ".class";
    private static final String SUFFIX_JAR = ".jar";
    
	private String[] profiles;

	private HashMap<Class<?>, Set<Class<?>>> componentSets;
    
    public ClassesResolver(String... profiles) {
    	this.profiles = profiles;
    	this.componentSets = new HashMap<>();
    }

	public <T> Class<T> determineRelevantClass(Class<T> c) {
		
		Set<Class<T>> filteredClasses = filter(getAllClassesInHierarchy(c));
		
		if (filteredClasses.isEmpty()) {
			throw new NoClassesLeftAfterFiltering("No classes left after profile/non-abstract filtering (" + c.getName() + ").");
		}
		
		if (filteredClasses.size() > 1) {
			throw new MultipleClassesLeftAfterFiltering("Multiple concrete classes left after profile/non-abstract filtering class hierarchy of class '" + c.getName() + "'. Cannot decide object instantiation.");
		}
		
		return filteredClasses.iterator().next();
	}

	private <T> Set<Class<T>> getAllClassesInHierarchy(Class<T> c) {
		Set<Class<T>> derivedClasses = getDerivedClasses(c, c.getPackageName());
		Set<Class<T>> allClasses = new HashSet<>();
		allClasses.addAll(derivedClasses);
		allClasses.add(c);
		return allClasses;
	}

	private <T> Set<Class<T>> filter(Set<Class<T>> allClasses) {
		return nonAbstractClasses(withProfiles(injectables(allClasses)));
	}

	private <T> Set<Class<T>> getDerivedClasses(Class<T> parentClass, String packageName) {
	    
        try {
			
        	Set<Class<T>> classes = new HashSet<>();
		    
		    for (String classpathEntry : getClassPathEntries()) {
				classes.addAll(getDerivedClassesInClasspath(parentClass, packageName, classpathEntry));
		    }
		    
		    return classes;
		    
		} catch (ClassNotFoundException | IOException e) {
			throw new RuntimeException(e);
		}
	}
	
	private <T> Set<Class<T>> getDerivedClassesInClasspath(Class<T> parentClass, String packageName, String classpathEntry) throws ClassNotFoundException, IOException {
	    Set<Class<T>> classes = new HashSet<>();
	    
	    if (classpathEntry.endsWith(SUFFIX_JAR)) {
	        classes.addAll(getDerivedClassesFromJar(parentClass, toPathName(packageName), classpathEntry));
	    } else {
		    File entry = new File(classpathEntry);
	    	if (entry.isFile() && classpathEntry.endsWith(SUFFIX_CLASS)) {
		    	classes.addAll(getDerivedClassesFromDirectoryOrClassFile(parentClass, packageName, entry));
		    } else {
		        File directory = new File(classpathEntry, toPathName(packageName));
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
	    Set<Class<T>> classes = new HashSet<>();
		try (JarFile jarFile = new JarFile(classpathEntry)) {
		    Enumeration<JarEntry> entries = jarFile.entries();
		    while (entries.hasMoreElements()) {
		        Class<T> classInJarEntry = findAssignableClassInJarEntry(parentClass, path, entries.nextElement());
		        if (classInJarEntry != null) {
		        	classes.add(classInJarEntry);
		        }
		    }
		}
		return classes;
	}

	private <T> Class<T> findAssignableClassInJarEntry(Class<T> parentClass, String path, JarEntry jarEntry) throws ClassNotFoundException {
        String jarEntryName = jarEntry.getName();
		if (jarEntryName.startsWith(path) && jarEntryName.endsWith(SUFFIX_CLASS)) {
		    String className = toClassName(jarEntryName);
		    Class<?> clazz = Class.forName(className);
		    if (parentClass.isAssignableFrom(clazz) && !parentClass.equals(clazz)) {
		        return (Class<T>) clazz;
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

	private <T> Set<Class<T>> getDerivedClassesFromDirectory(Class<T> parentClass, String packageName, File directory) throws ClassNotFoundException {
        Set<Class<T>> classes = new HashSet<>();
		for (File file : directory.listFiles()) {
			classes.addAll(getDerivedClassesFromDirectoryOrClassFile(parentClass, packageName, file));
		}
		return classes;
	}

	private <T> Set<Class<T>> getDerivedClassesFromDirectoryOrClassFile(Class<T> parentClass, String packageName, File file) throws ClassNotFoundException {
        Set<Class<T>> classes = new HashSet<>();
		if (file.isDirectory()) {
		    classes.addAll(getDerivedClasses(parentClass, packageName + PACKAGE_SEPARATOR + file.getName()));
		} else {
			Class<T> clazz = findAssignableClassInFile(parentClass, packageName, file);
			if (clazz != null) {
				classes.add(clazz);
			}
		}
		return classes;
	}

	private <T> Class<T> findAssignableClassInFile(Class<T> parentClass, String packageName, File file) throws ClassNotFoundException {
		if (file.getName().endsWith(SUFFIX_CLASS)) {
		    String className = toClassName(packageName, file);
		    Class<?> clazz = Class.forName(className);
		    if (parentClass.isAssignableFrom(clazz) && !parentClass.equals(clazz)) {
		        return (Class<T>) clazz;
		    }
		}
		return null;
	}

	private String toClassName(String packageName, File file) {
		return packageName + PACKAGE_SEPARATOR + file.getName().substring(0, file.getName().length() - SUFFIX_CLASS.length());
	}

	private static <T> Set<Class<T>> injectables(Set<Class<T>> classes) {
	    return classes.stream()
	            .filter(OOPDIReflection::isInjectable)
	            .collect(Collectors.toSet());
	}

	private <T> Set<Class<T>> withProfiles(Set<Class<T>> classes) {
	    return classes.stream()
	            .flatMap(this::filterClassesByInjectableProfile)
	            .collect(Collectors.toSet());
	}

	private <T> Stream<Class<T>> filterClassesByInjectableProfile(Class<T> derivedClass) {
		Set<Class<T>> filteredClasses = new HashSet<>();
		Injectable injectable = derivedClass.getAnnotation(Injectable.class);
		String[] derivedClassProfiles = injectable.profiles();
		if (derivedClassProfiles.length == 0) {
			filteredClasses.add(derivedClass);
		} else {
			filteredClasses.addAll(filterClassesByProfileMatch(derivedClass, derivedClassProfiles));
		}
		return filteredClasses.stream();
	}

	private <T> Set<Class<T>> filterClassesByProfileMatch(Class<T> derivedClass, String[] derivedClassProfiles) {
		
	    Set<Class<T>> filteredClasses = new HashSet<>();
	    
	    for (String activeProfile : this.profiles) {
	        if (isProfileMatch(derivedClassProfiles, activeProfile)) {
	            filteredClasses.add(derivedClass);
	        }
	    }

	    return filteredClasses;
	}
	
	private boolean isProfileMatch(String[] derivedClassProfiles, String activeProfile) {
	    return Arrays.stream(derivedClassProfiles).anyMatch(derivedProfile -> derivedProfile.equals(activeProfile));
	}

	private static <T> Set<Class<T>> nonAbstractClasses(Set<Class<T>> classes) {
	    return classes.stream()
	                  .filter(clazz -> !Modifier.isAbstract(clazz.getModifiers()))
	                  .collect(Collectors.toSet());
	}

	private <T> void registerComponents(Class<T> clazz) throws ClassNotFoundException, IOException, URISyntaxException, InstantiationException, IllegalAccessException, InvocationTargetException, NoSuchMethodException, IllegalArgumentException {
		if (!this.componentSets.containsKey(clazz)) {
			Set<Class<?>> componentClasses = new HashSet<>();
			this.componentSets.put(clazz, componentClasses);
			Set<Class<T>> classes = filter(getDerivedClasses(clazz, clazz.getPackageName()));
			for (Class<T> c : classes) {
				componentClasses.add(c);
			}
		}
	}

	public <T> Set<Class<?>> getSet(Class<T> hint) throws ClassNotFoundException, InstantiationException, IllegalAccessException, InvocationTargetException, NoSuchMethodException, IllegalArgumentException, IOException, URISyntaxException {
		if (componentSets.get(hint) == null) {
			registerComponents(hint);
		}
		return componentSets.get(hint);
	}

}