package org.mockserver.cors;

import com.google.common.net.InternetDomainName;
import org.mockserver.configuration.Configuration;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static org.apache.commons.lang3.StringUtils.isBlank;

/**
 * Decides which browser origins may use MockServer's control plane: the CORS headers on its own
 * API and dashboard responses, and the dashboard / callback WebSocket upgrades. Mocked and proxied
 * responses are not governed by this class (see {@code corsAllowOrigin}).
 * <p>
 * An http or https origin whose host and port match the request's {@code Host} header (a page
 * MockServer served itself, such as the dashboard) is allowed only on a host that DNS rebinding
 * cannot take over: {@code localhost}, an IP literal, a single-label name (a Docker, Compose or
 * Kubernetes short service name), a name ending in {@code .svc}, {@code .svc.cluster.local} or
 * {@code .internal} (reserved for private use), or a name ending in one of the
 * {@code controlPlaneTrustedHostSuffixes}. This holds whatever {@code controlPlaneCorsAllowOrigin} is: a
 * same-host page on any other name, such as a public domain, must be listed in it.
 * <p>
 * With {@code controlPlaneCorsAllowOrigin} blank (the default) an http or https origin on
 * {@code localhost}, {@code 127.0.0.1} or {@code [::1]} (any port) is also allowed; otherwise the
 * property is a comma-separated allow-list, where {@code *} allows any origin.
 * {@code Origin: null} is never allowed by default, because sandboxed iframes and {@code file://}
 * pages send it.
 */
public final class CORSOriginPolicy {

    private static final String ANY_ORIGIN = "*";
    private static final String[] BUILT_IN_TRUSTED_HOST_SUFFIXES = {"svc", "svc.cluster.local", "internal"};

    private CORSOriginPolicy() {
    }

    /**
     * {@link #isControlPlaneOriginAllowed(String, String, String, String)} for a server's
     * configuration and every {@code Host} value the request carried.
     */
    public static boolean isControlPlaneOriginAllowed(Configuration configuration, String origin, List<String> hostHeaders) {
        return isControlPlaneOriginAllowed(
            configuration != null ? configuration.controlPlaneCorsAllowOrigin() : null,
            configuration != null ? configuration.controlPlaneTrustedHostSuffixes() : null,
            origin,
            consistentHost(hostHeaders)
        );
    }

