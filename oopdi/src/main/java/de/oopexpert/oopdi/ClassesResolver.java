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

	public Class<?> determineRelevantClass(Class<?> c) {
		Set<Class<?>> derivedClasses = getDerivedClasses(c, c.getPackageName());
		
		Set<Class<?>> allClasses = new HashSet<>();
		allClasses.addAll(derivedClasses);
		allClasses.add(c);
		
		Set<Class<?>> filteredClasses = nonAbstractClasses(withProfiles(injectables(allClasses)));
		
		if (filteredClasses.isEmpty()) {
			throw new NoClassesLeftAfterFiltering("No classes left after profile/non-abstract filtering (" + c.getName() + ").");
		}
		
		if (filteredClasses.size() > 1) {
			throw new MultipleClassesLeftAfterFiltering("Multiple concrete classes left after profile/non-abstract filtering class hierarchy of class '" + c.getName() + "'. Cannot decide object instantiation.");
		}
		
		return filteredClasses.iterator().next();
	}

	private Set<Class<?>> getDerivedClasses(Class<?> parentClass, String packageName) {
	    
		Set<Class<?>> classes = new HashSet<>();
	    
	    for (String classpathEntry : getClassPathEntries()) {
	        try {
				classes.addAll(getDerivedClassesInPath(parentClass, packageName, classpathEntry));
			} catch (ClassNotFoundException | IOException | URISyntaxException e) {
				throw new RuntimeException(e);
			}
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

	private static Set<Class<?>> injectables(Set<Class<?>> classes) {
		Set<Class<?>> filteredClasses = new HashSet<>();
		for (Class<?> clazz : classes) {
			if (clazz.isAnnotationPresent(Injectable.class)) {
				filteredClasses.add(clazz);
			}
		}
		return filteredClasses;
	}

	private <X> Set<Class<?>> withProfiles(Set<Class<?>> classes) {
		Set<Class<?>> filteredClasses = new HashSet<>();
		for (Class<?> derivedClass : classes) {
			filteredClasses.addAll(filterClassesByInjectableProfile(derivedClass));
		}
		return filteredClasses;
	}

	private Set<Class<?>> filterClassesByInjectableProfile(Class<?> derivedClass) {
		Set<Class<?>> filteredClasses = new HashSet<>();
		Injectable injectable = derivedClass.getAnnotation(Injectable.class);
		String[] derivedClassProfiles = injectable.profiles();
		if (derivedClassProfiles.length == 0) {
			filteredClasses.add(derivedClass);
		} else {
			filteredClasses.addAll(filterClassesByProfileMatch(derivedClass, derivedClassProfiles));
		}
		return filteredClasses;
	}

	private Set<Class<?>> filterClassesByProfileMatch(Class<?> derivedClass, String[] derivedClassProfiles) {
		
	    Set<Class<?>> filteredClasses = new HashSet<>();
	    
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

	private static Set<Class<?>> nonAbstractClasses(Set<Class<?>> classes) {
		Set<Class<?>> filteredClasses = new HashSet<>();
		for (Class<?> clazz : classes) {
			if (!Modifier.isAbstract(clazz.getModifiers())) {
				filteredClasses.add(clazz);
			}
		}
		return filteredClasses;
	}

	private void registerComponents(Class<?> clazz) throws ClassNotFoundException, IOException, URISyntaxException, InstantiationException, IllegalAccessException, InvocationTargetException, NoSuchMethodException, IllegalArgumentException {
		if (!this.componentSets.containsKey(clazz)) {
			Set<Class<?>> componentClasses = new HashSet<>();
			this.componentSets.put(clazz, componentClasses);
			Set<Class<?>> classes = nonAbstractClasses(withProfiles(injectables(getDerivedClasses(clazz, clazz.getPackageName()))));
			for (Class<?> c : classes) {
				componentClasses.add(c);
			}
		}
	}

	public Set<Class<?>> getSet(Class<?> hint) throws ClassNotFoundException, InstantiationException, IllegalAccessException, InvocationTargetException, NoSuchMethodException, IllegalArgumentException, IOException, URISyntaxException {
		if (componentSets.get(hint) == null) {
			registerComponents(hint);
		}
		return componentSets.get(hint);
	}

}