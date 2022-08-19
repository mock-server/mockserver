package org.mockserver.matchers;

import org.apache.commons.lang3.StringUtils;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.validator.xmlschema.XmlSchemaValidator;

import static org.apache.commons.lang3.StringUtils.isNotBlank;

/**
 * See http://xml-schema.org/
 *
 * @author jamesdbloom
 */
public class XmlSchemaMatcher extends BodyMatcher<String> {
    private static final String[] EXCLUDED_FIELDS = {"mockServerLogger", "xmlSchemaValidator"};
    private final MockServerLogger mockServerLogger;
    private String matcher;
    private XmlSchemaValidator xmlSchemaValidator;

    XmlSchemaMatcher(MockServerLogger mockServerLogger, String matcher) {
        this.mockServerLogger = mockServerLogger;
        this.matcher = matcher;
        xmlSchemaValidator = new XmlSchemaValidator(mockServerLogger, matcher);
    }

    protected String[] fieldsExcludedFromEqualsAndHashCode() {
        return EXCLUDED_FIELDS;
    }

    public boolean matches(final MatchDifference context, String matched) {
        boolean result = false;

        if (isNotBlank(matched)) {
            try {
                String validation = xmlSchemaValidator.isValid(matched);

                result = validation.isEmpty();

                if (!result && context != null) {
                    context.addDifference(mockServerLogger, "xml schema match failed expected:{}found:{}failed because:{}", this.matcher, matched, validation);
                }
            } catch (Throwable throwable) {
                if (context != null) {
                    context.addDifference(mockServerLogger, throwable, "xml schema match failed expected:{}found:{}failed because:{}", this.matcher, matched, throwable.getMessage());
                }
            }
        } else {
            if (context != null) {
                context.addDifference(mockServerLogger, "xml schema match failed expected:{}found:{}failed because xml is null or empty", this.matcher, matched);
            }
        }

        return not != result;
    }

    public boolean isBlank() {
        return StringUtils.isBlank(matcher);
    }

}
