package org.mockserver.dashboard.serializers;

import org.apache.commons.lang3.StringUtils;
import org.mockserver.dashboard.model.DashboardLogEntryDTO;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;
import org.mockserver.model.OpenAPIDefinition;

import static org.apache.commons.lang3.StringUtils.isNotBlank;

public class DescriptionProcessor {
    private int maxHttpRequestLength;
    private int maxOpenAPILength;
    private int maxLogEventLength;

    public int getMaxHttpRequestLength() {
        return maxHttpRequestLength;
    }

    public int getMaxOpenAPILength() {
        return maxOpenAPILength;
    }

    public int getMaxLogEventLength() {
        return maxLogEventLength;
    }

    public Description description(Object object) {
        Description description = null;
        if (object instanceof HttpRequest) {
            HttpRequest httpRequest = (HttpRequest) object;
            description = new HttpObjectDescription(httpRequest.getMethod().getValue(), httpRequest.getPath().getValue(), this, false);
            if (description.length() >= maxHttpRequestLength) {
                maxHttpRequestLength = description.length();
            }
        } else if (object instanceof HttpResponse) {
            HttpResponse httpResponse = (HttpResponse) object;
            description = new HttpObjectDescription(String.valueOf(httpResponse.getStatusCode()), httpResponse.getReasonPhrase(), this, false);
            if (description.length() >= maxHttpRequestLength) {
                maxHttpRequestLength = description.length();
            }
        } else if (object instanceof OpenAPIDefinition) {
            OpenAPIDefinition openAPIDefinition = (OpenAPIDefinition) object;
            String operationId = openAPIDefinition.getOperationId();
            String specUrlOrPayload = openAPIDefinition.getSpecUrlOrPayload().trim();
            description = new HttpObjectDescription(specUrlOrPayload.endsWith(".json") || specUrlOrPayload.endsWith(".yaml") ? StringUtils.substringAfterLast(specUrlOrPayload, "/") : "spec", isNotBlank(operationId) ? operationId : "", this, true);
            if (description.length() >= maxOpenAPILength) {
                maxOpenAPILength = description.length();
            }
        } else if (object instanceof DashboardLogEntryDTO) {
            DashboardLogEntryDTO logEntryDTO = (DashboardLogEntryDTO) object;
            description = new LogMessageDescription(StringUtils.substringAfter(logEntryDTO.getTimestamp(), "-"), logEntryDTO.getType().name(), this);
            if (description.length() >= maxLogEventLength) {
                maxLogEventLength = description.length();
            }
        }

        return description;
    }
}