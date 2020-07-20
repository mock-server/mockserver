package org.mockserver.mock;

import org.mockserver.closurecallback.websocketregistry.WebSocketClientRegistry;
import org.mockserver.collections.CircularPriorityQueue;
import org.mockserver.configuration.ConfigurationProperties;
import org.mockserver.log.model.LogEntry;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.matchers.HttpRequestMatcher;
import org.mockserver.matchers.MatchDifference;
import org.mockserver.matchers.MatcherBuilder;
import org.mockserver.metrics.Metrics;
import org.mockserver.mock.listeners.MockServerMatcherNotifier;
import org.mockserver.model.Action;
import org.mockserver.model.HttpObjectCallback;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.RequestDefinition;
import org.mockserver.scheduler.Scheduler;
import org.slf4j.event.Level;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static org.mockserver.configuration.ConfigurationProperties.maxExpectations;
import static org.mockserver.log.model.LogEntry.LogMessageType.*;
import static org.mockserver.log.model.LogEntryMessages.*;
import static org.mockserver.metrics.Metrics.Name.*;
import static org.mockserver.mock.SortableExpectationId.EXPECTATION_SORTABLE_PRIORITY_COMPARATOR;
import static org.mockserver.mock.SortableExpectationId.NULL;
import static org.slf4j.event.Level.DEBUG;

/**
 * @author jamesdbloom
 */
public class RequestMatchers extends MockServerMatcherNotifier {

    final CircularPriorityQueue<String, HttpRequestMatcher, SortableExpectationId> httpRequestMatchers = new CircularPriorityQueue<>(
        maxExpectations(),
        EXPECTATION_SORTABLE_PRIORITY_COMPARATOR,
        httpRequestMatcher -> httpRequestMatcher.getExpectation() != null ? httpRequestMatcher.getExpectation().getSortableId() : NULL,
        httpRequestMatcher -> httpRequestMatcher.getExpectation() != null ? httpRequestMatcher.getExpectation().getId() : ""
    );
    private final MockServerLogger mockServerLogger;
    private final Scheduler scheduler;
    private WebSocketClientRegistry webSocketClientRegistry;
    private MatcherBuilder matcherBuilder;

    public RequestMatchers(MockServerLogger mockServerLogger, Scheduler scheduler, WebSocketClientRegistry webSocketClientRegistry) {
        super(scheduler);
        this.scheduler = scheduler;
        this.matcherBuilder = new MatcherBuilder(mockServerLogger);
        this.mockServerLogger = mockServerLogger;
        this.webSocketClientRegistry = webSocketClientRegistry;
    }

    public Expectation add(Expectation expectation, Cause cause) {
        Expectation upsertedExpectation = null;
        if (expectation != null) {
            upsertedExpectation = httpRequestMatchers
                .getByKey(expectation.getId())
                .map(httpRequestMatcher -> {
                    if (httpRequestMatcher.getExpectation() != null && httpRequestMatcher.getExpectation().getAction() != null) {
                        Metrics.decrement(httpRequestMatcher.getExpectation().getAction().getType());
                    }
                    if (httpRequestMatcher.getExpectation() != null) {
                        // propagate created time from previous entry to avoid re-ordering on update
                        expectation.withCreated(httpRequestMatcher.getExpectation().getCreated());
                    }
                    httpRequestMatchers.removePriorityKey(httpRequestMatcher);
                    if (httpRequestMatcher.update(expectation)) {
                        httpRequestMatchers.addPriorityKey(httpRequestMatcher);
                        if (MockServerLogger.isEnabled(Level.DEBUG)) {
                            mockServerLogger.logEvent(
                                new LogEntry()
                                    .setType(UPDATED_EXPECTATION)
                                    .setLogLevel(Level.DEBUG)
                                    .setHttpRequest(expectation.getHttpRequest())
                                    .setMessageFormat(UPDATED_EXPECTATION_MESSAGE_FORMAT)
                                    .setArguments(expectation.clone())
                            );
                        }
                        if (expectation.getAction() != null) {
                            Metrics.increment(expectation.getAction().getType());
                        }
                    } else {
                        httpRequestMatchers.addPriorityKey(httpRequestMatcher);
                    }
                    return httpRequestMatcher;
                })
                .orElseGet(() -> addPrioritisedExpectation(expectation))
                .getExpectation();
            notifyListeners(this, cause);
        }
        return upsertedExpectation;
    }

