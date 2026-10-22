package org.mockserver.server.initialize;

import com.google.common.annotations.VisibleForTesting;
import org.apache.commons.lang3.ArrayUtils;
import org.mockserver.cache.LRUCache;
import org.mockserver.configuration.Configuration;
import org.mockserver.file.FilePath;
import org.mockserver.file.FileReader;
import org.mockserver.log.model.LogEntry;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.mock.Expectation;
import org.mockserver.mock.ExpectationLinter;
import org.mockserver.mock.RequestMatchers;
import org.mockserver.mock.listeners.MockServerMatcherNotifier;
import org.mockserver.mock.listeners.MockServerMatcherNotifier.Cause;
import org.mockserver.openapi.OpenAPIConverter;
import org.mockserver.openapi.OpenAPIParser;
import org.mockserver.serialization.ExpectationSerializer;

import java.lang.reflect.Constructor;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static org.mockserver.log.model.LogEntry.LogMessageType.SERVER_CONFIGURATION;
import static org.slf4j.event.Level.*;

/**
 * @author jamesdbloom
 */
public class ExpectationInitializerLoader {

    private static final LRUCache<String, List<String>> EXPANDED_INITIALIZATION_JSON_PATHS = new LRUCache<>(new MockServerLogger(LRUCache.class), 10, TimeUnit.HOURS.toMillis(1));
    private static final LRUCache<String, List<String>> EXPANDED_INITIALIZATION_OPENAPI_PATHS = new LRUCache<>(new MockServerLogger(LRUCache.class), 10, TimeUnit.HOURS.toMillis(1));
    private static final int MAX_LINT_DIGESTS = 1_000;
    private final Configuration configuration;
    private final ExpectationSerializer expectationSerializer;
    private final OpenAPIConverter openAPIConverter;
    private final MockServerLogger mockServerLogger;
    private final RequestMatchers requestMatchers;
    // per file, the problems last logged, so a --watch reload that finds the same ones stays quiet
    private final Map<String, Integer> lintDigests = new ConcurrentHashMap<>();

    public ExpectationInitializerLoader(Configuration configuration, MockServerLogger mockServerLogger, RequestMatchers requestMatchers) {
        this.configuration = configuration;
        this.expectationSerializer = new ExpectationSerializer(mockServerLogger, configuration);
        this.openAPIConverter = new OpenAPIConverter(mockServerLogger, configuration);
        this.mockServerLogger = mockServerLogger;
        this.requestMatchers = requestMatchers;
        addExpectationsFromInitializer();
    }

    public static List<String> expandedInitializationJsonPaths(String initializationJsonPath) {
        if (isNotBlank(initializationJsonPath)) {
            List<String> expandedInitializationJsonPaths = EXPANDED_INITIALIZATION_JSON_PATHS.get(initializationJsonPath);
            if (expandedInitializationJsonPaths == null) {
                expandedInitializationJsonPaths = FilePath.expandFilePathGlobs(initializationJsonPath);
                EXPANDED_INITIALIZATION_JSON_PATHS.put(initializationJsonPath, expandedInitializationJsonPaths);
            }
            return expandedInitializationJsonPaths;
        } else {
            return Collections.emptyList();
        }
    }

    public static List<String> expandedInitializationOpenAPIPaths(String initializationOpenAPIPath) {
        if (isNotBlank(initializationOpenAPIPath)) {
            List<String> expandedPaths = EXPANDED_INITIALIZATION_OPENAPI_PATHS.get(initializationOpenAPIPath);
            if (expandedPaths == null) {
                expandedPaths = FilePath.expandFilePathGlobs(initializationOpenAPIPath);
                EXPANDED_INITIALIZATION_OPENAPI_PATHS.put(initializationOpenAPIPath, expandedPaths);
            }
            return expandedPaths;
        } else {
            return Collections.emptyList();
        }
    }

    /**
     * When {@code failOnInitializationError} is enabled, a failed expectation initializer load must
     * fail server startup rather than logging a WARN and continuing with zero expectations from that
     * source. The throwable has already been logged at WARN by the caller; here it is re-thrown
     * wrapped in an {@link ExpectationInitializerException} so the {@code HttpState} constructor (and
     * therefore the whole startup) aborts.
     */
    private void failFastIfConfigured(String sourceType, String source, Throwable throwable) {
        if (configuration.failOnInitializationError()) {
            throw new ExpectationInitializerException("exception while loading " + sourceType + " expectation initializer (failOnInitializationError=true), failing startup for:" + source, throwable);
        }
    }

