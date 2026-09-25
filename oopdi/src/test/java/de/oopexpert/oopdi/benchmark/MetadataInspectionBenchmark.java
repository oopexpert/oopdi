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

import de.oopexpert.oopdi.metadata.MetadataMode;
import de.oopexpert.oopdi.metadata.MetadataRepository;
import de.oopexpert.teststructure.ClassB1;

/**
 * Baseline for metadata caching: reflective inspection per access ({@code DISABLED}) versus a
 * pre-populated cache ({@code METADATA_ONLY}). Run manually, never part of {@code mvn test}.
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 2, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 3, time = 1, timeUnit = TimeUnit.SECONDS)
@Fork(1)
@State(Scope.Benchmark)
public class MetadataInspectionBenchmark {

	private MetadataRepository cachedRepository;

	@Setup
	public void setup() {
		cachedRepository = new MetadataRepository(MetadataMode.METADATA_ONLY);
		cachedRepository.getMetadata(ClassB1.class);
	}

	@Benchmark
	public void inspectUncached(Blackhole blackhole) {
		blackhole.consume(new MetadataRepository(MetadataMode.DISABLED).getMetadata(ClassB1.class));
	}

	@Benchmark
	public void lookupCached(Blackhole blackhole) {
		blackhole.consume(cachedRepository.getMetadata(ClassB1.class));
	}
}
