package org.mockserver.dashboard.serializers;

import io.netty.buffer.ByteBufUtil;
import io.swagger.v3.oas.models.OpenAPI;
import org.apache.commons.lang3.StringUtils;
import org.mockserver.configuration.Configuration;
import org.mockserver.configuration.ConfigurationProperties;
import org.mockserver.dashboard.model.DashboardLogEntryDTO;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.model.BinaryRequestDefinition;
import org.mockserver.model.DnsRequestDefinition;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.OpenAPIDefinition;
import org.mockserver.openapi.OpenAPIParser;

import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static org.mockserver.openapi.OpenAPIParser.buildOpenAPI;

public class DescriptionProcessor {

    private static final MockServerLogger MOCK_SERVER_LOGGER = new MockServerLogger();
    static final int BINARY_PREVIEW_BYTES = 16;
    private final Configuration configuration;
    private int maxHttpRequestLength;
    private int maxOpenAPILength;
    private int maxOpenAPIObjectLength;
    private int maxLogEventLength;

    /**
     * @param configuration the running server's configuration, which applies to what parsing an OpenAPI spec fetches
     */
    public DescriptionProcessor(Configuration configuration) {
        this.configuration = configuration;
    }

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
            OpenAPI openAPI = OpenAPIParser.isSpecUrl(specUrlOrPayload) ? null : parse(specUrlOrPayload);
            if (openAPI == null) {
                String name = OpenAPIParser.isSpecUrl(specUrlOrPayload) ? StringUtils.substringAfterLast(specUrlOrPayload, "/") : "spec";
                description = new RequestDefinitionDescription(idMessage + name, operationId, this, true);
                if (description.length() >= maxOpenAPILength) {
                    maxOpenAPILength = description.length();
                }
            } else {
                description = new RequestDefinitionObjectDescription(idMessage + "spec ", openAPI, operationId, this);
                if (description.length() >= maxOpenAPIObjectLength) {
                    maxOpenAPIObjectLength = description.length();
                }
            }
        } else if (object instanceof DashboardLogEntryDTO) {
            DashboardLogEntryDTO logEntryDTO = (DashboardLogEntryDTO) object;
            description = new LogMessageDescription(idMessage + StringUtils.substringAfter(logEntryDTO.getTimestamp(), "-"), logEntryDTO.getType() != null ? logEntryDTO.getType().name() : "", this);
            if (description.length() >= maxLogEventLength) {
                maxLogEventLength = description.length();
            }
        }

        return description;
    }

    /**
     * As {@link #description(Object)}, also describing a DNS query by its name and type, and a binary message by its
     * length and first bytes in hex. The bytes are left out when {@code redactSecretsInLog} is on, as the message
     * itself is masked then.
     *
     * @param binaryLength the length of a binary message as received, which may be longer than the bytes the
     *                     dashboard shows; {@code null} to use the length of the bytes in {@code request}
     */
    public Description receivedRequestDescription(Object request, Integer binaryLength) {
        if (request instanceof DnsRequestDefinition) {
            DnsRequestDefinition dnsRequest = (DnsRequestDefinition) request;
            String name = StringUtils.defaultString(dnsRequest.getDnsName());
            return new ReceivedRequestDescription("DNS", dnsRequest.getDnsType() != null ? name + " " + dnsRequest.getDnsType().name() : name);
        } else if (request instanceof BinaryRequestDefinition) {
            return new ReceivedRequestDescription("BINARY", binarySummary(((BinaryRequestDefinition) request).getBinaryData(), binaryLength));
        }
        return description(request);
    }

    private String binarySummary(byte[] bytes, Integer binaryLength) {
        if (configuration != null ? configuration.redactSecretsInLog() : ConfigurationProperties.redactSecretsInLog()) {
            return "bytes redacted";
        }
        int available = bytes != null ? bytes.length : 0;
        int length = binaryLength != null ? binaryLength : available;
        int shown = Math.min(available, BINARY_PREVIEW_BYTES);
        if (shown == 0) {
            return length + " bytes";
        }
        return length + " bytes " + ByteBufUtil.hexDump(bytes, 0, shown) + (length > shown ? "..." : "");
    }

    /**
     * A spec that cannot be parsed, a refused fetch included, is described by name alone rather than failing the
     * whole dashboard update.
     */
    private OpenAPI parse(String specUrlOrPayload) {
        try {
            return buildOpenAPI(specUrlOrPayload, MOCK_SERVER_LOGGER, configuration);
        } catch (IllegalArgumentException unparseable) {
            return null;
        }
    }
}