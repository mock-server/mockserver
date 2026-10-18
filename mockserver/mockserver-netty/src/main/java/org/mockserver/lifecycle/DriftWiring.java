package org.mockserver.lifecycle;

import io.netty.channel.EventLoopGroup;
import org.mockserver.collections.MostRecentRegistration;
import org.mockserver.configuration.Configuration;
import org.mockserver.httpclient.NettyHttpClient;
import org.mockserver.llm.client.LlmBackend;
import org.mockserver.llm.client.LlmBackendResolver;
import org.mockserver.llm.client.LlmCompletionService;
import org.mockserver.llm.client.NettyHttpClientLlmTransport;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.mock.drift.DriftAlertNotifier;
import org.mockserver.mock.drift.DriftAnalyzer;
import org.mockserver.mock.drift.SemanticDriftExtension;
import org.mockserver.mock.drift.SemanticSeverity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;

/**
 * One server's drift settings: the alert webhook, LLM semantic analysis and the p95 response time threshold.
 *
 * <p>{@link DriftAnalyzer} and {@link DriftAlertNotifier} are process-wide, so only one server's settings can be in
 * effect at a time: those of the most recently started server still running that turns at least one of them on. A
 * server that turns none on leaves them alone. When the server whose settings are in effect stops, the settings of
 * the one that put its own in effect before it, if still running, take effect again; otherwise all three are
 * switched off. A server's settings therefore never outlive it.
 */
final class DriftWiring {

    private static final Logger LOGGER = LoggerFactory.getLogger(LifeCycle.class);
    private static final MostRecentRegistration<DriftWiring> IN_EFFECT = new MostRecentRegistration<>();

    private final Configuration configuration;
    private final SemanticDriftExtension semanticExtension;
    private final long responseTimeThresholdMs;
    private final String webhookUrl;
    private final SemanticSeverity webhookSeverityThreshold;
    private final long webhookCooldownMillis;

    private DriftWiring(Configuration configuration, MockServerLogger mockServerLogger, EventLoopGroup eventLoopGroup) {
        this.configuration = configuration;
        this.semanticExtension = semanticExtension(configuration, mockServerLogger, eventLoopGroup);
        this.responseTimeThresholdMs = responseTimeThreshold(configuration);
        SemanticSeverity severityThreshold = null;
        String url = null;
        if (configuration.driftAlertWebhookEnabled()) {
            url = configuration.driftAlertWebhookUrl();
            severityThreshold = webhookSeverityThreshold(configuration, url);
        }
        this.webhookSeverityThreshold = severityThreshold;
        this.webhookUrl = severityThreshold != null ? url : null;
        this.webhookCooldownMillis = configuration.driftAlertCooldownMillis();
        if (this.webhookUrl != null) {
            // INFO logs only scheme+host: the configured webhook URL often embeds a secret token
            // (e.g. a Slack incoming-webhook path), so the full URL must never be written at INFO.
            LOGGER.info("drift alert webhook enabled (endpoint: {}, severity>={}, cooldown: {} ms)", redactWebhookUrl(webhookUrl), webhookSeverityThreshold, webhookCooldownMillis);
            LOGGER.debug("drift alert webhook full url: {}", webhookUrl);
        }
    }

    /**
     * Build {@code configuration}'s drift settings and, when they turn anything on, put them in effect in place of
     * those another running server put in effect.
     */
    static DriftWiring start(Configuration configuration, MockServerLogger mockServerLogger, EventLoopGroup eventLoopGroup) {
        DriftWiring wiring = new DriftWiring(configuration, mockServerLogger, eventLoopGroup);
        if (!wiring.turnsAnythingOn()) {
            return wiring;
        }
        synchronized (IN_EFFECT) {
            IN_EFFECT.register(wiring);
            apply(IN_EFFECT.get());
        }
        return wiring;
    }

    /**
     * Withdraw these settings, which the server that started them no longer serves. If they are in effect, the
     * settings of the most recently started server still running take their place, or none.
     */
    void stop() {
        if (!turnsAnythingOn()) {
            return;
        }
        synchronized (IN_EFFECT) {
            DriftWiring inEffect = IN_EFFECT.get();
            IN_EFFECT.unregister(this);
            // re-applying unchanged settings would reset the running server's alert cooldown
            if (IN_EFFECT.get() != inEffect) {
                apply(IN_EFFECT.get());
            }
        }
    }

