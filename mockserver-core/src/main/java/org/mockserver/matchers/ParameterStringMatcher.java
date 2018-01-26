package org.mockserver.matchers;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.netty.handler.codec.http.QueryStringDecoder;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.KeyToMultiValue;
import org.mockserver.model.Parameters;

import java.util.ArrayList;
import java.util.List;

/**
 * @author jamesdbloom
 */
public class ParameterStringMatcher extends BodyMatcher<String> {
    private static final String[] excludedFields = {"mockServerLogger"};
    private final MockServerLogger mockServerLogger;
    private final MultiValueMapMatcher matcher;

    public ParameterStringMatcher(MockServerLogger mockServerLogger, Parameters parameters) {
        this.mockServerLogger = mockServerLogger;
        this.matcher = new MultiValueMapMatcher(mockServerLogger, parameters);
    }

    public boolean matches(HttpRequest context, String matched) {
        boolean result = false;

        if (matcher.matches(null, parseString(matched))) {
            result = true;
        }

        if (!result) {
            mockServerLogger.trace(context, "Failed to match [{}] with [{}]", matched, this.matcher);
        }

        return reverseResultIfNot(result);
    }

    private Parameters parseString(String matched) {
        return new Parameters().withEntries(new QueryStringDecoder("?" + matched).parameters());
    }

    @Override
    @JsonIgnore
    protected String[] fieldsExcludedFromEqualsAndHashCode() {
        return excludedFields;
    }
}
