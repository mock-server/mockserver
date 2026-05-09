package org.mockserver.model;

import java.net.URI;
import java.util.Objects;

public class ProxyPassMapping {
    private String pathPrefix;
    private String targetUri;
    private boolean preserveHost;

    private transient String targetHost;
    private transient int targetPort;
    private transient String targetPath;
    private transient boolean targetSecure;

    public static ProxyPassMapping proxyPass(String pathPrefix, String targetUri) {
        return new ProxyPassMapping().withPathPrefix(pathPrefix).withTargetUri(targetUri);
    }

    public String getPathPrefix() {
        return pathPrefix;
    }

    public ProxyPassMapping withPathPrefix(String pathPrefix) {
        this.pathPrefix = pathPrefix;
        return this;
    }

    public String getTargetUri() {
        return targetUri;
    }

    public ProxyPassMapping withTargetUri(String targetUri) {
        this.targetUri = targetUri;
        parseTargetUri();
        return this;
    }

    public boolean isPreserveHost() {
        return preserveHost;
    }

    public ProxyPassMapping withPreserveHost(boolean preserveHost) {
        this.preserveHost = preserveHost;
        return this;
    }

    public String getTargetHost() {
        if (targetHost == null) {
            parseTargetUri();
        }
        return targetHost;
    }

    public int getTargetPort() {
        if (targetHost == null) {
            parseTargetUri();
        }
        return targetPort;
    }

    public String getTargetPath() {
        if (targetHost == null) {
            parseTargetUri();
        }
        return targetPath;
    }

    public boolean isTargetSecure() {
        if (targetHost == null) {
            parseTargetUri();
        }
        return targetSecure;
    }

    private void parseTargetUri() {
        if (targetUri != null) {
            URI uri = URI.create(targetUri);
            targetHost = uri.getHost();
            targetSecure = "https".equalsIgnoreCase(uri.getScheme());
            targetPort = uri.getPort() > 0 ? uri.getPort() : (targetSecure ? 443 : 80);
            targetPath = uri.getPath() != null ? uri.getPath() : "";
            while (targetPath.endsWith("/")) {
                targetPath = targetPath.substring(0, targetPath.length() - 1);
            }
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        ProxyPassMapping that = (ProxyPassMapping) o;
        return preserveHost == that.preserveHost
            && Objects.equals(pathPrefix, that.pathPrefix)
            && Objects.equals(targetUri, that.targetUri);
    }

    @Override
    public int hashCode() {
        return Objects.hash(pathPrefix, targetUri, preserveHost);
    }
}
