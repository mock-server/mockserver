package org.mockserver.client;

import com.google.common.collect.ImmutableList;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import org.mockserver.authentication.AuthenticationException;
import org.mockserver.client.MockServerEventBus.EventType;
import org.mockserver.closurecallback.websocketregistry.LocalCallbackRegistry;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.mockserver.configuration.ClientConfiguration;
import org.mockserver.configuration.Configuration;
import org.mockserver.configuration.ConfigurationProperties;
import org.mockserver.file.FileReader;
import org.mockserver.httpclient.NettyHttpClient;
import org.mockserver.httpclient.SocketCommunicationException;
import org.mockserver.httpclient.SocketConnectionException;
import org.mockserver.load.LoadScenario;
import org.mockserver.log.model.LogEntry;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.matchers.TimeToLive;
import org.mockserver.matchers.Times;
import org.mockserver.mock.Expectation;
import org.mockserver.mock.ExpectationFilter;
import org.mockserver.mock.MockMode;
import org.mockserver.mock.OpenAPIExpectation;
import org.mockserver.mock.breakpoint.BreakpointPhase;
import org.mockserver.oidc.OidcProviderConfiguration;
import org.mockserver.model.*;
import org.mockserver.proxyconfiguration.ProxyConfiguration;
import org.mockserver.saml.SamlProviderConfiguration;
import org.mockserver.scim.ScimProviderConfiguration;
import org.mockserver.scheduler.Scheduler;
import org.mockserver.serialization.*;
import org.mockserver.serialization.model.HttpChaosProfileDTO;
import org.mockserver.slo.SloCriteria;
import org.mockserver.slo.SloVerdict;
import org.mockserver.socket.tls.NettySslContextFactory;
import org.mockserver.stop.Stoppable;
import org.mockserver.uuid.UUIDService;
import org.mockserver.verify.Verification;
import org.mockserver.verify.VerificationSequence;
import org.mockserver.verify.VerificationTimes;
import org.mockserver.version.Version;

import javax.net.ssl.SSLException;
import java.awt.*;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.util.*;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.function.Function;
import java.util.function.Supplier;

import static io.netty.handler.codec.http.HttpHeaderNames.*;
import static io.netty.handler.codec.http.HttpResponseStatus.BAD_REQUEST;
import static io.netty.handler.codec.http.HttpResponseStatus.FORBIDDEN;
import static io.netty.handler.codec.http.HttpResponseStatus.INTERNAL_SERVER_ERROR;
import static io.netty.handler.codec.http.HttpResponseStatus.NOT_ACCEPTABLE;
import static io.netty.handler.codec.http.HttpResponseStatus.OK;
import static io.netty.handler.codec.http.HttpResponseStatus.UNAUTHORIZED;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.apache.commons.lang3.StringUtils.*;
import static org.mockserver.character.Character.NEW_LINE;
import static org.mockserver.configuration.ClientConfiguration.clientConfiguration;
import static org.mockserver.formatting.StringFormatter.formatLogMessage;
import static org.mockserver.mock.HttpState.LOG_SEPARATOR;
import static org.mockserver.model.ExpectationId.expectationId;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.MediaType.APPLICATION_JSON_UTF_8;
import static org.mockserver.model.PortBinding.portBinding;
import static org.mockserver.socket.tls.PEMToFile.privateKeyFromPEMFile;
import static org.mockserver.socket.tls.PEMToFile.x509ChainFromPEMFile;
import static org.mockserver.verify.Verification.verification;
import static org.mockserver.verify.VerificationSequence.verificationSequence;
import static org.mockserver.verify.VerificationTimes.atLeast;
import static org.mockserver.verify.VerificationTimes.exactly;
import static org.slf4j.event.Level.*;

/**
 * Client for communicating with a running MockServer instance.
 * <p>
 * Instances can be created directly via one of the constructors, for example:
 * <pre>
 * MockServerClient client = new MockServerClient("localhost", 1080);
 * </pre>
 * or, for a more discoverable and self-documenting alternative that covers every
 * construction dimension (host, port, context path, TLS, control plane JWT, proxy
 * configuration and request override) with sensible defaults, via the fluent
 * {@link #builder()}:
 * <pre>
 * MockServerClient client = MockServerClient.builder()
 *     .host("localhost")
 *     .port(1080)
 *     .contextPath("/mockserver")
 *     .secure(true)
 *     .build();
 * </pre>
 * The builder simply delegates to the existing constructors and {@code with...}
 * setters, so both approaches produce identically-configured clients; the
 * constructors remain fully supported.
 *
 * @author jamesdbloom
 */
@SuppressWarnings({"UnusedReturnValue", "FieldMayBeFinal"})
public class MockServerClient implements Stoppable {

    private static final MockServerLogger MOCK_SERVER_LOGGER = new MockServerLogger(MockServerClient.class);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final Map<Integer, MockServerEventBus> EVENT_BUS_MAP = new ConcurrentHashMap<>();
    private final EventLoopGroup eventLoopGroup;
    private final String host;
    private final String contextPath;
    private final Class<MockServerClient> clientClass;
    protected CompletableFuture<Integer> portFuture;
    private Boolean secure;
    private Integer port;
    private HttpRequest requestOverride;
    private ClientConfiguration configuration;
    private ProxyConfiguration proxyConfiguration;
    private Supplier<String> controlPlaneJWTSupplier;
    private volatile NettyHttpClient nettyHttpClient;
    private NettySslContextFactory controlPlaneSslContextFactory;
    // not this client's monitor: a WebSocket reconnect on an event loop reads the factory while
    // addBreakpoint holds that monitor waiting for the WebSocket to register
    private final Object controlPlaneSslContextFactoryLock = new Object();
    private RequestDefinitionSerializer requestDefinitionSerializer = new RequestDefinitionSerializer(MOCK_SERVER_LOGGER);
    private ExpectationIdSerializer expectationIdSerializer = new ExpectationIdSerializer(MOCK_SERVER_LOGGER);
    private LogEventRequestAndResponseSerializer httpRequestResponseSerializer = new LogEventRequestAndResponseSerializer(MOCK_SERVER_LOGGER);
    private PortBindingSerializer portBindingSerializer = new PortBindingSerializer(MOCK_SERVER_LOGGER);
    private ExpectationSerializer expectationSerializer = new ExpectationSerializer(MOCK_SERVER_LOGGER);
    private OpenAPIExpectationSerializer openAPIExpectationSerializer = new OpenAPIExpectationSerializer(MOCK_SERVER_LOGGER);
    private CrudExpectationsDefinitionSerializer crudExpectationsDefinitionSerializer = new CrudExpectationsDefinitionSerializer(MOCK_SERVER_LOGGER);
    private VerificationSerializer verificationSerializer = new VerificationSerializer(MOCK_SERVER_LOGGER);
    private VerificationSequenceSerializer verificationSequenceSerializer = new VerificationSequenceSerializer(MOCK_SERVER_LOGGER);
    private LogEntrySerializer logEntrySerializer = new LogEntrySerializer(MOCK_SERVER_LOGGER);
    private HttpRequestSerializer httpRequestSerializer = new HttpRequestSerializer(MOCK_SERVER_LOGGER);
    private HttpResponseSerializer httpResponseSerializer = new HttpResponseSerializer(MOCK_SERVER_LOGGER);
    private LoadScenarioSerializer loadScenarioSerializer = new LoadScenarioSerializer(MOCK_SERVER_LOGGER);
    private SloCriteriaSerializer sloCriteriaSerializer = new SloCriteriaSerializer(MOCK_SERVER_LOGGER);
    private final CompletableFuture<MockServerClient> stopFuture = new CompletableFuture<>();
    private volatile BreakpointWebSocketClient breakpointWebSocketClient;

    /**
     * Start the client communicating to a MockServer on localhost at the port
     * specified with the Future
     *
     * @param portFuture the port for the MockServer to communicate with
     */
    public MockServerClient(Configuration configuration, CompletableFuture<Integer> portFuture) {
        this(clientConfiguration(configuration), portFuture);
    }

    /**
     * Start the client communicating to a MockServer on localhost at the port
     * specified with the Future
     *
     * @param portFuture the port for the MockServer to communicate with
     */
    public MockServerClient(ClientConfiguration configuration, CompletableFuture<Integer> portFuture) {
        if (configuration == null) {
            configuration = clientConfiguration();
        }
        this.clientClass = MockServerClient.class;
        this.host = "127.0.0.1";
        this.portFuture = portFuture;
        this.contextPath = "";
        this.configuration = configuration;
        this.eventLoopGroup = eventLoopGroup();
        LocalCallbackRegistry.setMaxWebSocketExpectations(configuration.maxWebSocketExpectations());
    }

    /**
     * Start the client communicating to a MockServer at the specified host and port
     * for example:
     * <p>
     * MockServerClient mockServerClient = new MockServerClient("localhost", 1080);
     *
     * @param host the host for the MockServer to communicate with
     * @param port the port for the MockServer to communicate with
     */
    public MockServerClient(String host, int port) {
        this(host, port, "");
    }

    /**
     * Start the client communicating to a MockServer at the specified host and port
     * for example:
     * <p>
     * MockServerClient mockServerClient = new MockServerClient("localhost", 1080);
     *
     * @param host the host for the MockServer to communicate with
     * @param port the port for the MockServer to communicate with
     */
    public MockServerClient(Configuration configuration, String host, int port) {
        this(configuration, host, port, "");
    }

    /**
     * Start the client communicating to a MockServer at the specified host and port
     * for example:
     * <p>
     * MockServerClient mockServerClient = new MockServerClient("localhost", 1080);
     *
     * @param host the host for the MockServer to communicate with
     * @param port the port for the MockServer to communicate with
     */
    public MockServerClient(ClientConfiguration configuration, String host, int port) {
        this(configuration, host, port, "");
    }

    /**
     * Start the client communicating to a MockServer at the specified host and port
     * and contextPath for example:
     * <p>
     * MockServerClient mockServerClient = new MockServerClient("localhost", 1080, "/mockserver");
     *
     * @param host        the host for the MockServer to communicate with
     * @param port        the port for the MockServer to communicate with
     * @param contextPath the context path that the MockServer war is deployed to
     */
    public MockServerClient(String host, int port, String contextPath) {
        this.clientClass = MockServerClient.class;
        if (isEmpty(host)) {
            throw new IllegalArgumentException("Host can not be null or empty");
        }
        if (contextPath == null) {
            throw new IllegalArgumentException("ContextPath can not be null");
        }
        this.host = host;
        this.port = port;
        this.contextPath = contextPath;
        this.configuration = clientConfiguration();
        this.eventLoopGroup = eventLoopGroup();
        LocalCallbackRegistry.setMaxWebSocketExpectations(configuration.maxWebSocketExpectations());
    }

    /**
     * Start the client communicating to a MockServer at the specified host and port
     * and contextPath for example:
     * <p>
     * MockServerClient mockServerClient = new MockServerClient("localhost", 1080, "/mockserver");
     *
     * @param host        the host for the MockServer to communicate with
     * @param port        the port for the MockServer to communicate with
     * @param contextPath the context path that the MockServer war is deployed to
     */
    public MockServerClient(Configuration configuration, String host, int port, String contextPath) {
        this(clientConfiguration(configuration), host, port, contextPath);
    }

    /**
     * Start the client communicating to a MockServer at the specified host and port
     * and contextPath for example:
     * <p>
     * MockServerClient mockServerClient = new MockServerClient("localhost", 1080, "/mockserver");
     *
     * @param host        the host for the MockServer to communicate with
     * @param port        the port for the MockServer to communicate with
     * @param contextPath the context path that the MockServer war is deployed to
     */
    public MockServerClient(ClientConfiguration configuration, String host, int port, String contextPath) {
        this.clientClass = MockServerClient.class;
        if (isEmpty(host)) {
            throw new IllegalArgumentException("Host can not be null or empty");
        }
        if (contextPath == null) {
            throw new IllegalArgumentException("ContextPath can not be null");
        }
        if (configuration == null) {
            configuration = clientConfiguration();
        }
        this.configuration = configuration;
        this.host = host;
        this.port = port;
        this.contextPath = contextPath;
        this.eventLoopGroup = eventLoopGroup();
    }

    private NioEventLoopGroup eventLoopGroup() {
        return new NioEventLoopGroup(configuration.clientNioEventLoopThreadCount(), new Scheduler.SchedulerThreadFactory(this.getClass().getSimpleName() + "-eventLoop"));
    }

    /**
     * Create a fluent {@link Builder} for constructing a {@link MockServerClient}.
     * <p>
     * The builder is a discoverable alternative to the constructors that covers every
     * construction dimension in one place, with sensible defaults ({@code host} =
     * {@code "localhost"}, {@code port} = {@code 1080}, empty context path). It delegates
     * to the existing constructors and {@code with...} setters, so it introduces no new
     * behaviour; the constructors remain fully supported. For example:
     * <pre>
     * MockServerClient client = MockServerClient.builder()
     *     .host("localhost")
     *     .port(1080)
     *     .build();
     * </pre>
     *
     * @return a new builder pre-populated with the default host and port
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Fluent builder for {@link MockServerClient}.
     * <p>
     * Covers every dimension exposed by the {@link MockServerClient} constructors and
     * {@code with...} setters:
     * <ul>
     *     <li>{@link #configuration(ClientConfiguration)} / {@link #configuration(Configuration)}</li>
     *     <li>{@link #host(String)} (default {@code "localhost"})</li>
     *     <li>{@link #port(int)} (default {@code 1080})</li>
     *     <li>{@link #contextPath(String)} (default empty)</li>
     *     <li>{@link #portFuture(CompletableFuture)} (mutually exclusive with host/port/contextPath)</li>
     *     <li>{@link #secure(boolean)} — TLS, delegates to {@link MockServerClient#withSecure(boolean)}</li>
     *     <li>{@link #proxyConfiguration(ProxyConfiguration)} — delegates to {@link MockServerClient#withProxyConfiguration(ProxyConfiguration)}</li>
     *     <li>{@link #controlPlaneJWT(String)} / {@link #controlPlaneJWT(Supplier)} — delegates to {@link MockServerClient#withControlPlaneJWT(Supplier)}</li>
     *     <li>{@link #requestOverride(HttpRequest)} — delegates to {@link MockServerClient#withRequestOverride(HttpRequest)}</li>
     * </ul>
     * The builder performs no construction work until {@link #build()} is called, at which
     * point it selects the matching constructor and applies the configured setters, so the
     * resulting client is identical to one built directly. Validation (for example, the
     * empty-host check) is inherited from the constructors and surfaces from {@link #build()}.
     */
    public static final class Builder {

        private ClientConfiguration configuration;
        private String host = "localhost";
        private int port = 1080;
        private String contextPath = "";
        private CompletableFuture<Integer> portFuture;
        private Boolean secure;
        private ProxyConfiguration proxyConfiguration;
        private Supplier<String> controlPlaneJWTSupplier;
        private HttpRequest requestOverride;
        private boolean hostSet;
        private boolean portSet;
        private boolean contextPathSet;

        private Builder() {
        }

        /**
         * Use the given {@link ClientConfiguration}. When {@code null} the client falls back
         * to the default configuration, matching the constructors' behaviour.
         */
        public Builder configuration(ClientConfiguration configuration) {
            this.configuration = configuration;
            return this;
        }

        /**
         * Use the given {@link Configuration}, wrapped as a {@link ClientConfiguration} (the
         * same conversion the {@code MockServerClient(Configuration, ...)} constructors perform).
         */
        public Builder configuration(Configuration configuration) {
            this.configuration = configuration == null ? null : clientConfiguration(configuration);
            return this;
        }

        /**
         * The host of the MockServer to communicate with. Defaults to {@code "localhost"}.
         */
        public Builder host(String host) {
            this.host = host;
            this.hostSet = true;
            return this;
        }

        /**
         * The port of the MockServer to communicate with. Defaults to {@code 1080}.
         */
        public Builder port(int port) {
            this.port = port;
            this.portSet = true;
            return this;
        }

        /**
         * The context path that the MockServer war is deployed to. Defaults to empty.
         */
        public Builder contextPath(String contextPath) {
            this.contextPath = contextPath;
            this.contextPathSet = true;
            return this;
        }

        /**
         * Resolve the port lazily from a {@link CompletableFuture}, for use when the MockServer
         * port is not yet known at client-construction time. This is mutually exclusive with
         * {@link #host(String)}, {@link #port(int)} and {@link #contextPath(String)}: the
         * future-based constructor always communicates with {@code localhost} using an empty
         * context path.
         */
        public Builder portFuture(CompletableFuture<Integer> portFuture) {
            this.portFuture = portFuture;
            return this;
        }

        /**
         * Whether to communicate with MockServer over TLS. Delegates to
         * {@link MockServerClient#withSecure(boolean)}.
         */
        public Builder secure(boolean secure) {
            this.secure = secure;
            return this;
        }

        /**
         * Route communication to MockServer via a proxy. Delegates to
         * {@link MockServerClient#withProxyConfiguration(ProxyConfiguration)}.
         */
        public Builder proxyConfiguration(ProxyConfiguration proxyConfiguration) {
            this.proxyConfiguration = proxyConfiguration;
            return this;
        }

        /**
         * A fixed JWT for control plane authorisation. Delegates to
         * {@link MockServerClient#withControlPlaneJWT(String)}.
         */
        public Builder controlPlaneJWT(String controlPlaneJWT) {
            this.controlPlaneJWTSupplier = () -> controlPlaneJWT;
            return this;
        }

        /**
         * A JWT supplier for control plane authorisation. Delegates to
         * {@link MockServerClient#withControlPlaneJWT(Supplier)}.
         */
        public Builder controlPlaneJWT(Supplier<String> controlPlaneJWTSupplier) {
            this.controlPlaneJWTSupplier = controlPlaneJWTSupplier;
            return this;
        }

        /**
         * A request override applied to every control plane request. Delegates to
         * {@link MockServerClient#withRequestOverride(HttpRequest)}.
         */
        public Builder requestOverride(HttpRequest requestOverride) {
            this.requestOverride = requestOverride;
            return this;
        }

        /**
         * Build the {@link MockServerClient}, selecting the matching constructor and applying
         * the configured {@code with...} setters. Constructor validation (such as the
         * empty-host check) surfaces from here.
         *
         * @throws IllegalArgumentException if {@link #portFuture(CompletableFuture)} is combined
         *                                  with an explicit host, port or context path, or if a
         *                                  constructor rejects the supplied values
         */
        public MockServerClient build() {
            MockServerClient client;
            if (portFuture != null) {
                if (hostSet || portSet || contextPathSet) {
                    throw new IllegalArgumentException("portFuture(...) can not be combined with host(...), port(...) or contextPath(...); the future-based client always communicates with localhost using an empty context path");
                }
                client = new MockServerClient(configuration, portFuture);
            } else {
                client = new MockServerClient(configuration, host, port, contextPath);
            }
            if (secure != null) {
                client.withSecure(secure);
            }
            if (proxyConfiguration != null) {
                client.withProxyConfiguration(proxyConfiguration);
            }
            if (controlPlaneJWTSupplier != null) {
                client.withControlPlaneJWT(controlPlaneJWTSupplier);
            }
            if (requestOverride != null) {
                client.withRequestOverride(requestOverride);
            }
            return client;
        }
    }

    /**
     * @deprecated use withProxyConfiguration which is more consistent with MockServer API style
     */
    @Deprecated
    public MockServerClient setProxyConfiguration(ProxyConfiguration proxyConfiguration) {
        return withProxyConfiguration(proxyConfiguration);
    }

    /**
     * Configure communication to MockServer to go via a proxy
     */
    public MockServerClient withProxyConfiguration(ProxyConfiguration proxyConfiguration) {
        this.proxyConfiguration = proxyConfiguration;
        return this;
    }

    /**
     * Specify JWT to use for control plane authorisation
     */
    public MockServerClient withControlPlaneJWT(String controlPlaneJWT) {
        return withControlPlaneJWT(() -> controlPlaneJWT);
    }

    /**
     * Specify JWT supplier to use for control plane authorisation
     */
    public MockServerClient withControlPlaneJWT(Supplier<String> controlPlaneJWTSupplier) {
        this.controlPlaneJWTSupplier = controlPlaneJWTSupplier;
        return this;
    }

    /**
     * @deprecated use withRequestOverride which is more consistent with MockServer API style
     */
    @Deprecated
    public MockServerClient setRequestOverride(HttpRequest requestOverride) {
        return withRequestOverride(requestOverride);
    }

    public MockServerClient withRequestOverride(HttpRequest requestOverride) {
        if (requestOverride == null) {
            throw new IllegalArgumentException("Request with default properties can not be null");
        } else {
            this.requestOverride = requestOverride;
        }
        return this;
    }

    private MockServerEventBus getMockServerEventBus() {
        return EVENT_BUS_MAP.computeIfAbsent(this.port(), k -> new MockServerEventBus());
    }

    private void removeMockServerEventBus() {
        EVENT_BUS_MAP.remove(this.port());
    }

    public boolean isSecure() {
        return secure != null ? secure : false;
    }

    public MockServerClient withSecure(boolean secure) {
        this.secure = secure;
        return this;
    }

    private int port() {
        if (this.port == null) {
            try {
                port = portFuture.get(configuration.maxFutureTimeoutInMillis(), MILLISECONDS);
            } catch (Exception ex) {
                throw new RuntimeException(ex);
            }
        }
        return this.port;
    }

    public InetSocketAddress remoteAddress() {
        return new InetSocketAddress(this.host, port());
    }

    public String contextPath() {
        return contextPath;
    }

    public Integer getPort() {
        return port();
    }

    @SuppressWarnings("DuplicatedCode")
    private String calculatePath(String path) {
        String cleanedPath = "/mockserver/" + path;
        if (isNotBlank(contextPath)) {
            cleanedPath =
                (!contextPath.startsWith("/") ? "/" : "") +
                    contextPath +
                    (!contextPath.endsWith("/") ? "/" : "") +
                    (cleanedPath.startsWith("/") ? cleanedPath.substring(1) : cleanedPath);
        }
        return (!cleanedPath.startsWith("/") ? "/" : "") + cleanedPath;
    }

    private NettyHttpClient getNettyHttpClient() {
        NettyHttpClient localClient = nettyHttpClient;
        if (localClient != null) {
            return localClient;
        }
        synchronized (this) {
            if (nettyHttpClient != null) {
                return nettyHttpClient;
            }
            this.nettyHttpClient = new NettyHttpClient(
                configuration.toServerConfiguration(),
                MOCK_SERVER_LOGGER,
                eventLoopGroup,
                proxyConfiguration != null ? ImmutableList.of(proxyConfiguration) : null,
                false,
                controlPlaneSslContextFactory()
            );
            return nettyHttpClient;
        }
    }

    /**
     * The TLS context factory for every connection this client makes to MockServer: its HTTP requests and its
     * callback and breakpoint WebSockets all verify MockServer's certificate against the same certificates and,
     * with control-plane mTLS, present the same client certificate.
     */
    private NettySslContextFactory controlPlaneSslContextFactory() {
        synchronized (controlPlaneSslContextFactoryLock) {
            if (controlPlaneSslContextFactory == null) {
                controlPlaneSslContextFactory = newControlPlaneSslContextFactory();
            }
            return controlPlaneSslContextFactory;
        }
    }

    private NettySslContextFactory newControlPlaneSslContextFactory() {
        NettySslContextFactory nettySslContextFactory = NettySslContextFactory.forMockServerClient(configuration.toServerConfiguration(), MOCK_SERVER_LOGGER);
        Function<SslContextBuilder, SslContext> clientSslContextBuilderFunction = NettySslContextFactory.clientSslContextBuilderFunction;
        if (configuration.controlPlaneTLSMutualAuthenticationRequired()) {
            if (isBlank(configuration.controlPlanePrivateKeyPath()) || isBlank(configuration.controlPlaneX509CertificatePath()) || isBlank(configuration.controlPlaneTLSMutualAuthenticationCAChain())) {
                throw new IllegalArgumentException(
                    "when 'controlPlaneTLSMutualAuthenticationRequired' is enabled 'controlPlanePrivateKeyPath', 'controlPlaneX509CertificatePath' and 'controlPlaneTLSMutualAuthenticationCAChain' must all be specified,\n\tfound controlPlanePrivateKeyPath: \"" + configuration.controlPlanePrivateKeyPath() + "\"\n\tand controlPlaneX509CertificatePath: \"" + configuration.controlPlaneX509CertificatePath() + "\"\n\tand controlPlaneTLSMutualAuthenticationCAChain: \"" + configuration.controlPlaneTLSMutualAuthenticationCAChain() + "\"");
            }
            clientSslContextBuilderFunction =
                sslContextBuilder -> {
                    try {
                        PrivateKey key = privateKeyFromPEMFile(configuration.controlPlanePrivateKeyPath());
                        X509Certificate[] keyCertChain = x509ChainFromPEMFile(configuration.controlPlaneX509CertificatePath()).toArray(new X509Certificate[0]);
                        X509Certificate[] trustCertCollection = NettySslContextFactory.controlPlaneTrustCertificates(configuration.controlPlaneTLSMutualAuthenticationCAChain());
                        sslContextBuilder
                            .keyManager(
                                key,
                                keyCertChain
                            )
                            .trustManager(trustCertCollection);
                        return sslContextBuilder.build();
                    } catch (SSLException e) {
                        throw new RuntimeException(e);
                    }
                };
        }
        return nettySslContextFactory.withClientSslContextBuilderFunction(clientSslContextBuilderFunction);
    }

    /**
     * The {@code Authorization} header value for a callback WebSocket upgrade, so it passes control-plane
     * authentication as this client's HTTP requests do: the control-plane JWT as a bearer token, else the
     * request override's {@code Authorization} header, else null.
     */
    String webSocketAuthorization() {
        Supplier<String> supplier = controlPlaneJWTSupplier;
        if (supplier != null) {
            String jwt = supplier.get();
            if (isNotBlank(jwt)) {
                return "Bearer " + jwt;
            }
        }
        HttpRequest override = requestOverride;
        if (override != null) {
            String authorization = override.getFirstHeader(AUTHORIZATION.toString());
            if (isNotBlank(authorization)) {
                return authorization;
            }
        }
        return null;
    }

