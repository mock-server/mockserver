package org.mockserver.matchers;

import com.fasterxml.jackson.annotation.JsonIgnore;
import org.mockserver.codec.FormParameterDecoder;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.model.Parameters;

/**
 * @author jamesdbloom
 */
public class ParameterStringMatcher extends BodyMatcher<String> {
    private static final String[] EXCLUDED_FIELDS = {"mockServerLogger"};
    private final MultiValueMapMatcher matcher;
    private final FormParameterDecoder formParameterParser;

    ParameterStringMatcher(MockServerLogger mockServerLogger, Parameters parameters, boolean controlPlaneMatcher) {
        this.matcher = new MultiValueMapMatcher(mockServerLogger, parameters, controlPlaneMatcher);
        this.formParameterParser = new FormParameterDecoder(mockServerLogger);
    }

    public boolean matches(final MatchDifference context, String matched) {
        boolean result = false;

        if (matcher.matches(context, formParameterParser.retrieveFormParameters(matched, matched != null && matched.contains("?")))) {
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
