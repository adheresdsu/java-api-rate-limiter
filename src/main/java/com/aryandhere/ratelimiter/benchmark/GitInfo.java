package com.aryandhere.ratelimiter.benchmark;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/** Best-effort lookup of the current Git commit, for report reproducibility. */
final class GitInfo {

    private static final long PROCESS_TIMEOUT_SECONDS = 5;

    private GitInfo() {
    }

    /** @return the short commit hash, or empty if Git is unavailable or this isn't a Git checkout */
    static Optional<String> currentShortCommit() {
        try {
            Process process = new ProcessBuilder("git", "rev-parse", "--short", "HEAD")
                    .redirectErrorStream(false)
                    .start();
            String output;
            try (BufferedReader reader =
                    new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                output = reader.readLine();
            }
            boolean finished = process.waitFor(PROCESS_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                return Optional.empty();
            }
            if (process.exitValue() != 0 || output == null || output.isBlank()) {
                return Optional.empty();
            }
            return Optional.of(output.trim());
        } catch (IOException e) {
            return Optional.empty();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Optional.empty();
        }
    }
}
