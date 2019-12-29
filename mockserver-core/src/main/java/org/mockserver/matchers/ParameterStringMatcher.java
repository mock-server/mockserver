package org.mockserver.matchers;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.netty.handler.codec.http.QueryStringDecoder;
import org.mockserver.log.model.LogEntry;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.Parameters;

import static org.slf4j.event.Level.DEBUG;

/**
 * @author jamesdbloom
 */
public class ParameterStringMatcher extends BodyMatcher<String> {
    private static final String[] EXCLUDED_FIELDS = {"mockServerLogger"};
    private final MockServerLogger mockServerLogger;
    private final MultiValueMapMatcher matcher;

    ParameterStringMatcher(MockServerLogger mockServerLogger, Parameters parameters, boolean controlPlaneMatcher) {
        this.mockServerLogger = mockServerLogger;
        this.matcher = new MultiValueMapMatcher(mockServerLogger, parameters, controlPlaneMatcher);
    }

    public boolean matches(final HttpRequest context, String matched) {
        boolean result = false;

        if (matcher.matches(context, parseString(matched))) {
            result = true;
        }

        if (!result) {
            mockServerLogger.logEvent(
                new LogEntry()
                    .setType(LogEntry.LogMessageType.DEBUG)
                    .setLogLevel(DEBUG)
                    .setHttpRequest(context)
                    .setMessageFormat("Failed to perform parameter match {} with {}")
                    .setArguments(matched, this.matcher)
            );
        }

        return not != result;
    }

    private Parameters parseString(String matched) {
        return new Parameters().withEntries(new QueryStringDecoder("?" + matched).parameters());
    }

    @Override
    @JsonIgnore
    protected String[] fieldsExcludedFromEqualsAndHashCode() {
        return EXCLUDED_FIELDS;
    }
}