    public void update(Expectation[] expectations, Cause cause) {
        AtomicInteger numberOfChanges = new AtomicInteger(0);
        if (expectations != null) {
            Map<String, HttpRequestMatcher> httpRequestMatchersByKey = httpRequestMatchers.keyMap();
            Set<String> existingKeys = new HashSet<>(httpRequestMatchersByKey.keySet());
            Arrays
                .stream(expectations)
                .forEach(expectation -> {
                    existingKeys.remove(expectation.getId());
                    if (httpRequestMatchersByKey.containsKey(expectation.getId())) {
                        HttpRequestMatcher httpRequestMatcher = httpRequestMatchersByKey.get(expectation.getId());
                        if (httpRequestMatcher.getExpectation() != null && httpRequestMatcher.getExpectation().getAction() != null) {
                            Metrics.decrement(httpRequestMatcher.getExpectation().getAction().getType());
                        }
                        if (httpRequestMatcher.getExpectation() != null) {
                            // propagate created time from previous entry to avoid re-ordering on update
                            expectation.withCreated(httpRequestMatcher.getExpectation().getCreated());
                        }
                        httpRequestMatchers.removePriorityKey(httpRequestMatcher);
                        if (httpRequestMatcher.update(expectation)) {
                            httpRequestMatchers.addPriorityKey(httpRequestMatcher);
                            numberOfChanges.getAndIncrement();
                            if (MockServerLogger.isEnabled(Level.INFO)) {
                                mockServerLogger.logEvent(
                                    new LogEntry()
                                        .setType(UPDATED_EXPECTATION)
                                        .setLogLevel(Level.INFO)
                                        .setHttpRequest(expectation.getHttpRequest())
                                        .setMessageFormat(UPDATED_EXPECTATION_MESSAGE_FORMAT)
                                        .setArguments(expectation.clone())
                                );
                            }
                            if (expectation.getAction() != null) {
                                Metrics.increment(expectation.getAction().getType());
                            }
                        } else {
                            httpRequestMatchers.addPriorityKey(httpRequestMatcher);
                        }
                    } else {
                        addPrioritisedExpectation(expectation);
                        numberOfChanges.getAndIncrement();
                    }
                });
            existingKeys
                .forEach(key -> {
                    numberOfChanges.getAndIncrement();
                    HttpRequestMatcher httpRequestMatcher = httpRequestMatchersByKey.get(key);
                    removeHttpRequestMatcher(httpRequestMatcher, cause, false);
                    if (httpRequestMatcher.getExpectation() != null && httpRequestMatcher.getExpectation().getAction() != null) {
                        Metrics.decrement(httpRequestMatcher.getExpectation().getAction().getType());
                    }
                });
            if (numberOfChanges.get() > 0) {
                notifyListeners(this, cause);
            }
        }
    }

    private HttpRequestMatcher addPrioritisedExpectation(Expectation expectation) {
        HttpRequestMatcher httpRequestMatcher = matcherBuilder.transformsToMatcher(expectation);
        httpRequestMatchers.add(httpRequestMatcher);
        if (expectation.getAction() != null) {
            Metrics.increment(expectation.getAction().getType());
        }
        if (MockServerLogger.isEnabled(Level.INFO)) {
            mockServerLogger.logEvent(
                new LogEntry()
                    .setType(CREATED_EXPECTATION)
                    .setLogLevel(Level.INFO)
                    .setHttpRequest(expectation.getHttpRequest())
                    .setMessageFormat(CREATED_EXPECTATION_MESSAGE_FORMAT)
                    .setArguments(expectation.clone())
            );
        }
        return httpRequestMatcher;
    }

    public int size() {
        return httpRequestMatchers.size();
    }

    public void setMaxSize(int maxSize) {
        httpRequestMatchers.setMaxSize(maxSize);
    }

    public void reset(Cause cause) {
        httpRequestMatchers.stream().forEach(httpRequestMatcher -> removeHttpRequestMatcher(httpRequestMatcher, cause, false));
        Metrics.clearActionMetrics();
        notifyListeners(this, cause);
    }

    public void reset() {
        reset(Cause.API);
    }

    public Expectation firstMatchingExpectation(HttpRequest httpRequest) {
        Optional<Expectation> first = getHttpRequestMatchersCopy()
            .map(httpRequestMatcher -> {
                Expectation matchingExpectation = null;
                boolean remainingMatchesDecremented = false;
                if (httpRequestMatcher.matches(MockServerLogger.isEnabled(DEBUG) ? new MatchDifference(httpRequest) : null, httpRequest)) {
                    matchingExpectation = httpRequestMatcher.getExpectation();
                    httpRequestMatcher.setResponseInProgress(true);
                    if (matchingExpectation.decrementRemainingMatches()) {
                        remainingMatchesDecremented = true;
                    }
                } else if (!httpRequestMatcher.isResponseInProgress() && !httpRequestMatcher.isActive()) {
                    scheduler.submit(() -> removeHttpRequestMatcher(httpRequestMatcher));
                }
                if (remainingMatchesDecremented) {
                    notifyListeners(this, Cause.API);
                }
                return matchingExpectation;
            })
            .filter(Objects::nonNull)
            .findFirst();
        if (ConfigurationProperties.metricsEnabled()) {
            if (!first.isPresent() || first.get().getAction() == null) {
                Metrics.increment(EXPECTATION_NOT_MATCHED_COUNT);
            } else if (first.get().getAction().getType().direction == Action.Direction.FORWARD) {
                Metrics.increment(FORWARD_EXPECTATION_MATCHED_COUNT);
            } else {
                Metrics.increment(RESPONSE_EXPECTATION_MATCHED_COUNT);
            }
        }
        return first.orElse(null);
    }