    private void addExpectationsFromInitializer() {
        retrieveExpectationsFromJson();
        retrieveExpectationsFromOpenAPI();
        for (Expectation expectation : retrieveExpectationsFromInitializerClass()) {
            requestMatchers.add(expectation, new Cause("", Cause.Type.CLASS_INITIALISER));
        }
    }

    private Expectation[] retrieveExpectationsFromInitializerClass() {
        Expectation[] expectations = new Expectation[0];
        String initializationClass = configuration.initializationClass();
        try {
            if (isNotBlank(initializationClass)) {
                if (mockServerLogger != null && mockServerLogger.isEnabledForInstance(INFO)) {
                    mockServerLogger.logEvent(
                        new LogEntry()
                            .setType(SERVER_CONFIGURATION)
                            .setLogLevel(INFO)
                            .setMessageFormat("loading class initialization file:{}")
                            .setArguments(initializationClass)
                    );
                }
                ClassLoader contextClassLoader = ExpectationInitializerLoader.class.getClassLoader();
                if (contextClassLoader != null && isNotBlank(initializationClass)) {
                    Constructor<?> initializerClassConstructor = contextClassLoader.loadClass(initializationClass).getDeclaredConstructor();
                    Object expectationInitializer = initializerClassConstructor.newInstance();
                    if (expectationInitializer instanceof ExpectationInitializer) {
                        expectations = ((ExpectationInitializer) expectationInitializer).initializeExpectations();
                    }
                }
            }
            if (expectations.length > 0) {
                if (mockServerLogger != null && mockServerLogger.isEnabledForInstance(TRACE)) {
                    mockServerLogger.logEvent(
                        new LogEntry()
                            .setLogLevel(TRACE)
                            .setMessageFormat("loaded expectations:{}from class:{}")
                            .setArguments(Arrays.asList(expectations), initializationClass)
                    );
                }
                requestMatchers.update(expectations, new MockServerMatcherNotifier.Cause(initializationClass, Cause.Type.CLASS_INITIALISER));
            }
        } catch (Throwable throwable) {
            if (mockServerLogger != null && mockServerLogger.isEnabledForInstance(WARN)) {
                mockServerLogger.logEvent(
                    new LogEntry()
                        .setType(SERVER_CONFIGURATION)
                        .setLogLevel(WARN)
                        .setMessageFormat("exception while loading JSON initialization class, ignoring class:{}")
                        .setArguments(initializationClass)
                        .setThrowable(throwable)
                );
            }
            failFastIfConfigured("class", initializationClass, throwable);
        }
        return expectations;
    }

    private Expectation[] retrieveExpectationsFromJson() {
        return retrieveExpectationsFromFile("loading JSON initialization file:{}", "exception while loading JSON initialization file, ignoring file:{}", "loaded expectations:{}from file:{}", Cause.Type.FILE_INITIALISER).toArray(new Expectation[0]);
    }

    private Expectation[] retrieveExpectationsFromOpenAPI() {
        return retrieveExpectationsFromOpenAPIFile("loading OpenAPI initialization file:{}", "exception while loading OpenAPI initialization file, ignoring file:{}", "loaded expectations:{}from OpenAPI file:{}", Cause.Type.FILE_INITIALISER).toArray(new Expectation[0]);
    }

    public List<Expectation> retrieveExpectationsFromOpenAPIFile(String initialLogMessage, String exceptionLogMessage, String completedLogMessage, Cause.Type causeType) {
        return retrieveExpectationsFromOpenAPIFile(initialLogMessage, exceptionLogMessage, completedLogMessage, causeType, false);
    }

