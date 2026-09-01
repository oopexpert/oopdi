# OOPDI Developer Guide

Diese Doku richtet sich an Entwickler, die OOPDI in eigenen Projekten einsetzen oder am Framework selbst
weiterarbeiten. Alle Beispiele sind aus den echten Testfällen und Testfixtures des Projekts abgeleitet
(`oopdi/src/test/java/de/oopexpert/oopdi/**` und `.../teststructure/**`), damit sie garantiert kompilieren
und garantiert das aktuelle Verhalten widerspiegeln.

## Inhaltsverzeichnis

1. [Grundkonzept](#1-grundkonzept)
2. [Ein Bean registrieren: `@Injectable`](#2-ein-bean-registrieren-injectable)
3. [Bean auflösen: `OOPDI.getInstance`](#3-bean-auflösen-oopdigetinstance)
4. [Scopes](#4-scopes)
5. [Feld-Injection: `@InjectInstance`](#5-feld-injection-injectinstance)
6. [Mengen-Injection: `@InjectSet` und Profile](#6-mengen-injection-injectset-und-profile)
7. [Variablen-Injection: `@InjectVariable`](#7-variablen-injection-injectvariable)
8. [Lifecycle: `@PostConstruct` / `@PreDestroy`](#8-lifecycle-postconstruct--predestroy)
9. [Proxy-Verhalten](#9-proxy-verhalten)
10. [Nebenläufigkeit](#10-nebenläufigkeit)
11. [Fehlerbilder & Ausnahmen](#11-fehlerbilder--ausnahmen)
12. [Testen mit OOPDI](#12-testen-mit-oopdi)
13. [OOPDI als Dependency via GitHub Packages einbinden](#13-oopdi-als-dependency-via-github-packages-einbinden)

---

## 1. Grundkonzept

Jedes verwaltete Bean wird beim Erstellen in einen **Byte-Buddy-Subclass-Proxy** verpackt. Aufrufer
halten immer eine Referenz auf den Proxy, nie auf das reale Objekt. Der Proxy löst bei jedem
Methodenaufruf anhand des konfigurierten `Scope` die passende reale Instanz auf und delegiert an sie.

```java
OOPDI<ClassRoot> oopdi = new OOPDI<>(ClassRoot.class);
ClassRoot root = oopdi.getInstance(ClassRoot.class); // root ist ein Proxy, kein "nacktes" ClassRoot
```

Wichtige Klassen: `OOPDI` (Einstiegspunkt), `Context` (erzeugt/injiziert/verwaltet Beans),
`ProxyManager` (Byte-Buddy-Proxys, REQUEST-`ThreadLocal`), `ScopedInstances`/`InstancesState`
(Instanz-Cache pro Scope), `ClassesResolver` (Klassenpfad-Scan, Profilfilterung).

## 2. Ein Bean registrieren: `@Injectable`

```java
@Injectable
public class ClassA {
    // ...
}
```

`@Injectable` ist **Pflicht** für jede Klasse, die das Framework instanziieren soll. Ohne die Annotation
wirft die Auflösung eine Exception. Wichtige Attribute:

| Attribut | Standard | Bedeutung |
|----------|----------|-----------|
| `scope`  | `GLOBAL` | siehe [Scopes](#4-scopes) |
| `immediate` | `false` | Bean wird sofort beim Container-Start erzeugt statt lazy |
| `profiles` | `{}` | Klasse ist nur aktiv, wenn eines der Profile aktiv ist |

```java
@Injectable(profiles = {"profile1"})
public class ClassB2 extends ClassB {
}
```

Konstruktor-Regeln: **genau ein Konstruktor** pro Klasse (Constructor-Injection über Parameter),
sonst wirft die Registrierung `MultipleConstructors`. Verwaltete Klassen dürfen **nicht `final`** sein,
da Byte Buddy eine Subklasse erzeugen muss.

## 3. Bean auflösen: `OOPDI.getInstance`

```java
OOPDI<ClassRoot> oopdi = new OOPDI<>(ClassRoot.class);
ClassA instance = oopdi.getInstance(ClassA.class);
```

Ein `OOPDI`-Container kapselt genau einen `Context`. Für **Profile** wird der Konstruktor mit
Varargs verwendet:

```java
OOPDI<ClassRoot> oopdi = new OOPDI<>(ClassRoot.class, "profile1");
```

`OOPDI`-Container sind vollständig voneinander isoliert – auch `GLOBAL`-Singletons werden nie
zwischen zwei Containern geteilt:

```java
OOPDI<ClassRoot> oopdiOne = new OOPDI<>(ClassRoot.class);
OOPDI<ClassRoot> oopdiTwo = new OOPDI<>(ClassRoot.class);

ClassA one = oopdiOne.getInstance(ClassA.class);
ClassA two = oopdiTwo.getInstance(ClassA.class);

one.setI(123);

assertEquals(123, one.getI());     // eigener State
assertNotEquals(123, two.getI());  // kein geteilter GLOBAL-State
```
(`TestScopeBehavior.testGlobalScopeIsolatedAcrossDifferentContainers`)

## 4. Scopes

| Scope    | Lebensdauer | `immediate` erlaubt? |
|----------|-------------|-----------------------|
| `GLOBAL`  | Ein Objekt pro Container, über alle Threads geteilt | Ja |
| `THREAD`  | Ein Objekt pro Thread pro Container | Nein |
| `LOCAL`   | Neues reales Objekt bei **jedem** Proxy-Methodenaufruf | Nein |
| `REQUEST` | Ein Objekt pro äußerster Aufrufkette auf einem Thread | Nein |

### GLOBAL

```java
@Injectable
public class ClassA { /* scope = GLOBAL per Default */ }
```

```java
ClassA instance = oopdi.getInstance(ClassA.class);
instance.setI(3);
assertEquals(3, instance.getI());

Thread thread = new Thread(() -> {
    ClassA instanceInThread = oopdi.getInstance(ClassA.class);
    assertEquals(3, instanceInThread.getI()); // gleicher State, threadübergreifend
});
```
(`TestScopeBehavior.testGlobalScope`)

### THREAD

```java
@Injectable(scope = Scope.THREAD)
public class ClassC { /* ... */ }
```

```java
ClassC instance = oopdi.getInstance(ClassC.class);
instance.setI(3);

Thread thread = new Thread(() -> {
    ClassC instanceInThread = oopdi.getInstance(ClassC.class);
    assertNotEquals(3, instanceInThread.getI()); // eigener State pro Thread
});
```
(`TestScopeBehavior.testThreadScope`)

### LOCAL

```java
@Injectable(scope = Scope.LOCAL)
public class ClassB1 extends ClassB { /* ... */ }
```

```java
ClassB classBinstance = oopdi.getInstance(ClassB1.class);
classBinstance.setI(3);
Integer result = classBinstance.getI();

assertEquals(0, result); // jeder Proxy-Aufruf erzeugt ein frisches reales Objekt
```
(`TestScopeBehavior.testScopeLocal`) — `setI` und `getI` landen wegen `LOCAL` auf zwei
verschiedenen realen Instanzen.

### REQUEST

Ein REQUEST-Scope lebt vom ersten Proxy-Aufruf auf einem Thread bis die Aufruftiefe wieder auf 0
fällt (verschachtelte Aufrufe innerhalb derselben Kette teilen sich die Instanz):

```java
@Injectable(scope = Scope.REQUEST)
public class ClassRequestState {
    private int value;
    private final int id = INSTANCE_SEQUENCE.incrementAndGet();
    // getter/setter
}

@Injectable(scope = Scope.REQUEST)
public class ClassRequestReader {
    @InjectInstance
    private ClassRequestState state;

    public int readValue() { return state.getValue(); }
}

@Injectable
public class ClassRequestScenario {
    @InjectInstance private ClassRequestState state;
    @InjectInstance private ClassRequestReader reader;

    public Result execute(int value, boolean fail) {
        state.setValue(value);
        int readFromReader = reader.readValue(); // gleiche REQUEST-Instanz wie state!
        // ...
    }
}
```

```java
ClassRequestScenario.Result result = scenario.execute(77, false);

assertEquals(77, result.getReadFromReader());              // Wert kam über die geteilte Instanz an
assertEquals(result.getIdInScenario(), result.getIdInReader()); // gleiche REQUEST-State-Instanz
```
(`TestRequestAndConcurrency.testRequestScopeNestedCallsShareSameRequestScopedState`)

Wichtig: Wirft der äußere Aufruf eine Exception, wird der REQUEST-Scope trotzdem im `finally`
bereinigt, sodass der nächste Top-Level-Aufruf wieder eine frische Instanz bekommt
(`TestRequestAndConcurrency.testRequestScopeExceptionCleansContextForNextCall`). REQUEST-Scopes
sind außerdem strikt pro Thread isoliert
(`TestRequestAndConcurrency.testRequestScopeIsolatedAcrossThreads`).

### `immediate = true`

Nur für `GLOBAL` und `THREAD` sinnvoll (deterministisch initialisierbar). Für `LOCAL` und
`REQUEST` ist es eine Fehlkonfiguration, die erst beim ersten Proxy-Aufruf sichtbar wird:

```java
@Injectable(scope = Scope.LOCAL, immediate = true) // ungültig!
public class ClassImmediateLocalMisconfig { /* ... */ }
```

```java
OOPDI<ClassImmediateLocalMisconfig> oopdi = new OOPDI<>(ClassImmediateLocalMisconfig.class);
ClassImmediateLocalMisconfig instance = oopdi.getInstance(ClassImmediateLocalMisconfig.class);

RuntimeException ex = assertThrows(RuntimeException.class, instance::ping);
assertTrue(ex.getMessage().contains("Misconfiguration"));
```
(`TestScopeBehavior.testImmediateLocalScopeMisconfigurationThrows`, analog für `THREAD`/`REQUEST`)

## 5. Feld-Injection: `@InjectInstance`

```java
@Injectable(scope = Scope.LOCAL)
public class ClassRoot {

    @InjectInstance
    private ClassA classA;

    @InjectInstance
    private ClassD classD;
}
```

Konstruktor-Injection funktioniert genauso über den (einzigen) Konstruktor-Parameter:

```java
public class ClassPreDestroyOrderDependent {
    private final ClassPreDestroyOrderDependency dependency;

    public ClassPreDestroyOrderDependent(ClassPreDestroyOrderDependency dependency) {
        this.dependency = dependency;
    }
}
```

`@PostConstruct`-Methoden können zusätzlich Parameter injiziert bekommen — inklusive des
`OOPDI`-Containers selbst:

```java
@Injectable
public class ClassPostConstructWithParameters {

    @PostConstruct
    public void init(ClassA classA, OOPDI<?> oopdi) {
        // classA und oopdi werden automatisch aufgelöst
    }
}
```
(`TestLifecycleHooks.testPostConstructReceivesBeanAndOopdiParameters`)

## 6. Mengen-Injection: `@InjectSet` und Profile

`@InjectSet` injiziert alle aktiven konkreten Implementierungen/Subklassen einer Basisklasse.
Wegen Typlöschung ist `hint` Pflicht:

```java
@Injectable(scope = Scope.LOCAL)
public class ClassRoot {

    @InjectSet(hint = ClassB.class)
    private Set<ClassB> classesB;
}
```

Ohne aktives Profil ist nur `ClassB1` (kein Profil) aktiv, `ClassB2` (`profiles = {"profile1"}`)
und `ClassB3` (kein `@Injectable`) werden gefiltert:

```java
OOPDI<ClassRoot> oopdi = new OOPDI<>(ClassRoot.class);
Set<ClassB> classesB = oopdi.getInstance(ClassRoot.class).getClassesB();
assertEquals(1, classesB.size());
```
(`TestResolverAndSet.testProxyConsistencyInSets`)

Mit aktivem Profil kommen zusätzliche Implementierungen hinzu:

```java
OOPDI<ClassRoot> oopdi = new OOPDI<>(ClassRoot.class, "profile1");
Set<ClassB> classesB = oopdi.getInstance(ClassRoot.class).getClassesB();

assertEquals(2, classesB.size()); // ClassB1 (Default) + ClassB2 (profile1)
```
(`TestResolverAndSet.testInjectSetIncludesProfileSpecificImplementationsWhenProfileIsActive`)

Gibt es keine passende Implementierung, wird ein **leeres** Set injiziert, nie `null`:

```java
Set<?> values = oopdi.getInstance(ClassSetEmptyRoot.class).getValues();
assertNotNull(values);
assertTrue(values.isEmpty());
```
(`TestResolverAndSet.testInjectSetReturnsEmptySetWhenNoInjectableImplementationExists`)

Der direkte Versuch, eine profil-gefilterte Klasse ohne aktives Profil aufzulösen, wirft
`NoClassesLeftAfterFiltering`:

```java
assertThrows(NoClassesLeftAfterFiltering.class,
    () -> oopdi.getInstance(ClassB2.class).getI());
```
(`TestResolverAndSet.testProfileFilteredClassCannotBeInstantiatedWhenInactive`)

**Hinweis:** Interface-zu-Implementierung-Bindung (à la Spring) wird bewusst **nicht** unterstützt —
nur der Classpath-Scan über `@Injectable`-Subklassen entscheidet, was aktiv ist.

## 7. Variablen-Injection: `@InjectVariable`

```java
@Injectable
public class ClassA {

    @InjectVariable(key = "dbUrl", source = VariableSource.SYSTEM)
    private String dbURL;

    @InjectVariable(key = "dbUsername", source = VariableSource.PARAMETER)
    private String dbUsername;

    @InjectVariable(key = "counter", source = VariableSource.PARAMETER)
    private int counter; // wird automatisch nach int geparst
}
```

`source = SYSTEM` liest Umgebungsvariablen, `source = PARAMETER` liest System-Properties
(`-Dkey=value`). Unterstützte Feldtypen für automatisches Parsen: `Integer`, `Long`, `Short`,
`Float`, `Double`, `Boolean`, `Byte`, `Character` (primitiv und geboxt); alles andere wird als
`String` zugewiesen.

### Fehlender Key ohne Default → Exception

```java
@InjectVariable(key = "definitelyNotSetKey_12345", source = VariableSource.PARAMETER)
private String missingValue;
```

```java
RuntimeException ex = assertThrows(RuntimeException.class, instance::getMissingValue);
assertTrue(ex.getMessage().contains("definitelyNotSetKey_12345")); // Key steht in der Meldung
```
(`TestVariableInjection.testInjectVariableMissingKeyThrowsDescriptiveError`)

### `optional = true` → `null` statt Exception

```java
@InjectVariable(key = "definitelyNotSetKey_optional", source = VariableSource.PARAMETER, optional = true)
private String optionalValue;
```
→ `instance.getOptionalValue()` liefert `null`.

### `defaultValue` — auch für primitive Felder geparst

```java
@InjectVariable(key = "definitelyNotSetKey_default", source = VariableSource.PARAMETER, defaultValue = "fallback")
private String defaultValue;

@InjectVariable(key = "definitelyNotSetKey_defaultInt", source = VariableSource.PARAMETER, defaultValue = "42")
private int defaultInt;
```
→ `getDefaultValue()` = `"fallback"`, `getDefaultInt()` = `42`.

**Achtung:** `defaultValue = ""` bedeutet „kein Default“ — ein leerer String kann nicht selbst als
Default-Wert dienen.

### Ungültiges Zahlenformat

```java
try (var ignored = TestSystemProperties.withProperties(Map.of("matrix.invalid.int", "notANumber"))) {
    RuntimeException ex = assertThrows(RuntimeException.class, instance::getInvalidInt);
    assertTrue(ex.getCause() instanceof NumberFormatException);
}
```
(`TestVariableInjection.testInjectVariableInvalidNumericFormatThrows`) — die
`NumberFormatException` bleibt als `cause` erhalten.

## 8. Lifecycle: `@PostConstruct` / `@PreDestroy`

Beide Annotationen erlauben **genau eine** Methode pro Klassenhierarchie und durchlaufen die
gesamte Superklassen-Kette:

```java
public abstract class ClassPostConstructBase {
    @PostConstruct
    protected void initBase() { /* ... */ }
}

public class ClassPostConstructChild extends ClassPostConstructBase {
    // erbt @PostConstruct von der abstrakten Basisklasse
}
```
→ `instance.isBaseInitialized()` ist `true`, obwohl `@PostConstruct` nur in der Basisklasse steht
(`TestLifecycleHooks.testPostConstructInSuperclassIsInvoked`).

`@PreDestroy` wird durch `OOPDI.shutdown()` ausgelöst:

```java
OOPDI<ClassWithPreDestroy> oopdi = new OOPDI<>(ClassWithPreDestroy.class);
ClassWithPreDestroy instance = oopdi.getInstance(ClassWithPreDestroy.class);
assertFalse(instance.isDestroyed());

oopdi.shutdown();

assertTrue(instance.isDestroyed());
```
(`TestLifecycleHooks.testPreDestroyIsInvokedOnShutdown`)

### Reihenfolge beim Shutdown: umgekehrte Erzeugungsreihenfolge

Abhängige Beans werden **vor** ihren Abhängigkeiten zerstört:

```java
@Injectable
public class ClassPreDestroyOrderDependent {
    private final ClassPreDestroyOrderDependency dependency;
    @InjectInstance private ClassPreDestroyOrderLog log;

    public ClassPreDestroyOrderDependent(ClassPreDestroyOrderDependency dependency) {
        this.dependency = dependency;
    }

    @PreDestroy
    public void cleanup() { log.record("dependent"); }
}
```

```java
oopdi.getInstance(ClassPreDestroyOrderDependent.class).ping(); // erzeugt dependent + dependency
ClassPreDestroyOrderLog log = oopdi.getInstance(ClassPreDestroyOrderLog.class);

oopdi.shutdown();

assertEquals(List.of("dependent", "dependency"), log.getOrder());
```
(`TestLifecycleHooks.testPreDestroyIsInvokedInReverseCreationOrder`)

`@PreDestroy`-Methoden dürfen **keine Parameter** haben.

## 9. Proxy-Verhalten

Rückgabewerte und Exceptions gehen unverändert durch den Proxy:

```java
ClassProxyReturnTarget target = oopdi.getInstance(ClassProxyReturnTarget.class);
assertEquals(9, target.add(4, 5));
```
(`TestProxyBehavior.testProxyMethodReturnValuePassesThrough`)

```java
InvocationTargetException ex = assertThrows(InvocationTargetException.class, target::fail);
assertTrue(ex.getCause() instanceof IllegalArgumentException);
assertEquals("boom", ex.getCause().getMessage());
```
(`TestProxyBehavior.testProxyMethodExceptionPreservesCause`) — Proxy-Aufrufe wickeln reflektierte
Methodenaufrufe, weshalb die ursprüngliche Exception als `cause` einer
`InvocationTargetException` erscheint.

### Self-Invocation umgeht den Proxy

Ruft ein Bean intern `this.methode()` auf, greift **kein** Proxy — Standardverhalten bei
Java-Subclass-Proxies (analog Spring/CDI). Das bedeutet: `LOCAL`s „frische Instanz pro Aufruf“ und
`REQUEST`s Aufruftiefen-Tracking greifen bei solchen Aufrufen **nicht**. Details siehe
[README.md](README.md), Abschnitt „Self-Invocation Bypasses the Proxy“.

## 10. Nebenläufigkeit

### GLOBAL-Scope: nur eine Instanz, auch bei paralleler Erstanfrage

```java
Thread thread1 = new Thread(() -> proxy.getCount());
Thread thread2 = new Thread(() -> proxy.getCount());
// ...
assertEquals(1, ClassGlobalRace.instanceCount.get());
```
(`TestRequestAndConcurrency.testGlobalScopeRaceConditionProducesDuplicateInstances`) — der
per-Klasse-Lock (`scopedMap.getLockFor(c)`) serialisiert konkurrierende Erzeuger derselben Bean.

### Unterschiedliche GLOBAL-Beans dürfen parallel initialisieren

```java
ConcurrentTestSupport.runTwoWorkers(beanA::touch, beanB::touch, 5, TimeUnit.SECONDS);
// ...
assertTrue(ClassParallelInitTracker.getMaxConcurrentInits() >= 2);
```
(`TestRequestAndConcurrency.testParallelInitializationForDifferentGlobalBeans`) — Locks sind pro
Klasse, nicht global; nur die Erzeugung **derselben** Bean wird serialisiert.

### REQUEST-Scope ist strikt pro Thread isoliert

```java
ConcurrentTestSupport.runTwoWorkers(
    () -> { var r = scenario.execute(111, false); idThreadOne.set(r.getIdInScenario()); },
    () -> { var r = scenario.execute(222, false); idThreadTwo.set(r.getIdInScenario()); },
    5, TimeUnit.SECONDS);

assertNotEquals(idThreadOne.get(), idThreadTwo.get());
```
(`TestRequestAndConcurrency.testRequestScopeIsolatedAcrossThreads`)

## 11. Fehlerbilder & Ausnahmen

| Situation | Ausnahme | Test |
|-----------|----------|------|
| Mehrere Konstruktoren in einer `@Injectable`-Klasse | `MultipleConstructors` | — |
| `@InjectVariable` ohne Wert, ohne `optional`/`defaultValue` | `RuntimeException` mit Key im Text | `testInjectVariableMissingKeyThrowsDescriptiveError` |
| Ungültiges Zahlenformat bei `@InjectVariable` | `RuntimeException` mit `NumberFormatException`-Cause | `testInjectVariableInvalidNumericFormatThrows` |
| `immediate = true` bei `LOCAL`/`THREAD`/`REQUEST` (außer THREAD selbst erlaubt) | `RuntimeException` „Misconfiguration ...“ | `testImmediate*ScopeMisconfigurationThrows` |
| Profil-gefilterte Klasse ohne aktives Profil auflösen | `NoClassesLeftAfterFiltering` | `testProfileFilteredClassCannotBeInstantiatedWhenInactive` |
| Exception in proxied Methode | `InvocationTargetException` mit Original als `cause` | `testProxyMethodExceptionPreservesCause` |

## 12. Testen mit OOPDI

Für Tests, die System-Properties temporär setzen müssen (z. B. `@InjectVariable`-Matrix-Tests),
steht ein Try-with-Resources-Helfer bereit:

```java
try (TestSystemProperties.Scope ignored = TestSystemProperties.withProperties(Map.of(
        "matrix.long", "9000000000"))) {

    OOPDI<ClassVariableMatrix> oopdi = new OOPDI<>(ClassVariableMatrix.class);
    ClassVariableMatrix instance = oopdi.getInstance(ClassVariableMatrix.class);

    assertEquals(9000000000L, instance.getLongValue());
} // Properties werden beim Schließen automatisch zurückgesetzt
```

Für zwei parallele Worker-Threads mit Timeout gibt es `ConcurrentTestSupport.runTwoWorkers(...)`
(siehe Beispiele in Abschnitt [10](#10-nebenläufigkeit)).

Build & Tests lokal ausführen:

```powershell
$env:JAVA_HOME = "C:\Daten\Programmierung\environments\jdk-21.0.5+11"
$env:PATH = "$env:JAVA_HOME\bin;C:\Daten\Programmierung\environments\apache-maven-3.9.15\bin;$env:PATH"
cd oopdi
mvn test
```

Testklassen liegen unter `oopdi/src/test/java/de/oopexpert/oopdi/` (ein Feature pro Klasse, z. B.
`TestScopeBehavior`, `TestLifecycleHooks`), Fixture-Klassen unter
`oopdi/src/test/java/de/oopexpert/teststructure/`.

## 13. OOPDI als Dependency via GitHub Packages einbinden

Jedes Release wird zusätzlich zum GitHub Release auch nach [GitHub Packages](https://github.com/oopexpert/oopdi/packages)
(Maven-Registry) deployed, unter der unveränderten Koordinate `de.oopexpert.oopdi:oopdi-core`.

GitHub Packages erlaubt auch bei öffentlichen Repositories **keinen anonymen Lesezugriff** — jedes
konsumierende Projekt braucht einen GitHub Personal Access Token (Classic) mit mindestens dem Scope
`read:packages`.

In der `pom.xml` des konsumierenden Projekts das Repository eintragen:

```xml
<repositories>
    <repository>
        <id>github</id>
        <url>https://maven.pkg.github.com/oopexpert/oopdi</url>
    </repository>
</repositories>

<dependencies>
    <dependency>
        <groupId>de.oopexpert.oopdi</groupId>
        <artifactId>oopdi-core</artifactId>
        <version>0.1.0</version>
    </dependency>
</dependencies>
```

Und in der lokalen oder CI-`settings.xml` (`~/.m2/settings.xml`) den Server-Eintrag mit den Zugangsdaten
für die Server-`id` `github` hinterlegen:

```xml
<settings>
    <servers>
        <server>
            <id>github</id>
            <username>DEIN_GITHUB_BENUTZERNAME</username>
            <password>DEIN_PAT_MIT_READ_PACKAGES_SCOPE</password>
        </server>
    </servers>
</settings>
```

In GitHub-Actions-Workflows lässt sich das über `actions/setup-java`'s `server-id`/`server-username`/
`server-password`-Inputs erledigen, ohne `settings.xml` manuell anzulegen (siehe
`.github/workflows/release-version.yml` für ein Beispiel des Publish-Schritts).

