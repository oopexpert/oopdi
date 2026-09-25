package de.oopexpert.oopdi.benchmark;

import java.util.concurrent.TimeUnit;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;

import de.oopexpert.oopdi.OOPDI;
import de.oopexpert.teststructure.ClassProxyReturnTarget;

/**
 * Baseline for proxy-class sharing: the first container for a bean class pays ByteBuddy
 * generation, every later container reuses the cached proxy class. Compares steady-state
 * container creation (shared class) against proxy issuance plus first real-object resolution.
 *
 * <p>Run manually, never part of {@code mvn test} (see surefire excludes in {@code pom.xml}):
 * <pre>
 * java -cp &lt;test-classpath&gt; org.openjdk.jmh.Main ProxyClassBenchmark
 * </pre>
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 2, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 3, time = 1, timeUnit = TimeUnit.SECONDS)
@Fork(1)
@State(Scope.Benchmark)
public class ProxyClassBenchmark {

	private OOPDI<ClassProxyReturnTarget> warmContainer;
	private ClassProxyReturnTarget warmProxy;

	@Setup
	public void setup() {
		// Warms the shared proxy-class cache so steady-state measurements exclude generation.
		warmContainer = new OOPDI<>(ClassProxyReturnTarget.class);
		warmProxy = warmContainer.getInstance(ClassProxyReturnTarget.class);
		warmProxy.add(1, 2);
	}

	@Benchmark
	public void newContainerWithSharedProxyClass(Blackhole blackhole) {
		OOPDI<ClassProxyReturnTarget> oopdi = new OOPDI<>(ClassProxyReturnTarget.class);
		blackhole.consume(oopdi.getInstance(ClassProxyReturnTarget.class));
	}

	@Benchmark
	public void firstMethodCallResolvesRealObject(Blackhole blackhole) {
		OOPDI<ClassProxyReturnTarget> oopdi = new OOPDI<>(ClassProxyReturnTarget.class);
		blackhole.consume(oopdi.getInstance(ClassProxyReturnTarget.class).add(4, 5));
	}

	@Benchmark
	public void cachedProxyCall(Blackhole blackhole) {
		blackhole.consume(warmProxy.add(4, 5));
	}
}
