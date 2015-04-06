package org.mockserver.matchers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.fge.jsonschema.core.report.ProcessingReport;
import com.github.fge.jsonschema.main.JsonSchemaFactory;
import org.mockserver.client.serialization.ObjectMapperFactory;

/**
 * See http://json-schema.org/
 *
 * @author jamesdbloom
 */
public class JsonSchemaMatcher extends BodyMatcher<String> {
    private final String schema;
    private ObjectMapper objectMapper = ObjectMapperFactory.createObjectMapper();

    protected String[] fieldsExcludedFromEqualsAndHashCode() {
        return new String[]{"logger", "objectMapper"};
    }

    public JsonSchemaMatcher(String schema) {
        this.schema = schema;
    }

    public boolean matches(String matched) {
        boolean result = false;

        ProcessingReport processingReport;
        try {
            processingReport = validateJson(matched);

            if (processingReport.isSuccess()) {
                result = true;
            }

            if (!result) {
                logger.trace("Failed to perform JSON match \"{}\" with schema \"{}\" because {}", matched, this.schema, objectMapper.writeValueAsString(processingReport));
            }
        } catch (Exception e) {
            logger.trace("Failed to perform JSON match \"{}\" with \"{}\" because {}", matched, this.schema, e.getMessage());
        }

        return reverseResultIfNot(result);
    }

    public ProcessingReport validateJson(String json) throws Exception {
        return JsonSchemaFactory
                .byDefault()
                .getValidator()
                .validate(objectMapper.readTree(schema), objectMapper.readTree(json), true);
    }
}
