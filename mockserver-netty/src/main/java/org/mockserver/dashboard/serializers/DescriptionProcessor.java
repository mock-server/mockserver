package org.mockserver.dashboard.serializers;

import io.swagger.v3.oas.models.OpenAPI;
import org.apache.commons.lang3.StringUtils;
import org.mockserver.dashboard.model.DashboardLogEntryDTO;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.OpenAPIDefinition;

import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static org.mockserver.openapi.OpenAPIParser.buildOpenAPI;

public class DescriptionProcessor {
    private int maxHttpRequestLength;
    private int maxOpenAPILength;
    private int maxOpenAPIObjectLength;
    private int maxLogEventLength;

    public int getMaxHttpRequestLength() {
        return maxHttpRequestLength;
    }

    public int getMaxOpenAPILength() {
        return maxOpenAPILength;
    }

    public int getMaxOpenAPIObjectLength() {
        return maxOpenAPIObjectLength;
    }

    public int getMaxLogEventLength() {
        return maxLogEventLength;
    }

    public Description description(Object object) {
        return description(object, null);
    }

    public Description description(Object object, String id) {
        Description description = null;
        String idMessage = isNotBlank(id) ? id + ": " : "";
        if (object instanceof HttpRequest) {
            HttpRequest httpRequest = (HttpRequest) object;
            description = new RequestDefinitionDescription(idMessage + httpRequest.getMethod().getValue(), httpRequest.getPath().getValue(), this, false);
            if (description.length() >= maxHttpRequestLength) {
                maxHttpRequestLength = description.length();
            }
        } else if (object instanceof OpenAPIDefinition) {
            OpenAPIDefinition openAPIDefinition = (OpenAPIDefinition) object;
            String operationId = isNotBlank(openAPIDefinition.getOperationId()) ? openAPIDefinition.getOperationId() : "";
            String specUrlOrPayload = openAPIDefinition.getSpecUrlOrPayload().trim();
            if (specUrlOrPayload.endsWith(".json") || specUrlOrPayload.endsWith(".yaml")) {
                description = new RequestDefinitionDescription(idMessage + StringUtils.substringAfterLast(specUrlOrPayload, "/"), operationId, this, true);
                if (description.length() >= maxOpenAPILength) {
                    maxOpenAPILength = description.length();
                }
            } else {
                OpenAPI openAPI = buildOpenAPI(specUrlOrPayload);
                description = new RequestDefinitionObjectDescription(idMessage + "spec ", openAPI, operationId, this);
                if (description.length() >= maxOpenAPIObjectLength) {
                    maxOpenAPIObjectLength = description.length();
                }
            }
        } else if (object instanceof DashboardLogEntryDTO) {
            DashboardLogEntryDTO logEntryDTO = (DashboardLogEntryDTO) object;
            description = new LogMessageDescription(idMessage + StringUtils.substringAfter(logEntryDTO.getTimestamp(), "-"), logEntryDTO.getType().name(), this);
            if (description.length() >= maxLogEventLength) {
                maxLogEventLength = description.length();
            }
        }

        return description;
    }
}