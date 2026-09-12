package com.aryandhere.ratelimiter.benchmark;

import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/**
 * Builds timestamped report file paths under {@code reports/benchmarks/}, so
 * running the benchmark again never silently overwrites a previous report.
 * Filenames contain only a UTC timestamp — no username, hostname, or path
 * information that would identify the machine that generated them.
 */
final class ReportPaths {

    private static final DateTimeFormatter FILENAME_TIMESTAMP =
            DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").withZone(ZoneOffset.UTC);

    private ReportPaths() {
    }

    static Path csvPath(Path reportsDirectory, Instant generatedAt) {
        return reportsDirectory.resolve("benchmark-" + FILENAME_TIMESTAMP.format(generatedAt) + ".csv");
    }

    static Path markdownPath(Path reportsDirectory, Instant generatedAt) {
        return reportsDirectory.resolve("benchmark-" + FILENAME_TIMESTAMP.format(generatedAt) + ".md");
    }
}