    /**
     * The TLS context for this client's callback and breakpoint WebSockets to MockServer: the same trust and
     * client certificate as its HTTP requests, without ALPN because a WebSocket upgrade is HTTP/1.1.
     */
    SslContext webSocketSslContext() {
        return controlPlaneSslContextFactory().createClientSslContext(false, false, remoteAddress().getHostName());
    }

    private HttpResponse sendRequest(HttpRequest request, boolean ignoreErrors, boolean throwClientException) {
        if (!stopFuture.isDone()) {
            try {
                if (!request.containsHeader(CONTENT_TYPE.toString())
                    && request.getBody() != null
                    && isNotBlank(request.getBody().getContentType())) {
                    request.withHeader(CONTENT_TYPE.toString(), request.getBody().getContentType());
                }
                if (secure != null) {
                    request.withSecure(secure);
                }
                if (requestOverride != null) {
                    request = request.update(requestOverride, null);
                }
                if (controlPlaneJWTSupplier != null) {
                    String jwt = controlPlaneJWTSupplier.get();
                    if (isNotBlank(jwt)) {
                        request.withHeader(AUTHORIZATION.toString(), "Bearer " + jwt);
                    } else {
                        throw new IllegalArgumentException("Control plane jwt supplier returned invalid JWT \"" + jwt + "\"");
                    }
                }
                HttpResponse response = getNettyHttpClient().sendRequest(
                    request.withHeader(HOST.toString(), this.host + ":" + port()),
                    configuration.maxSocketTimeoutInMillis(),
                    TimeUnit.MILLISECONDS,
                    ignoreErrors
                );

                if (response != null) {
                    if (response.getStatusCode() != null) {
                        if (response.getStatusCode() == BAD_REQUEST.code()) {
                            throw new IllegalArgumentException(response.getBodyAsString());
                        } else if (response.getStatusCode() == UNAUTHORIZED.code()) {
                            throw new AuthenticationException(response.getBodyAsString());
                        } else if (response.getStatusCode() == INTERNAL_SERVER_ERROR.code()) {
                            // a failure inside MockServer, never a result: raised whatever the caller passed as throwClientException
                            throw new ClientException(formatLogMessage("error:{}while sending request:{}", response, request));
                        }
                    }
                    String serverVersion = response.getFirstHeader("version");
                    String clientVersion = Version.getVersion();
                    if (!Version.matchesMajorMinorVersion(serverVersion)) {
                        throw new ClientException("Client version \"" + clientVersion + "\" major and minor versions do not match server version \"" + serverVersion + "\"");
                    }
                }

                if (throwClientException && response != null && response.getStatusCode() != null && response.getStatusCode() >= 400) {
                    throw new ClientException(formatLogMessage("error:{}while sending request:{}", response, request));
                }

                return response;
            } catch (RuntimeException rex) {
                if (isNotBlank(rex.getMessage()) && (rex.getMessage().contains("executor not accepting a task") || rex.getMessage().contains("loop shut down"))) {
                    throw new IllegalStateException(this.getClass().getSimpleName() + " has already been closed, please create new " + this.getClass().getSimpleName() + " instance");
                } else {
                    throw rex;
                }
            }
        } else {
            throw new IllegalStateException(this.getClass().getSimpleName() + " has already been stopped, please create new " + this.getClass().getSimpleName() + " instance");
        }
    }

    private HttpResponse sendRequest(HttpRequest request, boolean throwClientException) {
        return sendRequest(request, false, throwClientException);
    }

    /**
     * Launch UI and wait the default period to allow the UI to launch and start collecting logs,
     * this ensures that the log are visible in the UI even if MockServer is shutdown by a test
     * shutdown function, such as After, AfterClass, AfterAll, etc
     */
    public MockServerClient openUI() {
        return openUI(SECONDS, 1);
    }

    /**
     * Launch UI and wait a specified period to allow the UI to launch and start collecting logs,
     * this ensures that the log are visible in the UI even if MockServer is shutdown by a test
     * shutdown function, such as After, AfterClass, AfterAll, etc
     *
     * @param timeUnit TimeUnit the time unit, for example TimeUnit.SECONDS
     * @param pause    the number of time units to delay before the function returns to ensure the UI is receiving logs
     */
    public MockServerClient openUI(TimeUnit timeUnit, long pause) {
        try {
            Desktop desktop = Desktop.getDesktop();
            if (desktop != null) {
                if (desktop.isSupported(Desktop.Action.BROWSE)) {
                    desktop.browse(new URI("http://" + host + ":" + port() + "/mockserver/dashboard"));
                    timeUnit.sleep(pause);
                } else {
                    if (MockServerLogger.isEnabled(WARN)) {
                        MOCK_SERVER_LOGGER.logEvent(
                            new LogEntry()
                                .setLogLevel(WARN)
                                .setMessageFormat("browse to URL not supported by the desktop instance from JVM")
                        );
                    }
                }
            } else {
                if (MockServerLogger.isEnabled(WARN)) {
                    MOCK_SERVER_LOGGER.logEvent(
                        new LogEntry()
                            .setLogLevel(WARN)
                            .setMessageFormat("unable to obtain the desktop instance from JVM")
                    );
                }
            }
        } catch (Throwable throwable) {
            MOCK_SERVER_LOGGER.logEvent(
                new LogEntry()
                    .setLogLevel(ERROR)
                    .setMessageFormat("exception while attempting to launch UI" + (isNotBlank(throwable.getMessage()) ? " " + throwable.getMessage() : ""))
                    .setThrowable(throwable)
            );
            throw new ClientException("exception while attempting to launch UI" + (isNotBlank(throwable.getMessage()) ? " " + throwable.getMessage() : ""));
        }
        return this;
    }

    /**
     * Returns whether MockServer is running, if called too quickly after starting MockServer
     * this may return false because MockServer has not yet started, to ensure MockServer has
     * started use hasStarted()
     *
     * @deprecated use hasStopped() or hasStarted() instead
     */
    @Deprecated
    @SuppressWarnings({"DeprecatedIsStillUsed", "RedundantSuppression"})
    public boolean isRunning() {
        return isRunning(10, 500, TimeUnit.MILLISECONDS);
    }

    /**
     * Returns whether server MockServer is running, by polling the MockServer a configurable
     * amount of times.  If called too quickly after starting MockServer this may return false
     * because MockServer has not yet started, to ensure MockServer has started use hasStarted()
     *
     * @deprecated use hasStopped() or hasStarted() instead
     */
    @Deprecated
    public boolean isRunning(int attempts, long timeout, TimeUnit timeUnit) {
        try {
            HttpResponse httpResponse = sendRequest(request().withMethod("PUT").withPath(calculatePath("status")), true, false);
            if (httpResponse != null && httpResponse.getStatusCode() == HttpStatusCode.OK_200.code()) {
                return true;
            } else if (attempts <= 0) {
                return false;
            } else {
                try {
                    timeUnit.sleep(timeout);
                } catch (InterruptedException e) {
                    // ignore interrupted exception
                }
                return isRunning(attempts - 1, timeout, timeUnit);
            }
        } catch (SocketConnectionException | IllegalStateException sce) {
            if (MockServerLogger.isEnabled(TRACE)) {
                MOCK_SERVER_LOGGER.logEvent(
                    new LogEntry()
                        .setLogLevel(TRACE)
                        .setMessageFormat("exception while checking if MockServer is running - " + sce.getMessage() + " if MockServer was stopped this exception is expected")
                        .setThrowable(sce)
                );
            }
            return false;
        }
    }

    /**
     * Returns whether MockServer has stopped, if called too quickly after starting MockServer
     * this may return false because MockServer has not yet started, to ensure MockServer has
     * started use hasStarted()
     */
    public boolean hasStopped() {
        return hasStopped(10, 500, TimeUnit.MILLISECONDS);
    }

    /**
     * Returns whether server MockServer has stopped, by polling the MockServer a configurable
     * amount of times.  If called too quickly after starting MockServer this may return false
     * because MockServer has not yet started, to ensure MockServer has started use hasStarted()
     */
    public boolean hasStopped(int attempts, long timeout, TimeUnit timeUnit) {
        try {
            // errors are deliberately NOT ignored here. With ignoreErrors=true both a connection
            // refusal (MockServer genuinely stopped) and a read timeout (MockServer alive but slow)
            // are swallowed into a null response, which cannot be told apart - and treating that
            // null as "stopped" is a fail-open. Letting the exceptions propagate is what makes the
            // two cases distinguishable below.
            HttpResponse httpResponse = sendRequest(request().withMethod("PUT").withPath(calculatePath("status")), false, false);
            if (httpResponse != null && httpResponse.getStatusCode() == HttpStatusCode.OK_200.code()) {
                if (attempts <= 0) {
                    return false;
                } else {
                    try {
                        timeUnit.sleep(timeout);
                    } catch (InterruptedException e) {
                        // ignore interrupted exception
                    }
                    return hasStopped(attempts - 1, timeout, timeUnit);
                }
            } else {
                return true;
            }
        } catch (SocketConnectionException | IllegalStateException sce) {
            // the socket refused the connection, or this client's event loop is already closed:
            // MockServer is not accepting connections, so it really has stopped
            return true;
        } catch (RuntimeException rex) {
            // Neither a timeout nor any other unexpected failure tells us MockServer has stopped, so
            // none of them may report "stopped". A read timeout in particular is AMBIGUOUS - MockServer
            // may still be alive and still holding its port, just too slow to answer (GC pause, CPU
            // contention, a saturated event loop). Reporting "stopped" there is a fail-open: callers
            // rebind the port while the server still owns it and get a BindException far away from the
            // real cause.
            //
            // Returning false for every unrecognised failure also keeps this method total. It
            // previously swallowed such failures to a null response and answered "stopped"; throwing
            // instead would be a new way for callers to fail (see InstanceHolder in the Maven plugin,
            // which rethrows), so "not confirmed stopped" is both the safe and the compatible answer.
            //
            // Deliberately not retried: each probe already costs up to maxSocketTimeoutInMillis
            // (20 seconds by default), so retrying here would multiply shutdown latency. Callers that
            // want to keep waiting drive their own retry loop.
            if (MockServerLogger.isEnabled(WARN)) {
                String reason = isTimeoutFailure(rex)
                    ? "timeout while checking if MockServer has stopped - " + rex.getMessage()
                    + " - a timeout cannot distinguish a stopped MockServer from one that is still "
                    + "running but slow to respond"
                    : "unexpected failure while checking if MockServer has stopped - " + rex.getMessage();
                MOCK_SERVER_LOGGER.logEvent(
                    new LogEntry()
                        .setLogLevel(WARN)
                        .setMessageFormat(reason + " - reporting NOT stopped")
                        .setThrowable(rex)
                );
            }
            return false;
        }
    }

    /**
     * How long {@link #stop(boolean)} keeps waiting for a confirmed stop before giving up.
     *
     * <p>This must never be shorter than the shutdown it is waiting for. MockServer stops accepting
     * connections and then holds its bound port draining in-flight requests for up to
     * {@code stopDrainMillis}, during which {@code /status} still answers, so each {@link #hasStopped()}
     * probe costs real time and can itself take up to {@code maxSocketTimeoutInMillis}. A fixed budget
     * that ignored both would abandon the wait while the server still held its port - completing
     * {@code stopFuture} early and handing the caller the very {@code BindException} this is meant to
     * prevent. {@code LifeCycle.stop()} sizes its own budget from {@code stopDrainMillis} for the same
     * reason.
     *
     * <p><strong>Cross-JVM caveat.</strong> {@code stopDrainMillis} is read from this client's JVM but
     * describes the <em>server's</em> drain. Against a remote MockServer configured with a longer drain
     * than the client knows about, this budget under-estimates and the wait can still end before the
     * server has released its port. That is inherent to a client that cannot see the server's
     * configuration; when it happens the wait now logs a warning rather than reporting a false stop.
     *
     * @param stopDrainMillis         how long the server may spend draining in-flight requests
     * @param maxSocketTimeoutMillis  how long a single status probe may block
     */
    static long stopWaitTimeoutMillis(long stopDrainMillis, long maxSocketTimeoutMillis) {
        return Math.max(30_000L, stopDrainMillis + maxSocketTimeoutMillis);
    }

    /**
     * How long the blocking {@link #stop()} waits for the {@link #stop(boolean)} future to complete.
     *
     * <p>This must <em>exceed</em> {@link #stopWaitTimeoutMillis} - the budget the background wait uses -
     * or the blocking wrapper would always abandon the future before that wait could conclude, which
     * would be a new way to report a stop that had not happened. The margin is one further status probe,
     * because the background loop checks its deadline at the head of each iteration and so can overshoot
     * by up to one {@link #hasStopped()} call.
     *
     * @param stopDrainMillis         how long the server may spend draining in-flight requests
     * @param maxSocketTimeoutMillis  how long a single status probe may block
     */
    static long stopBlockingTimeoutMillis(long stopDrainMillis, long maxSocketTimeoutMillis) {
        return stopWaitTimeoutMillis(stopDrainMillis, maxSocketTimeoutMillis) + Math.max(0L, maxSocketTimeoutMillis);
    }

    /**
     * Whether a failure represents a read timeout rather than a definite answer about liveness.
     *
     * <p>A timeout can reach us in more than one shape, and which one wins is a race between two
     * independent timers, so both must be recognised:
     * <ul>
     *   <li>the client-side {@code future.get(timeout)} expiring, wrapped as
     *       {@link SocketCommunicationException}; and</li>
     *   <li>Netty's own {@code ReadTimeoutHandler} firing first, which surfaces as a plain
     *       {@link RuntimeException} wrapping a Netty timeout because that exception is not an
     *       {@code IOException} and so is not translated into a {@link SocketConnectionException}.</li>
     * </ul>
     * Matching only the first shape would leave the fail-open in place under exactly the load that
     * triggers it. The whole cause chain is inspected because these arrive wrapped.
     */
    static boolean isTimeoutFailure(Throwable throwable) {
        for (Throwable cause = throwable; cause != null; cause = cause.getCause()) {
            if (cause instanceof SocketCommunicationException
                || cause instanceof io.netty.handler.timeout.TimeoutException
                || cause instanceof java.util.concurrent.TimeoutException) {
                return true;
            }
            if (cause.getCause() == cause) {
                break;
            }
        }
        return false;
    }

    /**
     * Returns whether MockServer has started, if called after MockServer has been stopped
     * this method will block for 5 seconds while confirming MockServer is not starting
     */
    public boolean hasStarted() {
        return hasStarted(10, 500, TimeUnit.MILLISECONDS);
    }

    /**
     * Returns whether server MockServer has started, by polling the MockServer a configurable amount of times
     */
    public boolean hasStarted(int attempts, long timeout, TimeUnit timeUnit) {
        try {
            HttpResponse httpResponse = sendRequest(request().withMethod("PUT").withPath(calculatePath("status")), false);
            if (httpResponse.getStatusCode() == HttpStatusCode.OK_200.code()) {
                return true;
            } else if (attempts <= 0) {
                return false;
            } else {
                try {
                    timeUnit.sleep(timeout);
                } catch (InterruptedException e) {
                    // ignore interrupted exception
                }
                return hasStarted(attempts - 1, timeout, timeUnit);
            }
        } catch (SocketConnectionException | IllegalStateException sce) {
            if (attempts <= 0) {
                if (MockServerLogger.isEnabled(DEBUG)) {
                    MOCK_SERVER_LOGGER.logEvent(
                        new LogEntry()
                            .setLogLevel(DEBUG)
                            .setMessageFormat("exception while checking if MockServer has started - " + sce.getMessage())
                            .setThrowable(sce)
                    );
                }
                return false;
            } else {
                try {
                    timeUnit.sleep(timeout);
                } catch (InterruptedException e) {
                    // ignore interrupted exception
                }
                return hasStarted(attempts - 1, timeout, timeUnit);
            }
        }
    }

    /**
     * Bind new ports to listen on
     */
    public List<Integer> bind(Integer... ports) {
        String boundPorts = sendRequest(
            request()
                .withMethod("PUT")
                .withPath(calculatePath("bind"))
                .withBody(portBindingSerializer.serialize(portBinding(ports)), StandardCharsets.UTF_8),
            true
        ).getBodyAsString();
        return portBindingSerializer.deserialize(boundPorts).getPorts();
    }

    /**
     * Stop MockServer gracefully (only support for Netty version, not supported for WAR version)
     */
    public Future<MockServerClient> stopAsync() {
        return stop(true);
    }

    /**
     * Stop MockServer gracefully (only support for Netty version, not supported for WAR version)
     */
    public void stop() {
        try {
            // Derived rather than fixed, for the same reason as the background wait it is waiting on:
            // MockServer holds its bound port while draining, so a hardcoded budget shorter than the
            // drain would return to the caller while the port was still in use. LifeCycle.stop() sizes
            // its own budget the same way.
            stopAsync().get(
                stopBlockingTimeoutMillis(
                    ConfigurationProperties.stopDrainMillis(),
                    configuration.maxSocketTimeoutInMillis()
                ),
                MILLISECONDS
            );
        } catch (Throwable throwable) {
            // a stop that did not stop must not be silent. Logging this at DEBUG meant the first
            // visible symptom was an unrelated BindException later, when something tried to rebind
            // a port this MockServer was in fact still holding.
            if (MockServerLogger.isEnabled(WARN)) {
                MOCK_SERVER_LOGGER.logEvent(
                    new LogEntry()
                        .setLogLevel(WARN)
                        .setMessageFormat("exception while stopping MockServer, it may still be running and holding its port - "
                            + throwable.getMessage())
                        .setThrowable(throwable)
                );
            }
        }
    }

    /**
     * Stop MockServer gracefully (only support for Netty version, not supported for WAR version)
     */
    public CompletableFuture<MockServerClient> stop(boolean ignoreFailure) {
        if (!stopFuture.isDone()) {
            try {
                port();
            } catch (RuntimeException portNeverKnown) {
                releaseEventLoops();
                throw portNeverKnown;
            }
            getMockServerEventBus().publish(EventType.STOP);
            removeMockServerEventBus();
            new Scheduler.SchedulerThreadFactory("ClientStop").newThread(() -> {
                try {
                    sendRequest(request().withMethod("PUT").withPath(calculatePath("stop")), false);
                    // Bounded by iteration count and by a wall-clock deadline. hasStopped() no longer
                    // returns early on a read timeout, so against an unresponsive MockServer each probe
                    // can cost up to maxSocketTimeoutInMillis - an iteration-only bound could spin here
                    // for many minutes. The budget is derived from the shutdown it is waiting for
                    // rather than fixed, so that raising stopDrainMillis cannot make this give up while
                    // the server is still draining and still holding its port.
                    //
                    // The deadline is checked at the head of each iteration, so the elapsed time can
                    // overshoot it by up to one hasStopped() call; the wait is bounded, not precise.
                    long deadline = System.currentTimeMillis() + stopWaitTimeoutMillis(
                        ConfigurationProperties.stopDrainMillis(),
                        configuration.maxSocketTimeoutInMillis()
                    );
                    boolean stopped = hasStopped();
                    for (int i = 0; !stopped && i < 50 && System.currentTimeMillis() < deadline; i++) {
                        TimeUnit.MILLISECONDS.sleep(5);
                        stopped = hasStopped();
                    }
                    if (!stopped && MockServerLogger.isEnabled(WARN)) {
                        // giving up without a confirmed stop is exactly the state that later surfaces
                        // as an unrelated BindException, so it must not be silent
                        MOCK_SERVER_LOGGER.logEvent(
                            new LogEntry()
                                .setLogLevel(WARN)
                                .setMessageFormat("gave up waiting for MockServer to stop - it was not confirmed "
                                    + "stopped and may still be running and holding its port")
                        );
                    }
                } catch (RejectedExecutionException ree) {
                    if (!ignoreFailure && MockServerLogger.isEnabled(TRACE)) {
                        MOCK_SERVER_LOGGER.logEvent(
                            new LogEntry()
                                .setLogLevel(TRACE)
                                .setMessageFormat("request rejected while closing down, logging in case due other error " + ree)
                                .setThrowable(ree)
                        );
                    }
                } catch (Exception e) {
                    if (!ignoreFailure && MockServerLogger.isEnabled(WARN)) {
                        MOCK_SERVER_LOGGER.logEvent(
                            new LogEntry()
                                .setLogLevel(WARN)
                                .setMessageFormat("failed to send stop request to MockServer " + e.getMessage())
                        );
                    }
                }
                // stopClient is handled by the event bus subscription (STOP event above
                // triggers the lambda registered in ensureBreakpointWebSocketClient), so
                // we do NOT call stopClient again here to avoid a double-stop. The field
                // is nulled by the same lambda.
                if (!eventLoopGroup.isShuttingDown()) {
                    eventLoopGroup.shutdownGracefully();
                }
                stopFuture.complete(clientClass.cast(this));
            }).start();
        }
        return stopFuture;
    }

    /**
     * Stops this client without sending anything to MockServer, for a client whose MockServer never
     * started: there is no server to stop, only this client's event loops to release.
     */
    protected void releaseEventLoops() {
        if (!eventLoopGroup.isShuttingDown()) {
            eventLoopGroup.shutdownGracefully(0, 0, MILLISECONDS);
        }
        stopFuture.complete(clientClass.cast(this));
    }

    @Override
    public void close() {
        stop();
    }

    /**
     * Reset MockServer by clearing all expectations
     */
    public MockServerClient reset() {
        getMockServerEventBus().publish(EventType.RESET);
        sendRequest(
            request()
                .withMethod("PUT")
                .withPath(calculatePath("reset")),
            true
        );
        return clientClass.cast(this);
    }

    /**
     * Clear all expectations and logs that match the request matcher
     *
     * @param requestDefinition the http request that is matched against when deciding whether to clear each expectation if null all expectations are cleared
     */
    public MockServerClient clear(RequestDefinition requestDefinition) {
        sendRequest(
            request()
                .withMethod("PUT")
                .withContentType(APPLICATION_JSON_UTF_8)
                .withPath(calculatePath("clear"))
                .withBody(requestDefinition != null ? requestDefinitionSerializer.serialize(requestDefinition) : "", StandardCharsets.UTF_8),
            true
        );
        return clientClass.cast(this);
    }

    /**
     * Clear all expectations and logs that match the expectation id
     *
     * @param expectationId the expectation id that is used to clear expectations and logs
     */
    public MockServerClient clear(String expectationId) {
        return clear(expectationId(requireExpectationId(expectationId)));
    }

    // a missing id would be sent as an empty body, which clears every expectation
    private static String requireExpectationId(String expectationId) {
        if (isBlank(expectationId)) {
            throw new IllegalArgumentException("the expectation id must not be null or blank; use clear((RequestDefinition) null) to clear everything");
        }
        return expectationId;
    }

    /**
     * Clear all expectations and logs that match the expectation id
     *
     * @param expectationId the expectation id that is used to clear expectations and logs
     */
    public MockServerClient clear(ExpectationId expectationId) {
        requireExpectationId(expectationId != null ? expectationId.getId() : null);
        sendRequest(
            request()
                .withMethod("PUT")
                .withContentType(APPLICATION_JSON_UTF_8)
                .withPath(calculatePath("clear"))
                .withBody(expectationId != null ? expectationIdSerializer.serialize(expectationId) : "", StandardCharsets.UTF_8),
            true
        );
        return clientClass.cast(this);
    }

    /**
     * Clear expectations, logs or both that match the request matcher
     *
     * @param requestDefinition the http request that is matched against when deciding whether to clear each expectation if null all expectations are cleared
     * @param type              the type to clear, EXPECTATION, LOG or BOTH
     */
    public MockServerClient clear(RequestDefinition requestDefinition, ClearType type) {
        sendRequest(
            request()
                .withMethod("PUT")
                .withContentType(APPLICATION_JSON_UTF_8)
                .withPath(calculatePath("clear"))
                .withQueryStringParameter("type", type.name().toLowerCase())
                .withBody(requestDefinition != null ? requestDefinitionSerializer.serialize(requestDefinition) : "", StandardCharsets.UTF_8),
            true
        );
        return clientClass.cast(this);
    }

    /**
     * Clear only the expectations belonging to a single namespace (tenant), leaving
     * expectations in other namespaces and global (no-namespace) expectations intact.
     * <p>
     * This is the primary multi-tenancy teardown call: a CI job that registers its
     * expectations under its own namespace can clean up after itself on a shared
     * MockServer instance without disturbing other tenants. The event log is not
     * namespaced, so logs are left untouched (only {@code expectations} are cleared).
     *
     * @param namespace the namespace (tenant) whose expectations to clear
     */
    public MockServerClient clearByNamespace(String namespace) {
        if (isBlank(namespace)) {
            throw new IllegalArgumentException("the namespace must not be null or blank; a blank namespace would clear every namespace");
        }
        sendRequest(
            request()
                .withMethod("PUT")
                .withContentType(APPLICATION_JSON_UTF_8)
                .withPath(calculatePath("clear"))
                .withQueryStringParameter("type", ClearType.EXPECTATIONS.name().toLowerCase())
                .withQueryStringParameter("namespace", namespace),
            true
        );
        return clientClass.cast(this);
    }

