package com.aryandhere.ratelimiter.config;

/** The rate limiting algorithms selectable from the command line. */
public enum AlgorithmType {
    FIXED_WINDOW("fixed-window"),
    SLIDING_WINDOW("sliding-window"),
    TOKEN_BUCKET("token-bucket");

    private final String cliName;

    AlgorithmType(String cliName) {
        this.cliName = cliName;
    }

    public String cliName() {
        return cliName;
    }

    /**
     * @param cliName one of {@code fixed-window}, {@code sliding-window}, {@code token-bucket}
     * @return the matching algorithm
     * @throws IllegalArgumentException if {@code cliName} does not match a known algorithm
     */
    public static AlgorithmType fromCliName(String cliName) {
        for (AlgorithmType type : values()) {
            if (type.cliName.equals(cliName)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown algorithm '" + cliName
                + "'. Supported values: fixed-window, sliding-window, token-bucket");
    }
}
