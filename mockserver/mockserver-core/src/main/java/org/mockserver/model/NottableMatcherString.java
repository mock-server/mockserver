package org.mockserver.model;

import org.mockserver.logging.MockServerLogger;
import org.mockserver.matchers.MatchDifference;

/**
 * A matcher value that decides a match itself instead of by exact or regex comparison: a JSON schema
 * ({@link NottableSchemaString}) or a date-time comparison ({@link NottableDateTimeString}). It is only
 * ever created from the explicit object form of a value, never inferred from a plain string, so a plain
 * value always keeps its exact / regex meaning.
 */
public abstract class NottableMatcherString extends NottableString {

    NottableMatcherString(String value, Boolean not, ParameterStyle parameterStyle, String schemaType) {
        super(value, not, parameterStyle, schemaType);
    }

    NottableMatcherString(String value) {
        super(value);
    }

    /**
     * @param value the actual value
     * @return whether {@code value} matches, with this matcher's negation already applied
     */
    public abstract boolean matches(MockServerLogger mockServerLogger, MatchDifference context, String value);
}