    /**
     * Clear only the expectations that match both the request matcher and the filter, for example by
     * metadata or because they have never matched a request. The event log is left untouched.
     *
     * @param requestDefinition the http request matched against each expectation, or null for all expectations
     * @param filter            the metadata and unmatched selection (limit and offset are not supported for clear)
     * @throws IllegalArgumentException if the filter is null, selects nothing, or pages
     */
    public MockServerClient clear(RequestDefinition requestDefinition, ExpectationFilter filter) {
        // fail closed: a filter that selects nothing would otherwise be sent as a plain clear of every expectation
        if (filter == null || !filter.hasSelection()) {
            throw new IllegalArgumentException("clear with a filter requires a metadata or unmatched selection; use clear(RequestDefinition) to clear without one");
        }
        if (filter.hasPaging()) {
            throw new IllegalArgumentException("limit and offset are not supported for clear");
        }
        sendRequest(
            filter.applyTo(
                request()
                    .withMethod("PUT")
                    .withContentType(APPLICATION_JSON_UTF_8)
                    .withPath(calculatePath("clear"))
                    .withQueryStringParameter("type", ClearType.EXPECTATIONS.name().toLowerCase())
                    .withBody(requestDefinition != null ? requestDefinitionSerializer.serialize(requestDefinition) : "", StandardCharsets.UTF_8)
            ),
            true
        );
        return clientClass.cast(this);
    }

    /**
     * Clear the expectations whose metadata has {@code key} with a value equal to {@code value}. The event log is
     * left untouched.
     *
     * @param key   the metadata key, must not be blank
     * @param value the metadata value, must not be null (use {@link #clearByMetadataKey(String)} for any value, or
     *              {@code NullNode.getInstance()} for a JSON null); matched as described on {@link ExpectationFilter#withMetadata(String, Object)}
     * @throws IllegalArgumentException if the key is blank or the value is null
     */
    public MockServerClient clearByMetadata(String key, Object value) {
        return clear(null, ExpectationFilter.expectationFilter().withMetadata(key, value));
    }

    /**
     * Clear the expectations whose metadata has {@code key}, whatever its value. The event log is left untouched.
     *
     * @param key the metadata key, must not be blank
     */
    public MockServerClient clearByMetadataKey(String key) {
        return clear(null, ExpectationFilter.expectationFilter().withMetadataKey(key));
    }

    /**
     * Clear the active expectations that have never matched a request ("remove unused mocks"). The event log is
     * left untouched.
     */
    public MockServerClient clearUnmatchedExpectations() {
        return clear(null, ExpectationFilter.expectationFilter().withUnmatched(true));
    }

    /**
     * Clear expectations, logs or both that match the expectation id
     *
     * @param expectationId the expectation id that is used to clear expectations and logs
     * @param type          the type to clear, EXPECTATION, LOG or BOTH
     */
    public MockServerClient clear(String expectationId, ClearType type) {
        return clear(expectationId(requireExpectationId(expectationId)), type);
    }

    /**
     * Clear expectations, logs or both that match the expectation id
     *
     * @param expectationId the expectation id that is used to clear expectations and logs
     * @param type          the type to clear, EXPECTATION, LOG or BOTH
     */
    public MockServerClient clear(ExpectationId expectationId, ClearType type) {
        requireExpectationId(expectationId != null ? expectationId.getId() : null);
        sendRequest(
            request()
                .withMethod("PUT")
                .withContentType(APPLICATION_JSON_UTF_8)
                .withPath(calculatePath("clear"))
                .withQueryStringParameter("type", type.name().toLowerCase())
                .withBody(expectationId != null ? expectationIdSerializer.serialize(expectationId) : "", StandardCharsets.UTF_8),
            true
        );
        return clientClass.cast(this);
    }

    /**
     * Verify a list of requests have been sent in the order specified for example:
     * <pre>
     * mockServerClient
     *  .verify(
     *      request()
     *          .withPath("/first_request")
     *          .withBody("some_request_body"),
     *      request()
     *          .withPath("/second_request")
     *          .withBody("some_request_body")
     *  );
     * </pre>
     *
     * @param requestDefinitions the http requests that must be matched for this verification to pass
     * @throws AssertionError if the request has not been found
     */
    public MockServerClient verify(RequestDefinition... requestDefinitions) throws AssertionError {
        return verify(null, requestDefinitions);
    }

    /**
     * Verify a list of requests have been sent in the order specified for example:
     * <pre>
     * mockServerClient
     *  .verify(
     *      request()
     *          .withPath("/first_request")
     *          .withBody("some_request_body"),
     *      request()
     *          .withPath("/second_request")
     *          .withBody("some_request_body")
     *  );
     * </pre>
     *
     * @param maximumNumberOfRequestToReturnInVerificationFailure the maximum number requests return in the error response when the verification fails
     * @param requestDefinitions                                  the http requests that must be matched for this verification to pass
     * @throws AssertionError if the request has not been found
     */
    public MockServerClient verify(Integer maximumNumberOfRequestToReturnInVerificationFailure, RequestDefinition... requestDefinitions) throws AssertionError {
        if (requestDefinitions == null || requestDefinitions.length == 0 || requestDefinitions[0] == null) {
            throw new IllegalArgumentException("verify(RequestDefinition...) requires a non-null non-empty array of RequestDefinition objects");
        }

        try {
            VerificationSequence verificationSequence = new VerificationSequence()
                .withRequests(requestDefinitions)
                .withMaximumNumberOfRequestToReturnInVerificationFailure(maximumNumberOfRequestToReturnInVerificationFailure);
            String result = sendRequest(
                request()
                    .withMethod("PUT")
                    .withContentType(APPLICATION_JSON_UTF_8)
                    .withPath(calculatePath("verifySequence"))
                    .withBody(verificationSequenceSerializer.serialize(verificationSequence), StandardCharsets.UTF_8),
                false
            ).getBodyAsString();

            if (result != null && !result.isEmpty()) {
                throw new AssertionError(result);
            }
        } catch (AuthenticationException authenticationException) {
            throw authenticationException;
        } catch (Throwable throwable) {
            throw new AssertionError(throwable.getMessage());
        }
        return clientClass.cast(this);
    }

    /**
     * Verify a list of requests have been sent in the order specified for example:
     * <pre>
     * mockServerClient
     *  .verify(
     *      request()
     *          .withPath("/first_request")
     *          .withBody("some_request_body"),
     *      request()
     *          .withPath("/second_request")
     *          .withBody("some_request_body")
     *  );
     * </pre>
     *
     * @param expectationIds the http requests that must be matched for this verification to pass
     * @throws AssertionError if the request has not been found
     */
    public MockServerClient verify(String... expectationIds) throws AssertionError {
        return verify(Arrays.stream(expectationIds).map(ExpectationId::expectationId).toArray(ExpectationId[]::new));
    }

    /**
     * Verify a list of requests have been sent in the order specified for example:
     * <pre>
     * mockServerClient
     *  .verify(
     *      request()
     *          .withPath("/first_request")
     *          .withBody("some_request_body"),
     *      request()
     *          .withPath("/second_request")
     *          .withBody("some_request_body")
     *  );
     * </pre>
     *
     * @param expectationIds the http requests that must be matched for this verification to pass
     * @throws AssertionError if the request has not been found
     */
    public MockServerClient verify(ExpectationId... expectationIds) throws AssertionError {
        return verify(null, expectationIds);
    }

    /**
     * Verify a list of requests have been sent in the order specified for example:
     * <pre>
     * mockServerClient
     *  .verify(
     *      request()
     *          .withPath("/first_request")
     *          .withBody("some_request_body"),
     *      request()
     *          .withPath("/second_request")
     *          .withBody("some_request_body")
     *  );
     * </pre>
     *
     * @param maximumNumberOfRequestToReturnInVerificationFailure the maximum number requests return in the error response when the verification fails
     * @param expectationIds                                      the http requests that must be matched for this verification to pass
     * @throws AssertionError if the request has not been found
     */
    public MockServerClient verify(Integer maximumNumberOfRequestToReturnInVerificationFailure, ExpectationId... expectationIds) throws AssertionError {
        if (expectationIds == null || expectationIds.length == 0 || expectationIds[0] == null) {
            throw new IllegalArgumentException("verify(ExpectationId...) requires a non-null non-empty array of ExpectationId objects");
        }

        try {
            VerificationSequence verificationSequence = new VerificationSequence()
                .withExpectationIds(expectationIds)
                .withMaximumNumberOfRequestToReturnInVerificationFailure(maximumNumberOfRequestToReturnInVerificationFailure);
            String result = sendRequest(
                request()
                    .withMethod("PUT")
                    .withContentType(APPLICATION_JSON_UTF_8)
                    .withPath(calculatePath("verifySequence"))
                    .withBody(verificationSequenceSerializer.serialize(verificationSequence), StandardCharsets.UTF_8),
                false
            ).getBodyAsString();

            if (result != null && !result.isEmpty()) {
                throw new AssertionError(result);
            }
        } catch (AuthenticationException authenticationException) {
            throw authenticationException;
        } catch (Throwable throwable) {
            throw new AssertionError(throwable.getMessage());
        }
        return clientClass.cast(this);
    }

    /**
     * Verify a request has been sent for example:
     * <pre>
     * mockServerClient
     *  .verify(
     *      request()
     *          .withPath("/some_path")
     *          .withBody("some_request_body"),
     *      VerificationTimes.exactly(3)
     *  );
     * </pre>
     * VerificationTimes supports multiple static factory methods:
     * <p>
     * once()      - verify the request was only received once
     * exactly(n)  - verify the request was only received exactly n times
     * atLeast(n)  - verify the request was only received at least n times
     *
     * @param requestDefinition the http request that must be matched for this verification to pass
     * @param times             the number of times this request must be matched
     * @throws AssertionError if the request has not been found
     */
    @SuppressWarnings("DuplicatedCode")
    public MockServerClient verify(RequestDefinition requestDefinition, VerificationTimes times) throws AssertionError {
        return verify(requestDefinition, times, (Integer) null);
    }

    /**
     * Verify a request has been sent for example:
     * <pre>
     * mockServerClient
     *  .verify(
     *      request()
     *          .withPath("/some_path")
     *          .withBody("some_request_body"),
     *      VerificationTimes.exactly(3)
     *  );
     * </pre>
     * VerificationTimes supports multiple static factory methods:
     * <p>
     * once()      - verify the request was only received once
     * exactly(n)  - verify the request was only received exactly n times
     * atLeast(n)  - verify the request was only received at least n times
     *
     * @param requestDefinition                                   the http request that must be matched for this verification to pass
     * @param times                                               the number of times this request must be matched
     * @param maximumNumberOfRequestToReturnInVerificationFailure the maximum number requests return in the error response when the verification fails
     * @throws AssertionError if the request has not been found
     */
    @SuppressWarnings("DuplicatedCode")
    public MockServerClient verify(RequestDefinition requestDefinition, VerificationTimes times, Integer maximumNumberOfRequestToReturnInVerificationFailure) throws AssertionError {
        if (requestDefinition == null) {
            throw new IllegalArgumentException("verify(RequestDefinition, VerificationTimes) requires a non null RequestDefinition object");
        }
        if (times == null) {
            throw new IllegalArgumentException("verify(RequestDefinition, VerificationTimes) requires a non null VerificationTimes object");
        }

        try {
            Verification verification = verification()
                .withRequest(requestDefinition)
                .withTimes(times)
                .withMaximumNumberOfRequestToReturnInVerificationFailure(maximumNumberOfRequestToReturnInVerificationFailure);
            String result = sendRequest(
                request()
                    .withMethod("PUT")
                    .withContentType(APPLICATION_JSON_UTF_8)
                    .withPath(calculatePath("verify"))
                    .withBody(verificationSerializer.serialize(verification), StandardCharsets.UTF_8),
                false
            ).getBodyAsString();

            if (result != null && !result.isEmpty()) {
                throw new AssertionError(result);
            }
        } catch (AuthenticationException authenticationException) {
            throw authenticationException;
        } catch (Throwable throwable) {
            throw new AssertionError(throwable.getMessage());
        }
        return clientClass.cast(this);
    }

    /**
     * Verify a request has been sent for example:
     * <pre>
     * mockServerClient
     *  .verify(
     *      request()
     *          .withPath("/some_path")
     *          .withBody("some_request_body"),
     *      VerificationTimes.exactly(3)
     *  );
     * </pre>
     * VerificationTimes supports multiple static factory methods:
     * <p>
     * once()      - verify the request was only received once
     * exactly(n)  - verify the request was only received exactly n times
     * atLeast(n)  - verify the request was only received at least n times
     *
     * @param expectationId the http request that must be matched for this verification to pass
     * @param times         the number of times this request must be matched
     * @throws AssertionError if the request has not been found
     */
    @SuppressWarnings("DuplicatedCode")
    public MockServerClient verify(String expectationId, VerificationTimes times) throws AssertionError {
        return verify(expectationId(expectationId), times);
    }

    /**
     * Verify a request has been sent for example:
     * <pre>
     * mockServerClient
     *  .verify(
     *      request()
     *          .withPath("/some_path")
     *          .withBody("some_request_body"),
     *      VerificationTimes.exactly(3)
     *  );
     * </pre>
     * VerificationTimes supports multiple static factory methods:
     * <p>
     * once()      - verify the request was only received once
     * exactly(n)  - verify the request was only received exactly n times
     * atLeast(n)  - verify the request was only received at least n times
     *
     * @param expectationId the http request that must be matched for this verification to pass
     * @param times         the number of times this request must be matched
     * @throws AssertionError if the request has not been found
     */
    @SuppressWarnings("DuplicatedCode")
    public MockServerClient verify(ExpectationId expectationId, VerificationTimes times) throws AssertionError {
        return verify(expectationId, times, null);
    }

    /**
     * Verify a request has been sent for example:
     * <pre>
     * mockServerClient
     *  .verify(
     *      request()
     *          .withPath("/some_path")
     *          .withBody("some_request_body"),
     *      VerificationTimes.exactly(3)
     *  );
     * </pre>
     * VerificationTimes supports multiple static factory methods:
     * <p>
     * once()      - verify the request was only received once
     * exactly(n)  - verify the request was only received exactly n times
     * atLeast(n)  - verify the request was only received at least n times
     *
     * @param expectationId                                       the http request that must be matched for this verification to pass
     * @param times                                               the number of times this request must be matched
     * @param maximumNumberOfRequestToReturnInVerificationFailure the maximum number requests return in the error response when the verification fails
     * @throws AssertionError if the request has not been found
     */
    @SuppressWarnings("DuplicatedCode")
    public MockServerClient verify(ExpectationId expectationId, VerificationTimes times, Integer maximumNumberOfRequestToReturnInVerificationFailure) throws AssertionError {
        if (expectationId == null) {
            throw new IllegalArgumentException("verify(ExpectationId, VerificationTimes) requires a non null ExpectationId object");
        }
        if (times == null) {
            throw new IllegalArgumentException("verify(ExpectationId, VerificationTimes) requires a non null VerificationTimes object");
        }

        try {
            Verification verification = verification()
                .withExpectationId(expectationId)
                .withTimes(times)
                .withMaximumNumberOfRequestToReturnInVerificationFailure(maximumNumberOfRequestToReturnInVerificationFailure);
            String result = sendRequest(
                request()
                    .withMethod("PUT")
                    .withContentType(APPLICATION_JSON_UTF_8)
                    .withPath(calculatePath("verify"))
                    .withBody(verificationSerializer.serialize(verification), StandardCharsets.UTF_8),
                false
            ).getBodyAsString();

            if (result != null && !result.isEmpty()) {
                throw new AssertionError(result);
            }
        } catch (AuthenticationException authenticationException) {
            throw authenticationException;
        } catch (Throwable throwable) {
            throw new AssertionError(throwable.getMessage());
        }
        return clientClass.cast(this);
    }

    /**
     * Verify multiple verifications, collecting <b>all</b> failures and throwing a single
     * {@link AssertionError} that lists every mismatch. Unlike {@link #verify(Verification...)}
     * and the other {@code verify(...)} methods (which throw on the first failure), this runs
     * every supplied {@link Verification} and only throws once all have been evaluated, so a
     * test sees all failures at once. For example:
     * <pre>
     * mockServerClient
     *  .verifyAll(
     *      verification().withRequest(request().withPath("/one")).withTimes(once()),
     *      verification().withRequest(request().withPath("/two")).withTimes(once())
     *  );
     * </pre>
     *
     * @param verifications the verifications that must all pass
     * @throws AssertionError if any verification fails, with a message listing every failure
     */
    @SuppressWarnings({"DuplicatedCode", "UnusedReturnValue"})
    public MockServerClient verifyAll(Verification... verifications) throws AssertionError {
        if (verifications == null || verifications.length == 0) {
            throw new IllegalArgumentException("verifyAll(Verification...) requires a non-null non-empty array of Verification objects");
        }

        List<String> failures = new ArrayList<>();
        for (Verification verification : verifications) {
            if (verification == null) {
                throw new IllegalArgumentException("verifyAll(Verification...) requires non-null Verification objects");
            }
            try {
                String result = sendRequest(
                    request()
                        .withMethod("PUT")
                        .withContentType(APPLICATION_JSON_UTF_8)
                        .withPath(calculatePath("verify"))
                        .withBody(verificationSerializer.serialize(verification), StandardCharsets.UTF_8),
                    false
                ).getBodyAsString();
                if (result != null && !result.isEmpty()) {
                    failures.add(result);
                }
            } catch (AuthenticationException authenticationException) {
                throw authenticationException;
            } catch (Throwable throwable) {
                failures.add(throwable.getMessage());
            }
        }

        if (!failures.isEmpty()) {
            throw new AssertionError(String.join(System.lineSeparator() + System.lineSeparator(), failures));
        }
        return clientClass.cast(this);
    }

    /**
     * Verify no requests have been sent.
     *
     * @throws AssertionError if any request has been found
     */
    @SuppressWarnings({"DuplicatedCode", "UnusedReturnValue"})
    public MockServerClient verifyZeroInteractions() throws AssertionError {
        try {
            Verification verification = verification().withRequest(request()).withTimes(exactly(0));
            String result = sendRequest(
                request()
                    .withMethod("PUT")
                    .withContentType(APPLICATION_JSON_UTF_8)
                    .withPath(calculatePath("verify"))
                    .withBody(verificationSerializer.serialize(verification), StandardCharsets.UTF_8),
                false
            ).getBodyAsString();

            if (result != null && !result.isEmpty()) {
                throw new AssertionError(result);
            }
        } catch (AuthenticationException authenticationException) {
            throw authenticationException;
        } catch (Throwable throwable) {
            throw new AssertionError(throwable.getMessage());
        }
        return clientClass.cast(this);
    }

    /**
     * Default interval used between verification poll attempts by the timeout-aware
     * {@code verify(..., Duration)} and {@code verifyNever(..., Duration)} methods.
     */
    private static final Duration DEFAULT_VERIFY_POLL_INTERVAL = Duration.ofMillis(100);

    /**
     * Eventual verification: poll the event log, retrying the supplied verification until it
     * passes or the timeout expires. This is useful when the application under test sends
     * requests asynchronously (fire-and-forget, background workers), so the request may not
     * have arrived at MockServer at the instant the test calls verify. Instead of a single
     * snapshot check (like {@link #verify(Verification)}), this re-runs the verification with a
     * small backoff until it passes or the window elapses, throwing the last failure on timeout.
     * <pre>
     * mockServerClient
     *  .verify(
     *      request().withPath("/some_path"),
     *      VerificationTimes.once(),
     *      Duration.ofSeconds(5)
     *  );
     * </pre>
     * This is implemented purely client-side (a poll loop over the standard verify endpoint);
     * no server-side change or wait is involved.
     *
     * @param requestDefinition the http request that must be matched for this verification to pass
     * @param times             the number of times this request must be matched
     * @param timeout           the maximum time to wait for the verification to pass
     * @throws AssertionError if the verification does not pass before the timeout expires
     */
    @SuppressWarnings("UnusedReturnValue")
    public MockServerClient verify(RequestDefinition requestDefinition, VerificationTimes times, Duration timeout) throws AssertionError {
        if (requestDefinition == null) {
            throw new IllegalArgumentException("verify(RequestDefinition, VerificationTimes, Duration) requires a non null RequestDefinition object");
        }
        if (times == null) {
            throw new IllegalArgumentException("verify(RequestDefinition, VerificationTimes, Duration) requires a non null VerificationTimes object");
        }
        return verify(verification().withRequest(requestDefinition).withTimes(times), timeout);
    }

    /**
     * Eventual verification: poll the event log, retrying the supplied {@link Verification} until
     * it passes or the timeout expires. See {@link #verify(RequestDefinition, VerificationTimes, Duration)}
     * for the rationale; this generic overload accepts a fully built {@link Verification} (so it
     * also covers response and expectation-id verifications).
     * <pre>
     * mockServerClient
     *  .verify(
     *      verification()
     *          .withRequest(request().withPath("/some_path"))
     *          .withTimes(VerificationTimes.atLeast(1)),
     *      Duration.ofSeconds(5)
     *  );
     * </pre>
     *
     * @param verification the verification object containing the request, response, and/or times to verify
     * @param timeout      the maximum time to wait for the verification to pass
     * @throws AssertionError if the verification does not pass before the timeout expires
     */
    @SuppressWarnings("UnusedReturnValue")
    public MockServerClient verify(Verification verification, Duration timeout) throws AssertionError {
        if (verification == null) {
            throw new IllegalArgumentException("verify(Verification, Duration) requires a non null Verification object");
        }
        if (timeout == null || timeout.isNegative()) {
            throw new IllegalArgumentException("verify(Verification, Duration) requires a non null non-negative Duration object");
        }

        long deadlineNanos = System.nanoTime() + timeout.toNanos();
        String lastFailure;
        while (true) {
            lastFailure = attemptVerification(verification, false);
            if (lastFailure == null) {
                return clientClass.cast(this);
            }
            if (System.nanoTime() - deadlineNanos >= 0) {
                throw new AssertionError(lastFailure);
            }
            sleepBeforeNextPoll(deadlineNanos);
        }
    }

    /**
     * Negative-within-timeout verification: assert that the supplied verification stays
     * <b>unsatisfied</b> for the whole window. This is useful for asserting "no matching request
     * was made within N seconds" — the opposite of eventual verification. The verification is
     * polled repeatedly for the duration of the window; if it ever passes (the condition becomes
     * met), an {@link AssertionError} is thrown immediately. If the window elapses without the
     * verification ever passing, the method returns normally.
     * <pre>
     * mockServerClient
     *  .verifyNever(
     *      request().withPath("/should_not_be_called"),
     *      Duration.ofSeconds(2)
     *  );
     * </pre>
     * The supplied request is verified with {@link VerificationTimes#atLeast(int)} {@code (1)} —
     * i.e. the window fails the moment one matching request is observed. Implemented purely
     * client-side (a poll loop over the standard verify endpoint).
     *
     * @param requestDefinition the http request that must <b>not</b> be matched during the window
     * @param window            the time to keep checking that no matching request arrives
     * @throws AssertionError if a matching request is observed before the window elapses
     */
    @SuppressWarnings("UnusedReturnValue")
    public MockServerClient verifyNever(RequestDefinition requestDefinition, Duration window) throws AssertionError {
        if (requestDefinition == null) {
            throw new IllegalArgumentException("verifyNever(RequestDefinition, Duration) requires a non null RequestDefinition object");
        }
        return verifyNever(verification().withRequest(requestDefinition).withTimes(VerificationTimes.atLeast(1)), window);
    }

    /**
     * Negative-within-timeout verification: assert that the supplied {@link Verification} stays
     * <b>unsatisfied</b> for the whole window. See {@link #verifyNever(RequestDefinition, Duration)}
     * for the rationale; this generic overload accepts a fully built {@link Verification} so the
     * caller controls the matched condition (e.g. {@code atLeast(1)} to fail on the first match,
     * or another {@link VerificationTimes} to fail when a threshold is reached).
     *
     * @param verification the verification that must <b>not</b> pass during the window
     * @param window       the time to keep checking that the verification does not pass
     * @throws AssertionError if the verification passes before the window elapses
     */
    @SuppressWarnings("UnusedReturnValue")
    public MockServerClient verifyNever(Verification verification, Duration window) throws AssertionError {
        if (verification == null) {
            throw new IllegalArgumentException("verifyNever(Verification, Duration) requires a non null Verification object");
        }
        if (window == null || window.isNegative()) {
            throw new IllegalArgumentException("verifyNever(Verification, Duration) requires a non null non-negative Duration object");
        }

        long deadlineNanos = System.nanoTime() + window.toNanos();
        while (true) {
            if (attemptVerification(verification, true) == null) {
                throw new AssertionError("Found request matching verification within the " + window + " window that was expected to find no match" + NEW_LINE + verificationSerializer.serialize(verification));
            }
            if (System.nanoTime() - deadlineNanos >= 0) {
                return clientClass.cast(this);
            }
            sleepBeforeNextPoll(deadlineNanos);
        }
    }

    /**
     * Run a single verification attempt against the server, returning the failure message
     * (the verify endpoint's non-empty response body) or {@code null} when the verification passes.
     * Authentication failures are rethrown rather than treated as a verification failure so that
     * the poll loop does not silently retry an unauthorized client. With {@code failOnError} any
     * other error, such as a verification the server rejects (400), is rethrown too: verifyNever
     * reads a failure message as "not matched", so a swallowed error would let it pass.
     */
    private String attemptVerification(Verification verification, boolean failOnError) throws AssertionError {
        try {
            String result = sendRequest(
                request()
                    .withMethod("PUT")
                    .withContentType(APPLICATION_JSON_UTF_8)
                    .withPath(calculatePath("verify"))
                    .withBody(verificationSerializer.serialize(verification), StandardCharsets.UTF_8),
                false
            ).getBodyAsString();
            return result == null || result.isEmpty() ? null : result;
        } catch (AuthenticationException authenticationException) {
            throw authenticationException;
        } catch (Throwable throwable) {
            if (failOnError) {
                throw new AssertionError(throwable.getMessage(), throwable);
            }
            // never return null here: a null would be read as "verification passed" by the poll loop,
            // turning an exception into a silent false-positive
            String message = throwable.getMessage();
            return message != null ? message : throwable.getClass().getName();
        }
    }

