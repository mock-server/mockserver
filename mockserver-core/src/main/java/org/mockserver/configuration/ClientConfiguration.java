package org.mockserver.configuration;

import static org.mockserver.configuration.Configuration.configuration;

@SuppressWarnings("UnusedReturnValue")
public class ClientConfiguration {

    private Configuration serverConfiguration;

    public static ClientConfiguration clientConfiguration() {
        return new ClientConfiguration();
    }

    public static ClientConfiguration clientConfiguration(Configuration configuration) {
        return new ClientConfiguration(configuration);
    }

    public ClientConfiguration() {
    }

    public ClientConfiguration(Configuration serverConfiguration) {
        this.serverConfiguration = serverConfiguration;
        if (serverConfiguration == null) {
            serverConfiguration = configuration();
        }
        webSocketClientEventLoopThreadCount(serverConfiguration.webSocketClientEventLoopThreadCount());
        clientNioEventLoopThreadCount(serverConfiguration.clientNioEventLoopThreadCount());
        maxSocketTimeoutInMillis(serverConfiguration.maxSocketTimeoutInMillis());
        maxFutureTimeoutInMillis(serverConfiguration.maxFutureTimeoutInMillis());
        controlPlaneTLSMutualAuthenticationRequired(serverConfiguration.controlPlaneTLSMutualAuthenticationRequired());
        controlPlaneTLSMutualAuthenticationCAChain(serverConfiguration.controlPlaneTLSMutualAuthenticationCAChain());
        controlPlanePrivateKeyPath(serverConfiguration.controlPlanePrivateKeyPath());
        controlPlaneX509CertificatePath(serverConfiguration.controlPlaneX509CertificatePath());
        controlPlaneJWTAuthenticationRequired(serverConfiguration.controlPlaneJWTAuthenticationRequired());
        controlPlaneJWTAuthenticationJWKSource(serverConfiguration.controlPlaneJWTAuthenticationJWKSource());
    }

    public Configuration toServerConfiguration() {
        if (serverConfiguration == null) {
            serverConfiguration = configuration();
        }
        serverConfiguration.webSocketClientEventLoopThreadCount(this.webSocketClientEventLoopThreadCount);
        serverConfiguration.clientNioEventLoopThreadCount(this.clientNioEventLoopThreadCount);
        serverConfiguration.maxSocketTimeoutInMillis(this.maxSocketTimeoutInMillis);
        serverConfiguration.maxFutureTimeoutInMillis(this.maxFutureTimeoutInMillis);
        serverConfiguration.controlPlaneTLSMutualAuthenticationRequired(this.controlPlaneTLSMutualAuthenticationRequired);
        serverConfiguration.controlPlaneTLSMutualAuthenticationCAChain(this.controlPlaneTLSMutualAuthenticationCAChain);
        serverConfiguration.controlPlanePrivateKeyPath(this.controlPlanePrivateKeyPath);
        serverConfiguration.controlPlaneX509CertificatePath(this.controlPlaneX509CertificatePath);
        serverConfiguration.controlPlaneJWTAuthenticationRequired(this.controlPlaneJWTAuthenticationRequired);
        serverConfiguration.controlPlaneJWTAuthenticationJWKSource(this.controlPlaneJWTAuthenticationJWKSource);
        return serverConfiguration;
    }

    // memory usage
    private Integer maxWebSocketExpectations;

    // scalability
    private Integer webSocketClientEventLoopThreadCount;
    private Integer clientNioEventLoopThreadCount;

    // socket
    private Long maxSocketTimeoutInMillis;
    private Long maxFutureTimeoutInMillis;

    // control plane authentication
    private Boolean controlPlaneTLSMutualAuthenticationRequired;
    private String controlPlaneTLSMutualAuthenticationCAChain;
    private String controlPlanePrivateKeyPath;
    private String controlPlaneX509CertificatePath;
    private Boolean controlPlaneJWTAuthenticationRequired;
    private String controlPlaneJWTAuthenticationJWKSource;

    public Integer maxWebSocketExpectations() {
        if (maxWebSocketExpectations == null) {
            maxWebSocketExpectations = ConfigurationProperties.maxWebSocketExpectations();
        }
        return maxWebSocketExpectations;
    }

    /**
     * <p>
     * Maximum number of remote (not the same JVM) method callbacks (i.e. web sockets) registered for expectations.  The web socket client registry entries are stored in a circular queue so once this limit is reach the oldest are overwritten.
     * </p>
     * <p>
     * The default is 1500
     * </p>
     *
     * @param maxWebSocketExpectations maximum number of method callbacks (i.e. web sockets) registered for expectations
     */
    public ClientConfiguration maxWebSocketExpectations(Integer maxWebSocketExpectations) {
        this.maxWebSocketExpectations = maxWebSocketExpectations;
        return this;
    }

    public Integer webSocketClientEventLoopThreadCount() {
        if (webSocketClientEventLoopThreadCount == null) {
            webSocketClientEventLoopThreadCount = ConfigurationProperties.webSocketClientEventLoopThreadCount();
        }
        return webSocketClientEventLoopThreadCount;
    }

    /**
     * <p>Client Netty worker thread pool size for handling requests and response.  These threads handle deserializing and serialising HTTP requests and responses and some other fast logic.</p>
     *
     * <p>Default is 5 threads</p>
     *
     * @param webSocketClientEventLoopThreadCount Client Netty worker thread pool size
     */
    public ClientConfiguration webSocketClientEventLoopThreadCount(Integer webSocketClientEventLoopThreadCount) {
        this.webSocketClientEventLoopThreadCount = webSocketClientEventLoopThreadCount;
        return this;
    }