    public void clear(RequestDefinition requestDefinition) {
        if (requestDefinition != null) {
            HttpRequestMatcher clearHttpRequestMatcher = matcherBuilder.transformsToMatcher(requestDefinition);
            getHttpRequestMatchersCopy().forEach(httpRequestMatcher -> {
                RequestDefinition request = httpRequestMatcher
                    .getExpectation()
                    .getHttpRequest();
                if (isNotBlank(requestDefinition.getLogCorrelationId())) {
                    request = request
                        .shallowClone()
                        .withLogCorrelationId(requestDefinition.getLogCorrelationId());
                }
                if (clearHttpRequestMatcher.matches(request)) {
                    removeHttpRequestMatcher(httpRequestMatcher);
                }
            });
            if (MockServerLogger.isEnabled(Level.INFO)) {
                mockServerLogger.logEvent(
                    new LogEntry()
                        .setType(CLEARED)
                        .setLogLevel(Level.INFO)
                        .setCorrelationId(requestDefinition.getLogCorrelationId())
                        .setHttpRequest(requestDefinition)
                        .setMessageFormat("cleared expectations that match:{}")
                        .setArguments(requestDefinition)
                );
            }
        } else {
            reset();
        }
    }

    Expectation postProcess(Expectation expectation) {
        if (expectation != null) {
            getHttpRequestMatchersCopy()
                .filter(httpRequestMatcher -> httpRequestMatcher.getExpectation() == expectation)
                .findFirst()
                .ifPresent(httpRequestMatcher -> {
                    if (!expectation.isActive()) {
                        removeHttpRequestMatcher(httpRequestMatcher);
                    }
                    httpRequestMatcher.setResponseInProgress(false);
                });
        }
        return expectation;
    }

    private void removeHttpRequestMatcher(HttpRequestMatcher httpRequestMatcher) {
        removeHttpRequestMatcher(httpRequestMatcher, Cause.API, true);
    }

    @SuppressWarnings("rawtypes")
    private void removeHttpRequestMatcher(HttpRequestMatcher httpRequestMatcher, Cause cause, boolean notifyAndUpdateMetrics) {
        if (httpRequestMatchers.remove(httpRequestMatcher)) {
            if (httpRequestMatcher.getExpectation() != null && MockServerLogger.isEnabled(Level.INFO)) {
                mockServerLogger.logEvent(
                    new LogEntry()
                        .setType(REMOVED_EXPECTATION)
                        .setLogLevel(Level.INFO)
                        .setHttpRequest(httpRequestMatcher.getExpectation().getHttpRequest())
                        .setMessageFormat(REMOVED_EXPECTATION_MESSAGE_FORMAT)
                        .setArguments(httpRequestMatcher.getExpectation().clone())
                );
            }
            if (httpRequestMatcher.getExpectation() != null) {
                final Action action = httpRequestMatcher.getExpectation().getAction();
                if (action instanceof HttpObjectCallback) {
                    webSocketClientRegistry.unregisterClient(((HttpObjectCallback) action).getClientId());
                }
                if (notifyAndUpdateMetrics && action != null) {
                    Metrics.decrement(action.getType());
                }
            }
            if (notifyAndUpdateMetrics) {
                notifyListeners(this, cause);
            }
        }
    }

    public List<Expectation> retrieveActiveExpectations(RequestDefinition requestDefinition) {
        if (requestDefinition == null) {
            return httpRequestMatchers.stream().map(HttpRequestMatcher::getExpectation).collect(Collectors.toList());
        } else {
            List<Expectation> expectations = new ArrayList<>();
            HttpRequestMatcher requestMatcher = matcherBuilder.transformsToMatcher(requestDefinition);
            getHttpRequestMatchersCopy().forEach(httpRequestMatcher -> {
                if (requestMatcher.matches(httpRequestMatcher.getExpectation().getHttpRequest())) {
                    expectations.add(httpRequestMatcher.getExpectation());
                }
            });
            return expectations;
        }
    }

    public List<HttpRequestMatcher> retrieveRequestMatchers(RequestDefinition requestDefinition) {
        if (requestDefinition == null) {
            return httpRequestMatchers.stream().collect(Collectors.toList());
        } else {
            List<HttpRequestMatcher> httpRequestMatchers = new ArrayList<>();
            HttpRequestMatcher requestMatcher = matcherBuilder.transformsToMatcher(requestDefinition);
            getHttpRequestMatchersCopy().forEach(httpRequestMatcher -> {
                if (requestMatcher.matches(httpRequestMatcher.getExpectation().getHttpRequest())) {
                    httpRequestMatchers.add(httpRequestMatcher);
                }
            });
            return httpRequestMatchers;
        }
    }

    public boolean isEmpty() {
        return httpRequestMatchers.isEmpty();
    }

    protected void notifyListeners(final RequestMatchers notifier, Cause cause) {
        super.notifyListeners(notifier, cause);
    }

    private Stream<HttpRequestMatcher> getHttpRequestMatchersCopy() {
        return httpRequestMatchers.stream();
    }
}
