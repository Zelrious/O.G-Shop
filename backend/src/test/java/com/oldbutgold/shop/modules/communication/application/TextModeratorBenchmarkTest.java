package com.oldbutgold.shop.modules.communication.application;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Isolated CPU latency & throughput micro-benchmark for {@link TextModerator}.
 *
 * <p>OPT-IN EXECUTION ONLY:
 * Excluded from the default Maven Surefire unit-test suite to avoid wall-clock noise
 * caused by background CPU contention during full regression runs.
 * Run explicitly via Maven profile {@code -Pperformance-benchmark}.
 */
@Tag("benchmark")
class TextModeratorBenchmarkTest {

    private TextModerator moderator;

    @BeforeEach
    void setUp() {
        moderator = new TextModerator();
        moderator.clearAllBuffers();
    }

    @Test
    @DisplayName("Latency and Throughput Benchmark: Verify ultra-fast processing across message lengths")
    void latencyAndThroughputBenchmark() {
        String shortClean = "Chào shop, sản phẩm này còn hàng không shop ơi?";
        String shortViolation = "Alo shop, gọi mình số 0901234567 trao đổi nhé";
        String mediumComposite = "Chào bạn, add da lô số 0987-654-321 hoặc kết bạn fb.com/shopdoCu nhé, có cccd 079199012345, cọc trước 100k.";

        StringBuilder longBuilder = new StringBuilder();
        while (longBuilder.length() < 1850) {
            longBuilder.append("Bàn dài 120cm, nặng 15kg, fix 200k. Liên hệ 0901234567 hoặc chotot[.]vn, cccd 079199012345 nha. ");
        }
        String longComposite = longBuilder.toString();

        // 1. Warm up JIT compiler
        for (int i = 0; i < 500; i++) {
            moderator.moderate(1L, 100L, shortClean);
            moderator.moderate(1L, 100L, shortViolation);
            moderator.moderate(1L, 100L, mediumComposite);
            moderator.moderate(1L, 100L, longComposite);
        }

        // 2. Measure Short Clean
        int shortIterations = 2000;
        long t0 = System.nanoTime();
        for (int i = 0; i < shortIterations; i++) {
            moderator.moderate(1L, 100L, shortClean);
        }
        long t1 = System.nanoTime();
        double avgShortCleanMicros = (double) (t1 - t0) / shortIterations / 1000.0;

        // 3. Measure Short Violation
        t0 = System.nanoTime();
        for (int i = 0; i < shortIterations; i++) {
            moderator.moderate(1L, 100L, shortViolation);
        }
        t1 = System.nanoTime();
        double avgShortViolationMicros = (double) (t1 - t0) / shortIterations / 1000.0;

        // 4. Measure Medium Composite
        t0 = System.nanoTime();
        for (int i = 0; i < shortIterations; i++) {
            moderator.moderate(1L, 100L, mediumComposite);
        }
        t1 = System.nanoTime();
        double avgMediumCompositeMicros = (double) (t1 - t0) / shortIterations / 1000.0;

        // 5. Measure Long Composite (~1900 chars)
        int longIterations = 500;
        t0 = System.nanoTime();
        for (int i = 0; i < longIterations; i++) {
            moderator.moderate(1L, 100L, longComposite);
        }
        t1 = System.nanoTime();
        double avgLongCompositeMicros = (double) (t1 - t0) / longIterations / 1000.0;

        double opsPerSec = (double) longIterations / ((double) (t1 - t0) / 1_000_000_000.0);

        System.out.println("======================================================================");
        System.out.println("TEXT MODERATOR LATENCY BENCHMARK RESULTS (CPU LAPTOP):");
        System.out.printf(" - Short Clean (50 chars):        %.3f µs (%.5f ms)%n", avgShortCleanMicros, avgShortCleanMicros / 1000.0);
        System.out.printf(" - Short Violation (50 chars):    %.3f µs (%.5f ms)%n", avgShortViolationMicros, avgShortViolationMicros / 1000.0);
        System.out.printf(" - Medium Composite (115 chars):  %.3f µs (%.5f ms)%n", avgMediumCompositeMicros, avgMediumCompositeMicros / 1000.0);
        System.out.printf(" - Long Composite (~1900 chars):  %.3f µs (%.5f ms)%n", avgLongCompositeMicros, avgLongCompositeMicros / 1000.0);
        System.out.printf(" - Throughput (Long Composite):   %,.0f msgs/sec%n", opsPerSec);
        System.out.println("======================================================================");

        // Verification: Average latency must be < 10.0 ms even for 1900-char composite with multiple leaks
        assertThat(avgLongCompositeMicros / 1000.0).isLessThan(10.0);
        assertThat(avgShortCleanMicros / 1000.0).isLessThan(1.0);
    }
}

