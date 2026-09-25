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
import de.oopexpert.teststructure.ClassB1;
import de.oopexpert.teststructure.ClassC;
import de.oopexpert.teststructure.ClassD;
import de.oopexpert.teststructure.ClassProxyReturnTarget;

/**
 * Baseline for per-scope proxy dispatch cost after warmup: GLOBAL (cached real object), THREAD
 * (per-thread state), LOCAL (fresh instance per call) and REQUEST (chain setup + teardown).
 * Single-threaded by design — this measures dispatch overhead, not contention. Run manually,
 * never part of {@code mvn test}.
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 2, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 3, time = 1, timeUnit = TimeUnit.SECONDS)
@Fork(1)
@State(Scope.Benchmark)
public class ScopeDispatchBenchmark {

	private ClassProxyReturnTarget globalProxy;
	private ClassC threadProxy;
	private ClassB1 localProxy;
	private ClassD requestProxy;

	@Setup
	public void setup() {
		globalProxy = new OOPDI<>(ClassProxyReturnTarget.class).getInstance(ClassProxyReturnTarget.class);
		globalProxy.add(1, 2);
		threadProxy = new OOPDI<>(ClassC.class).getInstance(ClassC.class);
		threadProxy.setI(1);
		localProxy = new OOPDI<>(ClassB1.class).getInstance(ClassB1.class);
		requestProxy = new OOPDI<>(ClassD.class).getInstance(ClassD.class);
	}

	@Benchmark
	public void globalCachedCall(Blackhole blackhole) {
		blackhole.consume(globalProxy.add(4, 5));
	}

	@Benchmark
	public void threadScopedCall(Blackhole blackhole) {
		blackhole.consume(threadProxy.executeFunction2(41));
	}

	@Benchmark
	public void localFreshInstanceCall(Blackhole blackhole) {
		localProxy.setI(7);
		blackhole.consume(localProxy.getI());
	}

	@Benchmark
	public void requestChainCall(Blackhole blackhole) {
		requestProxy.execute(d -> d.setI(3));
		blackhole.consume(requestProxy.getI());
	}
}