    /**
     * Sleep for the poll interval, clamped so it never overshoots the deadline. Restores the
     * interrupt flag and aborts the poll loop if the thread is interrupted while waiting.
     */
    private void sleepBeforeNextPoll(long deadlineNanos) throws AssertionError {
        long remainingMillis = (deadlineNanos - System.nanoTime()) / 1_000_000L;
        long sleepMillis = Math.min(DEFAULT_VERIFY_POLL_INTERVAL.toMillis(), Math.max(0L, remainingMillis));
        if (sleepMillis <= 0) {
            return;
        }
        try {
            Thread.sleep(sleepMillis);
        } catch (InterruptedException interruptedException) {
            Thread.currentThread().interrupt();
            throw new AssertionError("Verification polling was interrupted", interruptedException);
        }
    }

    /**
     * Verify a request-response pair has been recorded for example:
     * <pre>
     * mockServerClient
     *  .verify(
     *      request()
     *          .withPath("/some_path"),
     *      response()
     *          .withStatusCode(200),
     *      VerificationTimes.atLeast(1)
     *  );
     * </pre>
     * VerificationTimes supports multiple static factory methods:
     * <p>
     * once()      - verify the request-response pair was matched only once
     * exactly(n)  - verify the request-response pair was matched exactly n times
     * atLeast(n)  - verify the request-response pair was matched at least n times
     *
     * @param requestDefinition the http request that must be matched for this verification to pass (may be null to match any request)
     * @param httpResponse      the http response that must be matched for this verification to pass
     * @param times             the number of times this request-response pair must be matched
     * @throws AssertionError if the request-response pair has not been found
     */
    public MockServerClient verify(RequestDefinition requestDefinition, org.mockserver.model.HttpResponse httpResponse, VerificationTimes times) throws AssertionError {
        if (httpResponse == null) {
            throw new IllegalArgumentException("verify(RequestDefinition, HttpResponse, VerificationTimes) requires a non null HttpResponse object");
        }
        if (times == null) {
            throw new IllegalArgumentException("verify(RequestDefinition, HttpResponse, VerificationTimes) requires a non null VerificationTimes object");
        }

        try {
            Verification verificationObj = verification()
                .withRequest(requestDefinition)
                .withResponse(httpResponse)
                .withTimes(times);
            String result = sendRequest(
                request()
                    .withMethod("PUT")
                    .withContentType(APPLICATION_JSON_UTF_8)
                    .withPath(calculatePath("verify"))
                    .withBody(verificationSerializer.serialize(verificationObj), StandardCharsets.UTF_8),
                false
            ).getBodyAsString();

            if (result != null && !result.isEmpty()) {
                throw new AssertionError(result);
            }
        } catch (AuthenticationException authenticationException) {
            throw authenticationException;
        } catch (Throwable throwable) {
            throw new AssertionError(throwable.getMessage());
        }
        return clientClass.cast(this);
    }

    /**
     * Verify a response has been recorded (matching any request) for example:
     * <pre>
     * mockServerClient
     *  .verify(
     *      response()
     *          .withStatusCode(200),
     *      VerificationTimes.atLeast(1)
     *  );
     * </pre>
     *
     * @param httpResponse the http response that must be matched for this verification to pass
     * @param times        the number of times this response must be matched
     * @throws AssertionError if the response has not been found
     */
    public MockServerClient verify(org.mockserver.model.HttpResponse httpResponse, VerificationTimes times) throws AssertionError {
        return verify((RequestDefinition) null, httpResponse, times);
    }

    /**
     * Verify a response has been recorded (matching any request), defaulting to at least once
     * <pre>
     * mockServerClient
     *  .verify(
     *      response()
     *          .withStatusCode(200)
     *  );
     * </pre>
     *
     * @param httpResponse the http response that must be matched for this verification to pass
     * @throws AssertionError if the response has not been found
     */
    public MockServerClient verify(org.mockserver.model.HttpResponse httpResponse) throws AssertionError {
        return verify((RequestDefinition) null, httpResponse, atLeast(1));
    }

    /**
     * Verify using a pre-built Verification object for advanced use cases such as
     * request-response pair verification:
     * <pre>
     * mockServerClient
     *  .verify(
     *      verification()
     *          .withRequest(request().withPath("/some_path"))
     *          .withResponse(response().withStatusCode(200))
     *          .withTimes(VerificationTimes.atLeast(1))
     *  );
     * </pre>
     *
     * @param verification the verification object containing the request, response, and/or times to verify
     * @throws AssertionError if the verification fails
     */
    @SuppressWarnings("DuplicatedCode")
    public MockServerClient verify(Verification verification) throws AssertionError {
        if (verification == null) {
            throw new IllegalArgumentException("verify(Verification) requires a non null Verification object");
        }

        try {
            String result = sendRequest(
                request()
                    .withMethod("PUT")
                    .withContentType(APPLICATION_JSON_UTF_8)
                    .withPath(calculatePath("verify"))
                    .withBody(verificationSerializer.serialize(verification), StandardCharsets.UTF_8),
                false
            ).getBodyAsString();

            if (result != null && !result.isEmpty()) {
                throw new AssertionError(result);
            }
        } catch (AuthenticationException authenticationException) {
            throw authenticationException;
        } catch (Throwable throwable) {
            throw new AssertionError(throwable.getMessage());
        }
        return clientClass.cast(this);
    }

    /**
     * Verify using a pre-built VerificationSequence object for advanced use cases such as
     * request-response sequence verification:
     * <pre>
     * mockServerClient
     *  .verify(
     *      verificationSequence()
     *          .withRequests(request().withPath("/first"), request().withPath("/second"))
     *          .withResponses(response().withStatusCode(200), response().withStatusCode(201))
     *  );
     * </pre>
     *
     * @param verificationSequence the verification sequence object containing the requests, responses, and/or expectation ids to verify
     * @throws AssertionError if the verification sequence fails
     */
    @SuppressWarnings("DuplicatedCode")
    public MockServerClient verify(VerificationSequence verificationSequence) throws AssertionError {
        if (verificationSequence == null) {
            throw new IllegalArgumentException("verify(VerificationSequence) requires a non null VerificationSequence object");
        }

        try {
            String result = sendRequest(
                request()
                    .withMethod("PUT")
                    .withContentType(APPLICATION_JSON_UTF_8)
                    .withPath(calculatePath("verifySequence"))
                    .withBody(verificationSequenceSerializer.serialize(verificationSequence), StandardCharsets.UTF_8),
                false
            ).getBodyAsString();

            if (result != null && !result.isEmpty()) {
                throw new AssertionError(result);
            }
        } catch (AuthenticationException authenticationException) {
            throw authenticationException;
        } catch (Throwable throwable) {
            throw new AssertionError(throwable.getMessage());
        }
        return clientClass.cast(this);
    }

    /**
     * Retrieve the recorded HTTP requests that match the httpRequest parameter, use null for the parameter to retrieve all requests;
     * to retrieve DNS queries or binary messages use {@link #retrieveRecordedRequestDefinitions(RequestDefinition)}
     *
     * @param requestDefinition the http request that is matched against when deciding whether to return each request, use null for the parameter to retrieve for all requests
     * @return an array of all requests that have been recorded by the MockServer in the order they have been received and including duplicates where the same request has been received multiple times
     */
    public HttpRequest[] retrieveRecordedRequests(RequestDefinition requestDefinition) {
        return Arrays.stream(retrieveRecordedRequestDefinitions(requestDefinition)).filter(HttpRequest.class::isInstance).map(HttpRequest.class::cast).toArray(HttpRequest[]::new);
    }

    /**
     * Retrieve the recorded requests that match the requestDefinition parameter, which can be an http, DNS or binary request;
     * DNS queries and binary messages are returned only for a DNS or binary requestDefinition, use null for the parameter to retrieve all http requests
     *
     * @param requestDefinition the request that is matched against when deciding whether to return each request, use null for the parameter to retrieve all http requests
     * @return an array of all requests that have been recorded by the MockServer in the order they have been received and including duplicates where the same request has been received multiple times
     */
    public RequestDefinition[] retrieveRecordedRequestDefinitions(RequestDefinition requestDefinition) {
        String recordedRequests = retrieveRecordedRequests(requestDefinition, Format.JSON);
        if (isNotBlank(recordedRequests) && !recordedRequests.equals("[]")) {
            return requestDefinitionSerializer.deserializeArray(recordedRequests);
        }
        return new RequestDefinition[0];
    }

    /**
     * Retrieve the recorded requests that match the httpRequest parameter, use null for the parameter to retrieve all requests
     *
     * @param requestDefinition the http request that is matched against when deciding whether to return each request, use null for the parameter to retrieve for all requests
     * @param format            the format to retrieve the expectations, either JAVA or JSON
     * @return an array of all requests that have been recorded by the MockServer in the order they have been received and including duplicates where the same request has been received multiple times
     */
    public String retrieveRecordedRequests(RequestDefinition requestDefinition, Format format) {
        HttpResponse httpResponse = sendRequest(
            request()
                .withMethod("PUT")
                .withContentType(APPLICATION_JSON_UTF_8)
                .withPath(calculatePath("retrieve"))
                .withQueryStringParameter("type", RetrieveType.REQUESTS.name())
                .withQueryStringParameter("format", format.name())
                .withBody(requestDefinition != null ? requestDefinitionSerializer.serialize(requestDefinition) : "", StandardCharsets.UTF_8),
            true
        );
        return httpResponse.getBodyAsString();
    }

    /**
     * Retrieve the recorded requests and responses that match the httpRequest parameter, use null for the parameter to retrieve all requests and responses
     *
     * @param requestDefinition the http request that is matched against when deciding whether to return each request (and its corresponding response), use null for the parameter to retrieve for all requests
     * @return an array of all requests and responses that have been recorded by the MockServer in the order they have been received and including duplicates where the same request has been received multiple times
     */
    public LogEventRequestAndResponse[] retrieveRecordedRequestsAndResponses(RequestDefinition requestDefinition) {
        String recordedRequests = retrieveRecordedRequestsAndResponses(requestDefinition, Format.JSON);
        if (isNotBlank(recordedRequests) && !recordedRequests.equals("[]")) {
            return httpRequestResponseSerializer.deserializeArray(recordedRequests);
        } else {
            return new LogEventRequestAndResponse[0];
        }
    }

    /**
     * Retrieve the recorded requests that match the httpRequest parameter, use null for the parameter to retrieve all requests
     *
     * @param requestDefinition the http request that is matched against when deciding whether to return each request, use null for the parameter to retrieve for all requests
     * @param format            the format to retrieve the expectations, either JAVA or JSON
     * @return an array of all requests that have been recorded by the MockServer in the order they have been received and including duplicates where the same request has been received multiple times
     */
    public String retrieveRecordedRequestsAndResponses(RequestDefinition requestDefinition, Format format) {
        HttpResponse httpResponse = sendRequest(
            request()
                .withMethod("PUT")
                .withContentType(APPLICATION_JSON_UTF_8)
                .withPath(calculatePath("retrieve"))
                .withQueryStringParameter("type", RetrieveType.REQUEST_RESPONSES.name())
                .withQueryStringParameter("format", format.name())
                .withBody(requestDefinition != null ? requestDefinitionSerializer.serialize(requestDefinition) : "", StandardCharsets.UTF_8),
            true
        );
        return httpResponse.getBodyAsString();
    }

    /**
     * Retrieve the request-response combinations that have been recorded as a list of expectations, only those that match the httpRequest parameter are returned, use null to retrieve all requests
     *
     * @param requestDefinition the http request that is matched against when deciding whether to return each request, use null for the parameter to retrieve for all requests
     * @return an array of all expectations that have been recorded by the MockServer in the order they have been received and including duplicates where the same request has been received multiple times
     */
    public Expectation[] retrieveRecordedExpectations(RequestDefinition requestDefinition) {
        String recordedExpectations = retrieveRecordedExpectations(requestDefinition, Format.JSON);
        if (isNotBlank(recordedExpectations) && !recordedExpectations.equals("[]")) {
            return expectationSerializer.deserializeArray(recordedExpectations, true);
        } else {
            return new Expectation[0];
        }
    }

    /**
     * Retrieve one page of the recorded expectations that match the request matcher, in the order the requests were
     * received.
     *
     * @param requestDefinition the http request matched against each recorded request, or null for all
     * @param offset            the number of recorded expectations to skip, zero or more
     * @param limit             the maximum number to return, zero or more
     * @return the page of recorded expectations
     */
    public Expectation[] retrieveRecordedExpectations(RequestDefinition requestDefinition, int offset, int limit) {
        HttpResponse httpResponse = sendRequest(
            ExpectationFilter.expectationFilter().withOffset(offset).withLimit(limit).applyTo(
                request()
                    .withMethod("PUT")
                    .withContentType(APPLICATION_JSON_UTF_8)
                    .withPath(calculatePath("retrieve"))
                    .withQueryStringParameter("type", RetrieveType.RECORDED_EXPECTATIONS.name())
                    .withQueryStringParameter("format", Format.JSON.name())
                    .withBody(requestDefinition != null ? requestDefinitionSerializer.serialize(requestDefinition) : "", StandardCharsets.UTF_8)
            ),
            true
        );
        String recordedExpectations = httpResponse.getBodyAsString();
        if (isNotBlank(recordedExpectations) && !recordedExpectations.equals("[]")) {
            return expectationSerializer.deserializeArray(recordedExpectations, true);
        } else {
            return new Expectation[0];
        }
    }

    /**
     * Retrieve the request-response combinations that have been recorded as a list of expectations, only those that match the httpRequest parameter are returned, use null to retrieve all requests
     *
     * @param requestDefinition the http request that is matched against when deciding whether to return each request, use null for the parameter to retrieve for all requests
     * @param format            the format to retrieve the expectations, either JAVA or JSON
     * @return an array of all expectations that have been recorded by the MockServer in the order they have been received and including duplicates where the same request has been received multiple times
     */
    public String retrieveRecordedExpectations(RequestDefinition requestDefinition, Format format) {
        return retrieveRecordedExpectations(requestDefinition, format, null);
    }

    /**
     * Retrieve the request-response combinations that have been recorded as a list of expectations, shaped by the
     * given options, for example to replay a request recorded with different responses in order
     * ({@link RecordedExpectationsOptions#withRepeatsAsScenarios(Boolean)}), keep only some request headers
     * ({@link RecordedExpectationsOptions#withCaptureHeaders(String...)}) or move large bodies into files
     * ({@link RecordedExpectationsOptions#withExtractBodiesLargerThan(Long)}).
     *
     * @param requestDefinition the http request that is matched against when deciding whether to return each request, use null for the parameter to retrieve for all requests
     * @param options           the options to apply, use null for none
     * @return the recorded expectations
     */
    public Expectation[] retrieveRecordedExpectations(RequestDefinition requestDefinition, RecordedExpectationsOptions options) {
        String recordedExpectations = retrieveRecordedExpectations(requestDefinition, Format.JSON, options);
        if (isNotBlank(recordedExpectations) && !recordedExpectations.equals("[]")) {
            return expectationSerializer.deserializeArray(recordedExpectations, true);
        } else {
            return new Expectation[0];
        }
    }

    /**
     * As {@link #retrieveRecordedExpectations(RequestDefinition, RecordedExpectationsOptions)}, in the given format.
     *
     * @param requestDefinition the http request that is matched against when deciding whether to return each request, use null for the parameter to retrieve for all requests
     * @param format            the format to retrieve the expectations in
     * @param options           the options to apply, use null for none
     * @return the recorded expectations in the given format
     */
    public String retrieveRecordedExpectations(RequestDefinition requestDefinition, Format format, RecordedExpectationsOptions options) {
        HttpRequest retrieveRequest = request()
            .withMethod("PUT")
            .withContentType(APPLICATION_JSON_UTF_8)
            .withPath(calculatePath("retrieve"))
            .withQueryStringParameter("type", RetrieveType.RECORDED_EXPECTATIONS.name())
            .withQueryStringParameter("format", format.name())
            .withBody(requestDefinition != null ? requestDefinitionSerializer.serialize(requestDefinition) : "", StandardCharsets.UTF_8);
        if (options != null) {
            options.addTo(retrieveRequest);
        }
        HttpResponse httpResponse = sendRequest(retrieveRequest, true);
        return httpResponse.getBodyAsString();
    }

    /**
     * Retrieve the logs associated to a specific requests, this shows all logs for expectation matching, verification, clearing, etc
     *
     * @param requestDefinition the http request that is matched against when deciding whether to return each request, use null for the parameter to retrieve for all requests
     * @return all log messages recorded by the MockServer when creating expectations, matching expectations, performing verification, clearing logs, etc
     */
    public String retrieveLogMessages(RequestDefinition requestDefinition) {
        HttpResponse httpResponse = sendRequest(
            request()
                .withMethod("PUT")
                .withContentType(APPLICATION_JSON_UTF_8)
                .withPath(calculatePath("retrieve"))
                .withQueryStringParameter("type", RetrieveType.LOGS.name())
                .withBody(requestDefinition != null ? requestDefinitionSerializer.serialize(requestDefinition) : "", StandardCharsets.UTF_8),
            true
        );
        return httpResponse.getBodyAsString();
    }

    /**
     * Retrieve the logs associated to a specific requests, this shows all logs for expectation matching, verification, clearing, etc
     *
     * @param requestDefinition the http request that is matched against when deciding whether to return each request, use null for the parameter to retrieve for all requests
     * @return an array of all log messages recorded by the MockServer when creating expectations, matching expectations, performing verification, clearing logs, etc
     */
    public String[] retrieveLogMessagesArray(RequestDefinition requestDefinition) {
        return retrieveLogMessages(requestDefinition).split(LOG_SEPARATOR);
    }

    /**
     * Retrieve log entries as typed objects that match the httpRequest parameter, use null for the parameter to retrieve all log entries.
     * Uses the LOG_ENTRIES format to get structured JSON log entries from the server.
     *
     * @param requestDefinition the http request that is matched against when deciding whether to return each log entry, use null to retrieve all
     * @return an array of all log entries that match
     */
    public LogEntry[] retrieveLogEntries(RequestDefinition requestDefinition) {
        HttpResponse httpResponse = sendRequest(
            request()
                .withMethod("PUT")
                .withContentType(APPLICATION_JSON_UTF_8)
                .withPath(calculatePath("retrieve"))
                .withQueryStringParameter("type", RetrieveType.LOGS.name())
                .withQueryStringParameter("format", Format.LOG_ENTRIES.name())
                .withBody(requestDefinition != null ? requestDefinitionSerializer.serialize(requestDefinition) : "", StandardCharsets.UTF_8),
            true
        );
        return logEntrySerializer.deserializeArray(httpResponse.getBodyAsString());
    }

    /**
     * Retrieve log entries as typed objects filtered by correlation ID.
     * A correlationId groups all log entries for a single incoming HTTP request lifecycle.
     *
     * @param correlationId the correlation ID to filter by
     * @return an array of all log entries for the given correlation ID
     */
    public LogEntry[] retrieveLogEntriesByCorrelationId(String correlationId) {
        HttpResponse httpResponse = sendRequest(
            request()
                .withMethod("PUT")
                .withContentType(APPLICATION_JSON_UTF_8)
                .withPath(calculatePath("retrieve"))
                .withQueryStringParameter("type", RetrieveType.LOGS.name())
                .withQueryStringParameter("format", Format.LOG_ENTRIES.name())
                .withQueryStringParameter("correlationId", correlationId)
                .withBody("", StandardCharsets.UTF_8),
            true
        );
        return logEntrySerializer.deserializeArray(httpResponse.getBodyAsString());
    }

    /**
     * Retrieve log entries as typed objects that match the httpRequest parameter, filtered to a time window.
     * Only entries with epochTime between fromEpochMillis (inclusive) and toEpochMillis (exclusive) are returned.
     * Note: time filtering is performed client-side after fetching all matching entries from the server.
     *
     * @param requestDefinition the http request that is matched against when deciding whether to return each log entry, use null to retrieve all
     * @param fromEpochMillis   start of time window (inclusive), milliseconds since epoch
     * @param toEpochMillis     end of time window (exclusive), milliseconds since epoch
     * @return an array of log entries within the specified time window
     */
    public LogEntry[] retrieveLogEntries(RequestDefinition requestDefinition, long fromEpochMillis, long toEpochMillis) {
        LogEntry[] allEntries = retrieveLogEntries(requestDefinition);
        return Arrays.stream(allEntries)
            .filter(entry -> entry.getEpochTime() >= fromEpochMillis && entry.getEpochTime() < toEpochMillis)
            .toArray(LogEntry[]::new);
    }

    /**
     * Specify an unlimited expectation that will respond regardless of the number of matching http
     * for example:
     * <pre>
     * mockServerClient
     *  .when(
     *      request()
     *          .withPath("/some_path")
     *          .withBody("some_request_body")
     *  )
     *  .respond(
     *      response()
     *          .withBody("some_response_body")
     *          .withHeader("responseName", "responseValue")
     *  )
     * </pre>
     *
     * @param requestDefinition the http request that must be matched for this expectation to respond
     * @return an Expectation object that can be used to specify the response
     */
    public ForwardChainExpectation when(RequestDefinition requestDefinition) {
        return when(requestDefinition, Times.unlimited());
    }

    /**
     * Specify a limited expectation that will respond a specified number of times when the http is matched
     * <p>
     * Example use:
     * <pre>
     * mockServerClient
     *  .when(
     *      request()
     *          .withPath("/some_path")
     *          .withBody("some_request_body"),
     *      Times.exactly(5)
     *  )
     *  .respond(
     *      response()
     *          .withBody("some_response_body")
     *          .withHeader("responseName", "responseValue")
     *  )
     * </pre>
     *
     * @param requestDefinition the http request that must be matched for this expectation to respond
     * @param times             the number of times to respond when this http is matched
     * @return an Expectation object that can be used to specify the response
     */
    public ForwardChainExpectation when(RequestDefinition requestDefinition, Times times) {
        return new ForwardChainExpectation(configuration, MOCK_SERVER_LOGGER, getMockServerEventBus(), this, new Expectation(requestDefinition, times, TimeToLive.unlimited(), 0));
    }

    /**
     * Specify a limited expectation that will respond a specified number of times when the http is matched
     * <p>
     * Example use:
     * <pre>
     * mockServerClient
     *  .when(
     *      request()
     *          .withPath("/some_path")
     *          .withBody("some_request_body"),
     *      Times.exactly(5),
     *      TimeToLive.exactly(TimeUnit.SECONDS, 120)
     *  )
     *  .respond(
     *      response()
     *          .withBody("some_response_body")
     *          .withHeader("responseName", "responseValue")
     *  )
     * </pre>
     *
     * @param requestDefinition the http request that must be matched for this expectation to respond
     * @param times             the number of times to respond when this http is matched
     * @param timeToLive        the length of time from when the server receives the expectation that the expectation should be active
     * @return an Expectation object that can be used to specify the response
     */
    public ForwardChainExpectation when(RequestDefinition requestDefinition, Times times, TimeToLive timeToLive) {
        return new ForwardChainExpectation(configuration, MOCK_SERVER_LOGGER, getMockServerEventBus(), this, new Expectation(requestDefinition, times, timeToLive, 0));
    }

    /**
     * Specify a limited expectation that will respond a specified number of times when the http is matched and will be matched according to priority as follows:
     * <p>
     * - higher priority expectation will be matched first
     * - identical priority expectations will be match in the order they were submitted
     * - default priority is 0
     * <p>
     * Example use:
     * <pre>
     * mockServerClient
     *  .when(
     *      request()
     *          .withPath("/some_path")
     *          .withBody("some_request_body"),
     *      Times.exactly(5),
     *      TimeToLive.exactly(TimeUnit.SECONDS, 120),
     *      10
     *  )
     *  .respond(
     *      response()
     *          .withBody("some_response_body")
     *          .withHeader("responseName", "responseValue")
     *  )
     * </pre>
     *
     * @param requestDefinition the http request that must be matched for this expectation to respond
     * @param times             the number of times to respond when this http is matched
     * @param timeToLive        the length of time from when the server receives the expectation that the expectation should be active
     * @param priority          the priority for the expectation when matching, higher priority expectation will be matched first, identical priority expectations will be match in the order they were submitted
     * @return an Expectation object that can be used to specify the response
     */
    public ForwardChainExpectation when(RequestDefinition requestDefinition, Times times, TimeToLive timeToLive, Integer priority) {
        return new ForwardChainExpectation(configuration, MOCK_SERVER_LOGGER, getMockServerEventBus(), this, new Expectation(requestDefinition, times, timeToLive, priority));
    }

    /**
     * Mock a complete OpenID Connect / OAuth2 identity provider with a single call, using the default
     * configuration (standard endpoint paths, RS256 signing, and an issuer derived per request from the
     * {@code Host} header so the provider works unchanged on a random/mapped port).
     *
     * <p>This generates and upserts the discovery document, JWKS, token, authorize, userinfo,
     * introspection, revocation, and end-session endpoints, all signed with a freshly generated key
     * pair whose public key is published at the JWKS endpoint so issued tokens verify end-to-end.
     *
     * @return the upserted OIDC provider expectations
     */
    public Expectation[] mockOpenIdProvider() {
        return mockOpenIdProvider(null);
    }

