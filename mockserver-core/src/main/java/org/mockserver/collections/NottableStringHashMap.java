package org.mockserver.collections;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;
import org.mockserver.log.model.LogEntry;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.matchers.RegexStringMatcher;
import org.mockserver.model.KeyAndValue;
import org.mockserver.model.NottableString;

import java.util.*;
import java.util.stream.Collectors;

import static org.mockserver.collections.ImmutableEntry.entry;
import static org.mockserver.collections.ImmutableEntry.listsEqual;
import static org.mockserver.collections.SubSets.distinctSubSetsMap;
import static org.mockserver.model.NottableString.string;
import static org.slf4j.event.Level.TRACE;

/**
 * @author jamesdbloom
 */
public class NottableStringHashMap {

    private final Map<NottableString, NottableString> backingMap = new LinkedHashMap<>();
    private final RegexStringMatcher regexStringMatcher;
    private final MockServerLogger mockServerLogger;
    private boolean noOptionals = true;

    public NottableStringHashMap(MockServerLogger mockServerLogger, boolean controlPlaneMatcher) {
        this.mockServerLogger = mockServerLogger;
        regexStringMatcher = new RegexStringMatcher(mockServerLogger, controlPlaneMatcher);
    }

    public NottableStringHashMap(MockServerLogger mockServerLogger, boolean controlPlaneMatcher, List<? extends KeyAndValue> entries) {
        this.mockServerLogger = mockServerLogger;
        regexStringMatcher = new RegexStringMatcher(mockServerLogger, controlPlaneMatcher);
        for (KeyAndValue keyToMultiValue : entries) {
            put(keyToMultiValue.getName(), keyToMultiValue.getValue());
        }
    }

    @VisibleForTesting
    public NottableStringHashMap(MockServerLogger mockServerLogger, boolean controlPlaneMatcher, String... keyAndValues) {
        this.mockServerLogger = mockServerLogger;
        regexStringMatcher = new RegexStringMatcher(mockServerLogger, controlPlaneMatcher);
        for (int i = 0; i < keyAndValues.length - 1; i += 2) {
            put(string(keyAndValues[i]), string(keyAndValues[i + 1]));
        }
    }

    @VisibleForTesting
    public NottableStringHashMap(MockServerLogger mockServerLogger, boolean controlPlaneMatcher, NottableString... keyAndValues) {
        this.mockServerLogger = mockServerLogger;
        regexStringMatcher = new RegexStringMatcher(mockServerLogger, controlPlaneMatcher);
        for (int i = 0; i < keyAndValues.length - 1; i += 2) {
            put(keyAndValues[i], keyAndValues[i + 1]);
        }
    }

    // TODO(jamesdbloom) remove once tests are cleaned up
    @VisibleForTesting
    public NottableStringHashMap(MockServerLogger mockServerLogger, boolean controlPlaneMatcher, String[]... keyAndValues) {
        this.mockServerLogger = mockServerLogger;
        regexStringMatcher = new RegexStringMatcher(mockServerLogger, controlPlaneMatcher);
        for (String[] keyAndValue : keyAndValues) {
            if (keyAndValue.length >= 2) {
                put(string(keyAndValue[0]), string(keyAndValue[1]));
            }
        }
    }

    // TODO(jamesdbloom) remove once tests are cleaned up
    @VisibleForTesting
    public NottableStringHashMap(MockServerLogger mockServerLogger, boolean controlPlaneMatcher, NottableString[]... keyAndValues) {
        this.mockServerLogger = mockServerLogger;
        regexStringMatcher = new RegexStringMatcher(mockServerLogger, controlPlaneMatcher);
        for (NottableString[] keyAndValue : keyAndValues) {
            if (keyAndValue.length >= 2) {
                put(keyAndValue[0], keyAndValue[1]);
            }
        }
    }

    // TODO(jamesdbloom) remove once tests are cleaned up
    @VisibleForTesting
    public static NottableStringHashMap hashMap(boolean controlPlaneMatcher, String[]... keyAndValues) {
        return new NottableStringHashMap(new MockServerLogger(), controlPlaneMatcher, keyAndValues);
    }

    // TODO(jamesdbloom) remove once tests are cleaned up
    @VisibleForTesting
    public static NottableStringHashMap hashMap(boolean controlPlaneMatcher, NottableString[]... keyAndValues) {
        return new NottableStringHashMap(new MockServerLogger(), controlPlaneMatcher, keyAndValues);
    }

    public boolean isNoOptionals() {
        return noOptionals;
    }

    public boolean containsAll(NottableStringHashMap matcher) {
        return containsAll(matcher, null);
    }

