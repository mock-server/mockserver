package org.mockserver.templates.engine.model;

import org.mockserver.codec.MultipartFormDataDecoder.DecodedPart;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * One part of a {@code multipart/form-data} request body, as a template sees it through {@code request.parts}.
 * <p>
 * {@code headers} holds the part's {@code Content-Disposition} and, when the part declared one, its
 * {@code Content-Type}; other part headers are not retained by the decoder. {@code body} and {@code bodyAsBase64} are
 * built only when read, so reading a part's name, filename or headers never copies its content.
 */
public class MultipartPartTemplateObject {

    private final DecodedPart part;
    private Map<String, List<String>> headers;
    private String body;
    private String bodyAsBase64;

    MultipartPartTemplateObject(DecodedPart part) {
        this.part = part;
    }

    public String getName() {
        return part.getName();
    }

    public String getFilename() {
        return part.getFilename();
    }

    public String getContentType() {
        return part.getContentType();
    }

    public Map<String, List<String>> getHeaders() {
        if (headers == null) {
            Map<String, List<String>> partHeaders = new CaseInsensitiveHeaderMap();
            StringBuilder contentDisposition = new StringBuilder("form-data; name=").append(quoted(part.getName()));
            if (part.getFilename() != null) {
                contentDisposition.append("; filename=").append(quoted(part.getFilename()));
            }
            partHeaders.put("Content-Disposition", Collections.singletonList(contentDisposition.toString()));
            if (part.getContentType() != null) {
                partHeaders.put("Content-Type", Collections.singletonList(part.getContentType()));
            }
            headers = partHeaders;
        }
        return headers;
    }

    public String getBody() {
        if (body == null) {
            Charset charset = part.getCharset() != null ? part.getCharset() : StandardCharsets.UTF_8;
            body = new String(part.getBytes(), charset);
        }
        return body;
    }

    public String getBodyAsBase64() {
        if (bodyAsBase64 == null) {
            bodyAsBase64 = Base64.getEncoder().encodeToString(part.getBytes());
        }
        return bodyAsBase64;
    }

    /**
     * Every field except {@code body} and {@code bodyAsBase64}, for the JavaScript engine, which adds those two as
     * getters that fetch them only when read.
     */
    Map<String, Object> describeWithoutBody() {
        Map<String, Object> description = new LinkedHashMap<>();
        description.put("name", getName());
        if (getFilename() != null) {
            description.put("filename", getFilename());
        }
        if (getContentType() != null) {
            description.put("contentType", getContentType());
        }
        description.put("headers", getHeaders());
        return description;
    }

    private static String quoted(String value) {
        return '"' + String.valueOf(value).replace("\\", "\\\\").replace("\"", "\\\"") + '"';
    }

    @Override
    public String toString() {
        return getBody();
    }
}