    /**
     * Mock a complete OpenID Connect / OAuth2 identity provider with a single call.
     *
     * <p>This generates and upserts the discovery document, JWKS, token, authorize, userinfo,
     * introspection, revocation, and end-session endpoints. Tokens are minted at request time and
     * signed with the configured (or generated) key pair, whose public key is published at the JWKS
     * endpoint so issued tokens verify end-to-end. The configuration controls the issuer, endpoint
     * paths, subject / clientId / audience / scopes, token expiry, additional claims, signing
     * algorithm and key material, and the negative-testing flags.
     *
     * @param configuration the OIDC provider configuration, or {@code null} to use the defaults
     * @return the upserted OIDC provider expectations
     */
    public Expectation[] mockOpenIdProvider(OidcProviderConfiguration configuration) {
        String body = "";
        if (configuration != null) {
            try {
                ObjectMapper objectMapper = ObjectMapperFactory.createObjectMapper();
                ObjectNode configNode = objectMapper.valueToTree(configuration);
                // clientSecret and privateKeyPem are WRITE_ONLY on the configuration so the SERVER
                // never serializes the secret back out (no credential / key leak via JSON / discovery /
                // response). That same annotation, however, excludes them from this outbound
                // serialization, which would silently drop a user-supplied value. Re-add them
                // explicitly for this CLIENT -> server control-plane PUT only, so a supplied secret
                // actually reaches the provider.
                if (isNotBlank(configuration.getClientSecret())) {
                    configNode.put("clientSecret", configuration.getClientSecret());
                }
                if (isNotBlank(configuration.getPrivateKeyPem())) {
                    configNode.put("privateKeyPem", configuration.getPrivateKeyPem());
                }
                body = objectMapper.writeValueAsString(configNode);
            } catch (Throwable throwable) {
                throw new ClientException(formatLogMessage("error:{}while serializing OIDC provider configuration:{}", throwable.getMessage(), configuration), throwable);
            }
        }
        HttpResponse httpResponse = sendRequest(
            request()
                .withMethod("PUT")
                .withContentType(APPLICATION_JSON_UTF_8)
                .withPath(calculatePath("oidc"))
                .withBody(body, StandardCharsets.UTF_8),
            false
        );
        if (httpResponse != null && httpResponse.getStatusCode() != 201) {
            throw new ClientException(formatLogMessage("error:{}while submitting OIDC provider configuration:{}", httpResponse, configuration));
        }
        if (httpResponse != null && isNotBlank(httpResponse.getBodyAsString())) {
            return expectationSerializer.deserializeArray(httpResponse.getBodyAsString(), true);
        }
        return new Expectation[0];
    }

    /**
     * Specify OpenAPI and operations and responses to create matchers and example responses
     *
     * @param openAPIExpectations the OpenAPI and operations and responses to create matchers and example responses
     * @return upserted expectations
     */
    public Expectation[] upsert(OpenAPIExpectation... openAPIExpectations) {
        if (openAPIExpectations != null) {
            HttpResponse httpResponse = null;
            if (openAPIExpectations.length == 1) {
                httpResponse =
                    sendRequest(
                        request()
                            .withMethod("PUT")
                            .withContentType(APPLICATION_JSON_UTF_8)
                            .withPath(calculatePath("openapi"))
                            .withBody(openAPIExpectationSerializer.serialize(openAPIExpectations[0]), StandardCharsets.UTF_8),
                        false
                    );
                if (httpResponse != null && httpResponse.getStatusCode() != 201) {
                    throw new ClientException(formatLogMessage("error:{}while submitted OpenAPI expectation:{}", httpResponse, openAPIExpectations[0]));
                }
            } else if (openAPIExpectations.length > 1) {
                httpResponse =
                    sendRequest(
                        request()
                            .withMethod("PUT")
                            .withContentType(APPLICATION_JSON_UTF_8)
                            .withPath(calculatePath("openapi"))
                            .withBody(openAPIExpectationSerializer.serialize(openAPIExpectations), StandardCharsets.UTF_8),
                        true
                    );
                if (httpResponse != null && httpResponse.getStatusCode() != 201) {
                    throw new ClientException(formatLogMessage("error:{}while submitted OpenAPI expectations:{}", httpResponse, openAPIExpectations));
                }
            }
            if (httpResponse != null && isNotBlank(httpResponse.getBodyAsString())) {
                return expectationSerializer.deserializeArray(httpResponse.getBodyAsString(), true);
            }
        }
        return new Expectation[0];
    }

    /**
     * Stand up a complete mock SAML 2.0 Identity Provider with default settings: a metadata endpoint,
     * an SP-initiated Web-Browser-SSO POST endpoint, and a Single-Logout endpoint, signed with a
     * freshly generated self-signed RSA credential whose certificate is published in the metadata.
     *
     * @return the upserted expectations (metadata + SSO + SLO)
     */
    public Expectation[] mockSamlProvider() {
        return mockSamlProvider(new SamlProviderConfiguration());
    }

    /**
     * Stand up a complete mock SAML 2.0 Identity Provider from the given configuration. The
     * configuration controls the IdP/SP entity ids, endpoint paths, the asserted subject and
     * attributes, the signing algorithm, and the negative-test flags (expired assertion, wrong
     * audience, tampered signature) for exercising an SP's rejection paths.
     *
     * @param samlProviderConfiguration the SAML provider configuration (defaults applied for unset fields)
     * @return the upserted expectations (metadata + SSO + SLO)
     */
    public Expectation[] mockSamlProvider(SamlProviderConfiguration samlProviderConfiguration) {
        if (samlProviderConfiguration == null) {
            samlProviderConfiguration = new SamlProviderConfiguration();
        }
        String body;
        try {
            ObjectMapper objectMapper = ObjectMapperFactory.createObjectMapper();
            ObjectNode configNode = objectMapper.valueToTree(samlProviderConfiguration);
            // signingPrivateKeyPem is WRITE_ONLY on the configuration so the SERVER never serializes
            // the private key back out (no key leak via JSON / metadata / response). That same
            // annotation, however, excludes it from this outbound serialization, which would silently
            // drop a user-supplied PEM credential. Re-add it explicitly for this CLIENT -> server
            // control-plane PUT only, so a supplied signing key actually reaches the IdP.
            if (isNotBlank(samlProviderConfiguration.getSigningPrivateKeyPem())) {
                configNode.put("signingPrivateKeyPem", samlProviderConfiguration.getSigningPrivateKeyPem());
            }
            body = objectMapper.writeValueAsString(configNode);
        } catch (Exception e) {
            throw new ClientException(formatLogMessage("error:{}while serializing SAML provider configuration:{}", e.getMessage(), samlProviderConfiguration), e);
        }
        HttpResponse httpResponse =
            sendRequest(
                request()
                    .withMethod("PUT")
                    .withContentType(APPLICATION_JSON_UTF_8)
                    .withPath(calculatePath("saml"))
                    .withBody(body, StandardCharsets.UTF_8),
                false
            );
        if (httpResponse != null && httpResponse.getStatusCode() != 201) {
            throw new ClientException(formatLogMessage("error:{}while submitting SAML provider configuration:{}", httpResponse, samlProviderConfiguration));
        }
        if (httpResponse != null && isNotBlank(httpResponse.getBodyAsString())) {
            return expectationSerializer.deserializeArray(httpResponse.getBodyAsString(), true);
        }
        return new Expectation[0];
    }

    /**
     * Specify one or more expectations to be create, or updated (if the id matches).
     * <p>
     * This method should be used to update existing expectation by id.  All fields will be updated for expectations with a matching id as the existing expectation is deleted and recreated.
     * <p>
     * To retrieve the id(s) for existing expectation(s) the retrieveActiveExpectations(HttpRequest httpRequest) method can be used.
     * <p>
     * Typically, to create expectations this method should not be used directly instead
     * the when(...) and response(...) or forward(...) or error(...) methods should be used
     * for example:
     * <pre>
     * mockServerClient
     *  .when(
     *      request()
     *          .withPath("/some_path")
     *          .withBody("some_request_body"),
     *      Times.exactly(5),
     *      TimeToLive.exactly(TimeUnit.SECONDS, 120)
     *  )
     *  .respond(
     *      response()
     *          .withBody("some_response_body")
     *          .withHeader("responseName", "responseValue")
     *  )
     * </pre>
     *
     * @param expectations one or more expectations to create or update (if the id field matches)
     * @return upserted expectations
     */
    public Expectation[] upsert(Expectation... expectations) {
        if (expectations != null) {
            HttpResponse httpResponse = null;
            if (expectations.length == 1) {
                httpResponse =
                    sendRequest(
                        request()
                            .withMethod("PUT")
                            .withContentType(APPLICATION_JSON_UTF_8)
                            .withPath(calculatePath("expectation"))
                            .withBody(expectationSerializer.serialize(expectations[0]), StandardCharsets.UTF_8),
                        false
                    );
                if (httpResponse != null && httpResponse.getStatusCode() != 201) {
                    throw new ClientException(formatLogMessage("error:{}while submitted expectation:{}", httpResponse, expectations[0]));
                }
            } else if (expectations.length > 1) {
                httpResponse =
                    sendRequest(
                        request()
                            .withMethod("PUT")
                            .withContentType(APPLICATION_JSON_UTF_8)
                            .withPath(calculatePath("expectation"))
                            .withBody(expectationSerializer.serialize(expectations), StandardCharsets.UTF_8),
                        false
                    );
                if (httpResponse != null && httpResponse.getStatusCode() != 201) {
                    throw new ClientException(formatLogMessage("error:{}while submitted expectations:{}", httpResponse, expectations));
                }
            }
            if (httpResponse != null && isNotBlank(httpResponse.getBodyAsString())) {
                return expectationSerializer.deserializeArray(httpResponse.getBodyAsString(), true);
            }
        }
        return new Expectation[0];
    }

    /**
     * Import one or more expectations from a JSON document into a running MockServer.
     * <p>
     * The JSON may be a single expectation object or an array of expectations — the same
     * format produced by {@link #retrieveActiveExpectations(RequestDefinition)} with
     * {@link Format#JSON}, persisted via the <code>--persist</code> flag, or exported from
     * the dashboard. Each imported expectation is created, or updated if its <code>id</code>
     * matches an existing expectation (an upsert).
     *
     * @param expectationsJson a JSON expectation object or array of expectation objects
     * @return the imported (created or updated) expectations
     */
    public Expectation[] importExpectations(String expectationsJson) {
        if (isBlank(expectationsJson)) {
            return new Expectation[0];
        }
        Expectation[] expectations = expectationSerializer.deserializeArray(expectationsJson, false);
        return upsert(expectations);
    }

    /**
     * Import one or more expectations from a JSON file into a running MockServer.
     * <p>
     * The file may contain a single expectation object or an array of expectations — the same
     * format produced by {@link #retrieveActiveExpectations(RequestDefinition)} with
     * {@link Format#JSON}, persisted via the <code>--persist</code> flag, or exported from
     * the dashboard. Each imported expectation is created, or updated if its <code>id</code>
     * matches an existing expectation (an upsert). The path is resolved from the classpath or
     * the filesystem.
     *
     * @param filePath path to a JSON file containing an expectation object or array of expectation objects
     * @return the imported (created or updated) expectations
     */
    public Expectation[] importExpectationsFromFile(String filePath) {
        return importExpectations(FileReader.readFileFromClassPathOrPath(filePath));
    }

    /**
     * Register a CRUD simulation that auto-generates RESTful endpoints for a given base path.
     * <p>
     * For example, with basePath "/api/users", MockServer will automatically handle:
     * <ul>
     *     <li>GET /api/users - list all items</li>
     *     <li>POST /api/users - create a new item</li>
     *     <li>GET /api/users/{id} - get an item by ID</li>
     *     <li>PUT /api/users/{id} - update an item by ID</li>
     *     <li>DELETE /api/users/{id} - delete an item by ID</li>
     * </ul>
     *
     * @param crudDefinition the CRUD expectations definition specifying basePath, idField, idStrategy, and optional initial data
     * @return this MockServerClient for fluent chaining
     */
    public MockServerClient crud(CrudExpectationsDefinition crudDefinition) {
        sendRequest(
            request()
                .withMethod("PUT")
                .withContentType(APPLICATION_JSON_UTF_8)
                .withPath(calculatePath("crud"))
                .withBody(crudExpectationsDefinitionSerializer.serialize(crudDefinition), StandardCharsets.UTF_8),
            true
        );
        return clientClass.cast(this);
    }

    /**
     * Register a mock SCIM 2.0 provider that auto-generates a complete set of SCIM endpoints for the
     * configured base path (default {@code /scim/v2}).
     * <p>
     * For example, with the default configuration MockServer will serve:
     * <ul>
     *     <li>{@code GET/POST /scim/v2/Users} and {@code GET/PUT/PATCH/DELETE /scim/v2/Users/{id}}</li>
     *     <li>{@code GET/POST /scim/v2/Groups} and {@code GET/PUT/PATCH/DELETE /scim/v2/Groups/{id}}</li>
     *     <li>{@code GET /scim/v2/ServiceProviderConfig}, {@code /ResourceTypes}, {@code /Schemas}</li>
     * </ul>
     * Responses use the {@code application/scim+json} media type, the SCIM ListResponse/Error
     * envelopes, and inject {@code schemas}/{@code id}/{@code meta} on every resource.
     *
     * @param scimConfiguration the SCIM provider configuration (basePath, idStrategy, initial data, enforcement flags)
     * @return the upserted SCIM provider expectations
     */
    public Expectation[] mockScimProvider(ScimProviderConfiguration scimConfiguration) {
        if (scimConfiguration == null) {
            scimConfiguration = new ScimProviderConfiguration();
        }
        String body;
        try {
            ObjectMapper objectMapper = ObjectMapperFactory.createObjectMapper();
            ObjectNode configNode = objectMapper.valueToTree(scimConfiguration);
            // expectedBearerToken is WRITE_ONLY on the configuration so the SERVER never serializes
            // the token back out (no credential leak via JSON / response). That same annotation,
            // however, excludes it from this outbound serialization, which would silently drop a
            // user-supplied token. Re-add it explicitly for this CLIENT -> server control-plane PUT
            // only, so a supplied token actually reaches the provider.
            if (isNotBlank(scimConfiguration.getExpectedBearerToken())) {
                configNode.put("expectedBearerToken", scimConfiguration.getExpectedBearerToken());
            }
            body = objectMapper.writeValueAsString(configNode);
        } catch (Exception e) {
            throw new ClientException(formatLogMessage("error:{}while serializing SCIM provider configuration:{}", e.getMessage(), scimConfiguration), e);
        }
        HttpResponse httpResponse =
            sendRequest(
                request()
                    .withMethod("PUT")
                    .withContentType(APPLICATION_JSON_UTF_8)
                    .withPath(calculatePath("scim"))
                    .withBody(body, StandardCharsets.UTF_8),
                false
            );
        if (httpResponse != null && httpResponse.getStatusCode() != 201) {
            throw new ClientException(formatLogMessage("error:{}while submitting SCIM provider configuration:{}", httpResponse, scimConfiguration));
        }
        if (httpResponse != null && isNotBlank(httpResponse.getBodyAsString())) {
            return expectationSerializer.deserializeArray(httpResponse.getBodyAsString(), true);
        }
        return new Expectation[0];
    }

    /**
     * Register a mock SCIM 2.0 provider using the default configuration (base path {@code /scim/v2}).
     *
     * @return the upserted SCIM provider expectations
     */
    public Expectation[] mockScimProvider() {
        return mockScimProvider(new ScimProviderConfiguration());
    }

    /**
     * Specify one or more expectations, normally this method should not be used directly instead the when(...) and response(...) or forward(...) or error(...) methods should be used
     * for example:
     * <pre>
     * mockServerClient
     *  .when(
     *      request()
     *          .withPath("/some_path")
     *          .withBody("some_request_body"),
     *      Times.exactly(5),
     *      TimeToLive.exactly(TimeUnit.SECONDS, 120)
     *  )
     *  .respond(
     *      response()
     *          .withBody("some_response_body")
     *          .withHeader("responseName", "responseValue")
     *  )
     * </pre>
     *
     * @param expectations one or more expectations
     * @return added or updated expectations
     * @deprecated this is deprecated due to unclear naming, use method upsert(Expectation... expectations) instead
     */
    @Deprecated
    public Expectation[] sendExpectation(Expectation... expectations) {
        return upsert(expectations);
    }

    /**
     * Retrieve the active expectations match the httpRequest parameter, use null for the parameter to retrieve all expectations
     *
     * @param requestDefinition the http request that is matched against when deciding whether to return each expectation, use null for the parameter to retrieve for all requests
     * @return an array of all expectations that have been setup and have not expired
     */
    public Expectation[] retrieveActiveExpectations(RequestDefinition requestDefinition) {
        String activeExpectations = retrieveActiveExpectations(requestDefinition, Format.JSON);
        if (isNotBlank(activeExpectations) && !activeExpectations.equals("[]")) {
            return expectationSerializer.deserializeArray(activeExpectations, true);
        } else {
            return new Expectation[0];
        }
    }

    /**
     * Retrieve the active expectations visible to a single namespace (tenant): the
     * expectations registered under {@code namespace} plus all global (no-namespace)
     * expectations. Other tenants' expectations are hidden.
     * <p>
     * Use null for {@code requestDefinition} to retrieve all of this namespace's
     * expectations regardless of request matcher.
     *
     * @param requestDefinition the http request matched against when deciding whether to return each expectation, or null for all
     * @param namespace         the namespace (tenant) whose expectations to view
     * @return an array of the active expectations visible to the given namespace
     */
    public Expectation[] retrieveActiveExpectations(RequestDefinition requestDefinition, String namespace) {
        HttpResponse httpResponse = sendRequest(
            request()
                .withMethod("PUT")
                .withContentType(APPLICATION_JSON_UTF_8)
                .withPath(calculatePath("retrieve"))
                .withQueryStringParameter("type", RetrieveType.ACTIVE_EXPECTATIONS.name())
                .withQueryStringParameter("format", Format.JSON.name())
                .withQueryStringParameter("namespace", namespace)
                .withBody(requestDefinition != null ? requestDefinitionSerializer.serialize(requestDefinition) : "", StandardCharsets.UTF_8),
            false
        );
        String activeExpectations = httpResponse.getBodyAsString();
        if (isNotBlank(activeExpectations) && !activeExpectations.equals("[]")) {
            return expectationSerializer.deserializeArray(activeExpectations, true);
        } else {
            return new Expectation[0];
        }
    }

    /**
     * Retrieve the active expectations that match both the request matcher and the filter, for example by metadata,
     * because they have never matched a request, or one page of them ({@code limit} and {@code offset}). Pages are
     * taken in the order expectations are matched (highest priority first, then oldest first).
     *
     * @param requestDefinition the http request matched against each expectation, or null for all expectations
     * @param filter            the metadata, unmatched and paging options
     * @return the selected active expectations
     */
    public Expectation[] retrieveActiveExpectations(RequestDefinition requestDefinition, ExpectationFilter filter) {
        String activeExpectations = retrieveActiveExpectations(requestDefinition, filter, Format.JSON);
        if (isNotBlank(activeExpectations) && !activeExpectations.equals("[]")) {
            return expectationSerializer.deserializeArray(activeExpectations, true);
        } else {
            return new Expectation[0];
        }
    }

    /**
     * As {@link #retrieveActiveExpectations(RequestDefinition, ExpectationFilter)}, in the requested format.
     *
     * @param requestDefinition the http request matched against each expectation, or null for all expectations
     * @param filter            the metadata, unmatched and paging options
     * @param format            the format to retrieve the expectations in
     * @return the selected active expectations in the requested format
     */
    public String retrieveActiveExpectations(RequestDefinition requestDefinition, ExpectationFilter filter, Format format) {
        HttpResponse httpResponse = sendRequest(
            (filter != null ? filter : ExpectationFilter.expectationFilter()).applyTo(
                request()
                    .withMethod("PUT")
                    .withContentType(APPLICATION_JSON_UTF_8)
                    .withPath(calculatePath("retrieve"))
                    .withQueryStringParameter("type", RetrieveType.ACTIVE_EXPECTATIONS.name())
                    .withQueryStringParameter("format", format.name())
                    .withBody(requestDefinition != null ? requestDefinitionSerializer.serialize(requestDefinition) : "", StandardCharsets.UTF_8)
            ),
            true
        );
        return httpResponse.getBodyAsString();
    }

    /**
     * Retrieve the active expectations whose metadata has {@code key} with a value equal to {@code value}.
     *
     * @param key   the metadata key
     * @param value the metadata value: a String is compared as text, any other value as JSON
     * @return the matching active expectations
     */
    public Expectation[] retrieveActiveExpectationsByMetadata(String key, Object value) {
        return retrieveActiveExpectations(null, ExpectationFilter.expectationFilter().withMetadata(key, value));
    }

    /**
     * Retrieve the active expectations whose metadata has {@code key}, whatever its value.
     *
     * @param key the metadata key, must not be blank
     * @return the matching active expectations
     */
    public Expectation[] retrieveActiveExpectationsByMetadataKey(String key) {
        return retrieveActiveExpectations(null, ExpectationFilter.expectationFilter().withMetadataKey(key));
    }

    /**
     * Retrieve the active expectations that have never matched a request, for example to find mocks a test suite
     * no longer uses. The count is per MockServer instance, and resets when an expectation is changed.
     *
     * @return the active expectations that have not matched any request
     */
    public Expectation[] retrieveUnmatchedExpectations() {
        return retrieveActiveExpectations(null, ExpectationFilter.expectationFilter().withUnmatched(true));
    }

    /**
     * Retrieve the active expectations match the httpRequest parameter, use null for the parameter to retrieve all expectations
     *
     * @param requestDefinition the http request that is matched against when deciding whether to return each expectation, use null for the parameter to retrieve for all requests
     * @param format            the format to retrieve the expectations, either JAVA or JSON
     * @return an array of all expectations that have been setup and have not expired
     */
    public String retrieveActiveExpectations(RequestDefinition requestDefinition, Format format) {
        HttpResponse httpResponse = sendRequest(
            request()
                .withMethod("PUT")
                .withContentType(APPLICATION_JSON_UTF_8)
                .withPath(calculatePath("retrieve"))
                .withQueryStringParameter("type", RetrieveType.ACTIVE_EXPECTATIONS.name())
                .withQueryStringParameter("format", format.name())
                .withBody(requestDefinition != null ? requestDefinitionSerializer.serialize(requestDefinition) : "", StandardCharsets.UTF_8),
            true
        );
        return httpResponse.getBodyAsString();
    }

    /**
     * Retrieve recorded requests, recorded request-responses, recorded expectations or log messages by
     * expectation id, in the requested format.
     * <p>
     * For {@link RetrieveType#ACTIVE_EXPECTATIONS} this returns only the expectation with that id. For
     * every other type it returns the entries matching that expectation's request, exactly as
     * {@link #verify(ExpectationId...)} matches them. An unknown id is rejected by MockServer.
     *
     * @param type          the type of object to retrieve
     * @param format        the format to retrieve the objects in
     * @param expectationId the id of the expectation to retrieve for
     * @return the raw response body in the requested format
     */
    private String retrieveById(RetrieveType type, Format format, String expectationId) {
        HttpResponse httpResponse = sendRequest(
            request()
                .withMethod("PUT")
                .withContentType(APPLICATION_JSON_UTF_8)
                .withPath(calculatePath("retrieve"))
                .withQueryStringParameter("type", type.name())
                .withQueryStringParameter("format", format.name())
                .withBody(expectationIdSerializer.serialize(expectationId(expectationId)), StandardCharsets.UTF_8),
            true
        );
        return httpResponse.getBodyAsString();
    }

    /**
     * Retrieve the recorded requests that match the request of the expectation with the given id
     *
     * @param expectationId the id of the expectation whose request is matched against when deciding whether to return each request
     * @return an array of all requests that have been recorded by the MockServer in the order they have been received and including duplicates where the same request has been received multiple times
     */
    public HttpRequest[] retrieveRecordedRequestsById(String expectationId) {
        RequestDefinition[] requestDefinitions = new RequestDefinition[0];
        String recordedRequests = retrieveRecordedRequestsById(expectationId, Format.JSON);
        if (isNotBlank(recordedRequests) && !recordedRequests.equals("[]")) {
            requestDefinitions = requestDefinitionSerializer.deserializeArray(recordedRequests);
        }
        return Arrays.stream(requestDefinitions).filter(HttpRequest.class::isInstance).map(HttpRequest.class::cast).toArray(HttpRequest[]::new);
    }

    /**
     * Retrieve the recorded requests that match the request of the expectation with the given id
     *
     * @param expectationId the id of the expectation whose request is matched against when deciding whether to return each request
     * @param format        the format to retrieve the requests in
     * @return all requests that have been recorded by the MockServer in the requested format
     */
    public String retrieveRecordedRequestsById(String expectationId, Format format) {
        return retrieveById(RetrieveType.REQUESTS, format, expectationId);
    }

    /**
     * Retrieve the recorded requests and responses that match the request of the expectation with the given id
     *
     * @param expectationId the id of the expectation whose request is matched against when deciding whether to return each request (and its corresponding response)
     * @return an array of all requests and responses that have been recorded by the MockServer in the order they have been received and including duplicates where the same request has been received multiple times
     */
    public LogEventRequestAndResponse[] retrieveRecordedRequestsAndResponsesById(String expectationId) {
        String recordedRequests = retrieveRecordedRequestsAndResponsesById(expectationId, Format.JSON);
        if (isNotBlank(recordedRequests) && !recordedRequests.equals("[]")) {
            return httpRequestResponseSerializer.deserializeArray(recordedRequests);
        } else {
            return new LogEventRequestAndResponse[0];
        }
    }

    /**
     * Retrieve the recorded requests and responses that match the request of the expectation with the given id
     *
     * @param expectationId the id of the expectation whose request is matched against when deciding whether to return each request (and its corresponding response)
     * @param format        the format to retrieve the requests and responses in
     * @return all requests and responses that have been recorded by the MockServer in the requested format
     */
    public String retrieveRecordedRequestsAndResponsesById(String expectationId, Format format) {
        return retrieveById(RetrieveType.REQUEST_RESPONSES, format, expectationId);
    }

    /**
     * Retrieve, as a list of expectations, the request-response combinations recorded for the request of
     * the expectation with the given id
     *
     * @param expectationId the id of the expectation whose request is matched against when deciding whether to return each recorded expectation
     * @return an array of all expectations that have been recorded by the MockServer in the order they have been received and including duplicates where the same request has been received multiple times
     */
    public Expectation[] retrieveRecordedExpectationsById(String expectationId) {
        String recordedExpectations = retrieveRecordedExpectationsById(expectationId, Format.JSON);
        if (isNotBlank(recordedExpectations) && !recordedExpectations.equals("[]")) {
            return expectationSerializer.deserializeArray(recordedExpectations, true);
        } else {
            return new Expectation[0];
        }
    }

