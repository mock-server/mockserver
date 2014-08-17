package org.mockserver.url;

/**
 * @author jamesdbloom
 */
public class URLParser {

    private static final String schemeRegex = "(https?:)?//.*";
    private static final String schemeHostAndPortRegex = "(https?:)?//([A-z0-9-_.:]*@)?[A-z0-9-_.]*(:[0-9]*)?";

    public static boolean isFullUrl(String uri) {
        return uri != null && uri.matches(schemeRegex);
    }

    public static String returnPath(String path) {
        if (URLParser.isFullUrl(path)) {
            return path.replaceAll(schemeHostAndPortRegex, "");
        } else {
            return path;
        }
    }
}
