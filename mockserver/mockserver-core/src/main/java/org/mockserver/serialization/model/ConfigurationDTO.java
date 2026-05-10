package org.mockserver.serialization.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.mockserver.configuration.Configuration;
import org.mockserver.socket.tls.ForwardProxyTLSX509CertificatesTrustManager;
import org.slf4j.event.Level;

import java.net.InetSocketAddress;
import java.util.Map;
import java.util.Set;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class ConfigurationDTO implements DTO<Configuration> {

    private String logLevel;
    private Boolean disableSystemOut;
    private Boolean disableLogging;
    private Boolean detailedMatchFailures;
    private Boolean launchUIForLogLevelDebug;
    private Boolean metricsEnabled;
    private Boolean mcpEnabled;
    private Map<String, String> logLevelOverrides;

    private Integer maxExpectations;
    private Integer maxLogEntries;
    private Integer maxWebSocketExpectations;
    private Boolean outputMemoryUsageCsv;
    private String memoryUsageCsvDirectory;

    private Integer nioEventLoopThreadCount;
    private Integer actionHandlerThreadCount;
    private Integer clientNioEventLoopThreadCount;
    private Integer webSocketClientEventLoopThreadCount;
    private Long maxFutureTimeoutInMillis;
    private Boolean matchersFailFast;

    private Long maxSocketTimeoutInMillis;
    private Long socketConnectionTimeoutInMillis;
    private Boolean alwaysCloseSocketConnections;
    private String localBoundIP;

    private Integer maxInitialLineLength;
    private Integer maxHeaderSize;
    private Integer maxChunkSize;
    private Boolean useSemicolonAsQueryParameterSeparator;
    private Boolean assumeAllRequestsAreHttp;

    private Boolean forwardBinaryRequestsWithoutWaitingForResponse;

    private Boolean enableCORSForAPI;
    private Boolean enableCORSForAllResponses;
    private String corsAllowOrigin;
    private String corsAllowMethods;
    private String corsAllowHeaders;
    private Boolean corsAllowCredentials;
    private Integer corsMaxAgeInSeconds;

    private String javascriptDisallowedClasses;
    private String javascriptDisallowedText;
    private Boolean velocityDisallowClassLoading;
    private String velocityDisallowedText;
    private String mustacheDisallowedText;

    private String initializationClass;
    private String initializationJsonPath;
    private String initializationOpenAPIPath;
    private String openAPIContextPathPrefix;
    private Boolean openAPIResponseValidation;
    private Boolean watchInitializationJson;

    private Boolean persistExpectations;
    private String persistedExpectationsPath;

    private Integer maximumNumberOfRequestToReturnInVerificationFailure;

    private Boolean attemptToProxyIfNoMatchingExpectation;
    private String forwardHttpProxy;
    private String forwardHttpsProxy;
    private String forwardSocksProxy;
    private String forwardProxyAuthenticationUsername;
    private String forwardProxyAuthenticationPassword;
    private String proxyAuthenticationRealm;
    private String proxyAuthenticationUsername;
    private String proxyAuthenticationPassword;
    private String noProxyHosts;

    private String livenessHttpGetPath;

    private Boolean controlPlaneTLSMutualAuthenticationRequired;
    private String controlPlaneTLSMutualAuthenticationCAChain;
    private String controlPlanePrivateKeyPath;
    private String controlPlaneX509CertificatePath;
    private Boolean controlPlaneJWTAuthenticationRequired;
    private String controlPlaneJWTAuthenticationJWKSource;
    private String controlPlaneJWTAuthenticationExpectedAudience;
    private Map<String, String> controlPlaneJWTAuthenticationMatchingClaims;
    private Set<String> controlPlaneJWTAuthenticationRequiredClaims;

    private Boolean proactivelyInitialiseTLS;
    private String tlsProtocols;
    private Boolean dynamicallyCreateCertificateAuthorityCertificate;
    private String directoryToSaveDynamicSSLCertificate;
    private Boolean preventCertificateDynamicUpdate;
    private String sslCertificateDomainName;
    private Set<String> sslSubjectAlternativeNameDomains;
    private Set<String> sslSubjectAlternativeNameIps;
    private String certificateAuthorityPrivateKey;
    private String certificateAuthorityCertificate;
    private String privateKeyPath;
    private String x509CertificatePath;
    private Boolean tlsMutualAuthenticationRequired;
    private String tlsMutualAuthenticationCertificateChain;

    private String forwardProxyTLSX509CertificatesTrustManagerType;
    private String forwardProxyTLSCustomTrustX509Certificates;
    private String forwardProxyPrivateKey;
    private String forwardProxyCertificateChain;

    public ConfigurationDTO() {
    }

    public ConfigurationDTO(Configuration configuration) {
        if (configuration != null) {
            Level level = configuration.logLevel();
            if (level != null) {
                this.logLevel = level.name();
            }
            this.disableSystemOut = configuration.disableSystemOut();
            this.disableLogging = configuration.disableLogging();
            this.detailedMatchFailures = configuration.detailedMatchFailures();
            this.launchUIForLogLevelDebug = configuration.launchUIForLogLevelDebug();
            this.metricsEnabled = configuration.metricsEnabled();
            this.mcpEnabled = configuration.mcpEnabled();
            Map<String, String> overrides = configuration.logLevelOverrides();
            this.logLevelOverrides = overrides != null && !overrides.isEmpty() ? overrides : null;

            this.maxExpectations = configuration.maxExpectations();
            this.maxLogEntries = configuration.maxLogEntries();
            this.maxWebSocketExpectations = configuration.maxWebSocketExpectations();
            this.outputMemoryUsageCsv = configuration.outputMemoryUsageCsv();
            this.memoryUsageCsvDirectory = configuration.memoryUsageCsvDirectory();

            this.nioEventLoopThreadCount = configuration.nioEventLoopThreadCount();
            this.actionHandlerThreadCount = configuration.actionHandlerThreadCount();
            this.clientNioEventLoopThreadCount = configuration.clientNioEventLoopThreadCount();
            this.webSocketClientEventLoopThreadCount = configuration.webSocketClientEventLoopThreadCount();
            this.maxFutureTimeoutInMillis = configuration.maxFutureTimeoutInMillis();
            this.matchersFailFast = configuration.matchersFailFast();

            this.maxSocketTimeoutInMillis = configuration.maxSocketTimeoutInMillis();
            this.socketConnectionTimeoutInMillis = configuration.socketConnectionTimeoutInMillis();
            this.alwaysCloseSocketConnections = configuration.alwaysCloseSocketConnections();
            this.localBoundIP = configuration.localBoundIP();

            this.maxInitialLineLength = configuration.maxInitialLineLength();
            this.maxHeaderSize = configuration.maxHeaderSize();
            this.maxChunkSize = configuration.maxChunkSize();
            this.useSemicolonAsQueryParameterSeparator = configuration.useSemicolonAsQueryParameterSeparator();
            this.assumeAllRequestsAreHttp = configuration.assumeAllRequestsAreHttp();

            this.forwardBinaryRequestsWithoutWaitingForResponse = configuration.forwardBinaryRequestsWithoutWaitingForResponse();

            this.enableCORSForAPI = configuration.enableCORSForAPI();
            this.enableCORSForAllResponses = configuration.enableCORSForAllResponses();
            this.corsAllowOrigin = configuration.corsAllowOrigin();
            this.corsAllowMethods = configuration.corsAllowMethods();
            this.corsAllowHeaders = configuration.corsAllowHeaders();
            this.corsAllowCredentials = configuration.corsAllowCredentials();
            this.corsMaxAgeInSeconds = configuration.corsMaxAgeInSeconds();

            this.javascriptDisallowedClasses = configuration.javascriptDisallowedClasses();
            this.javascriptDisallowedText = configuration.javascriptDisallowedText();
            this.velocityDisallowClassLoading = configuration.velocityDisallowClassLoading();
            this.velocityDisallowedText = configuration.velocityDisallowedText();
            this.mustacheDisallowedText = configuration.mustacheDisallowedText();

            this.initializationClass = configuration.initializationClass();
            this.initializationJsonPath = configuration.initializationJsonPath();
            this.initializationOpenAPIPath = configuration.initializationOpenAPIPath();
            this.openAPIContextPathPrefix = configuration.openAPIContextPathPrefix();
            this.openAPIResponseValidation = configuration.openAPIResponseValidation();
            this.watchInitializationJson = configuration.watchInitializationJson();

            this.persistExpectations = configuration.persistExpectations();
            this.persistedExpectationsPath = configuration.persistedExpectationsPath();

            this.maximumNumberOfRequestToReturnInVerificationFailure = configuration.maximumNumberOfRequestToReturnInVerificationFailure();

            this.attemptToProxyIfNoMatchingExpectation = configuration.attemptToProxyIfNoMatchingExpectation();
            InetSocketAddress httpProxy = configuration.forwardHttpProxy();
            if (httpProxy != null) {
                this.forwardHttpProxy = httpProxy.getHostString() + ":" + httpProxy.getPort();
            }
            InetSocketAddress httpsProxy = configuration.forwardHttpsProxy();
            if (httpsProxy != null) {
                this.forwardHttpsProxy = httpsProxy.getHostString() + ":" + httpsProxy.getPort();
            }
            InetSocketAddress socksProxy = configuration.forwardSocksProxy();
            if (socksProxy != null) {
                this.forwardSocksProxy = socksProxy.getHostString() + ":" + socksProxy.getPort();
            }
            this.forwardProxyAuthenticationUsername = configuration.forwardProxyAuthenticationUsername();
            this.forwardProxyAuthenticationPassword = configuration.forwardProxyAuthenticationPassword();
            this.proxyAuthenticationRealm = configuration.proxyAuthenticationRealm();
            this.proxyAuthenticationUsername = configuration.proxyAuthenticationUsername();
            this.proxyAuthenticationPassword = configuration.proxyAuthenticationPassword();
            this.noProxyHosts = configuration.noProxyHosts();

            this.livenessHttpGetPath = configuration.livenessHttpGetPath();

            this.controlPlaneTLSMutualAuthenticationRequired = configuration.controlPlaneTLSMutualAuthenticationRequired();
            this.controlPlaneTLSMutualAuthenticationCAChain = configuration.controlPlaneTLSMutualAuthenticationCAChain();
            this.controlPlanePrivateKeyPath = configuration.controlPlanePrivateKeyPath();
            this.controlPlaneX509CertificatePath = configuration.controlPlaneX509CertificatePath();
            this.controlPlaneJWTAuthenticationRequired = configuration.controlPlaneJWTAuthenticationRequired();
            this.controlPlaneJWTAuthenticationJWKSource = configuration.controlPlaneJWTAuthenticationJWKSource();
            this.controlPlaneJWTAuthenticationExpectedAudience = configuration.controlPlaneJWTAuthenticationExpectedAudience();
            this.controlPlaneJWTAuthenticationMatchingClaims = configuration.controlPlaneJWTAuthenticationMatchingClaims();
            this.controlPlaneJWTAuthenticationRequiredClaims = configuration.controlPlaneJWTAuthenticationRequiredClaims();

            this.proactivelyInitialiseTLS = configuration.proactivelyInitialiseTLS();
            this.tlsProtocols = configuration.tlsProtocols();
            this.dynamicallyCreateCertificateAuthorityCertificate = configuration.dynamicallyCreateCertificateAuthorityCertificate();
            this.directoryToSaveDynamicSSLCertificate = configuration.directoryToSaveDynamicSSLCertificate();
            this.preventCertificateDynamicUpdate = configuration.preventCertificateDynamicUpdate();
            this.sslCertificateDomainName = configuration.sslCertificateDomainName();
            this.sslSubjectAlternativeNameDomains = configuration.sslSubjectAlternativeNameDomains();
            this.sslSubjectAlternativeNameIps = configuration.sslSubjectAlternativeNameIps();
            this.certificateAuthorityPrivateKey = configuration.certificateAuthorityPrivateKey();
            this.certificateAuthorityCertificate = configuration.certificateAuthorityCertificate();
            this.privateKeyPath = configuration.privateKeyPath();
            this.x509CertificatePath = configuration.x509CertificatePath();
            this.tlsMutualAuthenticationRequired = configuration.tlsMutualAuthenticationRequired();
            this.tlsMutualAuthenticationCertificateChain = configuration.tlsMutualAuthenticationCertificateChain();

            ForwardProxyTLSX509CertificatesTrustManager trustManagerType = configuration.forwardProxyTLSX509CertificatesTrustManagerType();
            if (trustManagerType != null) {
                this.forwardProxyTLSX509CertificatesTrustManagerType = trustManagerType.name();
            }
            this.forwardProxyTLSCustomTrustX509Certificates = configuration.forwardProxyTLSCustomTrustX509Certificates();
            this.forwardProxyPrivateKey = configuration.forwardProxyPrivateKey();
            this.forwardProxyCertificateChain = configuration.forwardProxyCertificateChain();
        }
    }

    private void validateFields() {
        if (logLevel != null) {
            try {
                Level.valueOf(logLevel);
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("Invalid logLevel: \"" + logLevel + "\", valid values are TRACE, DEBUG, INFO, WARN, ERROR");
            }
        }
        if (maxExpectations != null && (maxExpectations < 0 || maxExpectations > 100000)) {
            throw new IllegalArgumentException("maxExpectations must be between 0 and 100000, got: " + maxExpectations);
        }
        if (maxLogEntries != null && (maxLogEntries < 0 || maxLogEntries > 1000000)) {
            throw new IllegalArgumentException("maxLogEntries must be between 0 and 1000000, got: " + maxLogEntries);
        }
        if (maxWebSocketExpectations != null && (maxWebSocketExpectations < 0 || maxWebSocketExpectations > 100000)) {
            throw new IllegalArgumentException("maxWebSocketExpectations must be between 0 and 100000, got: " + maxWebSocketExpectations);
        }
        if (forwardProxyTLSX509CertificatesTrustManagerType != null) {
            try {
                ForwardProxyTLSX509CertificatesTrustManager.valueOf(forwardProxyTLSX509CertificatesTrustManagerType);
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("Invalid forwardProxyTLSX509CertificatesTrustManagerType: \"" + forwardProxyTLSX509CertificatesTrustManagerType + "\"");
            }
        }
        if (forwardHttpProxy != null) {
            parseInetSocketAddress(forwardHttpProxy);
        }
        if (forwardHttpsProxy != null) {
            parseInetSocketAddress(forwardHttpsProxy);
        }
        if (forwardSocksProxy != null) {
            parseInetSocketAddress(forwardSocksProxy);
        }
    }

    @Override
    public Configuration buildObject() {
        validateFields();
        Configuration configuration = Configuration.configuration();
        if (logLevel != null) {
            configuration.logLevel(Level.valueOf(logLevel));
        }
        configuration.disableSystemOut(disableSystemOut);
        configuration.disableLogging(disableLogging);
        configuration.detailedMatchFailures(detailedMatchFailures);
        configuration.launchUIForLogLevelDebug(launchUIForLogLevelDebug);
        configuration.metricsEnabled(metricsEnabled);
        configuration.mcpEnabled(mcpEnabled);
        configuration.logLevelOverrides(logLevelOverrides);

        configuration.maxExpectations(maxExpectations);
        configuration.maxLogEntries(maxLogEntries);
        configuration.maxWebSocketExpectations(maxWebSocketExpectations);
        configuration.outputMemoryUsageCsv(outputMemoryUsageCsv);
        configuration.memoryUsageCsvDirectory(memoryUsageCsvDirectory);

        configuration.nioEventLoopThreadCount(nioEventLoopThreadCount);
        configuration.actionHandlerThreadCount(actionHandlerThreadCount);
        configuration.clientNioEventLoopThreadCount(clientNioEventLoopThreadCount);
        configuration.webSocketClientEventLoopThreadCount(webSocketClientEventLoopThreadCount);
        configuration.maxFutureTimeoutInMillis(maxFutureTimeoutInMillis);
        configuration.matchersFailFast(matchersFailFast);

        configuration.maxSocketTimeoutInMillis(maxSocketTimeoutInMillis);
        configuration.socketConnectionTimeoutInMillis(socketConnectionTimeoutInMillis);
        configuration.alwaysCloseSocketConnections(alwaysCloseSocketConnections);
        configuration.localBoundIP(localBoundIP);

        configuration.maxInitialLineLength(maxInitialLineLength);
        configuration.maxHeaderSize(maxHeaderSize);
        configuration.maxChunkSize(maxChunkSize);
        configuration.useSemicolonAsQueryParameterSeparator(useSemicolonAsQueryParameterSeparator);
        configuration.assumeAllRequestsAreHttp(assumeAllRequestsAreHttp);

        configuration.forwardBinaryRequestsWithoutWaitingForResponse(forwardBinaryRequestsWithoutWaitingForResponse);

        configuration.enableCORSForAPI(enableCORSForAPI);
        configuration.enableCORSForAllResponses(enableCORSForAllResponses);
        configuration.corsAllowOrigin(corsAllowOrigin);
        configuration.corsAllowMethods(corsAllowMethods);
        configuration.corsAllowHeaders(corsAllowHeaders);
        configuration.corsAllowCredentials(corsAllowCredentials);
        configuration.corsMaxAgeInSeconds(corsMaxAgeInSeconds);

        configuration.javascriptDisallowedClasses(javascriptDisallowedClasses);
        configuration.javascriptDisallowedText(javascriptDisallowedText);
        configuration.velocityDisallowClassLoading(velocityDisallowClassLoading);
        configuration.velocityDisallowedText(velocityDisallowedText);
        configuration.mustacheDisallowedText(mustacheDisallowedText);

        configuration.initializationClass(initializationClass);
        configuration.initializationJsonPath(initializationJsonPath);
        configuration.initializationOpenAPIPath(initializationOpenAPIPath);
        configuration.openAPIContextPathPrefix(openAPIContextPathPrefix);
        configuration.openAPIResponseValidation(openAPIResponseValidation);
        configuration.watchInitializationJson(watchInitializationJson);

        configuration.persistExpectations(persistExpectations);
        configuration.persistedExpectationsPath(persistedExpectationsPath);

        configuration.maximumNumberOfRequestToReturnInVerificationFailure(maximumNumberOfRequestToReturnInVerificationFailure);

        configuration.attemptToProxyIfNoMatchingExpectation(attemptToProxyIfNoMatchingExpectation);
        if (forwardHttpProxy != null) {
            configuration.forwardHttpProxy(parseInetSocketAddress(forwardHttpProxy));
        }
        if (forwardHttpsProxy != null) {
            configuration.forwardHttpsProxy(parseInetSocketAddress(forwardHttpsProxy));
        }
        if (forwardSocksProxy != null) {
            configuration.forwardSocksProxy(parseInetSocketAddress(forwardSocksProxy));
        }
        configuration.forwardProxyAuthenticationUsername(forwardProxyAuthenticationUsername);
        configuration.forwardProxyAuthenticationPassword(forwardProxyAuthenticationPassword);
        configuration.proxyAuthenticationRealm(proxyAuthenticationRealm);
        configuration.proxyAuthenticationUsername(proxyAuthenticationUsername);
        configuration.proxyAuthenticationPassword(proxyAuthenticationPassword);
        configuration.noProxyHosts(noProxyHosts);

        configuration.livenessHttpGetPath(livenessHttpGetPath);

        configuration.controlPlaneTLSMutualAuthenticationRequired(controlPlaneTLSMutualAuthenticationRequired);
        configuration.controlPlaneTLSMutualAuthenticationCAChain(controlPlaneTLSMutualAuthenticationCAChain);
        configuration.controlPlanePrivateKeyPath(controlPlanePrivateKeyPath);
        configuration.controlPlaneX509CertificatePath(controlPlaneX509CertificatePath);
        configuration.controlPlaneJWTAuthenticationRequired(controlPlaneJWTAuthenticationRequired);
        configuration.controlPlaneJWTAuthenticationJWKSource(controlPlaneJWTAuthenticationJWKSource);
        configuration.controlPlaneJWTAuthenticationExpectedAudience(controlPlaneJWTAuthenticationExpectedAudience);
        configuration.controlPlaneJWTAuthenticationMatchingClaims(controlPlaneJWTAuthenticationMatchingClaims);
        configuration.controlPlaneJWTAuthenticationRequiredClaims(controlPlaneJWTAuthenticationRequiredClaims);

        configuration.proactivelyInitialiseTLS(proactivelyInitialiseTLS);
        configuration.tlsProtocols(tlsProtocols);
        configuration.dynamicallyCreateCertificateAuthorityCertificate(dynamicallyCreateCertificateAuthorityCertificate);
        configuration.directoryToSaveDynamicSSLCertificate(directoryToSaveDynamicSSLCertificate);
        configuration.preventCertificateDynamicUpdate(preventCertificateDynamicUpdate);
        configuration.sslCertificateDomainName(sslCertificateDomainName);
        if (sslSubjectAlternativeNameDomains != null) {
            configuration.sslSubjectAlternativeNameDomains(sslSubjectAlternativeNameDomains);
        }
        if (sslSubjectAlternativeNameIps != null) {
            configuration.sslSubjectAlternativeNameIps(sslSubjectAlternativeNameIps);
        }
        configuration.certificateAuthorityPrivateKey(certificateAuthorityPrivateKey);
        configuration.certificateAuthorityCertificate(certificateAuthorityCertificate);
        configuration.privateKeyPath(privateKeyPath);
        configuration.x509CertificatePath(x509CertificatePath);
        configuration.tlsMutualAuthenticationRequired(tlsMutualAuthenticationRequired);
        configuration.tlsMutualAuthenticationCertificateChain(tlsMutualAuthenticationCertificateChain);

        if (forwardProxyTLSX509CertificatesTrustManagerType != null) {
            configuration.forwardProxyTLSX509CertificatesTrustManagerType(ForwardProxyTLSX509CertificatesTrustManager.valueOf(forwardProxyTLSX509CertificatesTrustManagerType));
        }
        configuration.forwardProxyTLSCustomTrustX509Certificates(forwardProxyTLSCustomTrustX509Certificates);
        configuration.forwardProxyPrivateKey(forwardProxyPrivateKey);
        configuration.forwardProxyCertificateChain(forwardProxyCertificateChain);

        return configuration;
    }

    public void applyTo(Configuration target) {
        validateFields();
        if (logLevel != null) {
            target.logLevel(Level.valueOf(logLevel));
        }
        if (disableSystemOut != null) {
            target.disableSystemOut(disableSystemOut);
        }
        if (disableLogging != null) {
            target.disableLogging(disableLogging);
        }
        if (detailedMatchFailures != null) {
            target.detailedMatchFailures(detailedMatchFailures);
        }
        if (launchUIForLogLevelDebug != null) {
            target.launchUIForLogLevelDebug(launchUIForLogLevelDebug);
        }
        if (metricsEnabled != null) {
            target.metricsEnabled(metricsEnabled);
        }
        if (mcpEnabled != null) {
            target.mcpEnabled(mcpEnabled);
        }
        if (logLevelOverrides != null) {
            target.logLevelOverrides(logLevelOverrides);
        }
        if (maxExpectations != null) {
            target.maxExpectations(maxExpectations);
        }
        if (maxLogEntries != null) {
            target.maxLogEntries(maxLogEntries);
        }
        if (maxWebSocketExpectations != null) {
            target.maxWebSocketExpectations(maxWebSocketExpectations);
        }
        if (outputMemoryUsageCsv != null) {
            target.outputMemoryUsageCsv(outputMemoryUsageCsv);
        }
        if (memoryUsageCsvDirectory != null) {
            target.memoryUsageCsvDirectory(memoryUsageCsvDirectory);
        }
        if (nioEventLoopThreadCount != null) {
            target.nioEventLoopThreadCount(nioEventLoopThreadCount);
        }
        if (actionHandlerThreadCount != null) {
            target.actionHandlerThreadCount(actionHandlerThreadCount);
        }
        if (clientNioEventLoopThreadCount != null) {
            target.clientNioEventLoopThreadCount(clientNioEventLoopThreadCount);
        }
        if (webSocketClientEventLoopThreadCount != null) {
            target.webSocketClientEventLoopThreadCount(webSocketClientEventLoopThreadCount);
        }
        if (maxFutureTimeoutInMillis != null) {
            target.maxFutureTimeoutInMillis(maxFutureTimeoutInMillis);
        }
        if (matchersFailFast != null) {
            target.matchersFailFast(matchersFailFast);
        }
        if (maxSocketTimeoutInMillis != null) {
            target.maxSocketTimeoutInMillis(maxSocketTimeoutInMillis);
        }
        if (socketConnectionTimeoutInMillis != null) {
            target.socketConnectionTimeoutInMillis(socketConnectionTimeoutInMillis);
        }
        if (alwaysCloseSocketConnections != null) {
            target.alwaysCloseSocketConnections(alwaysCloseSocketConnections);
        }
        if (localBoundIP != null) {
            target.localBoundIP(localBoundIP);
        }
        if (maxInitialLineLength != null) {
            target.maxInitialLineLength(maxInitialLineLength);
        }
        if (maxHeaderSize != null) {
            target.maxHeaderSize(maxHeaderSize);
        }
        if (maxChunkSize != null) {
            target.maxChunkSize(maxChunkSize);
        }
        if (useSemicolonAsQueryParameterSeparator != null) {
            target.useSemicolonAsQueryParameterSeparator(useSemicolonAsQueryParameterSeparator);
        }
        if (assumeAllRequestsAreHttp != null) {
            target.assumeAllRequestsAreHttp(assumeAllRequestsAreHttp);
        }
        if (forwardBinaryRequestsWithoutWaitingForResponse != null) {
            target.forwardBinaryRequestsWithoutWaitingForResponse(forwardBinaryRequestsWithoutWaitingForResponse);
        }
        if (enableCORSForAPI != null) {
            target.enableCORSForAPI(enableCORSForAPI);
        }
        if (enableCORSForAllResponses != null) {
            target.enableCORSForAllResponses(enableCORSForAllResponses);
        }
        if (corsAllowOrigin != null) {
            target.corsAllowOrigin(corsAllowOrigin);
        }
        if (corsAllowMethods != null) {
            target.corsAllowMethods(corsAllowMethods);
        }
        if (corsAllowHeaders != null) {
            target.corsAllowHeaders(corsAllowHeaders);
        }
        if (corsAllowCredentials != null) {
            target.corsAllowCredentials(corsAllowCredentials);
        }
        if (corsMaxAgeInSeconds != null) {
            target.corsMaxAgeInSeconds(corsMaxAgeInSeconds);
        }
        if (javascriptDisallowedClasses != null) {
            target.javascriptDisallowedClasses(javascriptDisallowedClasses);
        }
        if (javascriptDisallowedText != null) {
            target.javascriptDisallowedText(javascriptDisallowedText);
        }
        if (velocityDisallowClassLoading != null) {
            target.velocityDisallowClassLoading(velocityDisallowClassLoading);
        }
        if (velocityDisallowedText != null) {
            target.velocityDisallowedText(velocityDisallowedText);
        }
        if (mustacheDisallowedText != null) {
            target.mustacheDisallowedText(mustacheDisallowedText);
        }
        if (initializationClass != null) {
            target.initializationClass(initializationClass);
        }
        if (initializationJsonPath != null) {
            target.initializationJsonPath(initializationJsonPath);
        }
        if (initializationOpenAPIPath != null) {
            target.initializationOpenAPIPath(initializationOpenAPIPath);
        }
        if (openAPIContextPathPrefix != null) {
            target.openAPIContextPathPrefix(openAPIContextPathPrefix);
        }
        if (openAPIResponseValidation != null) {
            target.openAPIResponseValidation(openAPIResponseValidation);
        }
        if (watchInitializationJson != null) {
            target.watchInitializationJson(watchInitializationJson);
        }
        if (persistExpectations != null) {
            target.persistExpectations(persistExpectations);
        }
        if (persistedExpectationsPath != null) {
            target.persistedExpectationsPath(persistedExpectationsPath);
        }
        if (maximumNumberOfRequestToReturnInVerificationFailure != null) {
            target.maximumNumberOfRequestToReturnInVerificationFailure(maximumNumberOfRequestToReturnInVerificationFailure);
        }
        if (attemptToProxyIfNoMatchingExpectation != null) {
            target.attemptToProxyIfNoMatchingExpectation(attemptToProxyIfNoMatchingExpectation);
        }
        if (forwardHttpProxy != null) {
            target.forwardHttpProxy(parseInetSocketAddress(forwardHttpProxy));
        }
        if (forwardHttpsProxy != null) {
            target.forwardHttpsProxy(parseInetSocketAddress(forwardHttpsProxy));
        }
        if (forwardSocksProxy != null) {
            target.forwardSocksProxy(parseInetSocketAddress(forwardSocksProxy));
        }
        if (forwardProxyAuthenticationUsername != null) {
            target.forwardProxyAuthenticationUsername(forwardProxyAuthenticationUsername);
        }
        if (forwardProxyAuthenticationPassword != null) {
            target.forwardProxyAuthenticationPassword(forwardProxyAuthenticationPassword);
        }
        if (proxyAuthenticationRealm != null) {
            target.proxyAuthenticationRealm(proxyAuthenticationRealm);
        }
        if (proxyAuthenticationUsername != null) {
            target.proxyAuthenticationUsername(proxyAuthenticationUsername);
        }
        if (proxyAuthenticationPassword != null) {
            target.proxyAuthenticationPassword(proxyAuthenticationPassword);
        }
        if (noProxyHosts != null) {
            target.noProxyHosts(noProxyHosts);
        }
        if (livenessHttpGetPath != null) {
            target.livenessHttpGetPath(livenessHttpGetPath);
        }
        if (controlPlaneTLSMutualAuthenticationRequired != null) {
            target.controlPlaneTLSMutualAuthenticationRequired(controlPlaneTLSMutualAuthenticationRequired);
        }
        if (controlPlaneTLSMutualAuthenticationCAChain != null) {
            target.controlPlaneTLSMutualAuthenticationCAChain(controlPlaneTLSMutualAuthenticationCAChain);
        }
        if (controlPlanePrivateKeyPath != null) {
            target.controlPlanePrivateKeyPath(controlPlanePrivateKeyPath);
        }
        if (controlPlaneX509CertificatePath != null) {
            target.controlPlaneX509CertificatePath(controlPlaneX509CertificatePath);
        }
        if (controlPlaneJWTAuthenticationRequired != null) {
            target.controlPlaneJWTAuthenticationRequired(controlPlaneJWTAuthenticationRequired);
        }
        if (controlPlaneJWTAuthenticationJWKSource != null) {
            target.controlPlaneJWTAuthenticationJWKSource(controlPlaneJWTAuthenticationJWKSource);
        }
        if (controlPlaneJWTAuthenticationExpectedAudience != null) {
            target.controlPlaneJWTAuthenticationExpectedAudience(controlPlaneJWTAuthenticationExpectedAudience);
        }
        if (controlPlaneJWTAuthenticationMatchingClaims != null) {
            target.controlPlaneJWTAuthenticationMatchingClaims(controlPlaneJWTAuthenticationMatchingClaims);
        }
        if (controlPlaneJWTAuthenticationRequiredClaims != null) {
            target.controlPlaneJWTAuthenticationRequiredClaims(controlPlaneJWTAuthenticationRequiredClaims);
        }
        if (proactivelyInitialiseTLS != null) {
            target.proactivelyInitialiseTLS(proactivelyInitialiseTLS);
        }
        if (tlsProtocols != null) {
            target.tlsProtocols(tlsProtocols);
        }
        if (dynamicallyCreateCertificateAuthorityCertificate != null) {
            target.dynamicallyCreateCertificateAuthorityCertificate(dynamicallyCreateCertificateAuthorityCertificate);
        }
        if (directoryToSaveDynamicSSLCertificate != null) {
            target.directoryToSaveDynamicSSLCertificate(directoryToSaveDynamicSSLCertificate);
        }
        if (preventCertificateDynamicUpdate != null) {
            target.preventCertificateDynamicUpdate(preventCertificateDynamicUpdate);
        }
        if (sslCertificateDomainName != null) {
            target.sslCertificateDomainName(sslCertificateDomainName);
        }
        if (sslSubjectAlternativeNameDomains != null) {
            target.sslSubjectAlternativeNameDomains(sslSubjectAlternativeNameDomains);
        }
        if (sslSubjectAlternativeNameIps != null) {
            target.sslSubjectAlternativeNameIps(sslSubjectAlternativeNameIps);
        }
        if (certificateAuthorityPrivateKey != null) {
            target.certificateAuthorityPrivateKey(certificateAuthorityPrivateKey);
        }
        if (certificateAuthorityCertificate != null) {
            target.certificateAuthorityCertificate(certificateAuthorityCertificate);
        }
        if (privateKeyPath != null) {
            target.privateKeyPath(privateKeyPath);
        }
        if (x509CertificatePath != null) {
            target.x509CertificatePath(x509CertificatePath);
        }
        if (tlsMutualAuthenticationRequired != null) {
            target.tlsMutualAuthenticationRequired(tlsMutualAuthenticationRequired);
        }
        if (tlsMutualAuthenticationCertificateChain != null) {
            target.tlsMutualAuthenticationCertificateChain(tlsMutualAuthenticationCertificateChain);
        }
        if (forwardProxyTLSX509CertificatesTrustManagerType != null) {
            target.forwardProxyTLSX509CertificatesTrustManagerType(ForwardProxyTLSX509CertificatesTrustManager.valueOf(forwardProxyTLSX509CertificatesTrustManagerType));
        }
        if (forwardProxyTLSCustomTrustX509Certificates != null) {
            target.forwardProxyTLSCustomTrustX509Certificates(forwardProxyTLSCustomTrustX509Certificates);
        }
        if (forwardProxyPrivateKey != null) {
            target.forwardProxyPrivateKey(forwardProxyPrivateKey);
        }
        if (forwardProxyCertificateChain != null) {
            target.forwardProxyCertificateChain(forwardProxyCertificateChain);
        }
    }

    private InetSocketAddress parseInetSocketAddress(String hostAndPort) {
        try {
            java.net.URI uri = new java.net.URI("dummy://" + hostAndPort);
            String host = uri.getHost();
            int port = uri.getPort();
            if (host == null || port == -1) {
                throw new IllegalArgumentException("Invalid host:port format: \"" + hostAndPort + "\", expected format \"host:port\"");
            }
            return InetSocketAddress.createUnresolved(host, port);
        } catch (java.net.URISyntaxException e) {
            throw new IllegalArgumentException("Invalid host:port format: \"" + hostAndPort + "\", expected format \"host:port\"");
        }
    }

    public String getLogLevel() {
        return logLevel;
    }

    public ConfigurationDTO setLogLevel(String logLevel) {
        this.logLevel = logLevel;
        return this;
    }

    public Boolean getDisableSystemOut() {
        return disableSystemOut;
    }

    public ConfigurationDTO setDisableSystemOut(Boolean disableSystemOut) {
        this.disableSystemOut = disableSystemOut;
        return this;
    }

    public Boolean getDisableLogging() {
        return disableLogging;
    }

    public ConfigurationDTO setDisableLogging(Boolean disableLogging) {
        this.disableLogging = disableLogging;
        return this;
    }

    public Boolean getDetailedMatchFailures() {
        return detailedMatchFailures;
    }

    public ConfigurationDTO setDetailedMatchFailures(Boolean detailedMatchFailures) {
        this.detailedMatchFailures = detailedMatchFailures;
        return this;
    }

    public Boolean getLaunchUIForLogLevelDebug() {
        return launchUIForLogLevelDebug;
    }

    public ConfigurationDTO setLaunchUIForLogLevelDebug(Boolean launchUIForLogLevelDebug) {
        this.launchUIForLogLevelDebug = launchUIForLogLevelDebug;
        return this;
    }

    public Boolean getMetricsEnabled() {
        return metricsEnabled;
    }

    public ConfigurationDTO setMetricsEnabled(Boolean metricsEnabled) {
        this.metricsEnabled = metricsEnabled;
        return this;
    }

    public Boolean getMcpEnabled() {
        return mcpEnabled;
    }

    public ConfigurationDTO setMcpEnabled(Boolean mcpEnabled) {
        this.mcpEnabled = mcpEnabled;
        return this;
    }

    public Map<String, String> getLogLevelOverrides() {
        return logLevelOverrides;
    }

    public ConfigurationDTO setLogLevelOverrides(Map<String, String> logLevelOverrides) {
        this.logLevelOverrides = logLevelOverrides;
        return this;
    }

    public Integer getMaxExpectations() {
        return maxExpectations;
    }

    public ConfigurationDTO setMaxExpectations(Integer maxExpectations) {
        this.maxExpectations = maxExpectations;
        return this;
    }

    public Integer getMaxLogEntries() {
        return maxLogEntries;
    }

    public ConfigurationDTO setMaxLogEntries(Integer maxLogEntries) {
        this.maxLogEntries = maxLogEntries;
        return this;
    }

    public Integer getMaxWebSocketExpectations() {
        return maxWebSocketExpectations;
    }

    public ConfigurationDTO setMaxWebSocketExpectations(Integer maxWebSocketExpectations) {
        this.maxWebSocketExpectations = maxWebSocketExpectations;
        return this;
    }

    public Boolean getOutputMemoryUsageCsv() {
        return outputMemoryUsageCsv;
    }

    public ConfigurationDTO setOutputMemoryUsageCsv(Boolean outputMemoryUsageCsv) {
        this.outputMemoryUsageCsv = outputMemoryUsageCsv;
        return this;
    }

    public String getMemoryUsageCsvDirectory() {
        return memoryUsageCsvDirectory;
    }

    public ConfigurationDTO setMemoryUsageCsvDirectory(String memoryUsageCsvDirectory) {
        this.memoryUsageCsvDirectory = memoryUsageCsvDirectory;
        return this;
    }

    public Integer getNioEventLoopThreadCount() {
        return nioEventLoopThreadCount;
    }

    public ConfigurationDTO setNioEventLoopThreadCount(Integer nioEventLoopThreadCount) {
        this.nioEventLoopThreadCount = nioEventLoopThreadCount;
        return this;
    }

    public Integer getActionHandlerThreadCount() {
        return actionHandlerThreadCount;
    }

    public ConfigurationDTO setActionHandlerThreadCount(Integer actionHandlerThreadCount) {
        this.actionHandlerThreadCount = actionHandlerThreadCount;
        return this;
    }

    public Integer getClientNioEventLoopThreadCount() {
        return clientNioEventLoopThreadCount;
    }

    public ConfigurationDTO setClientNioEventLoopThreadCount(Integer clientNioEventLoopThreadCount) {
        this.clientNioEventLoopThreadCount = clientNioEventLoopThreadCount;
        return this;
    }

    public Integer getWebSocketClientEventLoopThreadCount() {
        return webSocketClientEventLoopThreadCount;
    }

    public ConfigurationDTO setWebSocketClientEventLoopThreadCount(Integer webSocketClientEventLoopThreadCount) {
        this.webSocketClientEventLoopThreadCount = webSocketClientEventLoopThreadCount;
        return this;
    }

    public Long getMaxFutureTimeoutInMillis() {
        return maxFutureTimeoutInMillis;
    }

    public ConfigurationDTO setMaxFutureTimeoutInMillis(Long maxFutureTimeoutInMillis) {
        this.maxFutureTimeoutInMillis = maxFutureTimeoutInMillis;
        return this;
    }

    public Boolean getMatchersFailFast() {
        return matchersFailFast;
    }

    public ConfigurationDTO setMatchersFailFast(Boolean matchersFailFast) {
        this.matchersFailFast = matchersFailFast;
        return this;
    }

    public Long getMaxSocketTimeoutInMillis() {
        return maxSocketTimeoutInMillis;
    }

    public ConfigurationDTO setMaxSocketTimeoutInMillis(Long maxSocketTimeoutInMillis) {
        this.maxSocketTimeoutInMillis = maxSocketTimeoutInMillis;
        return this;
    }

    public Long getSocketConnectionTimeoutInMillis() {
        return socketConnectionTimeoutInMillis;
    }

    public ConfigurationDTO setSocketConnectionTimeoutInMillis(Long socketConnectionTimeoutInMillis) {
        this.socketConnectionTimeoutInMillis = socketConnectionTimeoutInMillis;
        return this;
    }

    public Boolean getAlwaysCloseSocketConnections() {
        return alwaysCloseSocketConnections;
    }

    public ConfigurationDTO setAlwaysCloseSocketConnections(Boolean alwaysCloseSocketConnections) {
        this.alwaysCloseSocketConnections = alwaysCloseSocketConnections;
        return this;
    }

    public String getLocalBoundIP() {
        return localBoundIP;
    }

    public ConfigurationDTO setLocalBoundIP(String localBoundIP) {
        this.localBoundIP = localBoundIP;
        return this;
    }

    public Integer getMaxInitialLineLength() {
        return maxInitialLineLength;
    }

    public ConfigurationDTO setMaxInitialLineLength(Integer maxInitialLineLength) {
        this.maxInitialLineLength = maxInitialLineLength;
        return this;
    }

    public Integer getMaxHeaderSize() {
        return maxHeaderSize;
    }

    public ConfigurationDTO setMaxHeaderSize(Integer maxHeaderSize) {
        this.maxHeaderSize = maxHeaderSize;
        return this;
    }

    public Integer getMaxChunkSize() {
        return maxChunkSize;
    }

    public ConfigurationDTO setMaxChunkSize(Integer maxChunkSize) {
        this.maxChunkSize = maxChunkSize;
        return this;
    }

    public Boolean getUseSemicolonAsQueryParameterSeparator() {
        return useSemicolonAsQueryParameterSeparator;
    }

    public ConfigurationDTO setUseSemicolonAsQueryParameterSeparator(Boolean useSemicolonAsQueryParameterSeparator) {
        this.useSemicolonAsQueryParameterSeparator = useSemicolonAsQueryParameterSeparator;
        return this;
    }

    public Boolean getAssumeAllRequestsAreHttp() {
        return assumeAllRequestsAreHttp;
    }

    public ConfigurationDTO setAssumeAllRequestsAreHttp(Boolean assumeAllRequestsAreHttp) {
        this.assumeAllRequestsAreHttp = assumeAllRequestsAreHttp;
        return this;
    }

    public Boolean getForwardBinaryRequestsWithoutWaitingForResponse() {
        return forwardBinaryRequestsWithoutWaitingForResponse;
    }

    public ConfigurationDTO setForwardBinaryRequestsWithoutWaitingForResponse(Boolean forwardBinaryRequestsWithoutWaitingForResponse) {
        this.forwardBinaryRequestsWithoutWaitingForResponse = forwardBinaryRequestsWithoutWaitingForResponse;
        return this;
    }

    public Boolean getEnableCORSForAPI() {
        return enableCORSForAPI;
    }

    public ConfigurationDTO setEnableCORSForAPI(Boolean enableCORSForAPI) {
        this.enableCORSForAPI = enableCORSForAPI;
        return this;
    }

    public Boolean getEnableCORSForAllResponses() {
        return enableCORSForAllResponses;
    }

    public ConfigurationDTO setEnableCORSForAllResponses(Boolean enableCORSForAllResponses) {
        this.enableCORSForAllResponses = enableCORSForAllResponses;
        return this;
    }

    public String getCorsAllowOrigin() {
        return corsAllowOrigin;
    }

    public ConfigurationDTO setCorsAllowOrigin(String corsAllowOrigin) {
        this.corsAllowOrigin = corsAllowOrigin;
        return this;
    }

    public String getCorsAllowMethods() {
        return corsAllowMethods;
    }

    public ConfigurationDTO setCorsAllowMethods(String corsAllowMethods) {
        this.corsAllowMethods = corsAllowMethods;
        return this;
    }

    public String getCorsAllowHeaders() {
        return corsAllowHeaders;
    }

    public ConfigurationDTO setCorsAllowHeaders(String corsAllowHeaders) {
        this.corsAllowHeaders = corsAllowHeaders;
        return this;
    }

    public Boolean getCorsAllowCredentials() {
        return corsAllowCredentials;
    }

    public ConfigurationDTO setCorsAllowCredentials(Boolean corsAllowCredentials) {
        this.corsAllowCredentials = corsAllowCredentials;
        return this;
    }

    public Integer getCorsMaxAgeInSeconds() {
        return corsMaxAgeInSeconds;
    }

    public ConfigurationDTO setCorsMaxAgeInSeconds(Integer corsMaxAgeInSeconds) {
        this.corsMaxAgeInSeconds = corsMaxAgeInSeconds;
        return this;
    }

    public String getJavascriptDisallowedClasses() {
        return javascriptDisallowedClasses;
    }

    public ConfigurationDTO setJavascriptDisallowedClasses(String javascriptDisallowedClasses) {
        this.javascriptDisallowedClasses = javascriptDisallowedClasses;
        return this;
    }

    public String getJavascriptDisallowedText() {
        return javascriptDisallowedText;
    }

    public ConfigurationDTO setJavascriptDisallowedText(String javascriptDisallowedText) {
        this.javascriptDisallowedText = javascriptDisallowedText;
        return this;
    }

    public Boolean getVelocityDisallowClassLoading() {
        return velocityDisallowClassLoading;
    }

    public ConfigurationDTO setVelocityDisallowClassLoading(Boolean velocityDisallowClassLoading) {
        this.velocityDisallowClassLoading = velocityDisallowClassLoading;
        return this;
    }

    public String getVelocityDisallowedText() {
        return velocityDisallowedText;
    }

    public ConfigurationDTO setVelocityDisallowedText(String velocityDisallowedText) {
        this.velocityDisallowedText = velocityDisallowedText;
        return this;
    }

    public String getMustacheDisallowedText() {
        return mustacheDisallowedText;
    }

    public ConfigurationDTO setMustacheDisallowedText(String mustacheDisallowedText) {
        this.mustacheDisallowedText = mustacheDisallowedText;
        return this;
    }

    public String getInitializationClass() {
        return initializationClass;
    }

    public ConfigurationDTO setInitializationClass(String initializationClass) {
        this.initializationClass = initializationClass;
        return this;
    }

    public String getInitializationJsonPath() {
        return initializationJsonPath;
    }

    public ConfigurationDTO setInitializationJsonPath(String initializationJsonPath) {
        this.initializationJsonPath = initializationJsonPath;
        return this;
    }

    public String getInitializationOpenAPIPath() {
        return initializationOpenAPIPath;
    }

    public ConfigurationDTO setInitializationOpenAPIPath(String initializationOpenAPIPath) {
        this.initializationOpenAPIPath = initializationOpenAPIPath;
        return this;
    }

    public String getOpenAPIContextPathPrefix() {
        return openAPIContextPathPrefix;
    }

    public ConfigurationDTO setOpenAPIContextPathPrefix(String openAPIContextPathPrefix) {
        this.openAPIContextPathPrefix = openAPIContextPathPrefix;
        return this;
    }

    public Boolean getOpenAPIResponseValidation() {
        return openAPIResponseValidation;
    }

    public ConfigurationDTO setOpenAPIResponseValidation(Boolean openAPIResponseValidation) {
        this.openAPIResponseValidation = openAPIResponseValidation;
        return this;
    }

    public Boolean getWatchInitializationJson() {
        return watchInitializationJson;
    }

    public ConfigurationDTO setWatchInitializationJson(Boolean watchInitializationJson) {
        this.watchInitializationJson = watchInitializationJson;
        return this;
    }

    public Boolean getPersistExpectations() {
        return persistExpectations;
    }

    public ConfigurationDTO setPersistExpectations(Boolean persistExpectations) {
        this.persistExpectations = persistExpectations;
        return this;
    }

    public String getPersistedExpectationsPath() {
        return persistedExpectationsPath;
    }

    public ConfigurationDTO setPersistedExpectationsPath(String persistedExpectationsPath) {
        this.persistedExpectationsPath = persistedExpectationsPath;
        return this;
    }

    public Integer getMaximumNumberOfRequestToReturnInVerificationFailure() {
        return maximumNumberOfRequestToReturnInVerificationFailure;
    }

    public ConfigurationDTO setMaximumNumberOfRequestToReturnInVerificationFailure(Integer maximumNumberOfRequestToReturnInVerificationFailure) {
        this.maximumNumberOfRequestToReturnInVerificationFailure = maximumNumberOfRequestToReturnInVerificationFailure;
        return this;
    }

    public Boolean getAttemptToProxyIfNoMatchingExpectation() {
        return attemptToProxyIfNoMatchingExpectation;
    }

    public ConfigurationDTO setAttemptToProxyIfNoMatchingExpectation(Boolean attemptToProxyIfNoMatchingExpectation) {
        this.attemptToProxyIfNoMatchingExpectation = attemptToProxyIfNoMatchingExpectation;
        return this;
    }

    public String getForwardHttpProxy() {
        return forwardHttpProxy;
    }

    public ConfigurationDTO setForwardHttpProxy(String forwardHttpProxy) {
        this.forwardHttpProxy = forwardHttpProxy;
        return this;
    }

    public String getForwardHttpsProxy() {
        return forwardHttpsProxy;
    }

    public ConfigurationDTO setForwardHttpsProxy(String forwardHttpsProxy) {
        this.forwardHttpsProxy = forwardHttpsProxy;
        return this;
    }

    public String getForwardSocksProxy() {
        return forwardSocksProxy;
    }

    public ConfigurationDTO setForwardSocksProxy(String forwardSocksProxy) {
        this.forwardSocksProxy = forwardSocksProxy;
        return this;
    }

    public String getForwardProxyAuthenticationUsername() {
        return forwardProxyAuthenticationUsername;
    }

    public ConfigurationDTO setForwardProxyAuthenticationUsername(String forwardProxyAuthenticationUsername) {
        this.forwardProxyAuthenticationUsername = forwardProxyAuthenticationUsername;
        return this;
    }

    @JsonIgnore
    public String getForwardProxyAuthenticationPassword() {
        return forwardProxyAuthenticationPassword;
    }

    @JsonProperty
    public ConfigurationDTO setForwardProxyAuthenticationPassword(String forwardProxyAuthenticationPassword) {
        this.forwardProxyAuthenticationPassword = forwardProxyAuthenticationPassword;
        return this;
    }

    public String getProxyAuthenticationRealm() {
        return proxyAuthenticationRealm;
    }

    public ConfigurationDTO setProxyAuthenticationRealm(String proxyAuthenticationRealm) {
        this.proxyAuthenticationRealm = proxyAuthenticationRealm;
        return this;
    }

    public String getProxyAuthenticationUsername() {
        return proxyAuthenticationUsername;
    }

    public ConfigurationDTO setProxyAuthenticationUsername(String proxyAuthenticationUsername) {
        this.proxyAuthenticationUsername = proxyAuthenticationUsername;
        return this;
    }

    @JsonIgnore
    public String getProxyAuthenticationPassword() {
        return proxyAuthenticationPassword;
    }

    @JsonProperty
    public ConfigurationDTO setProxyAuthenticationPassword(String proxyAuthenticationPassword) {
        this.proxyAuthenticationPassword = proxyAuthenticationPassword;
        return this;
    }

    public String getNoProxyHosts() {
        return noProxyHosts;
    }

    public ConfigurationDTO setNoProxyHosts(String noProxyHosts) {
        this.noProxyHosts = noProxyHosts;
        return this;
    }

    public String getLivenessHttpGetPath() {
        return livenessHttpGetPath;
    }

    public ConfigurationDTO setLivenessHttpGetPath(String livenessHttpGetPath) {
        this.livenessHttpGetPath = livenessHttpGetPath;
        return this;
    }

    public Boolean getControlPlaneTLSMutualAuthenticationRequired() {
        return controlPlaneTLSMutualAuthenticationRequired;
    }

    public ConfigurationDTO setControlPlaneTLSMutualAuthenticationRequired(Boolean controlPlaneTLSMutualAuthenticationRequired) {
        this.controlPlaneTLSMutualAuthenticationRequired = controlPlaneTLSMutualAuthenticationRequired;
        return this;
    }

    public String getControlPlaneTLSMutualAuthenticationCAChain() {
        return controlPlaneTLSMutualAuthenticationCAChain;
    }

    public ConfigurationDTO setControlPlaneTLSMutualAuthenticationCAChain(String controlPlaneTLSMutualAuthenticationCAChain) {
        this.controlPlaneTLSMutualAuthenticationCAChain = controlPlaneTLSMutualAuthenticationCAChain;
        return this;
    }

    public String getControlPlanePrivateKeyPath() {
        return controlPlanePrivateKeyPath;
    }

    public ConfigurationDTO setControlPlanePrivateKeyPath(String controlPlanePrivateKeyPath) {
        this.controlPlanePrivateKeyPath = controlPlanePrivateKeyPath;
        return this;
    }

    public String getControlPlaneX509CertificatePath() {
        return controlPlaneX509CertificatePath;
    }

    public ConfigurationDTO setControlPlaneX509CertificatePath(String controlPlaneX509CertificatePath) {
        this.controlPlaneX509CertificatePath = controlPlaneX509CertificatePath;
        return this;
    }

    public Boolean getControlPlaneJWTAuthenticationRequired() {
        return controlPlaneJWTAuthenticationRequired;
    }

    public ConfigurationDTO setControlPlaneJWTAuthenticationRequired(Boolean controlPlaneJWTAuthenticationRequired) {
        this.controlPlaneJWTAuthenticationRequired = controlPlaneJWTAuthenticationRequired;
        return this;
    }

    public String getControlPlaneJWTAuthenticationJWKSource() {
        return controlPlaneJWTAuthenticationJWKSource;
    }

    public ConfigurationDTO setControlPlaneJWTAuthenticationJWKSource(String controlPlaneJWTAuthenticationJWKSource) {
        this.controlPlaneJWTAuthenticationJWKSource = controlPlaneJWTAuthenticationJWKSource;
        return this;
    }

    public String getControlPlaneJWTAuthenticationExpectedAudience() {
        return controlPlaneJWTAuthenticationExpectedAudience;
    }

    public ConfigurationDTO setControlPlaneJWTAuthenticationExpectedAudience(String controlPlaneJWTAuthenticationExpectedAudience) {
        this.controlPlaneJWTAuthenticationExpectedAudience = controlPlaneJWTAuthenticationExpectedAudience;
        return this;
    }

    public Map<String, String> getControlPlaneJWTAuthenticationMatchingClaims() {
        return controlPlaneJWTAuthenticationMatchingClaims;
    }

    public ConfigurationDTO setControlPlaneJWTAuthenticationMatchingClaims(Map<String, String> controlPlaneJWTAuthenticationMatchingClaims) {
        this.controlPlaneJWTAuthenticationMatchingClaims = controlPlaneJWTAuthenticationMatchingClaims;
        return this;
    }

    public Set<String> getControlPlaneJWTAuthenticationRequiredClaims() {
        return controlPlaneJWTAuthenticationRequiredClaims;
    }

    public ConfigurationDTO setControlPlaneJWTAuthenticationRequiredClaims(Set<String> controlPlaneJWTAuthenticationRequiredClaims) {
        this.controlPlaneJWTAuthenticationRequiredClaims = controlPlaneJWTAuthenticationRequiredClaims;
        return this;
    }

    public Boolean getProactivelyInitialiseTLS() {
        return proactivelyInitialiseTLS;
    }

    public ConfigurationDTO setProactivelyInitialiseTLS(Boolean proactivelyInitialiseTLS) {
        this.proactivelyInitialiseTLS = proactivelyInitialiseTLS;
        return this;
    }

    public String getTlsProtocols() {
        return tlsProtocols;
    }

    public ConfigurationDTO setTlsProtocols(String tlsProtocols) {
        this.tlsProtocols = tlsProtocols;
        return this;
    }

    public Boolean getDynamicallyCreateCertificateAuthorityCertificate() {
        return dynamicallyCreateCertificateAuthorityCertificate;
    }

    public ConfigurationDTO setDynamicallyCreateCertificateAuthorityCertificate(Boolean dynamicallyCreateCertificateAuthorityCertificate) {
        this.dynamicallyCreateCertificateAuthorityCertificate = dynamicallyCreateCertificateAuthorityCertificate;
        return this;
    }

    public String getDirectoryToSaveDynamicSSLCertificate() {
        return directoryToSaveDynamicSSLCertificate;
    }

    public ConfigurationDTO setDirectoryToSaveDynamicSSLCertificate(String directoryToSaveDynamicSSLCertificate) {
        this.directoryToSaveDynamicSSLCertificate = directoryToSaveDynamicSSLCertificate;
        return this;
    }

    public Boolean getPreventCertificateDynamicUpdate() {
        return preventCertificateDynamicUpdate;
    }

    public ConfigurationDTO setPreventCertificateDynamicUpdate(Boolean preventCertificateDynamicUpdate) {
        this.preventCertificateDynamicUpdate = preventCertificateDynamicUpdate;
        return this;
    }

    public String getSslCertificateDomainName() {
        return sslCertificateDomainName;
    }

    public ConfigurationDTO setSslCertificateDomainName(String sslCertificateDomainName) {
        this.sslCertificateDomainName = sslCertificateDomainName;
        return this;
    }

    public Set<String> getSslSubjectAlternativeNameDomains() {
        return sslSubjectAlternativeNameDomains;
    }

    public ConfigurationDTO setSslSubjectAlternativeNameDomains(Set<String> sslSubjectAlternativeNameDomains) {
        this.sslSubjectAlternativeNameDomains = sslSubjectAlternativeNameDomains;
        return this;
    }

    public Set<String> getSslSubjectAlternativeNameIps() {
        return sslSubjectAlternativeNameIps;
    }

    public ConfigurationDTO setSslSubjectAlternativeNameIps(Set<String> sslSubjectAlternativeNameIps) {
        this.sslSubjectAlternativeNameIps = sslSubjectAlternativeNameIps;
        return this;
    }

    @JsonIgnore
    public String getCertificateAuthorityPrivateKey() {
        return certificateAuthorityPrivateKey;
    }

    @JsonProperty
    public ConfigurationDTO setCertificateAuthorityPrivateKey(String certificateAuthorityPrivateKey) {
        this.certificateAuthorityPrivateKey = certificateAuthorityPrivateKey;
        return this;
    }

    public String getCertificateAuthorityCertificate() {
        return certificateAuthorityCertificate;
    }

    public ConfigurationDTO setCertificateAuthorityCertificate(String certificateAuthorityCertificate) {
        this.certificateAuthorityCertificate = certificateAuthorityCertificate;
        return this;
    }

    public String getPrivateKeyPath() {
        return privateKeyPath;
    }

    public ConfigurationDTO setPrivateKeyPath(String privateKeyPath) {
        this.privateKeyPath = privateKeyPath;
        return this;
    }

    public String getX509CertificatePath() {
        return x509CertificatePath;
    }

    public ConfigurationDTO setX509CertificatePath(String x509CertificatePath) {
        this.x509CertificatePath = x509CertificatePath;
        return this;
    }

    public Boolean getTlsMutualAuthenticationRequired() {
        return tlsMutualAuthenticationRequired;
    }

    public ConfigurationDTO setTlsMutualAuthenticationRequired(Boolean tlsMutualAuthenticationRequired) {
        this.tlsMutualAuthenticationRequired = tlsMutualAuthenticationRequired;
        return this;
    }

    public String getTlsMutualAuthenticationCertificateChain() {
        return tlsMutualAuthenticationCertificateChain;
    }

    public ConfigurationDTO setTlsMutualAuthenticationCertificateChain(String tlsMutualAuthenticationCertificateChain) {
        this.tlsMutualAuthenticationCertificateChain = tlsMutualAuthenticationCertificateChain;
        return this;
    }

    public String getForwardProxyTLSX509CertificatesTrustManagerType() {
        return forwardProxyTLSX509CertificatesTrustManagerType;
    }

    public ConfigurationDTO setForwardProxyTLSX509CertificatesTrustManagerType(String forwardProxyTLSX509CertificatesTrustManagerType) {
        this.forwardProxyTLSX509CertificatesTrustManagerType = forwardProxyTLSX509CertificatesTrustManagerType;
        return this;
    }

    public String getForwardProxyTLSCustomTrustX509Certificates() {
        return forwardProxyTLSCustomTrustX509Certificates;
    }

    public ConfigurationDTO setForwardProxyTLSCustomTrustX509Certificates(String forwardProxyTLSCustomTrustX509Certificates) {
        this.forwardProxyTLSCustomTrustX509Certificates = forwardProxyTLSCustomTrustX509Certificates;
        return this;
    }

    @JsonIgnore
    public String getForwardProxyPrivateKey() {
        return forwardProxyPrivateKey;
    }

    @JsonProperty
    public ConfigurationDTO setForwardProxyPrivateKey(String forwardProxyPrivateKey) {
        this.forwardProxyPrivateKey = forwardProxyPrivateKey;
        return this;
    }

    public String getForwardProxyCertificateChain() {
        return forwardProxyCertificateChain;
    }

    public ConfigurationDTO setForwardProxyCertificateChain(String forwardProxyCertificateChain) {
        this.forwardProxyCertificateChain = forwardProxyCertificateChain;
        return this;
    }
}
