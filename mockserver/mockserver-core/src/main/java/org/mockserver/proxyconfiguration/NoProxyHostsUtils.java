package org.mockserver.proxyconfiguration;

import org.apache.commons.lang3.StringUtils;

import java.util.Locale;
import java.util.stream.Stream;

public class NoProxyHostsUtils {

    public static boolean isHostOnNoProxyList(String host, String noProxyHosts) {
        if (StringUtils.isBlank(noProxyHosts) || StringUtils.isBlank(host)) {
            return false;
        }
        String hostOnly = extractHost(host);
        String hostLower = hostOnly.toLowerCase(Locale.ROOT);
        return Stream.of(noProxyHosts.split(","))
            .map(String::trim)
            .filter(StringUtils::isNotBlank)
            .anyMatch(pattern -> {
                String patternLower = pattern.toLowerCase(Locale.ROOT);
                if (patternLower.startsWith("*.")) {
                    String suffix = patternLower.substring(1);
                    return hostLower.endsWith(suffix)
                        || hostLower.equals(patternLower.substring(2));
                }
                return hostLower.equals(patternLower);
            });
    }

    static String extractHost(String hostHeader) {
        if (StringUtils.isBlank(hostHeader)) {
            return hostHeader;
        }
        String trimmed = hostHeader.trim();
        if (trimmed.startsWith("[")) {
            int closeBracket = trimmed.indexOf(']');
            if (closeBracket > 0) {
                return trimmed.substring(1, closeBracket);
            }
            return trimmed.substring(1);
        }
        long colonCount = trimmed.chars().filter(c -> c == ':').count();
        if (colonCount > 1) {
            return trimmed;
        }
        int lastColon = trimmed.lastIndexOf(':');
        if (lastColon <= 0) {
            return trimmed;
        }
        String afterColon = trimmed.substring(lastColon + 1);
        try {
            Integer.parseInt(afterColon);
            return trimmed.substring(0, lastColon);
        } catch (NumberFormatException e) {
            return trimmed;
        }
    }

}