    private boolean turnsAnythingOn() {
        return semanticExtension != null || responseTimeThresholdMs > 0 || webhookUrl != null;
    }

    private static void apply(DriftWiring wiring) {
        DriftAnalyzer analyzer = DriftAnalyzer.getInstance();
        analyzer.setSemanticExtension(wiring != null ? wiring.semanticExtension : null);
        analyzer.setResponseTimeThresholdMs(wiring != null ? wiring.responseTimeThresholdMs : 0);
        if (wiring != null && wiring.webhookUrl != null) {
            DriftAlertNotifier.getInstance().configure(true, wiring.webhookUrl, wiring.webhookSeverityThreshold, wiring.webhookCooldownMillis, wiring.configuration);
        } else {
            DriftAlertNotifier.getInstance().configure(false, "", SemanticSeverity.BREAKING, 0);
        }
    }

    /**
     * When semantic drift analysis is enabled and a runtime LLM backend resolves, a
     * {@link SemanticDriftExtension} using it; otherwise {@code null}. Fail-soft.
     */
    private static SemanticDriftExtension semanticExtension(Configuration configuration, MockServerLogger mockServerLogger, EventLoopGroup eventLoopGroup) {
        if (!configuration.driftSemanticAnalysisEnabled()) {
            return null;
        }
        try {
            Optional<LlmBackend> backend = new LlmBackendResolver(configuration).resolveDefault();
            if (!backend.isPresent()) {
                LOGGER.info("semantic drift analysis enabled but no LLM backend available; feature disabled");
                return null;
            }
            NettyHttpClient httpClient = new NettyHttpClient(configuration, mockServerLogger, eventLoopGroup, null, false);
            LlmCompletionService service = new LlmCompletionService(new NettyHttpClientLlmTransport(httpClient), configuration);
            SemanticDriftExtension extension = new SemanticDriftExtension(service, backend.get(), configuration.dataPlaneApiKeyAuthenticationHeader());
            LOGGER.info("semantic drift analysis enabled (backend: {})", backend.get().provider());
            return extension;
        } catch (Exception e) {
            // fail-soft — semantic drift analysis stays off
            LOGGER.warn("failed to enable semantic drift analysis ({}); continuing without it", e.getMessage());
            return null;
        }
    }

    /** The configured p95 response time threshold for performance drift; zero or less disables it. */
    private static long responseTimeThreshold(Configuration configuration) {
        long threshold = configuration.driftResponseTimeThresholdMs();
        if (threshold > 0) {
            LOGGER.info("performance drift detection enabled (p95 threshold: {} ms)", threshold);
        }
        return threshold;
    }

    /**
     * The severity threshold for an enabled webhook, or {@code null}, which leaves the webhook off, when no URL is
     * configured or the threshold is malformed. Fail-soft.
     */
    private static SemanticSeverity webhookSeverityThreshold(Configuration configuration, String url) {
        try {
            if (url == null || url.isBlank()) {
                LOGGER.warn("drift alert webhook enabled but no URL configured; feature disabled");
                return null;
            }
            try {
                return SemanticSeverity.valueOf(configuration.driftAlertSeverityThreshold().trim().toUpperCase());
            } catch (Exception e) {
                LOGGER.warn("drift alert webhook severity threshold '{}' is invalid; feature disabled", configuration.driftAlertSeverityThreshold());
                return null;
            }
        } catch (Exception e) {
            // fail-soft — drift alert webhook stays off
            LOGGER.warn("failed to enable drift alert webhook ({}); continuing without it", e.getMessage());
            return null;
        }
    }

    /**
     * Reduces a webhook URL to a token-free {@code scheme://host[:port]} form for INFO logging. The
     * path/query are dropped because they commonly carry a secret (e.g. a Slack incoming-webhook
     * token). Falls back to {@code <redacted url>} if the URL cannot be parsed.
     */
    private static String redactWebhookUrl(String url) {
        try {
            java.net.URI uri = java.net.URI.create(url);
            if (uri.getScheme() != null && uri.getHost() != null) {
                return uri.getScheme() + "://" + uri.getHost() + (uri.getPort() == -1 ? "" : ":" + uri.getPort());
            }
        } catch (Exception ignore) {
            // fall through to a fully-redacted placeholder
        }
        return "<redacted url>";
    }
}
