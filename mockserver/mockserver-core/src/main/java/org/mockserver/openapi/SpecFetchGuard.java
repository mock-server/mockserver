package org.mockserver.openapi;

import org.mockserver.proxyconfiguration.ForwardTargetBlockedException;
import org.mockserver.proxyconfiguration.ForwardTargetHostPolicy;
import org.mockserver.proxyconfiguration.InetAddressValidator;

import javax.annotation.Nullable;
import java.io.IOException;
import java.net.Proxy;
import java.net.ProxySelector;
import java.net.SocketAddress;
import java.net.URI;
import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;

import static org.apache.commons.lang3.StringUtils.isBlank;

/**
 * Applies {@code forwardProxyBlockPrivateNetworks}, {@code forwardProxyAllowedHosts} and {@code forwardProxyDeniedHosts}
 * to every connection swagger-parser opens while it reads a spec:
 * the spec URL, each remote {@code $ref} and each redirect, for OpenAPI 3 and Swagger 2 alike. swagger-parser fetches
 * with {@code HttpURLConnection}, which asks the default {@link ProxySelector} where to connect before each of these
 * connections, redirects it follows itself included, so the check is made there: only on a thread parsing a spec
 * with a check on. Every other connection is passed to the selector this one wraps, unchanged. A spec or {@code $ref}
 * read from a file or the classpath opens no network connection, so it is never checked.
 * <p>
 * A name is checked by a lookup where MockServer runs, as a spec URL's host is checked elsewhere; the connection then
 * looks it up again, which the JVM's address cache normally answers with the address checked.
 * <p>
 * The wrapper is JVM-wide and never removed. Each class loader that loads this class (a redeployed WAR) wraps the
 * selector again and keeps the previous wrapper, and its class loader, reachable. Installing reads and then replaces
 * the default selector, which is not atomic: a selector another thread sets in between is wrapped over and lost, and
 * one set during a parse leaves that parse unchecked until the next parse wraps again. swagger-parser's own
 * {@code ParseOptions.setSafelyResolveURL} is not used instead: it checks neither the spec URL, nor the redirects
 * {@code HttpURLConnection} follows, nor a Swagger 2 spec's {@code $ref}s, and refuses relative file {@code $ref}s.
 */
final class SpecFetchGuard extends ProxySelector {

    private static final ThreadLocal<Parse> PARSING = new ThreadLocal<>();

    private final ProxySelector wrapped;

    private SpecFetchGuard(@Nullable ProxySelector wrapped) {
        this.wrapped = wrapped;
    }

    private static final class Parse {
        private final boolean blockPrivateNetworks;
        @Nullable
        private final ForwardTargetHostPolicy hostPolicy;
        private ForwardTargetBlockedException refusal;

        private Parse(boolean blockPrivateNetworks, @Nullable ForwardTargetHostPolicy hostPolicy) {
            this.blockPrivateNetworks = blockPrivateNetworks;
            this.hostPolicy = hostPolicy;
        }
    }

    /**
     * Runs {@code parse} with every connection it opens on this thread checked, and refuses the spec if any was
     * blocked, whatever the parser made of the failed fetch.
     *
     * @param blockPrivateNetworks whether forwardProxyBlockPrivateNetworks was on when the parse began; a later change
     *                             to the configuration does not change it for this parse, whose result is cached under it
     * @param hostPolicy           forwardProxyAllowedHosts and forwardProxyDeniedHosts when the parse began, or null
     * @throws IllegalArgumentException naming the blocked target, when a fetch was blocked
     */
    static <T> T whileParsing(boolean blockPrivateNetworks, @Nullable ForwardTargetHostPolicy hostPolicy, Supplier<T> parse) {
        if (!blockPrivateNetworks && hostPolicy == null) {
            return parse.get();
        }
        install();
        Parse outer = PARSING.get();
        Parse current = new Parse(blockPrivateNetworks, hostPolicy);
        PARSING.set(current);
        T parsed;
        try {
            parsed = parse.get();
        } catch (RuntimeException failure) {
            throw current.refusal != null ? refused(current.refusal) : failure;
        } finally {
            if (outer != null) {
                PARSING.set(outer);
            } else {
                PARSING.remove();
            }
        }
        if (current.refusal != null) {
            throw refused(current.refusal);
        }
        return parsed;
    }

    private static IllegalArgumentException refused(ForwardTargetBlockedException refusal) {
        return new IllegalArgumentException(OpenAPIParser.OPEN_API_LOAD_ERROR + ", " + refusal.getMessage(), refusal);
    }

    /**
     * Wraps the default selector, again if something has replaced it since: a selector that is not this one would
     * let a parse fetch unchecked.
     */
    private static synchronized void install() {
        ProxySelector current = ProxySelector.getDefault();
        if (!(current instanceof SpecFetchGuard)) {
            ProxySelector.setDefault(new SpecFetchGuard(current));
        }
    }

    @Override
    public List<Proxy> select(URI uri) {
        Parse parse = PARSING.get();
        if (parse != null) {
            try {
                String host = uri.getHost();
                if (isBlank(host)) {
                    // HttpURLConnection reads the host from the URL more leniently than URI does, so refuse rather than skip
                    throw new ForwardTargetBlockedException("OpenAPI spec fetch from \"" + uri + "\" names no host that can be checked");
                }
                InetAddressValidator.validateForwardTarget(parse.blockPrivateNetworks, parse.hostPolicy, host);
            } catch (ForwardTargetBlockedException blocked) {
                if (parse.refusal == null) {
                    parse.refusal = blocked;
                }
                throw blocked;
            }
        }
        return wrapped != null ? wrapped.select(uri) : Collections.singletonList(Proxy.NO_PROXY);
    }

    @Override
    public void connectFailed(URI uri, SocketAddress address, IOException failure) {
        if (wrapped != null) {
            wrapped.connectFailed(uri, address, failure);
        }
    }
}