    /**
     * @param fileWatcherReload true when a watched file changed, so lifecycle listeners are told the cause is
     *                          {@code FILE_WATCHER}
     */
    public List<Expectation> retrieveExpectationsFromOpenAPIFile(String initialLogMessage, String exceptionLogMessage, String completedLogMessage, Cause.Type causeType, boolean fileWatcherReload) {
        List<String> initializationOpenAPIPaths = ExpectationInitializerLoader.expandedInitializationOpenAPIPaths(configuration.initializationOpenAPIPath());
        return initializationOpenAPIPaths
            .stream()
            .flatMap(initializationOpenAPIPath -> {
                List<Expectation> expectations = Collections.emptyList();
                if (isNotBlank(initializationOpenAPIPath)) {
                    if (isNotBlank(initialLogMessage) && mockServerLogger.isEnabledForInstance(INFO)) {
                        mockServerLogger.logEvent(
                            new LogEntry()
                                .setType(SERVER_CONFIGURATION)
                                .setLogLevel(INFO)
                                .setMessageFormat(initialLogMessage)
                                .setArguments(initializationOpenAPIPath)
                        );
                    }
                    try {
                        OpenAPIParser.clearCache(initializationOpenAPIPath);
                        expectations = openAPIConverter.buildExpectations(initializationOpenAPIPath, null, configuration.openAPIContextPathPrefix());
                    } catch (Throwable throwable) {
                        if (mockServerLogger != null && mockServerLogger.isEnabledForInstance(WARN)) {
                            mockServerLogger.logEvent(
                                new LogEntry()
                                    .setType(SERVER_CONFIGURATION)
                                    .setLogLevel(WARN)
                                    .setMessageFormat(exceptionLogMessage)
                                    .setArguments(initializationOpenAPIPath)
                                    .setThrowable(throwable)
                            );
                        }
                        failFastIfConfigured("OpenAPI file", initializationOpenAPIPath, throwable);
                    }
                }
                if (mockServerLogger != null && mockServerLogger.isEnabledForInstance(TRACE)) {
                    mockServerLogger.logEvent(
                        new LogEntry()
                            .setLogLevel(TRACE)
                            .setMessageFormat(completedLogMessage)
                            .setArguments(expectations, initializationOpenAPIPath)
                    );
                }
                update(expectations.toArray(new Expectation[0]), new Cause(initializationOpenAPIPath, causeType), fileWatcherReload);
                return expectations.stream();
            })
            .collect(Collectors.toList());
    }

    public List<Expectation> retrieveExpectationsFromFile(String initialLogMessage, String expectationLogMessage, String completedLogMessage, Cause.Type causeType) {
        return retrieveExpectationsFromFile(initialLogMessage, expectationLogMessage, completedLogMessage, causeType, false);
    }

