package org.mockserver.model;

import com.google.common.base.Joiner;
import com.google.common.base.Splitter;
import org.apache.commons.lang3.StringUtils;
import org.mockserver.log.model.LogEntry;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.serialization.ObjectMapperFactory;

import java.nio.charset.Charset;
import java.util.Map;
import java.util.TreeMap;

import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static org.slf4j.event.Level.TRACE;

@SuppressWarnings("unused")
public class MediaType extends ObjectWithJsonToString {

    private static final MockServerLogger MOCK_SERVER_LOGGER = new MockServerLogger(ObjectMapperFactory.class);
    private static final char TYPE_SEPARATOR = '/';
    private static final char PARAMETER_START = ';';
    private String type;
    private String subtype;
    private Map<String, String> parameters;
    private Charset charset;

    private static final String CHARSET_PARAMETER = "charset";
    private static final String MEDIA_TYPE_WILDCARD = "*";
    public final static MediaType WILDCARD = new MediaType(MEDIA_TYPE_WILDCARD, MEDIA_TYPE_WILDCARD);
    public final static MediaType APPLICATION_ATOM_XML = new MediaType("application", "atom+xml");
    public final static MediaType APPLICATION_XHTML_XML = new MediaType("application", "xhtml+xml");
    public final static MediaType APPLICATION_SVG_XML = new MediaType("application", "svg+xml");
    public final static MediaType APPLICATION_XML_UTF_8 = new MediaType("application", "xml", "utf-8", null);
    public final static MediaType XML_UTF_8 = new MediaType("text", "xml", "utf-8", null);
    public final static MediaType APPLICATION_JSON = new MediaType("application", "json");
    public final static MediaType JSON_UTF_8 = new MediaType("application", "json", "utf-8", null);
    public final static MediaType APPLICATION_FORM_URLENCODED = new MediaType("application", "x-www-form-urlencoded");
    public final static MediaType FORM_DATA = new MediaType("application", "x-www-form-urlencoded");
    public final static MediaType MULTIPART_FORM_DATA = new MediaType("multipart", "form-data");
    public final static MediaType APPLICATION_OCTET_STREAM = new MediaType("application", "octet-stream");
    public static final MediaType APPLICATION_BINARY = new MediaType("application", "binary");
    public static final MediaType PDF = new MediaType("application", "pdf");
    public static final MediaType ATOM_UTF_8 = new MediaType("application", "atom+xml", "utf-8", null);
    public final static MediaType TEXT_PLAIN = new MediaType("text", "plain");
    public final static MediaType PLAIN_TEXT_UTF_8 = new MediaType("text", "plain", "utf-8", null);
    public final static MediaType TEXT_XML = new MediaType("text", "xml");
    public final static MediaType TEXT_HTML = new MediaType("text", "html");
    public final static MediaType HTML_UTF_8 = new MediaType("text", "html", "utf-8", null);
    public static final MediaType SERVER_SENT_EVENTS = new MediaType("text", "event-stream");
    public static final MediaType APPLICATION_JSON_PATCH_JSON = new MediaType("application", "json-patch+json");
    public static final MediaType ANY_VIDEO_TYPE = new MediaType("video", MEDIA_TYPE_WILDCARD);
    public static final MediaType ANY_AUDIO_TYPE = new MediaType("audio", MEDIA_TYPE_WILDCARD);
    public static final MediaType ANY_IMAGE_TYPE = new MediaType("image", MEDIA_TYPE_WILDCARD);
    public static final MediaType QUICKTIME = new MediaType("video", "quicktime");
    public static final MediaType JPEG = new MediaType("image", "jpeg");
    public static final MediaType PNG = new MediaType("image", "png");

    @SuppressWarnings("UnstableApiUsage")
    public static MediaType parse(String mediaTypeHeader) {
        if (isNotBlank(mediaTypeHeader)) {
            int typeSeparator = mediaTypeHeader.indexOf(TYPE_SEPARATOR);
            int typeEndIndex = 0;
            String type = null;
            String subType = null;
            if (typeSeparator != -1) {
                typeEndIndex = mediaTypeHeader.indexOf(PARAMETER_START);
                if (typeEndIndex == -1) {
                    typeEndIndex = mediaTypeHeader.length();
                }
                String typeString = mediaTypeHeader.substring(0, typeEndIndex).trim();
                type = StringUtils.substringBefore(typeString, "/").trim().toLowerCase();
                subType = StringUtils.substringAfter(typeString, "/").trim().toLowerCase();
                if (typeEndIndex < mediaTypeHeader.length()) {
                    typeEndIndex++;
                }
            }
            String parameters = mediaTypeHeader.substring(typeEndIndex).trim().toLowerCase().replaceAll("\"", "");
            Map<String, String> parameterMap = null;
            if (isNotBlank(parameters)) {
                parameterMap = Splitter.on(';').withKeyValueSeparator('=').split(parameters);
            }
            return new MediaType(type, subType, parameterMap);
        } else {
            return new MediaType(null, null);
        }
    }

