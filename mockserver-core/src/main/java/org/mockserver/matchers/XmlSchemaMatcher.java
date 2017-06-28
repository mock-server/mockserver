package org.mockserver.matchers;

import org.mockserver.validator.XmlSchemaValidator;

/**
 * See http://xml-schema.org/
 *
 * @author jamesdbloom
 */
public class XmlSchemaMatcher extends BodyMatcher<String> {
    private String schema;
    private XmlSchemaValidator xmlSchemaValidator;

    public XmlSchemaMatcher(String schema) {
        this.schema = schema;
        xmlSchemaValidator = new XmlSchemaValidator(schema);
    }

    protected String[] fieldsExcludedFromEqualsAndHashCode() {
        return new String[]{"logger", "xmlSchemaValidator"};
    }

    public boolean matches(String matched) {
        boolean result = false;

        try {
            String validation = xmlSchemaValidator.isValid(matched);

            result = validation.isEmpty();

            if (!result) {
                logger.trace("Failed to perform XML match \"{}\" with schema \"{}\" because {}", matched, this.schema, validation);
            }
        } catch (Exception e) {
            logger.trace("Failed to perform XML match \"{}\" with schema \"{}\" because {}", matched, this.schema, e.getMessage());
        }

        return reverseResultIfNot(result);
    }

}