    /**
     * @param fileWatcherReload true when a watched file changed, so lifecycle listeners are told the cause is
     *                          {@code FILE_WATCHER}
     */
    public List<Expectation> retrieveExpectationsFromFile(String initialLogMessage, String expectationLogMessage, String completedLogMessage, Cause.Type causeType, boolean fileWatcherReload) {
        List<String> initializationJsonPaths = ExpectationInitializerLoader.expandedInitializationJsonPaths(configuration.initializationJsonPath());
        return initializationJsonPaths
            .stream()
            .flatMap(initializationJsonPath -> {
                Expectation[] expectations = new Expectation[0];
                if (isNotBlank(initializationJsonPath)) {
                    if (isNotBlank(initialLogMessage) && mockServerLogger.isEnabledForInstance(INFO)) {
                        mockServerLogger.logEvent(
                            new LogEntry()
                                .setType(SERVER_CONFIGURATION)
                                .setLogLevel(INFO)
                                .setMessageFormat(initialLogMessage)
                                .setArguments(initializationJsonPath)
                        );
                    }
                    // HashSet, not a List: this is a membership check on every deserialised
                    // expectation, so a List.contains() would be O(n) per item and O(n^2) over the
                    // file — a latent cost that grows with large initializer fixtures.
                    Set<String> expectationIds = new HashSet<>();
                    List<String> skippedEntries = new ArrayList<>();
                    try {
                        String jsonExpectations = FileReader.readFileFromClassPathOrPath(initializationJsonPath);
                        if (isNotBlank(jsonExpectations)) {
                            expectations = expectationSerializer.deserializeArrayDroppingInvalidEntries(jsonExpectations, (expectationString, deserialisedExpectations) -> {
                                for (int i = 0; i < deserialisedExpectations.size(); i++) {
                                    int counter = 0;
                                    String expectationId;
                                    do {
                                        expectationId = UUID.nameUUIDFromBytes(String.valueOf(Objects.hash(initializationJsonPath, expectationString, i, counter++)).getBytes(StandardCharsets.UTF_8)).toString();
                                    } while (expectationIds.contains(expectationId) && counter < 50);
                                    expectationIds.add(expectationId);
                                    deserialisedExpectations.get(i).withIdIfNull(expectationId);
                                }
                                return deserialisedExpectations;
                            }, (entry, errors) -> {
                                // one invalid entry is skipped, not the whole file
                                skippedEntries.add(entry);
                                if (mockServerLogger != null && mockServerLogger.isEnabledForInstance(WARN)) {
                                    mockServerLogger.logEvent(
                                        new LogEntry()
                                            .setType(SERVER_CONFIGURATION)
                                            .setLogLevel(WARN)
                                            .setMessageFormat("skipping an invalid expectation in JSON initialization file:{}which is:{}the other expectations in the file are loaded; it failed validation with:{}")
                                            .setArguments(initializationJsonPath, entry, errors)
                                    );
                                }
                            });
                        }
                        if (!skippedEntries.isEmpty() && configuration.failOnInitializationError()) {
                            throw new IllegalArgumentException("skipped " + skippedEntries.size() + " invalid expectation(s): " + String.join(", ", skippedEntries));
                        }
                    } catch (Throwable throwable) {
                        if (mockServerLogger != null && mockServerLogger.isEnabledForInstance(WARN)) {
                            mockServerLogger.logEvent(
                                new LogEntry()
                                    .setType(SERVER_CONFIGURATION)
                                    .setLogLevel(WARN)
                                    .setMessageFormat(expectationLogMessage)
                                    .setArguments(initializationJsonPath)
                                    .setThrowable(throwable)
                            );
                        }
                        failFastIfConfigured("JSON file", initializationJsonPath, throwable);
                    }
                    adviseOnLoad(initializationJsonPath, expectations, expectationIds);
                }
                if (mockServerLogger != null && mockServerLogger.isEnabledForInstance(TRACE)) {
                    mockServerLogger.logEvent(
                        new LogEntry()
                            .setLogLevel(TRACE)
                            .setMessageFormat(completedLogMessage)
                            .setArguments(Arrays.asList(expectations), initializationJsonPath)
                    );
                }
                update(expectations, new Cause(initializationJsonPath, causeType), fileWatcherReload);
                return Arrays.stream(expectations);
            })
            .collect(Collectors.toList());
    }

    private void update(Expectation[] expectations, Cause cause, boolean fileWatcherReload) {
        if (fileWatcherReload) {
            requestMatchers.update(expectations, cause, true);
        } else {
            requestMatchers.update(expectations, cause);
        }
    }

    // lint is advisory: a failure in it must never stop a file loading, or fail startup
    private void adviseOnLoad(String initializationJsonPath, Expectation[] expectations, Set<String> generatedIds) {
        try {
            Integer previousDigest = lintDigests.get(initializationJsonPath);
            int digest = ExpectationLinter.adviseOnLoad(mockServerLogger, initializationJsonPath, expectations, generatedIds, previousDigest != null ? previousDigest : 0);
            if (digest == 0) {
                lintDigests.remove(initializationJsonPath);
            } else {
                if (lintDigests.size() >= MAX_LINT_DIGESTS && !lintDigests.containsKey(initializationJsonPath)) {
                    lintDigests.clear();
                }
                lintDigests.put(initializationJsonPath, digest);
            }
        } catch (Throwable lintFailure) {
            if (mockServerLogger != null && mockServerLogger.isEnabledForInstance(WARN)) {
                mockServerLogger.logEvent(
                    new LogEntry()
                        .setType(SERVER_CONFIGURATION)
                        .setLogLevel(WARN)
                        .setMessageFormat("expectation lint failed for JSON initialization file:{}its expectations were loaded unchanged")
                        .setArguments(initializationJsonPath)
                        .setThrowable(lintFailure)
                );
            }
        }
    }

    @VisibleForTesting
    public Expectation[] loadExpectations() {
        final Expectation[] expectationsFromInitializerClass = retrieveExpectationsFromInitializerClass();
        final Expectation[] expectationsFromJson = retrieveExpectationsFromJson();
        final Expectation[] expectationsFromOpenAPI = retrieveExpectationsFromOpenAPI();
        return ArrayUtils.addAll(ArrayUtils.addAll(expectationsFromInitializerClass, expectationsFromJson), expectationsFromOpenAPI);
    }
}