    /**
     * Retrieve, as a list of expectations, the request-response combinations recorded for the request of
     * the expectation with the given id
     *
     * @param expectationId the id of the expectation whose request is matched against when deciding whether to return each recorded expectation
     * @param format        the format to retrieve the expectations in
     * @return all expectations that have been recorded by the MockServer in the requested format
     */
    public String retrieveRecordedExpectationsById(String expectationId, Format format) {
        return retrieveById(RetrieveType.RECORDED_EXPECTATIONS, format, expectationId);
    }

    /**
     * Retrieve the active expectation with the given id, or an empty array if no such expectation exists
     *
     * @param expectationId the id of the expectation to retrieve
     * @return an array containing the expectation with that id, if it has been setup and has not expired
     */
    public Expectation[] retrieveActiveExpectationsById(String expectationId) {
        String activeExpectations = retrieveActiveExpectationsById(expectationId, Format.JSON);
        if (isNotBlank(activeExpectations) && !activeExpectations.equals("[]")) {
            return expectationSerializer.deserializeArray(activeExpectations, true);
        } else {
            return new Expectation[0];
        }
    }

    /**
     * Retrieve the active expectation with the given id in the requested format
     *
     * @param expectationId the id of the expectation to retrieve
     * @param format        the format to retrieve the expectation in
     * @return the expectation with that id in the requested format
     */
    public String retrieveActiveExpectationsById(String expectationId, Format format) {
        return retrieveById(RetrieveType.ACTIVE_EXPECTATIONS, format, expectationId);
    }

    /**
     * Retrieve the logs associated with the request of the expectation with the given id, this shows all
     * logs for expectation matching, verification, clearing, etc
     *
     * @param expectationId the id of the expectation whose request is matched against when deciding whether to return each log message
     * @return all log messages recorded by the MockServer when creating expectations, matching expectations, performing verification, clearing logs, etc
     */
    public String retrieveLogMessagesById(String expectationId) {
        HttpResponse httpResponse = sendRequest(
            request()
                .withMethod("PUT")
                .withContentType(APPLICATION_JSON_UTF_8)
                .withPath(calculatePath("retrieve"))
                .withQueryStringParameter("type", RetrieveType.LOGS.name())
                .withBody(expectationIdSerializer.serialize(expectationId(expectationId)), StandardCharsets.UTF_8),
            true
        );
        return httpResponse.getBodyAsString();
    }

    /**
     * Retrieve the logs associated with the request of the expectation with the given id, this shows all
     * logs for expectation matching, verification, clearing, etc
     *
     * @param expectationId the id of the expectation whose request is matched against when deciding whether to return each log message
     * @return an array of all log messages recorded by the MockServer when creating expectations, matching expectations, performing verification, clearing logs, etc
     */
    public String[] retrieveLogMessagesArrayById(String expectationId) {
        return retrieveLogMessagesById(expectationId).split(LOG_SEPARATOR);
    }

    /**
     * Analyze why a request does not match any active expectations, showing per-field match failures for each expectation.
     * Returns a JSON string containing the total number of expectations, the closest match, and per-expectation results
     * with field-level differences.
     *
     * @param requestDefinition the request to debug against active expectations
     * @return a JSON string with structured match analysis
     */
    public String debugMismatch(RequestDefinition requestDefinition) {
        HttpResponse httpResponse = sendRequest(
            request()
                .withMethod("PUT")
                .withContentType(APPLICATION_JSON_UTF_8)
                .withPath(calculatePath("debugMismatch"))
                .withBody(requestDefinition != null ? requestDefinitionSerializer.serialize(requestDefinition) : "", StandardCharsets.UTF_8),
            true
        );
        return httpResponse.getBodyAsString();
    }

    /**
     * Explain how a recorded request would be served now: re-checks it against the current expectations, in the
     * order MockServer evaluates them, and returns the expectation that would serve it, why each expectation ahead
     * of that one was passed over, and which lower-priority expectations it shadows. Wraps
     * {@code PUT /mockserver/explain}. Read-only: it consumes no Times and moves no scenario.
     *
     * @param correlationId the correlation ID of the request's log entries (see {@link #retrieveLogEntries})
     * @return the explanation
     * @throws IllegalArgumentException if {@code correlationId} is blank, or the recorded request is not an HTTP request
     * @throws ClientException          if no request is recorded with this correlation ID (never received, cleared or
     *                                  evicted from the event log), or the server fails
     */
    public RequestExplanation explain(String correlationId) {
        if (isBlank(correlationId)) {
            throw new IllegalArgumentException("explain(correlationId) requires a non blank correlationId");
        }
        HttpResponse httpResponse = sendRequest(
            request()
                .withMethod("PUT")
                .withPath(calculatePath("explain"))
                .withQueryStringParameter("correlationId", correlationId),
            false
        );
        String body = httpResponse != null ? httpResponse.getBodyAsString() : null;
        try {
            JsonNode root = CONTRACT_OBJECT_MAPPER.readTree(isBlank(body) ? "{}" : body);
            if (httpResponse == null || httpResponse.getStatusCode() == null || httpResponse.getStatusCode() != OK.code()) {
                throw new ClientException("explain failed for correlationId \"" + correlationId + "\": "
                    + (root.hasNonNull("error") ? root.path("error").asText() : body));
            }
            return RequestExplanation.fromJson(root);
        } catch (ClientException e) {
            throw e;
        } catch (Exception e) {
            throw new ClientException("Unable to parse explain response: " + body, e);
        }
    }

    /**
     * Retrieve all log entries that share the specified correlationId.
     * A correlationId groups all log entries for a single incoming HTTP request lifecycle
     * (received, match attempts, response).
     *
     * @param correlationId the correlation ID to filter by
     * @return a string containing all log entries for the given correlation ID
     */
    public String retrieveLogsByCorrelationId(String correlationId) {
        HttpResponse httpResponse = sendRequest(
            request()
                .withMethod("PUT")
                .withContentType(APPLICATION_JSON_UTF_8)
                .withPath(calculatePath("retrieve"))
                .withQueryStringParameter("type", RetrieveType.LOGS.name())
                .withQueryStringParameter("correlationId", correlationId)
                .withBody("", StandardCharsets.UTF_8),
            true
        );
        return httpResponse.getBodyAsString();
    }

    public String retrieveMetrics() {
        HttpResponse httpResponse = sendRequest(
            request()
                .withMethod("PUT")
                .withContentType(APPLICATION_JSON_UTF_8)
                .withPath(calculatePath("retrieve"))
                .withQueryStringParameter("type", RetrieveType.METRICS.name())
                .withBody("", StandardCharsets.UTF_8),
            false
        );
        return httpResponse.getBodyAsString();
    }

    public String retrieveConfiguration() {
        HttpResponse httpResponse = sendRequest(
            request()
                .withMethod("GET")
                .withPath(calculatePath("configuration")),
            false
        );
        return httpResponse.getBodyAsString();
    }

    public String updateConfiguration(String configurationJson) {
        HttpResponse httpResponse = sendRequest(
            request()
                .withMethod("PUT")
                .withContentType(APPLICATION_JSON_UTF_8)
                .withPath(calculatePath("configuration"))
                .withBody(configurationJson != null ? configurationJson : "", StandardCharsets.UTF_8),
            false
        );
        return httpResponse.getBodyAsString();
    }

    public MockServerClient uploadGrpcDescriptor(byte[] descriptorSetBytes) {
        sendRequest(
            request()
                .withMethod("PUT")
                .withPath(calculatePath("grpc/descriptors"))
                .withBody(new BinaryBody(descriptorSetBytes)),
            true
        );
        return clientClass.cast(this);
    }

    public String retrieveGrpcServices() {
        HttpResponse httpResponse = sendRequest(
            request()
                .withMethod("PUT")
                .withPath(calculatePath("grpc/services")),
            false
        );
        return httpResponse.getBodyAsString();
    }

    public MockServerClient clearGrpcDescriptors() {
        sendRequest(
            request()
                .withMethod("PUT")
                .withPath(calculatePath("grpc/clear")),
            true
        );
        return clientClass.cast(this);
    }

    // -------------------------------------------------------------------
    // Metrics (Prometheus exposition)
    // -------------------------------------------------------------------

    /**
     * Scrape the Prometheus exposition-format metrics. Wraps {@code GET /mockserver/metrics}.
     * <p>
     * This is distinct from {@link #retrieveMetrics()} (which returns the JSON counter snapshot via
     * {@code PUT /mockserver/retrieve?type=METRICS}). When {@code metricsEnabled} is {@code false}
     * the server replies {@code 404 NOT_FOUND}.
     *
     * @return the Prometheus exposition text
     */
    public String scrapeMetrics() {
        HttpResponse httpResponse = sendRequest(
            request()
                .withMethod("GET")
                .withPath(calculatePath("metrics")),
            false
        );
        return httpResponse != null ? httpResponse.getBodyAsString() : null;
    }

    // -------------------------------------------------------------------
    // Drift detection
    // -------------------------------------------------------------------

    /**
     * Retrieve the recorded mock drift report. Wraps {@code GET /mockserver/drift}.
     * <p>
     * The report is returned as a JSON string of the form
     * {@code {"count": <n>, "drifts": [ ... ]}}, where each entry describes a difference detected
     * between a mock's configured response and the live upstream response for the same request.
     *
     * @return the drift report as a JSON string
     */
    public String retrieveDrift() {
        HttpResponse httpResponse = sendRequest(
            request()
                .withMethod("GET")
                .withPath(calculatePath("drift")),
            false
        );
        return httpResponse != null ? httpResponse.getBodyAsString() : null;
    }

    /**
     * Clear all recorded mock drift. Wraps {@code PUT /mockserver/drift/clear}.
     *
     * @return this MockServerClient
     */
    public MockServerClient clearDrift() {
        sendRequest(
            request()
                .withMethod("PUT")
                .withPath(calculatePath("drift/clear")),
            true
        );
        return clientClass.cast(this);
    }

    // -------------------------------------------------------------------
    // File store
    // -------------------------------------------------------------------

    /**
     * Store a UTF-8 text file in the in-memory file store. Wraps {@code PUT /mockserver/files/store}.
     *
     * @param name    the file name/key (required)
     * @param content the file content as a UTF-8 string (required)
     * @return this MockServerClient
     */
    public MockServerClient storeFile(String name, String content) {
        if (isBlank(name)) {
            throw new IllegalArgumentException("storeFile(name, content) requires a non null name");
        }
        ObjectNode requestBody = OBJECT_MAPPER.createObjectNode();
        requestBody.put("name", name);
        requestBody.put("content", content != null ? content : "");
        sendRequest(
            request()
                .withMethod("PUT")
                .withContentType(APPLICATION_JSON_UTF_8)
                .withPath(calculatePath("files/store"))
                .withBody(requestBody.toString(), StandardCharsets.UTF_8),
            false
        );
        return clientClass.cast(this);
    }

    /**
     * Store a binary file in the in-memory file store. Wraps {@code PUT /mockserver/files/store}.
     * The bytes are base64-encoded and sent with {@code "base64": true} so the server stores the
     * raw bytes verbatim.
     *
     * @param name    the file name/key (required)
     * @param content the file content as raw bytes (required)
     * @return this MockServerClient
     */
    public MockServerClient storeFile(String name, byte[] content) {
        if (isBlank(name)) {
            throw new IllegalArgumentException("storeFile(name, content) requires a non null name");
        }
        ObjectNode requestBody = OBJECT_MAPPER.createObjectNode();
        requestBody.put("name", name);
        requestBody.put("content", Base64.getEncoder().encodeToString(content != null ? content : new byte[0]));
        requestBody.put("base64", true);
        sendRequest(
            request()
                .withMethod("PUT")
                .withContentType(APPLICATION_JSON_UTF_8)
                .withPath(calculatePath("files/store"))
                .withBody(requestBody.toString(), StandardCharsets.UTF_8),
            false
        );
        return clientClass.cast(this);
    }

    /**
     * Retrieve a file's raw bytes from the in-memory file store. Wraps
     * {@code PUT /mockserver/files/retrieve}. The server replies {@code 404 NOT_FOUND} for an
     * unknown file.
     *
     * @param name the file name/key (required)
     * @return the raw file bytes
     */
    public byte[] retrieveFile(String name) {
        if (isBlank(name)) {
            throw new IllegalArgumentException("retrieveFile(name) requires a non null name");
        }
        ObjectNode requestBody = OBJECT_MAPPER.createObjectNode();
        requestBody.put("name", name);
        HttpResponse httpResponse = sendRequest(
            request()
                .withMethod("PUT")
                .withContentType(APPLICATION_JSON_UTF_8)
                .withPath(calculatePath("files/retrieve"))
                .withBody(requestBody.toString(), StandardCharsets.UTF_8),
            false
        );
        if (httpResponse != null && Integer.valueOf(404).equals(httpResponse.getStatusCode())) {
            throw new ClientException("file not found: " + name);
        }
        return httpResponse != null ? httpResponse.getBodyAsRawBytes() : null;
    }

    /**
     * List the names of all files in the in-memory file store. Wraps {@code PUT /mockserver/files/list}.
     *
     * @return the set of file names
     */
    @SuppressWarnings("unchecked")
    public Set<String> listFiles() {
        HttpResponse httpResponse = sendRequest(
            request()
                .withMethod("PUT")
                .withContentType(APPLICATION_JSON_UTF_8)
                .withPath(calculatePath("files/list")),
            false
        );
        if (httpResponse == null || isBlank(httpResponse.getBodyAsString())) {
            return new LinkedHashSet<>();
        }
        try {
            List<String> names = OBJECT_MAPPER.readValue(httpResponse.getBodyAsString(), List.class);
            return new LinkedHashSet<>(names);
        } catch (Throwable throwable) {
            throw new ClientException("Unable to parse files/list response", throwable);
        }
    }

    /**
     * Delete a file from the in-memory file store. Wraps {@code PUT /mockserver/files/delete}. The
     * server replies {@code 404 NOT_FOUND} for an unknown file.
     *
     * @param name the file name/key (required)
     * @return this MockServerClient
     */
    public MockServerClient deleteFile(String name) {
        if (isBlank(name)) {
            throw new IllegalArgumentException("deleteFile(name) requires a non null name");
        }
        ObjectNode requestBody = OBJECT_MAPPER.createObjectNode();
        requestBody.put("name", name);
        HttpResponse httpResponse = sendRequest(
            request()
                .withMethod("PUT")
                .withContentType(APPLICATION_JSON_UTF_8)
                .withPath(calculatePath("files/delete"))
                .withBody(requestBody.toString(), StandardCharsets.UTF_8),
            false
        );
        if (httpResponse != null && Integer.valueOf(404).equals(httpResponse.getStatusCode())) {
            throw new ClientException("file not found: " + name);
        }
        return clientClass.cast(this);
    }

    // -------------------------------------------------------------------
    // Import (HAR / Postman collection)
    // -------------------------------------------------------------------

    /**
     * Import a HAR (HTTP Archive) document as expectations. Wraps
     * {@code PUT /mockserver/import?format=har}.
     *
     * @param harJson the HAR JSON document
     * @return the upserted expectations
     */
    public Expectation[] importHar(String harJson) {
        return importDocument(harJson, "har");
    }

    /**
     * Import a Postman collection as expectations. Wraps
     * {@code PUT /mockserver/import?format=postman}.
     *
     * @param postmanJson the Postman collection JSON document
     * @return the upserted expectations
     */
    public Expectation[] importPostmanCollection(String postmanJson) {
        return importDocument(postmanJson, "postman");
    }

    /**
     * Import a HAR, Postman collection, Pact contract, WireMock stub mapping, Mountebank imposter or
     * Mockoon environment as expectations. Wraps {@code PUT /mockserver/import}. When {@code format}
     * is blank the server auto-detects the format from the JSON shape.
     *
     * <p>The WireMock, Mountebank and Mockoon importers also report each foreign construct they could
     * not map faithfully; those warnings are logged at {@code WARN}. Recorded-traffic re-import
     * ({@code format=recording}) loads request/response pairs into the request log rather than adding
     * expectations, so this method rejects it; send {@code PUT /mockserver/import?format=recording}
     * directly instead.
     *
     * @param json   the document JSON (required)
     * @param format one of {@code har}, {@code postman}, {@code pact}, {@code wiremock}, {@code mountebank},
     *               {@code mockoon}, or {@code null}/blank for auto-detect
     * @return the upserted expectations
     * @throws IllegalArgumentException if {@code json} is blank or {@code format} is {@code recording}
     * @throws ClientException          if the reply is an object without an {@code expectations} array
     */
    public Expectation[] importDocument(String json, String format) {
        if (isBlank(json)) {
            throw new IllegalArgumentException("importDocument(json, format) requires a non null document JSON");
        }
        if (format != null && "recording".equalsIgnoreCase(format.trim())) {
            throw new IllegalArgumentException("importDocument(json, format) does not support format=recording, which loads recorded request/response pairs into the request log rather than adding expectations - send PUT /mockserver/import?format=recording directly");
        }
        HttpRequest importRequest = request()
            .withMethod("PUT")
            .withContentType(APPLICATION_JSON_UTF_8)
            .withPath(calculatePath("import"))
            .withBody(json, StandardCharsets.UTF_8);
        if (isNotBlank(format)) {
            importRequest.withQueryStringParameter("format", format);
        }
        HttpResponse httpResponse = sendRequest(importRequest, false);
        if (httpResponse == null || isBlank(httpResponse.getBodyAsString())) {
            return new Expectation[0];
        }
        String body = httpResponse.getBodyAsString();
        JsonNode root = null;
        try {
            root = OBJECT_MAPPER.readTree(body);
        } catch (Exception ignored) {
            // not JSON: deserializeArray below reports it as it always has
        }
        if (root == null || !root.isObject()) {
            return expectationSerializer.deserializeArray(body, true);
        }
        // the migration importers (wiremock, mountebank, mockoon) reply {"expectations":[...],"warnings":[...]}
        JsonNode expectations = root.path("expectations");
        if (!expectations.isArray()) {
            throw new ClientException("import response has no expectations array - server responded: " + body);
        }
        logImportWarnings(format, root.path("warnings"));
        return expectationSerializer.deserializeArray(expectations.toString(), true);
    }

    private static void logImportWarnings(String format, JsonNode warnings) {
        if (!warnings.isArray() || warnings.isEmpty() || !MockServerLogger.isEnabled(WARN)) {
            return;
        }
        List<String> lines = new ArrayList<>();
        for (JsonNode warning : warnings) {
            lines.add("[" + warning.path("item").asText() + "] " + warning.path("construct").asText() + ": " + warning.path("detail").asText());
        }
        MOCK_SERVER_LOGGER.logEvent(
            new LogEntry()
                .setLogLevel(WARN)
                .setMessageFormat("import of {}document could not fully map{}construct(s), check the imported expectations:{}")
                .setArguments(isNotBlank(format) ? format : "auto-detected", warnings.size(), lines)
        );
    }

    // -------------------------------------------------------------------
    // Operating mode
    // -------------------------------------------------------------------

    /**
     * Set the high-level operating mode. Wraps {@code PUT /mockserver/mode?mode=<MODE>}. Setting the
     * mode also flips {@code attemptToProxyIfNoMatchingExpectation} server-side.
     *
     * @param mode one of {@link MockMode#SIMULATE}, {@link MockMode#SPY}, {@link MockMode#CAPTURE}
     * @return this MockServerClient
     */
    public MockServerClient setMode(MockMode mode) {
        if (mode == null) {
            throw new IllegalArgumentException("setMode(mode) requires a non null mode");
        }
        sendRequest(
            request()
                .withMethod("PUT")
                .withContentType(APPLICATION_JSON_UTF_8)
                .withPath(calculatePath("mode"))
                .withQueryStringParameter("mode", mode.name()),
            false
        );
        return clientClass.cast(this);
    }

    /**
     * Read the current high-level operating mode. Wraps {@code GET /mockserver/mode}.
     *
     * @return the current {@link MockMode}
     */
    public MockMode retrieveMode() {
        HttpResponse httpResponse = sendRequest(
            request()
                .withMethod("GET")
                .withPath(calculatePath("mode")),
            false
        );
        if (httpResponse == null || isBlank(httpResponse.getBodyAsString())) {
            throw new ClientException("Unable to read mode: empty response");
        }
        try {
            JsonNode node = OBJECT_MAPPER.readTree(httpResponse.getBodyAsString());
            return MockMode.parse(node.get("mode").asText());
        } catch (Throwable throwable) {
            throw new ClientException("Unable to parse mode response", throwable);
        }
    }

    // -------------------------------------------------------------------
    // WSDL
    // -------------------------------------------------------------------

    /**
     * Generate and upsert expectations from a WSDL document. Wraps {@code PUT /mockserver/wsdl} with
     * the raw WSDL XML as the request body (mirrors how {@code openAPIExpectation(...)} feeds the
     * OpenAPI upsert).
     *
     * @param wsdl the WSDL document XML (required)
     * @return the generated (upserted) expectations
     */
    public Expectation[] wsdlExpectation(String wsdl) {
        if (isBlank(wsdl)) {
            throw new IllegalArgumentException("wsdlExpectation(wsdl) requires a non null WSDL document");
        }
        HttpResponse httpResponse = sendRequest(
            request()
                .withMethod("PUT")
                .withContentType(MediaType.APPLICATION_XML_UTF_8)
                .withPath(calculatePath("wsdl"))
                .withBody(wsdl, StandardCharsets.UTF_8),
            false
        );
        if (httpResponse != null && isNotBlank(httpResponse.getBodyAsString())) {
            return expectationSerializer.deserializeArray(httpResponse.getBodyAsString(), true);
        }
        return new Expectation[0];
    }

    // -------------------------------------------------------------------
    // Clock Control
    // -------------------------------------------------------------------

    /**
     * Freeze the MockServer clock at the specified instant.
     *
     * @param instant the instant to freeze the clock at
     * @return this MockServerClient
     */
    public MockServerClient freezeClock(java.time.Instant instant) {
        sendRequest(
            request()
                .withMethod("PUT")
                .withContentType(APPLICATION_JSON_UTF_8)
                .withPath(calculatePath("clock"))
                .withBody(instant != null
                    ? "{\"action\":\"freeze\",\"instant\":\"" + instant + "\"}"
                    : "{\"action\":\"freeze\"}", StandardCharsets.UTF_8),
            true
        );
        return clientClass.cast(this);
    }

    /**
     * Freeze the MockServer clock at the current time.
     *
     * @return this MockServerClient
     */
    public MockServerClient freezeClock() {
        return freezeClock(null);
    }

    /**
     * Advance the MockServer clock by the specified duration.
     *
     * @param duration the duration to advance the clock by
     * @return this MockServerClient
     */
    public MockServerClient advanceClock(java.time.Duration duration) {
        sendRequest(
            request()
                .withMethod("PUT")
                .withContentType(APPLICATION_JSON_UTF_8)
                .withPath(calculatePath("clock"))
                .withBody("{\"action\":\"advance\",\"durationMillis\":" + duration.toMillis() + "}", StandardCharsets.UTF_8),
            true
        );
        return clientClass.cast(this);
    }

    /**
     * Reset the MockServer clock to real wall-clock time.
     *
     * @return this MockServerClient
     */
    public MockServerClient resetClock() {
        sendRequest(
            request()
                .withMethod("PUT")
                .withContentType(APPLICATION_JSON_UTF_8)
                .withPath(calculatePath("clock"))
                .withBody("{\"action\":\"reset\"}", StandardCharsets.UTF_8),
            true
        );
        return clientClass.cast(this);
    }

    /**
     * Retrieve the current clock status including the current instant,
     * epoch millis, and whether the clock is frozen.
     *
     * @return JSON string with fields: currentInstant, currentEpochMillis, frozen
     */
    public String clockStatus() {
        HttpResponse httpResponse = sendRequest(
            request()
                .withMethod("GET")
                .withPath(calculatePath("clock")),
            false
        );
        return httpResponse != null ? httpResponse.getBodyAsString() : "";
    }

    /**
     * Register a service-scoped HTTP chaos profile for an upstream host. The profile
     * is applied to every matched forward expectation to that host that does not
     * define its own {@code chaos} block (an expectation's own chaos always wins).
     * The host is matched case-insensitively, ignoring any {@code :port}. See
     * {@code PUT /mockserver/serviceChaos}.
     *
     * @param host  the upstream host to break
     * @param chaos the chaos profile to apply to that host's forwarded responses
     * @return this MockServerClient
     */
    public MockServerClient setServiceChaos(String host, HttpChaosProfile chaos) {
        return setServiceChaos(host, chaos, 0L);
    }

    /**
     * Register a service-scoped HTTP chaos profile for an upstream host that
     * auto-reverts after {@code ttlMillis} milliseconds (a "dead-man's switch" so
     * the chaos self-heals even if {@link #removeServiceChaos(String)} /
     * {@link #clearServiceChaos()} is never called). See {@code PUT /mockserver/serviceChaos}.
     *
     * @param host      the upstream host to break
     * @param chaos     the chaos profile to apply to that host's forwarded responses
     * @param ttlMillis milliseconds after which the chaos auto-reverts; {@code <= 0} means no expiry
     * @return this MockServerClient
     */
    public MockServerClient setServiceChaos(String host, HttpChaosProfile chaos, long ttlMillis) {
        try {
            ObjectMapper objectMapper = ObjectMapperFactory.createObjectMapper();
            ObjectNode body = objectMapper.createObjectNode();
            body.put("host", host);
            body.set("chaos", objectMapper.valueToTree(new HttpChaosProfileDTO(chaos)));
            if (ttlMillis > 0) {
                body.put("ttlMillis", ttlMillis);
            }
            sendRequest(
                request()
                    .withMethod("PUT")
                    .withContentType(APPLICATION_JSON_UTF_8)
                    .withPath(calculatePath("serviceChaos"))
                    .withBody(objectMapper.writeValueAsString(body), StandardCharsets.UTF_8),
                true
            );
        } catch (Exception exception) {
            throw new RuntimeException("Exception serializing service chaos profile for host \"" + host + "\"", exception);
        }
        return clientClass.cast(this);
    }