    /**
     * Whether a request carrying this {@code Origin} may use the control plane. A missing
     * {@code Origin} (a non-browser client) is always allowed.
     *
     * @param controlPlaneCorsAllowOrigin     the configured allow-list; blank selects the loopback default
     * @param controlPlaneTrustedHostSuffixes extra comma-separated host suffixes on which a same-host origin is allowed
     * @param hostHeader                      the request's single {@code Host}, see {@link #consistentHost(List)}
     */
    public static boolean isControlPlaneOriginAllowed(String controlPlaneCorsAllowOrigin, String controlPlaneTrustedHostSuffixes, String origin, String hostHeader) {
        if (origin == null || origin.isEmpty()) {
            return true;
        }
        WebOrigin webOrigin = WebOrigin.parse(origin);
        if (webOrigin != null && webOrigin.isSameHostAs(hostHeader) && isRebindingSafeHost(webOrigin.authority.host, controlPlaneTrustedHostSuffixes)) {
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
     * The request's {@code Host}, or null when it has none or carries several that disagree (for
     * example an HTTP/2 {@code :authority} and a different {@code host} header).
     */
    public static String consistentHost(List<String> hostHeaders) {
        if (hostHeaders == null || hostHeaders.isEmpty() || hostHeaders.get(0) == null) {
            return null;
        }
        String first = hostHeaders.get(0).trim();
        for (String host : hostHeaders) {
            if (host == null || !first.equalsIgnoreCase(host.trim())) {
                return null;
            }
        }
        return first;
    }

    /**
     * Whether a browser {@code Origin} passes the default rule (blank
     * {@code controlPlaneCorsAllowOrigin} and {@code controlPlaneTrustedHostSuffixes}).
     */
    static boolean isDefaultAllowedOrigin(String origin, String hostHeader) {
        return WebOrigin.parse(origin) != null && isControlPlaneOriginAllowed("", "", origin, hostHeader);
    }

    /**
     * Whether public DNS cannot point this host at an attacker's server, so a page that is
     * same-origin with MockServer on it was served by MockServer or the operator's own network.
     * Takes the host lower-cased, as {@link HostAndPort} parsed it.
     */
    static boolean isRebindingSafeHost(String host, String trustedHostSuffixes) {
        if (host.startsWith("[")) {
            String literal = host.substring(1, host.length() - 1);
            return literal.indexOf(':') >= 0
                && literal.chars().allMatch(c -> (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') || c == ':' || c == '.');
        }
        String name = withoutTrailingDot(host);
        if (!isAsciiHostName(name)) {
            return false;
        }
        if ("localhost".equals(name) || name.indexOf('.') < 0 || isIpv4Literal(name)) {
            return true;
        }
        for (String suffix : BUILT_IN_TRUSTED_HOST_SUFFIXES) {
            if (name.endsWith("." + suffix)) {
                return true;
            }
        }
        for (String suffix : ConfiguredSuffixes.of(trustedHostSuffixes).usable) {
            if (name.endsWith("." + suffix)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Rejected {@code controlPlaneTrustedHostSuffixes} entries, one message each: a public suffix
     * ({@code com}, {@code co.uk}, {@code github.io}), under which anyone can register a name. A
     * rejected entry trusts nothing. Empty if none is rejected.
     */
    public static List<String> trustedHostSuffixErrors(String trustedHostSuffixes) {
        return ConfiguredSuffixes.of(trustedHostSuffixes).errors;
    }

    /**
     * Problems with the other {@code controlPlaneTrustedHostSuffixes} entries, one message each: an
     * entry that is not a host-name suffix (and so trusts nothing), or a single-label suffix that is
     * not a public suffix (which trusts a whole top-level domain). Empty if every entry is usable.
     */
    public static List<String> trustedHostSuffixWarnings(String trustedHostSuffixes) {
        return ConfiguredSuffixes.of(trustedHostSuffixes).warnings;
    }

    private static boolean isPublicSuffix(String suffix) {
        if ("home.arpa".equals(suffix) || suffix.endsWith(".home.arpa")) {
            return false;
        }
        for (String builtIn : BUILT_IN_TRUSTED_HOST_SUFFIXES) {
            if (suffix.equals(builtIn) || suffix.endsWith("." + builtIn)) {
                return false;
            }
        }
        return InternetDomainName.isValid(suffix) && InternetDomainName.from(suffix).isPublicSuffix();
    }

    /**
     * A parsed {@code controlPlaneTrustedHostSuffixes} value. The last one parsed is kept, because
     * the value is fixed at startup and is read on every control-plane request.
     */
    private static final class ConfiguredSuffixes {
        private static volatile ConfiguredSuffixes last = new ConfiguredSuffixes("");

        private final String raw;
        private final List<String> usable = new ArrayList<>();
        private final List<String> errors = new ArrayList<>();
        private final List<String> warnings = new ArrayList<>();

        private ConfiguredSuffixes(String raw) {
            this.raw = raw;
            for (String configured : raw.split(",")) {
                String entry = configured.trim();
                if (entry.isEmpty()) {
                    continue;
                }
                String suffix = normaliseSuffix(entry);
                if (suffix == null) {
                    warnings.add("controlPlaneTrustedHostSuffixes entry \"" + entry + "\" is not a host-name suffix (such as corp.example or *.corp.example) and is ignored");
                } else if (isPublicSuffix(suffix)) {
                    errors.add("controlPlaneTrustedHostSuffixes entry \"" + entry + "\" is REJECTED because it is a public suffix, under which anyone can register a name and use the control plane through DNS rebinding; list your own private suffix instead");
                } else {
                    usable.add(suffix);
                    if (suffix.indexOf('.') < 0) {
                        warnings.add("controlPlaneTrustedHostSuffixes entry \"" + entry + "\" is a whole top-level domain, so any site under it can use the control plane through DNS rebinding");
                    }
                }
            }
        }

        static ConfiguredSuffixes of(String raw) {
            String value = raw == null ? "" : raw;
            ConfiguredSuffixes parsed = last;
            if (!parsed.raw.equals(value)) {
                parsed = new ConfiguredSuffixes(value);
                last = parsed;
            }
            return parsed;
        }
    }

    /**
     * {@code corp.example}, {@code .corp.example}, {@code *.corp.example} and a trailing dot all
     * name the same suffix; null for anything that is not an ASCII host name.
     */
    private static String normaliseSuffix(String configured) {
        String suffix = configured.trim();
        if (suffix.chars().anyMatch(c -> c >= 0x7f)) {
            return null;
        }
        suffix = withoutTrailingDot(suffix.toLowerCase(Locale.ROOT));
        if (suffix.startsWith("*.")) {
            suffix = suffix.substring(2);
        } else if (suffix.startsWith(".")) {
            suffix = suffix.substring(1);
        }
        return isAsciiHostName(suffix) ? suffix : null;
    }

    private static String withoutTrailingDot(String name) {
        return name.endsWith(".") ? name.substring(0, name.length() - 1) : name;
    }

    private static boolean isAsciiHostName(String name) {
        if (name.isEmpty() || name.startsWith(".") || name.endsWith(".") || name.contains("..")) {
            return false;
        }
        return name.chars().allMatch(c -> (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '-' || c == '_' || c == '.');
    }

    private static boolean isIpv4Literal(String name) {
        String[] octets = name.split("\\.", -1);
        if (octets.length != 4) {
            return false;
        }
        for (String octet : octets) {
            if (octet.isEmpty() || octet.length() > 3 || !octet.chars().allMatch(c -> c >= '0' && c <= '9') || Integer.parseInt(octet) > 255) {
                return false;
            }
        }
        return true;
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
         * including user info, a path, a query, a fragment, whitespace, or a non-ASCII character.
         */
        static HostAndPort parse(String authority, int defaultPort) {
            if (authority.isEmpty() || authority.chars().anyMatch(c -> c <= ' ' || c >= 0x7f || c == '/' || c == '?' || c == '#' || c == '@' || c == '\\' || c == ',')) {
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
