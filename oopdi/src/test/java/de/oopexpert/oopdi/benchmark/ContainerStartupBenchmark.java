package de.oopexpert.oopdi.benchmark;

import java.util.concurrent.TimeUnit;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;

import de.oopexpert.oopdi.OOPDI;
import de.oopexpert.teststructure.ClassB1;

/**
 * Baseline for cold container startup with default settings (no warmup): container creation
 * plus first proxy issuance plus first real-object resolution. Single-shot by design — steady
 * state is covered by {@link ProxyClassBenchmark}. Run manually, never part of
 * {@code mvn test}.
 */
@BenchmarkMode(Mode.SingleShotTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Warmup(iterations = 1)
@Measurement(iterations = 3)
@Fork(1)
@State(Scope.Benchmark)
public class ContainerStartupBenchmark {

	@Benchmark
	public void coldStartupFirstResolution(Blackhole blackhole) {
		OOPDI<ClassB1> oopdi = new OOPDI<>(ClassB1.class);
		blackhole.consume(oopdi.getInstance(ClassB1.class).getI());
	}
}
