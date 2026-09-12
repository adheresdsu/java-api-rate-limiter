package com.aryandhere.ratelimiter.simulation;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Set;

/**
 * Restricts the traffic simulator to local targets.
 *
 * <p>This tool is for load-testing a Gatekeeper instance running on the same
 * machine, not for sending traffic to arbitrary hosts. Only plain {@code
 * http} URLs whose host is {@code localhost}, {@code 127.0.0.1}, or {@code
 * ::1} are accepted; there is no override to target a remote host in this
 * milestone.
 */
final class LocalTargetValidator {

    private static final Set<String> ALLOWED_HOSTS = Set.of("localhost", "127.0.0.1", "::1", "[::1]", "0:0:0:0:0:0:0:1");

    private LocalTargetValidator() {
    }

    static URI requireLocalHttpBaseUri(String rawUri) throws SimulatorConfigException {
        URI uri;
        try {
            uri = new URI(rawUri);
        } catch (URISyntaxException e) {
            throw new SimulatorConfigException("--base-url is not a valid URI: " + e.getMessage());
        }

        if (!"http".equals(uri.getScheme())) {
            throw new SimulatorConfigException(
                    "--base-url must use the http scheme, got '" + uri.getScheme() + "'");
        }
        if (uri.getUserInfo() != null) {
            throw new SimulatorConfigException("--base-url must not contain user information");
        }
        if (uri.getRawQuery() != null) {
            throw new SimulatorConfigException("--base-url must not contain a query string");
        }
        if (uri.getRawFragment() != null) {
            throw new SimulatorConfigException("--base-url must not contain a fragment");
        }
        String path = uri.getRawPath();
        if (path != null && !path.isEmpty() && !path.equals("/")) {
            throw new SimulatorConfigException(
                    "--base-url must not contain a path; the simulator always targets /api/resource");
        }
        String host = uri.getHost();
        if (host == null || !ALLOWED_HOSTS.contains(host)) {
            throw new SimulatorConfigException(
                    "--base-url host must be localhost, 127.0.0.1, or ::1, got '" + host
                            + "'. Remote targets are not supported by this tool.");
        }
        if (uri.getPort() < -1 || uri.getPort() > 65535) {
            throw new SimulatorConfigException("--base-url has an invalid port");
        }
        return uri;
    }
}
