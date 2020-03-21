package org.mockserver.matchers;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.google.common.base.Joiner;
import net.javacrumbs.jsonunit.core.Configuration;
import net.javacrumbs.jsonunit.core.internal.Diff;
import net.javacrumbs.jsonunit.core.internal.Options;
import net.javacrumbs.jsonunit.core.listener.Difference;
import net.javacrumbs.jsonunit.core.listener.DifferenceContext;
import net.javacrumbs.jsonunit.core.listener.DifferenceListener;
import org.mockserver.log.model.LogEntry;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.model.HttpRequest;
import org.mockserver.serialization.ObjectMapperFactory;

import java.util.ArrayList;
import java.util.List;

import static net.javacrumbs.jsonunit.core.Option.*;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.slf4j.event.Level.DEBUG;

/**
 * @author jamesdbloom
 */
public class JsonStringMatcher extends BodyMatcher<String> {
    private static final String[] EXCLUDED_FIELDS = {"mockServerLogger"};
    private static final ObjectWriter PRETTY_PRINTER = ObjectMapperFactory.createObjectMapper().writerWithDefaultPrettyPrinter();
    private final MockServerLogger mockServerLogger;
    private final String matcher;
    private JsonNode matcherJsonNode;
    private final MatchType matchType;

    JsonStringMatcher(MockServerLogger mockServerLogger, String matcher, MatchType matchType) {
        this.mockServerLogger = mockServerLogger;
        this.matcher = matcher;

        this.matchType = matchType;
    }

    public boolean matches(final HttpRequest context, String matched) {
        boolean result = false;

        try {
            if (isBlank(matcher)) {
                result = true;
            } else {
                Options options = Options.empty();
                switch (matchType) {
                    case STRICT:
                        break;
                    case ONLY_MATCHING_FIELDS:
                        options = options.with(
                            IGNORING_ARRAY_ORDER,
                            IGNORING_EXTRA_ARRAY_ITEMS,
                            IGNORING_EXTRA_FIELDS
                        );
                        break;
                }
                final DiffListener diffListener = new DiffListener();
                Configuration diffConfig = Configuration.empty().withDifferenceListener(diffListener).withOptions(options);

                try {
                    if (matcherJsonNode == null) {
                        matcherJsonNode = ObjectMapperFactory.createObjectMapper().readTree(matcher);
                    }
                    result = Diff
                        .create(
                            matcherJsonNode,
                            ObjectMapperFactory.createObjectMapper().readTree(matched),
                            "",
                            "",
                            diffConfig
                        )
                        .similar();
                } catch (Throwable throwable) {
                    mockServerLogger.logEvent(
                        new LogEntry()
                            .setLogLevel(DEBUG)
                            .setHttpRequest(context)
                            .setMessageFormat("exception while perform json match of{}with{}")
                            .setArguments(matched, this.matcher)
                            .setThrowable(throwable)
                    );
                }

                if (!result) {
                    if (diffListener.differences.isEmpty()) {
                        mockServerLogger.logEvent(
                            new LogEntry()
                                .setLogLevel(DEBUG)
                                .setHttpRequest(context)
                                .setMessageFormat("failed to perform json match of{}with{}")
                                .setArguments(matched, this.matcher)
                        );
                    } else {
                        mockServerLogger.logEvent(
                            new LogEntry()
                                .setLogLevel(DEBUG)
                                .setHttpRequest(context)
                                .setMessageFormat("failed to perform json match of{}with{}because{}")
                                .setArguments(matched, this.matcher, Joiner.on(",\n").join(diffListener.differences))
                        );
                    }
                }
            }
        } catch (Exception e) {
            mockServerLogger.logEvent(
                new LogEntry()
                    .setLogLevel(DEBUG)
                    .setHttpRequest(context)
                    .setMessageFormat("failed to perform json match{}with{}because{}")
                    .setArguments(matched, this.matcher, e.getMessage())
                    .setThrowable(e)
            );
        }

        return not != result;
    }

    private static class DiffListener implements DifferenceListener {

        public List<String> differences = new ArrayList<>();

        @Override
        public void diff(Difference difference, DifferenceContext context) {
            switch (difference.getType()) {
                case EXTRA:
                    differences.add("additional element at \"" + difference.getActualPath() + "\" with value: " + prettyPrint(difference.getActual()));
                    break;
                case MISSING:
                    differences.add("missing element at \"" + difference.getActualPath() + "\"");
                    break;
                case DIFFERENT:
                    differences.add("wrong value at \"" + difference.getActualPath() + "\", expected: " + prettyPrint(difference.getExpected()) + " but was: " + prettyPrint(difference.getActual()));
                    break;
            }
        }

        private String prettyPrint(Object value) {
            try {
                return PRETTY_PRINTER.writeValueAsString(value);
            } catch (JsonProcessingException e) {
                return String.valueOf(value);
            }
        }
    }

    @Override
    @JsonIgnore
    protected String[] fieldsExcludedFromEqualsAndHashCode() {
        return EXCLUDED_FIELDS;
    }
}
