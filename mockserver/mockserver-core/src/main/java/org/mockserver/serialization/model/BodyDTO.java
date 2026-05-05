package org.mockserver.serialization.model;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.codec.binary.Base64;
import org.mockserver.log.model.LogEntry;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.model.*;
import org.mockserver.serialization.ObjectMapperFactory;

import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static org.slf4j.event.Level.ERROR;

/**
 * @author jamesdbloom
 */
public abstract class BodyDTO extends NotDTO implements DTO<Body<?>> {

    private static final MockServerLogger MOCK_SERVER_LOGGER = new MockServerLogger(BodyDTO.class);
    private static final ObjectMapper OBJECT_MAPPER = ObjectMapperFactory.createObjectMapper();
    private final Body.Type type;
    private Boolean optional;

    public BodyDTO(Body.Type type, Boolean not) {
        super(not);
        this.type = type;
    }

    public static BodyDTO createDTO(Body<?> body) {
        BodyDTO result = null;

        if (body instanceof BinaryBody) {
            BinaryBody binaryBody = (BinaryBody) body;
            result = new BinaryBodyDTO(binaryBody, binaryBody.getNot());
        } else if (body instanceof JsonBody) {
            JsonBody jsonBody = (JsonBody) body;
            result = new JsonBodyDTO(jsonBody, jsonBody.getNot());
        } else if (body instanceof JsonSchemaBody) {
            JsonSchemaBody jsonSchemaBody = (JsonSchemaBody) body;
            result = new JsonSchemaBodyDTO(jsonSchemaBody, jsonSchemaBody.getNot());
        } else if (body instanceof JsonPathBody) {
            JsonPathBody jsonPathBody = (JsonPathBody) body;
            result = new JsonPathBodyDTO(jsonPathBody, jsonPathBody.getNot());
        } else if (body instanceof ParameterBody) {
            ParameterBody parameterBody = (ParameterBody) body;
            result = new ParameterBodyDTO(parameterBody, parameterBody.getNot());
        } else if (body instanceof RegexBody) {
            RegexBody regexBody = (RegexBody) body;
            result = new RegexBodyDTO(regexBody, regexBody.getNot());
        } else if (body instanceof StringBody) {
            StringBody stringBody = (StringBody) body;
            result = new StringBodyDTO(stringBody, stringBody.getNot());
        } else if (body instanceof XmlBody) {
            XmlBody xmlBody = (XmlBody) body;
            result = new XmlBodyDTO(xmlBody, xmlBody.getNot());
        } else if (body instanceof XmlSchemaBody) {
            XmlSchemaBody xmlSchemaBody = (XmlSchemaBody) body;
            result = new XmlSchemaBodyDTO(xmlSchemaBody, xmlSchemaBody.getNot());
        } else if (body instanceof XPathBody) {
            XPathBody xPathBody = (XPathBody) body;
            result = new XPathBodyDTO(xPathBody, xPathBody.getNot());
        }

        if (result != null) {
            result.withOptional(body.getOptional());
        }

        return result;
    }

    public static String toString(BodyDTO body) {
        if (body instanceof BinaryBodyDTO) {
            return Base64.encodeBase64String(((BinaryBodyDTO) body).getBase64Bytes());
        } else if (body instanceof JsonBodyDTO) {
            return ((JsonBodyDTO) body).getJson();
        } else if (body instanceof JsonSchemaBodyDTO) {
            return ((JsonSchemaBodyDTO) body).getJson();
        } else if (body instanceof JsonPathBodyDTO) {
            return ((JsonPathBodyDTO) body).getJsonPath();
        } else if (body instanceof ParameterBodyDTO) {
            try {
                return OBJECT_MAPPER.writeValueAsString(((ParameterBodyDTO) body).getParameters().getMultimap().asMap());
            } catch (Throwable throwable) {
                MOCK_SERVER_LOGGER
                    .logEvent(
                        new LogEntry()
                            .setLogLevel(ERROR)
                            .setMessageFormat("serialising parameter body into json string for javascript template " + (isNotBlank(throwable.getMessage()) ? " " + throwable.getMessage() : ""))
                            .setThrowable(throwable)
                    );
                return "";
            }
        } else if (body instanceof RegexBodyDTO) {
            return ((RegexBodyDTO) body).getRegex();
        } else if (body instanceof StringBodyDTO) {
            return ((StringBodyDTO) body).getString();
        } else if (body instanceof XmlBodyDTO) {
            return ((XmlBodyDTO) body).getXml();
        } else if (body instanceof XmlSchemaBodyDTO) {
            return ((XmlSchemaBodyDTO) body).getXml();
        } else if (body instanceof XPathBodyDTO) {
            return ((XPathBodyDTO) body).getXPath();
        }

        return "";
    }

    public Body.Type getType() {
        return type;
    }

    public Boolean getOptional() {
        return optional;
    }

    public BodyDTO withOptional(Boolean optional) {
        this.optional = optional;
        return this;
    }

    public abstract Body<?> buildObject();

}