    public boolean containsAll(NottableStringHashMap matcher, String logCorrelationId) {

        List<ImmutableEntry> matchedEntries = entryList();
        Multimap<Integer, List<ImmutableEntry>> allMatchedSubSets
            = distinctSubSetsMap(matchedEntries, ArrayListMultimap.create(), matchedEntries.size() - 1);

        if (MockServerLogger.isEnabled(TRACE)) {
            mockServerLogger.logEvent(
                new LogEntry()
                    .setLogLevel(TRACE)
                    .setCorrelationId(logCorrelationId)
                    .setMessageFormat("attempting to match subset from{}against hashmap{}")
                    .setArguments(allMatchedSubSets, matcher.entryList())

            );
        }

        if (backingMap.isEmpty() && matcher.allKeysNotted()) {

            return true;

        } else if (noOptionals && matcher.isNoOptionals()) {

            // all non-optionals
            List<ImmutableEntry> matcherEntries = matcher.entryList();
            for (List<ImmutableEntry> matchedSubSet : allMatchedSubSets.get(matcherEntries.size())) {
                if (listsEqual(matcherEntries, matchedSubSet)) {
                    if (MockServerLogger.isEnabled(TRACE)) {
                        mockServerLogger.logEvent(
                            new LogEntry()
                                .setLogLevel(TRACE)
                                .setCorrelationId(logCorrelationId)
                                .setMessageFormat("hashmap{}containsAll subset{}in{}")
                                .setArguments(this, matchedSubSet, matcherEntries)

                        );
                    }
                    return true;
                }
            }
            if (MockServerLogger.isEnabled(TRACE)) {
                mockServerLogger.logEvent(
                    new LogEntry()
                        .setLogLevel(TRACE)
                        .setCorrelationId(logCorrelationId)
                        .setMessageFormat("hashmap{}containsAll found no subset equal to{}from{}")
                        .setArguments(this, matcherEntries, allMatchedSubSets)

                );
            }
        } else {

            // some optionals exist
            boolean result = false;

            // first check non-optionals
            List<ImmutableEntry> matcherEntriesWithoutOptionals = matcher.entryList().stream().filter(entry -> !entry.getKey().isOptional()).collect(Collectors.toList());
            for (List<ImmutableEntry> matchedSubSet : allMatchedSubSets.get(matcherEntriesWithoutOptionals.size())) {
                if (listsEqual(matcherEntriesWithoutOptionals, matchedSubSet)) {
                    if (MockServerLogger.isEnabled(TRACE)) {
                        mockServerLogger.logEvent(
                            new LogEntry()
                                .setLogLevel(TRACE)
                                .setCorrelationId(logCorrelationId)
                                .setMessageFormat("hashmap{}containsAll subset of non-optionals{}in{}")
                                .setArguments(this, matchedSubSet, matcherEntriesWithoutOptionals)

                        );
                    }
                    result = true;
                }
            }

            // then check optionals
            if (result) {
                List<ImmutableEntry> optionalMatcherEntries = matcher.entryList().stream().filter(entry -> entry.getKey().isOptional()).collect(Collectors.toList());
                if (!optionalMatcherEntries.isEmpty()) {
                    Set<ImmutableEntry> matchedSubSet = new HashSet<>();
                    for (ImmutableEntry optionalMatcherEntry : optionalMatcherEntries) {
                        for (ImmutableEntry matchedEntry : matchedEntries) {
                            if (optionalMatcherEntry.equals(matchedEntry)) {
                                matchedSubSet.add(matchedEntry);
                            } else {
                                if (MockServerLogger.isEnabled(TRACE)) {
                                    mockServerLogger.logEvent(
                                        new LogEntry()
                                            .setLogLevel(TRACE)
                                            .setCorrelationId(logCorrelationId)
                                            .setMessageFormat("hashmap{}failed to match optional{}with{}")
                                            .setArguments(this, optionalMatcherEntry, matchedEntry)

                                    );
                                }
                                return false;
                            }
                        }
                    }
                    if (MockServerLogger.isEnabled(TRACE)) {
                        mockServerLogger.logEvent(
                            new LogEntry()
                                .setLogLevel(TRACE)
                                .setCorrelationId(logCorrelationId)
                                .setMessageFormat("hashmap{}containsAll subset of optionals{}in{}")
                                .setArguments(this, matchedSubSet, optionalMatcherEntries)

                        );
                    }
                    return true;
                } else {
                    return true;
                }
            }

            if (MockServerLogger.isEnabled(TRACE)) {
                mockServerLogger.logEvent(
                    new LogEntry()
                        .setLogLevel(TRACE)
                        .setCorrelationId(logCorrelationId)
                        .setMessageFormat("hashmap{}containsAll found no subset equal to{}from{}")
                        .setArguments(this, matcher.entryList(), matchedEntries)

                );
            }
        }
        return false;
    }

    public boolean allKeysNotted() {
        for (NottableString key : backingMap.keySet()) {
            if (!key.isNot()) {
                return false;
            }
        }
        return true;
    }

    public boolean allKeysOptional() {
        for (NottableString key : backingMap.keySet()) {
            if (!key.isOptional()) {
                return false;
            }
        }
        return true;
    }

    public boolean isEmpty() {
        return backingMap.isEmpty();
    }

    private void put(NottableString key, NottableString value) {
        if (key.isOptional()) {
            noOptionals = false;
        }
        backingMap.put(key, value != null ? value : string(""));
    }

    private List<ImmutableEntry> entryList() {
        if (!backingMap.isEmpty()) {
            List<ImmutableEntry> entrySet = new ArrayList<>();
            for (Map.Entry<NottableString, NottableString> entry : backingMap.entrySet()) {
                entrySet.add(entry(regexStringMatcher, entry.getKey(), entry.getValue()));
            }
            return entrySet;
        } else {
            return Collections.emptyList();
        }
    }
}