    private static TreeMap<String, String> createParametersMap(Map<String, String> initialValues) {
        final TreeMap<String, String> map = new TreeMap<>(String::compareToIgnoreCase);
        if (initialValues != null) {
            for (Map.Entry<String, String> entry : initialValues.entrySet()) {
                map.put(entry.getKey().toLowerCase().trim(), entry.getValue().trim());
            }
        }
        return map;
    }

    public MediaType(String type, String subtype) {
        this(type, subtype, null, null);
    }

    public MediaType(String type, String subtype, Map<String, String> parameters) {
        this(type, subtype, null, parameters);
        if (charset == null) {
            try {
                if (parameters != null && parameters.containsKey(CHARSET_PARAMETER)) {
                    charset = Charset.forName(parameters.get(CHARSET_PARAMETER));
                }
            } catch (Throwable throwable) {
                MOCK_SERVER_LOGGER.logEvent(
                    new LogEntry()
                        .setType(LogEntry.LogMessageType.TRACE)
                        .setLogLevel(TRACE)
                        .setMessageFormat("Ignoring unsupported charset with value \"" + charset + "\"")
                        .setThrowable(throwable)
                );
            }
        }
    }

    private MediaType(String type, String subtype, String charset, Map<String, String> parameterMap) {
        this.type = isBlank(type) ? null : type;
        this.subtype = isBlank(subtype) ? null : subtype;
        this.parameters = new TreeMap<>(String::compareToIgnoreCase);
        if (parameterMap != null) {
            for (Map.Entry<String, String> entry : parameterMap.entrySet()) {
                this.parameters.put(entry.getKey().toLowerCase(), entry.getValue());
            }
        }
        if (isNotBlank(charset)) {
            this.parameters.put(CHARSET_PARAMETER, charset);
            try {
                this.charset = Charset.forName(charset);
            } catch (Throwable throwable) {
                MOCK_SERVER_LOGGER.logEvent(
                    new LogEntry()
                        .setType(LogEntry.LogMessageType.TRACE)
                        .setLogLevel(TRACE)
                        .setMessageFormat("Ignoring unsupported charset with value \"" + charset + "\"")
                        .setThrowable(throwable)
                );
            }
        }
    }

    public static MediaType create(String type, String subType) {
        return new MediaType(type, subType);
    }

    public String getType() {
        return this.type;
    }

    public String getSubtype() {
        return this.subtype;
    }

    public Map<String, String> getParameters() {
        return parameters;
    }

    public MediaType withCharset(Charset charset) {
        return withCharset(charset.name());
    }

    public MediaType withCharset(String charset) {
        return new MediaType(this.type, this.subtype, charset.toLowerCase(), this.parameters);
    }

    public Charset getCharset() {
        return charset;
    }

    public boolean isCompatible(MediaType other) {
        // return false if other is null, else
        return other != null &&
            // both are wildcard types, or
            (type == null || other.type == null ||
                type.equals(MEDIA_TYPE_WILDCARD) || other.type.equals(MEDIA_TYPE_WILDCARD) ||
                // same types, wildcard sub-types, or
                (type.equalsIgnoreCase(other.type) && (subtype == null || other.subtype == null)) ||
                (type.equalsIgnoreCase(other.type) && (subtype.equals(MEDIA_TYPE_WILDCARD) || other.subtype.equals(MEDIA_TYPE_WILDCARD))) ||
                // same types & sub-types
                (type.equalsIgnoreCase(other.type) && this.subtype.equalsIgnoreCase(other.subtype)));
    }

    public boolean isJson() {
        return type != null && type.equalsIgnoreCase("json") || subtype != null && subtype.equalsIgnoreCase("json");
    }

    @Override
    public String toString() {
        StringBuilder stringBuilder = new StringBuilder();
        if (type != null && subtype != null) {
            stringBuilder.append(type).append(TYPE_SEPARATOR).append(subtype);
        }
        if (!parameters.isEmpty()) {
            if (stringBuilder.length() > 0) {
                stringBuilder.append(PARAMETER_START).append(' ');
            }
            stringBuilder.append(Joiner.on("; ").withKeyValueSeparator("=").join(parameters));
        }
        return stringBuilder.toString();
    }
}
