package org.mockserver.serialization.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import org.mockserver.model.*;

/**
 * @author jamesdbloom
 */
public abstract class BodyWithContentTypeDTO extends BodyDTO {

    protected final String contentType;

    public BodyWithContentTypeDTO(Body.Type type, Boolean not, Body<?> body) {
        super(type, not);
        this.contentType = body.getContentType();
        withOptional(body.getOptional());
    }

    public static BodyWithContentTypeDTO createWithContentTypeDTO(BodyWithContentType<?> body) {
        BodyWithContentTypeDTO result = null;

        if (body instanceof BinaryBody) {
            BinaryBody binaryBody = (BinaryBody) body;
            result = new BinaryBodyDTO(binaryBody, binaryBody.getNot());
        } else if (body instanceof JsonBody) {
            JsonBody jsonBody = (JsonBody) body;
            result = new JsonBodyDTO(jsonBody, jsonBody.getNot());
        } else if (body instanceof StringBody) {
            StringBody stringBody = (StringBody) body;
            result = new StringBodyDTO(stringBody, stringBody.getNot());
        } else if (body instanceof XmlBody) {
            XmlBody xmlBody = (XmlBody) body;
            result = new XmlBodyDTO(xmlBody, xmlBody.getNot());
        } else if (body instanceof LogEventBody) {
            LogEventBody logEventBody = (LogEventBody) body;
            result = new LogEventBodyDTO(logEventBody);
        }

        if (result != null) {
            result.withOptional(body.getOptional());
        }

        return result;
    }

    public String getContentType() {
        return contentType;
    }

    @JsonIgnore
    MediaType getMediaType() {
        return contentType != null ? MediaType.parse(contentType) : null;
    }

    public abstract BodyWithContentType<?> buildObject();

}