    /**
     * Remove the service-scoped chaos profile registered for the given host.
     *
     * @param host the upstream host whose service-scoped chaos should be removed
     * @return this MockServerClient
     */
    public MockServerClient removeServiceChaos(String host) {
        try {
            ObjectMapper objectMapper = ObjectMapperFactory.createObjectMapper();
            ObjectNode body = objectMapper.createObjectNode();
            body.put("host", host);
            body.put("remove", true);
            sendRequest(
                request()
                    .withMethod("PUT")
                    .withContentType(APPLICATION_JSON_UTF_8)
                    .withPath(calculatePath("serviceChaos"))
                    .withBody(objectMapper.writeValueAsString(body), StandardCharsets.UTF_8),
                true
            );
        } catch (Exception exception) {
            throw new RuntimeException("Exception removing service chaos profile for host \"" + host + "\"", exception);
        }
        return clientClass.cast(this);
    }

    /**
     * Clear all service-scoped chaos profiles.
     *
     * @return this MockServerClient
     */
    public MockServerClient clearServiceChaos() {
        sendRequest(
            request()
                .withMethod("PUT")
                .withContentType(APPLICATION_JSON_UTF_8)
                .withPath(calculatePath("serviceChaos"))
                .withBody("{\"clear\":true}", StandardCharsets.UTF_8),
            true
        );
        return clientClass.cast(this);
    }

    /**
     * Retrieve the current service-scoped chaos registrations as a JSON string
     * ({@code {"services":{host: profile, ...}}}).
     *
     * @return JSON string of the current host → profile registrations
     */
    public String serviceChaosStatus() {
        HttpResponse httpResponse = sendRequest(
            request()
                .withMethod("GET")
                .withPath(calculatePath("serviceChaos")),
            false
        );
        return httpResponse != null ? httpResponse.getBodyAsString() : "";
    }

    // load-scenario (load injection) registry control-plane helpers

    /**
     * Register (load) a load-injection scenario into the load-scenario registry via
     * {@code PUT /mockserver/loadScenario}. Registration does <em>not</em> start the
     * scenario — it is loaded in the {@code LOADED} state and driving no traffic. Each
     * scenario is identified by its unique {@code name}; registering a scenario with an
     * existing name replaces it.
     *
     * <p>Registration is always permitted, even when {@code loadGenerationEnabled} is
     * off on the server — only {@link #startLoadScenarios(String...) starting} requires
     * load generation to be enabled.
     *
     * @param scenario the load scenario to register (see {@link org.mockserver.load.LoadScenario})
     * @return JSON string describing the registered scenario ({@code {"name":...,"state":...}})
     */
    public String loadScenario(LoadScenario scenario) {
        HttpResponse httpResponse = sendRequest(
            request()
                .withMethod("PUT")
                .withContentType(APPLICATION_JSON_UTF_8)
                .withPath(calculatePath("loadScenario"))
                .withBody(loadScenarioSerializer.serialize(scenario), StandardCharsets.UTF_8),
            false
        );
        if (httpResponse != null && httpResponse.getStatusCode() != null && httpResponse.getStatusCode() >= 400) {
            throw new ClientException(formatLogMessage("error:{}while registering load scenario", httpResponse.getBodyAsString()));
        }
        return httpResponse != null ? httpResponse.getBodyAsString() : "";
    }

    /**
     * List all registered load scenarios via {@code GET /mockserver/loadScenario}. The
     * response body is a JSON object {@code {"scenarios":[{"name":...,"state":...,"definition":...,"status":...}]}}
     * where each entry carries the scenario name, lifecycle state (one of {@code LOADED},
     * {@code PENDING}, {@code RUNNING}, {@code COMPLETED}, {@code STOPPED}), its definition,
     * and live status for running scenarios.
     *
     * @return JSON string listing all registered load scenarios
     */
    public String loadScenarios() {
        HttpResponse httpResponse = sendRequest(
            request()
                .withMethod("GET")
                .withPath(calculatePath("loadScenario")),
            false
        );
        return httpResponse != null ? httpResponse.getBodyAsString() : "";
    }

    /**
     * Retrieve a single registered load scenario by name via
     * {@code GET /mockserver/loadScenario/{name}}. The server responds {@code 404} if no
     * scenario with that name is registered.
     *
     * @param name the unique name of the registered load scenario
     * @return JSON string describing the named load scenario (name, state, definition, status)
     */
    public String getLoadScenario(String name) {
        HttpResponse httpResponse = sendRequest(
            request()
                .withMethod("GET")
                .withPath(calculatePath("loadScenario/" + name)),
            false
        );
        return httpResponse != null ? httpResponse.getBodyAsString() : "";
    }

    /**
     * Remove a single registered load scenario by name via
     * {@code DELETE /mockserver/loadScenario/{name}}. If the scenario is running it is
     * stopped before removal.
     *
     * @param name the unique name of the load scenario to remove
     * @return this MockServerClient
     */
    public MockServerClient deleteLoadScenario(String name) {
        sendRequest(
            request()
                .withMethod("DELETE")
                .withPath(calculatePath("loadScenario/" + name)),
            true
        );
        return clientClass.cast(this);
    }

    /**
     * Remove all registered load scenarios via {@code DELETE /mockserver/loadScenario}.
     * Any running scenarios are stopped before the registry is cleared.
     *
     * @return this MockServerClient
     */
    public MockServerClient clearLoadScenarios() {
        sendRequest(
            request()
                .withMethod("DELETE")
                .withPath(calculatePath("loadScenario")),
            true
        );
        return clientClass.cast(this);
    }

    /**
     * Start one or more previously-registered load scenarios by name via
     * {@code PUT /mockserver/loadScenario/start} with body {@code {"names":[...]}}. Each
     * named scenario begins running (honouring its {@code startDelayMillis}, which holds
     * the scenario in {@code PENDING} until the delay elapses).
     *
     * <p>Starting requires {@code loadGenerationEnabled} on the server — if it is off the
     * server responds {@code 403} and this throws a {@link ClientException} with a helpful
     * message. An unknown scenario name causes the server to respond {@code 404}, which is
     * also surfaced as a {@link ClientException}.
     *
     * @param names the names of the registered scenarios to start
     * @return JSON string describing the started scenarios ({@code {"started":[...],"status":...}})
     */
    public String startLoadScenarios(String... names) {
        HttpResponse httpResponse = sendRequest(
            request()
                .withMethod("PUT")
                .withContentType(APPLICATION_JSON_UTF_8)
                .withPath(calculatePath("loadScenario/start"))
                .withBody(namesBody(names), StandardCharsets.UTF_8),
            false
        );
        if (httpResponse != null && httpResponse.getStatusCode() != null) {
            if (httpResponse.getStatusCode() == FORBIDDEN.code()) {
                throw new ClientException("load generation is disabled on this MockServer; set loadGenerationEnabled=true to enable load scenarios - server responded: " + httpResponse.getBodyAsString());
            } else if (httpResponse.getStatusCode() >= 400) {
                throw new ClientException(formatLogMessage("error:{}while starting load scenarios", httpResponse.getBodyAsString()));
            }
        }
        return httpResponse != null ? httpResponse.getBodyAsString() : "";
    }

    /**
     * Stop one or more running load scenarios via {@code PUT /mockserver/loadScenario/stop}.
     * When {@code names} are supplied the body is {@code {"names":[...]}} and only those
     * scenarios are stopped; when called with no arguments the body is empty, which stops
     * all running scenarios. Stopped scenarios transition to the {@code STOPPED} state but
     * remain registered (and can be re-started).
     *
     * @param names the names of the scenarios to stop, or none to stop all running scenarios
     * @return JSON string describing the stopped scenarios ({@code {"stopped":[...],"status":...}})
     */
    public String stopLoadScenarios(String... names) {
        HttpResponse httpResponse = sendRequest(
            request()
                .withMethod("PUT")
                .withContentType(APPLICATION_JSON_UTF_8)
                .withPath(calculatePath("loadScenario/stop"))
                .withBody(names != null && names.length > 0 ? namesBody(names) : "", StandardCharsets.UTF_8),
            false
        );
        return httpResponse != null ? httpResponse.getBodyAsString() : "";
    }

    /**
     * Convenience helper that registers the given scenario and then immediately starts it,
     * combining {@link #loadScenario(LoadScenario)} and {@link #startLoadScenarios(String...)}.
     * Starting requires {@code loadGenerationEnabled} on the server (see
     * {@link #startLoadScenarios(String...)} for the {@code 403} behaviour).
     *
     * @param scenario the load scenario to register and start
     * @return JSON string describing the started scenario ({@code {"started":[...],"status":...}})
     */
    public String runLoadScenario(LoadScenario scenario) {
        loadScenario(scenario);
        return startLoadScenarios(scenario.getName());
    }

    /**
     * Retrieve the end-of-run summary report for a load scenario run via
     * {@code GET /mockserver/loadScenario/{name}/report}. The report is derived from the
     * run's status snapshot — the live snapshot while running, or the retained terminal
     * snapshot once finished — and carries counts, latency percentiles, the threshold
     * verdict and per-threshold results. The server responds {@code 404} if the named
     * scenario never ran.
     *
     * @param name the unique name of the load scenario whose run report is wanted
     * @return JSON string describing the run report (counts, percentiles, threshold verdict)
     */
    public String getLoadScenarioReport(String name) {
        return getLoadScenarioReport(name, null);
    }

    /**
     * Retrieve the end-of-run summary report for a load scenario run via
     * {@code GET /mockserver/loadScenario/{name}/report}, selecting the rendering with the
     * {@code format} query parameter. The default JSON form (when {@code format} is
     * {@code null} or empty, or any value other than {@code "junit"}) carries counts,
     * latency percentiles and per-threshold results; {@code format="junit"} renders the
     * same data as a JUnit-XML {@code <testsuite>} so a load run becomes a first-class CI
     * test artifact. The server responds {@code 404} if the named scenario never ran.
     *
     * @param name   the unique name of the load scenario whose run report is wanted
     * @param format the report format (e.g. {@code "junit"}); {@code null} or empty selects the default JSON report
     * @return the run report as a JSON string (or a JUnit-XML document when {@code format="junit"})
     */
    public String getLoadScenarioReport(String name, String format) {
        HttpRequest request = request()
            .withMethod("GET")
            .withPath(calculatePath("loadScenario/" + name + "/report"));
        if (format != null && !format.isEmpty()) {
            request.withQueryStringParameter("format", format);
        }
        HttpResponse httpResponse = sendRequest(request, false);
        return httpResponse != null ? httpResponse.getBodyAsString() : "";
    }

    /**
     * Generate an editable load scenario from an OpenAPI specification and load (register)
     * it via {@code PUT /mockserver/loadScenario/generateFromOpenAPI}. The generated
     * scenario is registered in the {@code LOADED} state — exactly like
     * {@link #loadScenario(LoadScenario)}, it drives no traffic and is allowed even when
     * {@code loadGenerationEnabled} is off — and is returned so it can be shown and edited
     * before a run is triggered.
     *
     * <p>The body is a JSON object the caller builds, carrying at least {@code name} and
     * {@code specUrlOrPayload} (the OpenAPI spec as an inline JSON/YAML payload, a URL, or a
     * file/classpath reference), with optional {@code target} and {@code profile}, e.g.
     * {@code {"name":"petstore-load","specUrlOrPayload":"..."}}.
     *
     * @param jsonBody the JSON request body ({@code {name, specUrlOrPayload, target?, profile?}})
     * @return JSON string describing the generated, loaded scenario ({@code {"status":"loaded","name":...,"scenario":...}})
     */
    public String generateLoadScenarioFromOpenAPI(String jsonBody) {
        HttpResponse httpResponse = sendRequest(
            request()
                .withMethod("PUT")
                .withContentType(APPLICATION_JSON_UTF_8)
                .withPath(calculatePath("loadScenario/generateFromOpenAPI"))
                .withBody(jsonBody != null ? jsonBody : "", StandardCharsets.UTF_8),
            false
        );
        if (httpResponse != null && httpResponse.getStatusCode() != null && httpResponse.getStatusCode() >= 400) {
            throw new ClientException(formatLogMessage("error:{}while generating load scenario from OpenAPI", httpResponse.getBodyAsString()));
        }
        return httpResponse != null ? httpResponse.getBodyAsString() : "";
    }

    /**
     * Generate an editable load scenario from recorded proxy traffic and load (register) it
     * via {@code PUT /mockserver/loadScenario/generateFromRecording}. The generated scenario
     * is registered in the {@code LOADED} state — exactly like
     * {@link #loadScenario(LoadScenario)}, it drives no traffic and is allowed even when
     * {@code loadGenerationEnabled} is off — and is returned so it can be shown and edited
     * before a run is triggered.
     *
     * <p>The body is a JSON object the caller builds, carrying at least {@code name}, with an
     * optional {@code mode} ({@code VERBATIM} (default) = one step per recorded request;
     * {@code TEMPLATIZED} = one step per unique route), plus optional {@code requestFilter},
     * {@code maxSteps}, {@code target} and {@code profile}, e.g.
     * {@code {"name":"replay-prod-traffic","mode":"TEMPLATIZED"}}.
     *
     * @param jsonBody the JSON request body ({@code {name, mode?, requestFilter?, maxSteps?, target?, profile?}})
     * @return JSON string describing the generated, loaded scenario ({@code {"status":"loaded","name":...,"scenario":...}})
     */
    public String generateLoadScenarioFromRecording(String jsonBody) {
        HttpResponse httpResponse = sendRequest(
            request()
                .withMethod("PUT")
                .withContentType(APPLICATION_JSON_UTF_8)
                .withPath(calculatePath("loadScenario/generateFromRecording"))
                .withBody(jsonBody != null ? jsonBody : "", StandardCharsets.UTF_8),
            false
        );
        if (httpResponse != null && httpResponse.getStatusCode() != null && httpResponse.getStatusCode() >= 400) {
            throw new ClientException(formatLogMessage("error:{}while generating load scenario from recording", httpResponse.getBodyAsString()));
        }
        return httpResponse != null ? httpResponse.getBodyAsString() : "";
    }

    // SRE control-plane: SLO verdicts and scheduled chaos experiments

    /**
     * Verify a service-level objective (SLO) over a window of recorded SLI samples via
     * {@code PUT /mockserver/verifySLO}. The supplied {@link SloCriteria} is evaluated
     * synchronously against the samples the server has already recorded and answered with
     * an {@link SloVerdict} whose {@link SloVerdict.Result result} is {@code PASS},
     * {@code FAIL} or {@code INCONCLUSIVE}.
     *
     * <p>Status mapping mirrors the server contract and the other clients:
     * <ul>
     *   <li>{@code 200 OK} — a {@code PASS} or {@code INCONCLUSIVE} verdict; the parsed
     *       {@link SloVerdict} is returned.</li>
     *   <li>{@code 406 NOT_ACCEPTABLE} — a {@code FAIL} verdict; this throws an
     *       {@link AssertionError} carrying the verdict body, exactly like the other
     *       {@code verify(...)} methods so an SLO gate is catchable the same way. The
     *       verdict is also attached via {@link Throwable#initCause(Throwable)} as an
     *       {@link SloVerdictAssertionError} for callers that want the parsed result.</li>
     *   <li>{@code 403 FORBIDDEN} — SLO tracking is disabled on the server (start it with
     *       {@code sloTrackingEnabled=true}); this surfaces as an
     *       {@link IllegalStateException} naming the flag to enable.</li>
     *   <li>{@code 400 BAD_REQUEST} — malformed criteria; this surfaces as an
     *       {@link IllegalArgumentException} carrying the server's error body (the common
     *       client convention — {@code sendRequest} maps every {@code 400} this way).</li>
     * </ul>
     *
     * <p>The disabled case used to share {@code 400} with malformed criteria, so a typo in the
     * criteria and a server with the feature switched off were indistinguishable to a caller.
     *
     * @param criteria the SLO criteria to evaluate (see {@link org.mockserver.slo.SloCriteria})
     * @return the parsed {@link SloVerdict} for a {@code PASS} or {@code INCONCLUSIVE} result
     * @throws AssertionError         if the verdict is {@code FAIL} (server responds {@code 406})
     * @throws IllegalStateException  if SLO tracking is disabled (server responds {@code 403})
     * @throws IllegalArgumentException if the criteria are invalid (server responds {@code 400})
     */
    public SloVerdict verifySLO(SloCriteria criteria) throws AssertionError {
        if (criteria == null) {
            throw new IllegalArgumentException("verifySLO requires a non-null SloCriteria");
        }
        HttpResponse httpResponse = sendRequest(
            request()
                .withMethod("PUT")
                .withContentType(APPLICATION_JSON_UTF_8)
                .withPath(calculatePath("verifySLO"))
                .withBody(sloCriteriaSerializer.serialize(criteria), StandardCharsets.UTF_8),
            false
        );
        Integer statusCode = httpResponse != null ? httpResponse.getStatusCode() : null;
        String body = httpResponse != null ? httpResponse.getBodyAsString() : "";
        if (statusCode != null) {
            if (statusCode == NOT_ACCEPTABLE.code()) {
                SloVerdictAssertionError assertionError = new SloVerdictAssertionError(body);
                try {
                    assertionError.initCause(new SloVerdictHolder(sloCriteriaSerializer.deserializeVerdict(body)));
                } catch (Throwable ignore) {
                    // body was not a parseable verdict — the message still carries it
                }
                throw assertionError;
            } else if (statusCode == FORBIDDEN.code()) {
                throw new IllegalStateException("SLO tracking is disabled - start MockServer with sloTrackingEnabled=true: " + body);
            } else if (statusCode >= 400) {
                // 400 (malformed criteria) is already mapped to IllegalArgumentException by
                // sendRequest; this covers any other unexpected error status.
                throw new ClientException(formatLogMessage("error:{}while verifying SLO", body));
            }
        }
        return sloCriteriaSerializer.deserializeVerdict(body);
    }

    /**
     * An {@link AssertionError} raised when {@link #verifySLO(SloCriteria)} receives a
     * {@code FAIL} verdict ({@code 406}). The raw verdict JSON is the message; when it
     * parsed, the {@link SloVerdict} is available via {@link #getVerdict()}.
     */
    public static class SloVerdictAssertionError extends AssertionError {
        public SloVerdictAssertionError(String message) {
            super(message);
        }

        public SloVerdict getVerdict() {
            Throwable cause = getCause();
            return cause instanceof SloVerdictHolder ? ((SloVerdictHolder) cause).verdict : null;
        }
    }

    private static class SloVerdictHolder extends Throwable {
        private final SloVerdict verdict;

        private SloVerdictHolder(SloVerdict verdict) {
            this.verdict = verdict;
        }
    }

    /**
     * Start a scheduled multi-stage chaos experiment via {@code PUT /mockserver/chaosExperiment}.
     * The experiment is an ordered sequence of stages, each applying service-scoped chaos
     * profiles to one or more hosts for a duration; stages progress automatically. Only one
     * experiment may be active at a time — starting a new one stops the previous one.
     *
     * <p>The body is a JSON object the caller builds, carrying at least {@code name} and a
     * {@code stages} array, e.g.
     * {@code {"name":"flaky-upstream","loop":false,"stages":[{"durationMillis":30000,"profiles":{"api.example.com":{...}}}]}}.
     *
     * <p>There is no server setting that disables chaos experiments. The server responds
     * {@code 403} only when control-plane authorization rejects the request, and this throws a
     * {@link ClientException} with a helpful message. An invalid definition (server responds
     * {@code 400}) surfaces as an {@link IllegalArgumentException} carrying the server's error
     * body (the common client convention — {@code sendRequest} maps every {@code 400} this way);
     * any other {@code >= 400} status is surfaced as a {@link ClientException}.
     *
     * @param experimentJson the experiment definition as JSON ({@code {name, loop?, stages[...]}})
     * @return JSON string describing the started experiment ({@code {"status":"started","name":...,"stages":...,"loop":...}})
     * @throws ClientException          if control-plane authorization is denied (server responds {@code 403}) or an unexpected error status is returned
     * @throws IllegalArgumentException if the definition is rejected (server responds {@code 400})
     */
    public String startChaosExperiment(String experimentJson) {
        if (experimentJson == null || experimentJson.isBlank()) {
            throw new IllegalArgumentException("startChaosExperiment requires a non-null non-empty experiment definition JSON string");
        }
        HttpResponse httpResponse = sendRequest(
            request()
                .withMethod("PUT")
                .withContentType(APPLICATION_JSON_UTF_8)
                .withPath(calculatePath("chaosExperiment"))
                .withBody(experimentJson, StandardCharsets.UTF_8),
            false
        );
        if (httpResponse != null && httpResponse.getStatusCode() != null) {
            if (httpResponse.getStatusCode() == FORBIDDEN.code()) {
                throw new ClientException("forbidden while starting chaos experiment (control-plane authorization denied) - server responded: " + httpResponse.getBodyAsString());
            } else if (httpResponse.getStatusCode() >= 400) {
                throw new ClientException(formatLogMessage("error:{}while starting chaos experiment", httpResponse.getBodyAsString()));
            }
        }
        return httpResponse != null ? httpResponse.getBodyAsString() : "";
    }

    private static String namesBody(String... names) {
        StringBuilder body = new StringBuilder("{\"names\":[");
        for (int i = 0; i < names.length; i++) {
            if (i > 0) {
                body.append(',');
            }
            body.append('"').append(names[i] != null ? names[i].replace("\\", "\\\\").replace("\"", "\\\"") : "").append('"');
        }
        return body.append("]}").toString();
    }

    // asyncapi control-plane helpers

    /**
     * Load an AsyncAPI spec into MockServer to start async messaging mocking.
     * The body can be either a plain AsyncAPI document (JSON or YAML) or a
     * wrapped body: {@code {"spec": <spec>, "brokerConfig": {...}}}.
     *
     * @param specOrWrappedJson the AsyncAPI spec or wrapped JSON body
     * @return the JSON response from the server describing the loaded spec
     */
    public String loadAsyncApi(String specOrWrappedJson) {
        HttpResponse httpResponse = sendRequest(
            request()
                .withMethod("PUT")
                .withContentType(APPLICATION_JSON_UTF_8)
                .withPath(calculatePath("asyncapi"))
                .withBody(specOrWrappedJson != null ? specOrWrappedJson : "", StandardCharsets.UTF_8),
            true
        );
        return httpResponse != null ? httpResponse.getBodyAsString() : "";
    }

    /**
     * Retrieve the current AsyncAPI mocking status including loaded spec info,
     * active channels, and recorded messages.
     *
     * @return JSON string of the current async mocking status
     */
    public String asyncApiStatus() {
        HttpResponse httpResponse = sendRequest(
            request()
                .withMethod("GET")
                .withPath(calculatePath("asyncapi")),
            false
        );
        return httpResponse != null ? httpResponse.getBodyAsString() : "";
    }

    /**
     * Verify async messages recorded by subscribers against the given criteria.
     * The verification JSON must contain at least a {@code channel} field, and
     * optionally {@code payloadSubstring}, {@code payloadJsonPath}, {@code expectedValue},
     * and {@code count} (with {@code atLeast}, {@code atMost}, or {@code exactly}).
     *
     * @param verificationJson the verification criteria as JSON
     * @throws AssertionError if the verification fails (server responds with 406)
     */
    public MockServerClient verifyAsyncMessage(String verificationJson) throws AssertionError {
        if (verificationJson == null || verificationJson.isBlank()) {
            throw new IllegalArgumentException("verifyAsyncMessage requires a non-null non-empty verification JSON string");
        }

        try {
            HttpResponse httpResponse = sendRequest(
                request()
                    .withMethod("PUT")
                    .withContentType(APPLICATION_JSON_UTF_8)
                    .withPath(calculatePath("asyncapi/verify"))
                    .withBody(verificationJson, StandardCharsets.UTF_8),
                false
            );

            if (httpResponse != null && httpResponse.getStatusCode() != null) {
                if (httpResponse.getStatusCode() == 406) {
                    throw new AssertionError(httpResponse.getBodyAsString());
                }
            }
        } catch (AssertionError ae) {
            throw ae;
        } catch (AuthenticationException authenticationException) {
            throw authenticationException;
        } catch (Throwable throwable) {
            throw new AssertionError(throwable.getMessage());
        }
        return clientClass.cast(this);
    }

    // -------------------------------------------------------------------
    // Breakpoint Matcher Registration (WS-callback)
    // -------------------------------------------------------------------

    /**
     * Ensure the breakpoint callback WebSocket is connected, returning the
     * {@link BreakpointWebSocketClient} instance. The caller should capture the
     * returned reference to a local variable and use that throughout its method
     * to avoid a data race with the STOP/RESET lambda that nulls the field.
     * The WS connection is reused across all breakpoints registered by this client.
     */
    private synchronized BreakpointWebSocketClient ensureBreakpointWebSocketClient() {
        if (breakpointWebSocketClient != null) {
            return breakpointWebSocketClient;
        }
        BreakpointWebSocketClient wsClient = null;
        try {
            String bpClientId = UUIDService.getUUID();
            wsClient = new BreakpointWebSocketClient(
                new NioEventLoopGroup(
                    configuration.webSocketClientEventLoopThreadCount(),
                    new Scheduler.SchedulerThreadFactory("BreakpointWSClient-eventLoop")
                ),
                bpClientId,
                MOCK_SERVER_LOGGER,
                this::webSocketSslContext
            ).withAuthorization(this::webSocketAuthorization);
            wsClient.register(
                remoteAddress(),
                contextPath(),
                isSecure()
            ).get(configuration.maxFutureTimeoutInMillis(), MILLISECONDS);
            this.breakpointWebSocketClient = wsClient;
            // Subscribe a lambda (not a method ref) so we can null the field AND
            // clear the handler map on both STOP and RESET. This ensures the next
            // addBreakpoint after reset/stop transparently re-establishes the WS.
            getMockServerEventBus().subscribe(() -> {
                BreakpointWebSocketClient client = breakpointWebSocketClient;
                breakpointWebSocketClient = null;
                if (client != null) {
                    try {
                        client.clearHandlers();
                        client.stopClient();
                    } catch (Exception ignored) {
                        // best-effort cleanup
                    }
                }
            }, EventType.STOP, EventType.RESET);
            return wsClient;
        } catch (Exception e) {
            ClientException notRegistered = new ClientException("Unable to establish breakpoint WebSocket connection", e);
            if (wsClient != null) {
                // nothing else refers to it, so its event loops must be released here
                try {
                    wsClient.stopClient();
                } catch (RuntimeException stopFailure) {
                    notRegistered.addSuppressed(stopFailure);
                }
            }
            throw notRegistered;
        }
    }