    public Integer clientNioEventLoopThreadCount() {
        if (clientNioEventLoopThreadCount == null) {
            clientNioEventLoopThreadCount = ConfigurationProperties.clientNioEventLoopThreadCount();
        }
        return clientNioEventLoopThreadCount;
    }

    /**
     * <p>Client Netty worker thread pool size for handling requests and response.  These threads handle deserializing and serialising HTTP requests and responses and some other fast logic.</p>
     *
     * <p>Default is 5 threads</p>
     *
     * @param clientNioEventLoopThreadCount Client Netty worker thread pool size
     */
    public ClientConfiguration clientNioEventLoopThreadCount(Integer clientNioEventLoopThreadCount) {
        this.clientNioEventLoopThreadCount = clientNioEventLoopThreadCount;
        return this;
    }

    public Long maxSocketTimeoutInMillis() {
        if (maxSocketTimeoutInMillis == null) {
            maxSocketTimeoutInMillis = ConfigurationProperties.maxSocketTimeout();
        }
        return maxSocketTimeoutInMillis;
    }

    /**
     * Maximum time in milliseconds allowed for a response from a socket
     * <p>
     * Default is 20,000 ms
     *
     * @param maxSocketTimeoutInMillis maximum time in milliseconds allowed
     */
    public ClientConfiguration maxSocketTimeoutInMillis(Long maxSocketTimeoutInMillis) {
        this.maxSocketTimeoutInMillis = maxSocketTimeoutInMillis;
        return this;
    }

    public Long maxFutureTimeoutInMillis() {
        if (maxFutureTimeoutInMillis == null) {
            maxFutureTimeoutInMillis = ConfigurationProperties.maxFutureTimeout();
        }
        return maxFutureTimeoutInMillis;
    }

    /**
     * Maximum time allowed in milliseconds for any future to wait, for example when waiting for a response over a web socket callback.
     * <p>
     * Default is 60,000 ms
     *
     * @param maxFutureTimeoutInMillis maximum time allowed in milliseconds
     */
    public ClientConfiguration maxFutureTimeoutInMillis(Long maxFutureTimeoutInMillis) {
        this.maxFutureTimeoutInMillis = maxFutureTimeoutInMillis;
        return this;
    }

    public Boolean controlPlaneTLSMutualAuthenticationRequired() {
        if (controlPlaneTLSMutualAuthenticationRequired == null) {
            controlPlaneTLSMutualAuthenticationRequired = ConfigurationProperties.controlPlaneTLSMutualAuthenticationRequired();
        }
        return controlPlaneTLSMutualAuthenticationRequired;
    }

    public ClientConfiguration controlPlaneTLSMutualAuthenticationRequired(Boolean controlPlaneTLSMutualAuthenticationRequired) {
        this.controlPlaneTLSMutualAuthenticationRequired = controlPlaneTLSMutualAuthenticationRequired;
        return this;
    }

    public String controlPlaneTLSMutualAuthenticationCAChain() {
        if (controlPlaneTLSMutualAuthenticationCAChain == null) {
            controlPlaneTLSMutualAuthenticationCAChain = ConfigurationProperties.controlPlaneTLSMutualAuthenticationCAChain();
        }
        return controlPlaneTLSMutualAuthenticationCAChain;
    }

    public ClientConfiguration controlPlaneTLSMutualAuthenticationCAChain(String controlPlaneTLSMutualAuthenticationCAChain) {
        this.controlPlaneTLSMutualAuthenticationCAChain = controlPlaneTLSMutualAuthenticationCAChain;
        return this;
    }

    public String controlPlanePrivateKeyPath() {
        if (controlPlanePrivateKeyPath == null) {
            controlPlanePrivateKeyPath = ConfigurationProperties.controlPlanePrivateKeyPath();
        }
        return controlPlanePrivateKeyPath;
    }

    public ClientConfiguration controlPlanePrivateKeyPath(String controlPlanePrivateKeyPath) {
        this.controlPlanePrivateKeyPath = controlPlanePrivateKeyPath;
        return this;
    }

    public String controlPlaneX509CertificatePath() {
        if (controlPlaneX509CertificatePath == null) {
            controlPlaneX509CertificatePath = ConfigurationProperties.controlPlaneX509CertificatePath();
        }
        return controlPlaneX509CertificatePath;
    }

    public ClientConfiguration controlPlaneX509CertificatePath(String controlPlaneX509CertificatePath) {
        this.controlPlaneX509CertificatePath = controlPlaneX509CertificatePath;
        return this;
    }

    public Boolean controlPlaneJWTAuthenticationRequired() {
        if (controlPlaneJWTAuthenticationRequired == null) {
            controlPlaneJWTAuthenticationRequired = ConfigurationProperties.controlPlaneJWTAuthenticationRequired();
        }
        return controlPlaneJWTAuthenticationRequired;
    }

    public ClientConfiguration controlPlaneJWTAuthenticationRequired(Boolean controlPlaneJWTAuthenticationRequired) {
        this.controlPlaneJWTAuthenticationRequired = controlPlaneJWTAuthenticationRequired;
        return this;
    }

    public String controlPlaneJWTAuthenticationJWKSource() {
        if (controlPlaneJWTAuthenticationJWKSource == null) {
            controlPlaneJWTAuthenticationJWKSource = ConfigurationProperties.controlPlaneJWTAuthenticationJWKSource();
        }
        return controlPlaneJWTAuthenticationJWKSource;
    }

    public ClientConfiguration controlPlaneJWTAuthenticationJWKSource(String controlPlaneJWTAuthenticationJWKSource) {
        this.controlPlaneJWTAuthenticationJWKSource = controlPlaneJWTAuthenticationJWKSource;
        return this;
    }
}
