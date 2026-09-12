package com.aryandhere.ratelimiter.benchmark;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class ReportPathsTest {

    @Test
    void csvPathEndsWithCsvExtensionInsideGivenDirectory() {
        Path dir = Path.of("reports", "benchmarks");
        Path path = ReportPaths.csvPath(dir, Instant.parse("2026-01-02T03:04:05Z"));

        assertEquals(dir, path.getParent());
        assertTrue(path.getFileName().toString().endsWith(".csv"));
    }

    @Test
    void markdownPathEndsWithMdExtensionInsideGivenDirectory() {
        Path dir = Path.of("reports", "benchmarks");
        Path path = ReportPaths.markdownPath(dir, Instant.parse("2026-01-02T03:04:05Z"));

        assertEquals(dir, path.getParent());
        assertTrue(path.getFileName().toString().endsWith(".md"));
    }

    @Test
    void differentTimestampsProduceDifferentFilenames() {
        Path dir = Path.of("reports", "benchmarks");
        Path first = ReportPaths.csvPath(dir, Instant.parse("2026-01-02T03:04:05Z"));
        Path second = ReportPaths.csvPath(dir, Instant.parse("2026-01-02T03:05:06Z"));

        assertNotEquals(first, second);
    }

    @Test
    void filenameContainsNoPersonalInformation() {
        Path path = ReportPaths.csvPath(Path.of("reports", "benchmarks"), Instant.parse("2026-01-02T03:04:05Z"));

        String userHome = System.getProperty("user.home");
        String userName = System.getProperty("user.name");
        assertFalse(path.toString().contains(userHome));
        assertFalse(path.toString().contains(userName));
    }
}
