package org.mockserver.matchers;

import com.fasterxml.jackson.annotation.JsonIgnore;
import org.mockserver.codec.ExpandedParameterDecoder;
import org.mockserver.configuration.Configuration;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.model.Parameters;

/**
 * @author jamesdbloom
 */
public class ParameterStringMatcher extends BodyMatcher<String> {
    private static final String[] EXCLUDED_FIELDS = {"mockServerLogger"};
    private final MultiValueMapMatcher matcher;
    private final ExpandedParameterDecoder formParameterParser;
    private final Parameters matcherParameters;
    private final ExpandedParameterDecoder expandedParameterDecoder;

    ParameterStringMatcher(Configuration configuration, MockServerLogger mockServerLogger, Parameters matcherParameters, boolean controlPlaneMatcher) {
        this.matcherParameters = matcherParameters;
        this.matcher = new MultiValueMapMatcher(mockServerLogger, matcherParameters, controlPlaneMatcher);
        this.formParameterParser = new ExpandedParameterDecoder(configuration, mockServerLogger);
        this.expandedParameterDecoder = new ExpandedParameterDecoder(configuration, mockServerLogger);
    }

    public boolean matches(final MatchDifference context, String matched) {
        boolean result = false;

        Parameters matchedParameters = formParameterParser.retrieveFormParameters(matched, matched != null && matched.contains("?"));
        expandedParameterDecoder.splitParameters(matcherParameters, matchedParameters);
        if (matcher.matches(context, matchedParameters)) {
            result = true;
        }

        return not != result;
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
