package org.mockserver.matchers;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.netty.handler.codec.http.QueryStringDecoder;
import org.mockserver.log.model.LogEntry;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.model.Parameters;

import static org.slf4j.event.Level.DEBUG;

/**
 * @author jamesdbloom
 */
public class ParameterStringMatcher extends BodyMatcher<String> {
    private static final String[] EXCLUDED_FIELDS = {"mockServerLogger"};
    private final MultiValueMapMatcher matcher;

    ParameterStringMatcher(MockServerLogger mockServerLogger, Parameters parameters, boolean controlPlaneMatcher) {
        this.matcher = new MultiValueMapMatcher(mockServerLogger, parameters, controlPlaneMatcher);
    }

    public boolean matches(final MatchDifference context, String matched) {
        boolean result = false;

        if (matcher.matches(context, parseString(matched))) {
            result = true;
        }

        return not != result;
    }

    private Parameters parseString(String matched) {
        return new Parameters().withEntries(new QueryStringDecoder("?" + matched).parameters());
    }

    public boolean isBlank() {
        return matcher.isBlank();
    }

    @Override
    @JsonIgnore
    protected String[] fieldsExcludedFromEqualsAndHashCode() {
        return EXCLUDED_FIELDS;
    }
}
