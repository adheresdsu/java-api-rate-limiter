package com.aryandhere.ratelimiter.benchmark;

/**
 * Non-identifying environment facts recorded alongside benchmark results, so
 * a reader knows what machine class produced them. Deliberately excludes
 * anything personal: no username, home directory, hostname, or IP address.
 */
public record EnvironmentInfo(String javaVersion, String osName, String osVersion, String osArch, int processors) {

    public static EnvironmentInfo capture() {
        return new EnvironmentInfo(
                System.getProperty("java.version", "unknown"),
                System.getProperty("os.name", "unknown"),
                System.getProperty("os.version", "unknown"),
                System.getProperty("os.arch", "unknown"),
                Runtime.getRuntime().availableProcessors());
    }
}
