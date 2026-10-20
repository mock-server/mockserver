package org.mockserver.cors;

import java.util.Locale;

import static org.apache.commons.lang3.StringUtils.isBlank;

/**
 * Decides which browser origins may use MockServer's control plane: the CORS headers on its own
 * API and dashboard responses, and the dashboard / callback WebSocket upgrades. Mocked and proxied
 * responses are not governed by this class (see {@code corsAllowOrigin}).
 * <p>
 * An http or https origin whose host and port match the request's {@code Host} header (a page
 * MockServer served itself, such as the dashboard) is always allowed. With
 * {@code controlPlaneCorsAllowOrigin} blank (the default) an http or https origin on
 * {@code localhost}, {@code 127.0.0.1} or {@code [::1]} (any port) is also allowed; otherwise the
 * property is a comma-separated allow-list, where {@code *} allows any origin.
 * {@code Origin: null} is never allowed by default, because sandboxed iframes and {@code file://}
 * pages send it.
 * <p>
 * This does not defend against DNS rebinding: a rebound page is same-origin with the
 * {@code Host} it sends. Control-plane authentication is the defence for that.
 */
public final class CORSOriginPolicy {

    private static final String ANY_ORIGIN = "*";

    private CORSOriginPolicy() {
    }

    /**
     * Whether a request carrying this {@code Origin} may use the control plane. A missing
     * {@code Origin} (a non-browser client) is always allowed.
     *
     * @param controlPlaneCorsAllowOrigin the configured allow-list; blank selects the loopback default
     */
    public static boolean isControlPlaneOriginAllowed(String controlPlaneCorsAllowOrigin, String origin, String hostHeader) {
        if (origin == null || origin.isEmpty()) {
            return true;
        }
        WebOrigin webOrigin = WebOrigin.parse(origin);
        if (webOrigin != null && webOrigin.isSameHostAs(hostHeader)) {
            return true;
        }
        if (isBlank(controlPlaneCorsAllowOrigin)) {
            return webOrigin != null && webOrigin.isLoopback();
        }
        String trimmedOrigin = origin.trim();
        for (String allowed : controlPlaneCorsAllowOrigin.split(",")) {
            String entry = allowed.trim();
            if (ANY_ORIGIN.equals(entry) || entry.equalsIgnoreCase(trimmedOrigin)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Whether a browser {@code Origin} passes the default rule (blank
     * {@code controlPlaneCorsAllowOrigin}): an http or https origin on a loopback host, or on the
     * request's own host and port.
     */
    static boolean isDefaultAllowedOrigin(String origin, String hostHeader) {
        WebOrigin webOrigin = WebOrigin.parse(origin);
        return webOrigin != null && (webOrigin.isLoopback() || webOrigin.isSameHostAs(hostHeader));
    }

    private static final class WebOrigin {
        private final HostAndPort authority;
        private final int defaultPort;

        private WebOrigin(HostAndPort authority, int defaultPort) {
            this.authority = authority;
            this.defaultPort = defaultPort;
        }

        static WebOrigin parse(String origin) {
            if (isBlank(origin)) {
                return null;
            }
            String trimmed = origin.trim();
            int schemeEnd = trimmed.indexOf("://");
            if (schemeEnd <= 0) {
                return null;
            }
            String scheme = trimmed.substring(0, schemeEnd).toLowerCase(Locale.ROOT);
            int defaultPort;
            if ("http".equals(scheme)) {
                defaultPort = 80;
            } else if ("https".equals(scheme)) {
                defaultPort = 443;
            } else {
                return null;
            }
            HostAndPort authority = HostAndPort.parse(trimmed.substring(schemeEnd + 3), defaultPort);
            return authority != null ? new WebOrigin(authority, defaultPort) : null;
        }

        boolean isLoopback() {
            String host = authority.host;
            return "localhost".equals(host) || "127.0.0.1".equals(host) || "[::1]".equals(host);
        }

        boolean isSameHostAs(String hostHeader) {
            if (isBlank(hostHeader)) {
                return false;
            }
            HostAndPort host = HostAndPort.parse(hostHeader.trim(), defaultPort);
            return host != null && authority.host.equals(host.host) && authority.port == host.port;
        }
    }

    private static final class HostAndPort {
        private final String host;
        private final int port;

        private HostAndPort(String host, int port) {
            this.host = host;
            this.port = port;
        }

        /**
         * Parses {@code host[:port]} or {@code [ipv6][:port]}; returns null for anything else,
         * including user info, a path, a query, a fragment, or whitespace.
         */
        static HostAndPort parse(String authority, int defaultPort) {
            if (authority.isEmpty() || authority.chars().anyMatch(c -> c <= ' ' || c == '/' || c == '?' || c == '#' || c == '@' || c == '\\' || c == ',')) {
                return null;
            }
            String host;
            String portPart;
            if (authority.startsWith("[")) {
                int close = authority.indexOf(']');
                if (close < 0) {
                    return null;
                }
                host = authority.substring(0, close + 1);
                String rest = authority.substring(close + 1);
                if (rest.isEmpty()) {
                    portPart = null;
                } else if (rest.startsWith(":")) {
                    portPart = rest.substring(1);
                } else {
                    return null;
                }
            } else {
                int colon = authority.indexOf(':');
                if (colon < 0) {
                    host = authority;
                    portPart = null;
                } else if (authority.indexOf(':', colon + 1) >= 0) {
                    return null;
                } else {
                    host = authority.substring(0, colon);
                    portPart = authority.substring(colon + 1);
                }
            }
            if (host.isEmpty() || "[]".equals(host)) {
                return null;
            }
            int port = defaultPort;
            if (portPart != null) {
                if (portPart.isEmpty() || portPart.length() > 5 || !portPart.chars().allMatch(c -> c >= '0' && c <= '9')) {
                    return null;
                }
                port = Integer.parseInt(portPart);
                if (port < 1 || port > 65535) {
                    return null;
                }
            }
            return new HostAndPort(host.toLowerCase(Locale.ROOT), port);
        }
    }
}
