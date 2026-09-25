package de.oopexpert.oopdi.benchmark;

import java.util.concurrent.TimeUnit;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;

import de.oopexpert.oopdi.OOPDI;
import de.oopexpert.oopdi.metadata.MetadataMode;
import de.oopexpert.oopdi.metadata.WarmupStatus;
import de.oopexpert.teststructure.ClassB1;

/**
 * Baseline for background metadata warmup: wall time until the scan reports {@code READY}, and
 * first-resolution latency once the cache is warm. The mode property is set per trial and
 * always cleared afterwards so forked JVMs never leak configuration into each other. Run
 * manually, never part of {@code mvn test}.
 */
@BenchmarkMode(Mode.SingleShotTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Warmup(iterations = 1)
@Measurement(iterations = 3)
@Fork(1)
@State(Scope.Benchmark)
public class WarmupBenchmark {

	@Setup(Level.Trial)
	public void enableWarmup() {
		System.setProperty(MetadataMode.SYSTEM_PROPERTY, MetadataMode.WARMUP_FAIL_FAST.name());
	}

	@TearDown(Level.Trial)
	public void clearWarmup() {
		System.clearProperty(MetadataMode.SYSTEM_PROPERTY);
	}

	@Benchmark
	public void timeUntilWarmupReady(Blackhole blackhole) throws InterruptedException {
		OOPDI<ClassB1> oopdi = new OOPDI<>(ClassB1.class);
		long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30);
		while (oopdi.getWarmupStatus() == WarmupStatus.RUNNING
				|| oopdi.getWarmupStatus() == WarmupStatus.NOT_STARTED) {
			if (System.nanoTime() > deadline) {
				throw new IllegalStateException("Warmup did not finish in time");
			}
			Thread.sleep(5);
		}
		blackhole.consume(oopdi.getWarmupStatus());
	}

	@Benchmark
	public void firstResolutionAfterWarmup(Blackhole blackhole) throws InterruptedException {
		OOPDI<ClassB1> oopdi = new OOPDI<>(ClassB1.class);
		long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30);
		while (oopdi.getWarmupStatus() != WarmupStatus.READY) {
			if (System.nanoTime() > deadline) {
				throw new IllegalStateException("Warmup did not finish in time");
			}
			Thread.sleep(5);
		}
		blackhole.consume(oopdi.getInstance(ClassB1.class).getI());
	}
}