    /**
     * Register a breakpoint matcher with request/response/stream-frame handlers.
     * The client's callback WebSocket is opened if not already connected.
     *
     * <p>Handlers are stored per breakpoint id (the id returned by the server), so
     * multiple concurrent breakpoints each route to their own handler. If a handler
     * is null for a phase that is in the phase set, items for that phase will be
     * auto-continued.
     *
     * @param matcher           the request definition to match against (same shape as an
     *                          expectation request matcher)
     * @param phases            the set of phases to intercept
     * @param requestHandler    handler for REQUEST phase (may be null if REQUEST not in phases)
     * @param responseHandler   handler for RESPONSE phase (may be null if RESPONSE not in phases)
     * @param streamFrameHandler handler for RESPONSE_STREAM / INBOUND_STREAM phases (may be null
     *                          if neither streaming phase is in phases)
     * @return the breakpoint matcher id assigned by the server
     */
    public String addBreakpoint(RequestDefinition matcher,
                                Set<BreakpointPhase> phases,
                                BreakpointRequestHandler requestHandler,
                                BreakpointResponseHandler responseHandler,
                                BreakpointStreamFrameHandler streamFrameHandler) {
        if (matcher == null) {
            throw new IllegalArgumentException("addBreakpoint requires a non-null matcher");
        }
        if (phases == null || phases.isEmpty()) {
            throw new IllegalArgumentException("addBreakpoint requires a non-null non-empty set of phases");
        }

        // Capture the WS client instance to a local to avoid data race (MAJOR C)
        BreakpointWebSocketClient wsClient = ensureBreakpointWebSocketClient();
        String wsClientId = wsClient.getClientId();

        // register the breakpoint matcher on the server first to get the id
        String breakpointId;
        try {
            ObjectMapper objectMapper = ObjectMapperFactory.createObjectMapper();
            ObjectNode body = objectMapper.createObjectNode();
            String serializedMatcher = requestDefinitionSerializer.serialize(matcher);
            body.set("httpRequest", objectMapper.readTree(serializedMatcher));
            ArrayNode phasesArray = objectMapper.createArrayNode();
            for (BreakpointPhase phase : phases) {
                phasesArray.add(phase.name());
            }
            body.set("phases", phasesArray);
            body.put("clientId", wsClientId);

            HttpResponse httpResponse = sendRequest(
                request()
                    .withMethod("PUT")
                    .withContentType(APPLICATION_JSON_UTF_8)
                    .withPath(calculatePath("breakpoint/matcher"))
                    .withBody(objectMapper.writeValueAsString(body), StandardCharsets.UTF_8),
                true
            );

            // parse the response to get the id
            if (httpResponse != null && isNotBlank(httpResponse.getBodyAsString())) {
                JsonNode responseNode = objectMapper.readTree(httpResponse.getBodyAsString());
                if (responseNode.has("id")) {
                    breakpointId = responseNode.get("id").asText();
                } else {
                    throw new ClientException("Server did not return a breakpoint id");
                }
            } else {
                throw new ClientException("Server did not return a breakpoint id");
            }
        } catch (IllegalArgumentException iae) {
            throw iae;
        } catch (ClientException ce) {
            throw ce;
        } catch (Exception exception) {
            throw new RuntimeException("Exception registering breakpoint matcher", exception);
        }

        // Install handlers keyed by the server-assigned breakpoint id (MAJOR A)
        wsClient.setRequestHandler(breakpointId, requestHandler);
        wsClient.setResponseHandler(breakpointId, responseHandler);
        wsClient.setStreamFrameHandler(breakpointId, streamFrameHandler);

        return breakpointId;
    }

    /**
     * Register a breakpoint matcher with a single request handler (REQUEST phase only).
     *
     * @param matcher        the request definition to match against
     * @param requestHandler handler for REQUEST phase breakpoints
     * @return the breakpoint matcher id
     */
    public String addBreakpoint(RequestDefinition matcher, BreakpointRequestHandler requestHandler) {
        return addBreakpoint(matcher, EnumSet.of(BreakpointPhase.REQUEST), requestHandler, null, null);
    }

    /**
     * Register a breakpoint matcher with request and response handlers.
     *
     * @param matcher         the request definition to match against
     * @param requestHandler  handler for REQUEST phase
     * @param responseHandler handler for RESPONSE phase
     * @return the breakpoint matcher id
     */
    public String addBreakpoint(RequestDefinition matcher,
                                BreakpointRequestHandler requestHandler,
                                BreakpointResponseHandler responseHandler) {
        return addBreakpoint(matcher, EnumSet.of(BreakpointPhase.REQUEST, BreakpointPhase.RESPONSE),
            requestHandler, responseHandler, null);
    }

    /**
     * Register a breakpoint matcher with a stream frame handler.
     *
     * @param matcher            the request definition to match against
     * @param phases             the set of streaming phases to intercept
     * @param streamFrameHandler handler for RESPONSE_STREAM / INBOUND_STREAM phases
     * @return the breakpoint matcher id
     */
    public String addBreakpoint(RequestDefinition matcher, Set<BreakpointPhase> phases,
                                BreakpointStreamFrameHandler streamFrameHandler) {
        return addBreakpoint(matcher, phases, null, null, streamFrameHandler);
    }

    /**
     * Register a breakpoint matcher with varargs phases and all handlers.
     * If zero phases are passed, phases are inferred from the non-null handlers:
     * requestHandler implies REQUEST, responseHandler implies RESPONSE,
     * streamFrameHandler implies RESPONSE_STREAM and INBOUND_STREAM.
     *
     * @param matcher            the request definition to match against
     * @param requestHandler     handler for REQUEST phase (may be null)
     * @param responseHandler    handler for RESPONSE phase (may be null)
     * @param streamFrameHandler handler for streaming phases (may be null)
     * @param phases             the phases to intercept (if empty, inferred from handlers)
     * @return the breakpoint matcher id
     * @throws IllegalArgumentException if no phases can be determined
     */
    public String addBreakpoint(RequestDefinition matcher,
                                BreakpointRequestHandler requestHandler,
                                BreakpointResponseHandler responseHandler,
                                BreakpointStreamFrameHandler streamFrameHandler,
                                BreakpointPhase... phases) {
        Set<BreakpointPhase> phaseSet;
        if (phases.length > 0) {
            phaseSet = EnumSet.copyOf(Arrays.asList(phases));
        } else {
            // Infer phases from non-null handlers
            phaseSet = EnumSet.noneOf(BreakpointPhase.class);
            if (requestHandler != null) {
                phaseSet.add(BreakpointPhase.REQUEST);
            }
            if (responseHandler != null) {
                phaseSet.add(BreakpointPhase.RESPONSE);
            }
            if (streamFrameHandler != null) {
                phaseSet.add(BreakpointPhase.RESPONSE_STREAM);
                phaseSet.add(BreakpointPhase.INBOUND_STREAM);
            }
            if (phaseSet.isEmpty()) {
                throw new IllegalArgumentException("at least one phase or handler is required");
            }
        }
        return addBreakpoint(matcher, phaseSet, requestHandler, responseHandler, streamFrameHandler);
    }

    /**
     * List all registered breakpoint matchers.
     * Returns a JSON string with the structure:
     * <pre>
     * {
     *   "matchers": [
     *     { "id": "...", "httpRequest": {...}, "phases": [...], "clientId": "..." }
     *   ]
     * }
     * </pre>
     *
     * @return JSON string describing all registered breakpoint matchers
     */
    public String listBreakpointMatchers() {
        HttpResponse httpResponse = sendRequest(
            request()
                .withMethod("GET")
                .withPath(calculatePath("breakpoint/matchers")),
            false
        );
        return httpResponse != null ? httpResponse.getBodyAsString() : "";
    }

    /**
     * Remove a breakpoint matcher by id.
     *
     * @param id the breakpoint matcher id to remove
     * @return this MockServerClient
     * @throws IllegalArgumentException if the id is blank
     */
    public MockServerClient removeBreakpointMatcher(String id) {
        if (isBlank(id)) {
            throw new IllegalArgumentException("removeBreakpointMatcher requires a non-blank id");
        }
        try {
            ObjectMapper objectMapper = ObjectMapperFactory.createObjectMapper();
            ObjectNode body = objectMapper.createObjectNode();
            body.put("id", id);
            sendRequest(
                request()
                    .withMethod("PUT")
                    .withContentType(APPLICATION_JSON_UTF_8)
                    .withPath(calculatePath("breakpoint/matcher/remove"))
                    .withBody(objectMapper.writeValueAsString(body), StandardCharsets.UTF_8),
                true
            );
        } catch (IllegalArgumentException iae) {
            throw iae;
        } catch (Exception exception) {
            throw new RuntimeException("Exception removing breakpoint matcher with id \"" + id + "\"", exception);
        }
        // Remove client-side handlers for this breakpoint
        BreakpointWebSocketClient wsClient = breakpointWebSocketClient;
        if (wsClient != null) {
            wsClient.removeHandlers(id);
        }
        return clientClass.cast(this);
    }

    /**
     * Clear all registered breakpoint matchers.
     *
     * @return this MockServerClient
     */
    public MockServerClient clearBreakpointMatchers() {
        sendRequest(
            request()
                .withMethod("PUT")
                .withPath(calculatePath("breakpoint/matcher/clear")),
            true
        );
        // Clear all client-side handlers
        BreakpointWebSocketClient wsClient = breakpointWebSocketClient;
        if (wsClient != null) {
            wsClient.clearHandlers();
        }
        return clientClass.cast(this);
    }

    // -------------------------------------------------------------------
    // Replay
    // -------------------------------------------------------------------

    /**
     * Replay a request to its upstream target and return the upstream response.
     * The request is sent exactly as specified — the target host/port is resolved
     * from the {@code Host} header or the explicit {@code socketAddress} field.
     *
     * @param requestToReplay the request to re-issue to the upstream server
     * @return the upstream response
     * @throws IllegalArgumentException if the request is null or the server rejects the request
     */
    public HttpResponse replay(HttpRequest requestToReplay) {
        if (requestToReplay == null) {
            throw new IllegalArgumentException("replay requires a non-null HttpRequest");
        }
        HttpResponse httpResponse = sendRequest(
            request()
                .withMethod("PUT")
                .withContentType(APPLICATION_JSON_UTF_8)
                .withPath(calculatePath("replay"))
                .withBody(httpRequestSerializer.serialize(requestToReplay), StandardCharsets.UTF_8),
            true
        );
        if (httpResponse != null && isNotBlank(httpResponse.getBodyAsString())) {
            return httpResponseSerializer.deserialize(httpResponse.getBodyAsString());
        }
        return httpResponse;
    }

    // -------------------------------------------------------------------
    // Contract testing & Pact
    // -------------------------------------------------------------------

    private static final ObjectMapper CONTRACT_OBJECT_MAPPER = new ObjectMapper();

    /**
     * Result of validating a single operation/request against an OpenAPI specification.
     *
     * @param operationId      the OpenAPI operationId exercised (contract test), or {@code null}
     * @param method           the HTTP method
     * @param path             the request path
     * @param matchedOperation the {@code METHOD /path} spec operation matched (traffic validation), or {@code null}
     * @param statusCode       the HTTP status code received (contract test), or {@code 0} when not applicable
     * @param passed           whether the request and response conformed to the spec
     * @param requestErrors    request-side validation errors (empty when none)
     * @param responseErrors   response-side validation errors (empty when none)
     */
    public record ContractResult(
        String operationId,
        String method,
        String path,
        String matchedOperation,
        int statusCode,
        boolean passed,
        List<String> requestErrors,
        List<String> responseErrors
    ) {
    }

    /**
     * Structured report returned by {@link #contractTest} and {@link #trafficValidate}.
     *
     * @param total     the total number of operations / requests validated
     * @param passed    the number that conformed to the spec
     * @param failed    the number that did not conform
     * @param allPassed {@code true} when every result passed
     * @param results   the per-operation / per-request results
     */
    public record ContractReport(int total, int passed, int failed, boolean allPassed, List<ContractResult> results) {
    }

    /**
     * Run an OpenAPI contract test against a live service: every operation in the spec is exercised
     * against {@code baseUrl} and the response is validated against the spec. Wraps
     * {@code PUT /mockserver/contractTest}.
     *
     * @param spec    a URL, file path, or inline OpenAPI specification
     * @param baseUrl the base URL of the service under test (e.g. {@code http://localhost:8080})
     * @return the structured contract-test report
     */
    public ContractReport contractTest(String spec, String baseUrl) {
        return contractTest(spec, baseUrl, null);
    }

    /**
     * Run an OpenAPI contract test against a live service, optionally restricted to a single operation.
     * Wraps {@code PUT /mockserver/contractTest}.
     *
     * @param spec        a URL, file path, or inline OpenAPI specification
     * @param baseUrl     the base URL of the service under test (e.g. {@code http://localhost:8080})
     * @param operationId an optional operationId filter; when non-blank only that operation is exercised
     * @return the structured contract-test report
     */
    public ContractReport contractTest(String spec, String baseUrl, String operationId) {
        if (isBlank(spec)) {
            throw new IllegalArgumentException("contractTest(spec, baseUrl) requires a non null spec");
        }
        if (isBlank(baseUrl)) {
            throw new IllegalArgumentException("contractTest(spec, baseUrl) requires a non null baseUrl");
        }
        ObjectNode requestBody = CONTRACT_OBJECT_MAPPER.createObjectNode();
        requestBody.put("spec", spec);
        requestBody.put("baseUrl", baseUrl);
        if (isNotBlank(operationId)) {
            requestBody.put("operationId", operationId);
        }
        return parseContractReport(sendContractPut("contractTest", requestBody));
    }

    /**
     * Validate the recorded request/response traffic against an OpenAPI specification. Wraps
     * {@code PUT /mockserver/trafficValidate}.
     *
     * @param spec a URL, file path, or inline OpenAPI specification
     * @return the structured traffic-validation report
     */
    public ContractReport trafficValidate(String spec) {
        if (isBlank(spec)) {
            throw new IllegalArgumentException("trafficValidate(spec) requires a non null spec");
        }
        ObjectNode requestBody = CONTRACT_OBJECT_MAPPER.createObjectNode();
        requestBody.put("spec", spec);
        return parseContractReport(sendContractPut("trafficValidate", requestBody));
    }

    /**
     * Import a Pact v3 consumer contract as expectations. Wraps {@code PUT /mockserver/pact/import}.
     *
     * @param pactJson the Pact v3 contract JSON document
     * @return the upserted expectations as a JSON array string (as returned by the server)
     */
    public String pactImport(String pactJson) {
        if (isBlank(pactJson)) {
            throw new IllegalArgumentException("pactImport(pactJson) requires a non null pact contract JSON");
        }
        HttpResponse httpResponse = sendRequest(
            request()
                .withMethod("PUT")
                .withContentType(APPLICATION_JSON_UTF_8)
                .withPath(calculatePath("pact/import"))
                .withBody(pactJson, StandardCharsets.UTF_8),
            false
        );
        return httpResponse != null ? httpResponse.getBodyAsString() : null;
    }

    /**
     * Export the active expectations as a Pact v3 consumer contract. Wraps {@code PUT /mockserver/pact}.
     *
     * @param consumer the consumer name, or {@code null}/blank to use the server default
     * @param provider the provider name, or {@code null}/blank to use the server default
     * @return the Pact v3 contract JSON document
     */
    public String pactExport(String consumer, String provider) {
        HttpRequest pactExportRequest = request()
            .withMethod("PUT")
            .withContentType(APPLICATION_JSON_UTF_8)
            .withPath(calculatePath("pact"));
        if (isNotBlank(consumer)) {
            pactExportRequest.withQueryStringParameter("consumer", consumer);
        }
        if (isNotBlank(provider)) {
            pactExportRequest.withQueryStringParameter("provider", provider);
        }
        HttpResponse httpResponse = sendRequest(pactExportRequest, false);
        return httpResponse != null ? httpResponse.getBodyAsString() : null;
    }

    /**
     * Verify a Pact v3 consumer contract against the active expectations. Wraps
     * {@code PUT /mockserver/pact/verify}. The server replies {@code 202 ACCEPTED} when every
     * interaction matches an expectation and {@code 406 NOT_ACCEPTABLE} otherwise; the verification
     * report body is returned in both cases.
     *
     * @param pactJson the Pact v3 contract JSON document
     * @return the verification report JSON document
     */
    public String pactVerify(String pactJson) {
        if (isBlank(pactJson)) {
            throw new IllegalArgumentException("pactVerify(pactJson) requires a non null pact contract JSON");
        }
        // Use throwClientException=false: the server replies 406 NOT_ACCEPTABLE (>= 400) with the
        // verification report body on a FAIL verdict, which is an expected outcome — the report must be
        // returned to the caller, not raised as a ClientException.
        HttpResponse httpResponse = sendRequest(
            request()
                .withMethod("PUT")
                .withContentType(APPLICATION_JSON_UTF_8)
                .withPath(calculatePath("pact/verify"))
                .withBody(pactJson, StandardCharsets.UTF_8),
            false
        );
        return httpResponse != null ? httpResponse.getBodyAsString() : null;
    }

    private String sendContractPut(String path, ObjectNode requestBody) {
        String body;
        try {
            body = CONTRACT_OBJECT_MAPPER.writeValueAsString(requestBody);
        } catch (Exception e) {
            throw new ClientException("Unable to serialize " + path + " request", e);
        }
        HttpResponse httpResponse = sendRequest(
            request()
                .withMethod("PUT")
                .withContentType(APPLICATION_JSON_UTF_8)
                .withPath(calculatePath(path))
                .withBody(body, StandardCharsets.UTF_8),
            false
        );
        return httpResponse != null ? httpResponse.getBodyAsString() : null;
    }

    private ContractReport parseContractReport(String body) {
        if (isBlank(body)) {
            throw new ClientException("empty response from contract endpoint");
        }
        try {
            JsonNode root = CONTRACT_OBJECT_MAPPER.readTree(body);
            if (root.has("error")) {
                throw new ClientException("contract endpoint returned an error: " + root.path("error").asText());
            }
            // The contract-test report keys the total as "totalOperations"; the traffic-validation
            // report keys it as "totalRequests" — accept either.
            int total = root.has("totalOperations") ? root.path("totalOperations").asInt()
                : root.path("totalRequests").asInt();
            int passed = root.path("passed").asInt();
            int failed = root.path("failed").asInt();
            boolean allPassed = root.path("allPassed").asBoolean();
            List<ContractResult> results = new ArrayList<>();
            JsonNode resultsNode = root.path("results");
            if (resultsNode.isArray()) {
                for (JsonNode resultNode : resultsNode) {
                    results.add(new ContractResult(
                        textOrNull(resultNode, "operationId"),
                        textOrNull(resultNode, "method"),
                        textOrNull(resultNode, "path"),
                        textOrNull(resultNode, "matchedOperation"),
                        resultNode.has("statusCodeReceived") ? resultNode.path("statusCodeReceived").asInt() : 0,
                        resultNode.path("passed").asBoolean(),
                        readStringArray(resultNode, "validationErrors", "requestErrors"),
                        readStringArray(resultNode, "responseErrors")
                    ));
                }
            }
            return new ContractReport(total, passed, failed, allPassed, results);
        } catch (ClientException e) {
            throw e;
        } catch (Exception e) {
            throw new ClientException("Unable to parse contract report response: " + body, e);
        }
    }

    private static String textOrNull(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value != null && !value.isNull() ? value.asText() : null;
    }

    private static List<String> readStringArray(JsonNode node, String... fieldNames) {
        List<String> values = new ArrayList<>();
        for (String fieldName : fieldNames) {
            JsonNode array = node.get(fieldName);
            if (array != null && array.isArray()) {
                for (JsonNode element : array) {
                    values.add(element.asText());
                }
            }
        }
        return values;
    }

    // -------------------------------------------------------------------
    // Stateful scenarios
    // -------------------------------------------------------------------

    private static final ObjectMapper SCENARIO_OBJECT_MAPPER = new ObjectMapper();

    /**
     * A scenario and its current state, as returned by the scenario control-plane endpoints.
     *
     * @param scenarioName the name of the scenario state-machine
     * @param currentState the scenario's current state, or {@code null} if it has never been set
     */
    public record Scenario(String scenarioName, String currentState) {
    }

    /**
     * Obtain a typed handle for inspecting and driving a stateful scenario by name. The handle
     * wraps the {@code /mockserver/scenario/{name}} control-plane endpoints.
     *
     * @param name the scenario name
     * @return a handle for the named scenario
     */
    public ScenarioHandle scenario(String name) {
        if (isBlank(name)) {
            throw new IllegalArgumentException("scenario name can not be null or empty");
        }
        return new ScenarioHandle(name);
    }

    /**
     * List every known scenario and its current state.
     *
     * @return the list of scenarios, empty if none are known
     */
    public List<Scenario> scenarios() {
        HttpResponse httpResponse = sendRequest(
            request()
                .withMethod("GET")
                .withPath(calculatePath("scenario")),
            true
        );
        List<Scenario> scenarios = new ArrayList<>();
        String body = httpResponse != null ? httpResponse.getBodyAsString() : null;
        if (isNotBlank(body)) {
            try {
                JsonNode root = SCENARIO_OBJECT_MAPPER.readTree(body);
                JsonNode array = root.path("scenarios");
                if (array.isArray()) {
                    for (JsonNode node : array) {
                        scenarios.add(parseScenario(node));
                    }
                }
            } catch (Exception e) {
                throw new ClientException("Unable to parse scenarios response: " + body, e);
            }
        }
        return scenarios;
    }

    private Scenario parseScenario(JsonNode node) {
        String scenarioName = node.path("scenarioName").isMissingNode() ? null : node.path("scenarioName").asText(null);
        String currentState = node.path("currentState").isNull() || node.path("currentState").isMissingNode()
            ? null : node.path("currentState").asText(null);
        return new Scenario(scenarioName, currentState);
    }

    /**
     * Typed handle that wraps the scenario control-plane endpoints for a single named scenario.
     */
    public class ScenarioHandle {

        private final String name;

        private ScenarioHandle(String name) {
            this.name = name;
        }

        /**
         * Get the current state of this scenario via {@code GET /mockserver/scenario/{name}}.
         *
         * @return the current state, or {@code null} if the scenario has never had a state set
         */
        public String state() {
            HttpResponse httpResponse = sendRequest(
                request()
                    .withMethod("GET")
                    .withPath(calculatePath("scenario/" + name)),
                true
            );
            String body = httpResponse != null ? httpResponse.getBodyAsString() : null;
            if (isNotBlank(body)) {
                try {
                    return parseScenario(SCENARIO_OBJECT_MAPPER.readTree(body)).currentState();
                } catch (Exception e) {
                    throw new ClientException("Unable to parse scenario state response: " + body, e);
                }
            }
            return null;
        }

        /**
         * Set this scenario's state via {@code PUT /mockserver/scenario/{name}}.
         *
         * @param state the new state
         * @return this handle for chaining
         */
        public ScenarioHandle set(String state) {
            return set(state, null, null);
        }

        /**
         * Set this scenario's state and optionally schedule a timed transition to {@code nextState}
         * via {@code PUT /mockserver/scenario/{name}}.
         *
         * @param state              the new state
         * @param transitionAfterMs  delay before transitioning to {@code nextState}, or {@code null} for none
         * @param nextState          the state to transition to after {@code transitionAfterMs}, or {@code null} for none
         * @return this handle for chaining
         */
        public ScenarioHandle set(String state, Long transitionAfterMs, String nextState) {
            ObjectNode requestBody = SCENARIO_OBJECT_MAPPER.createObjectNode();
            requestBody.put("state", state);
            if (transitionAfterMs != null) {
                requestBody.put("transitionAfterMs", transitionAfterMs);
            }
            if (isNotBlank(nextState)) {
                requestBody.put("nextState", nextState);
            }
            sendScenarioPut("scenario/" + name, requestBody);
            return this;
        }

        /**
         * Externally trigger a state transition via {@code PUT /mockserver/scenario/{name}/trigger}.
         *
         * @param newState the state to transition to
         * @return this handle for chaining
         */
        public ScenarioHandle trigger(String newState) {
            ObjectNode requestBody = SCENARIO_OBJECT_MAPPER.createObjectNode();
            requestBody.put("newState", newState);
            sendScenarioPut("scenario/" + name + "/trigger", requestBody);
            return this;
        }

        private void sendScenarioPut(String path, ObjectNode requestBody) {
            String body;
            try {
                body = SCENARIO_OBJECT_MAPPER.writeValueAsString(requestBody);
            } catch (Exception e) {
                throw new ClientException("Unable to serialize scenario request", e);
            }
            sendRequest(
                request()
                    .withMethod("PUT")
                    .withContentType(APPLICATION_JSON_UTF_8)
                    .withPath(calculatePath(path))
                    .withBody(body, StandardCharsets.UTF_8),
                true
            );
        }
    }
}
