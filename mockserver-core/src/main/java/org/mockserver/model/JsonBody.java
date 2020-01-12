package org.mockserver.model;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.mockserver.matchers.MatchType;
import org.mockserver.serialization.ObjectMapperFactory;

import java.nio.charset.Charset;

import static org.mockserver.model.MediaType.DEFAULT_HTTP_CHARACTER_SET;

/**
 * @author jamesdbloom
 */
public class JsonBody extends BodyWithContentType<String> {

    public static final MatchType DEFAULT_MATCH_TYPE = MatchType.ONLY_MATCHING_FIELDS;
    public static final MediaType DEFAULT_CONTENT_TYPE = MediaType.create("application", "json");
    private final String json;
    private final MatchType matchType;
    private final byte[] rawBytes;

    public JsonBody(String json) {
        this(json, null, DEFAULT_CONTENT_TYPE, DEFAULT_MATCH_TYPE);
    }

    public JsonBody(String json, MatchType matchType) {
        this(json, null, DEFAULT_CONTENT_TYPE, matchType);
    }

    public JsonBody(String json, Charset charset, MatchType matchType) {
        this(json, null, (charset != null ? DEFAULT_CONTENT_TYPE.withCharset(charset) : null), matchType);
    }

    public JsonBody(String json, byte[] rawBytes, MediaType contentType, MatchType matchType) {
        super(Type.JSON, contentType);
        this.json = json;
        this.matchType = matchType;

        if (rawBytes == null && json != null) {
            this.rawBytes = json.getBytes(determineCharacterSet(contentType, DEFAULT_HTTP_CHARACTER_SET));
        } else {
            this.rawBytes = rawBytes;
        }
    }

    public static JsonBody json(String json) {
        return new JsonBody(json);
    }

    public static JsonBody json(String json, MatchType matchType) {
        return new JsonBody(json, matchType);
    }

    public static JsonBody json(String json, Charset charset) {
        return new JsonBody(json, null, (charset != null ? DEFAULT_CONTENT_TYPE.withCharset(charset) : null), DEFAULT_MATCH_TYPE);
    }

    public static JsonBody json(String json, Charset charset, MatchType matchType) {
        return new JsonBody(json, null, (charset != null ? DEFAULT_CONTENT_TYPE.withCharset(charset) : null), matchType);
    }

    public static JsonBody json(String json, MediaType contentType) {
        return new JsonBody(json, null, contentType, DEFAULT_MATCH_TYPE);
    }

    public static JsonBody json(String json, MediaType contentType, MatchType matchType) {
        return new JsonBody(json, null, contentType, matchType);
    }

    private static String toJson(Object object) {
        String json;
        try {
            json = ObjectMapperFactory.createObjectMapper().writeValueAsString(object);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("error mapping object for json body to JSON", e);
        }
        return json;
    }

    public static JsonBody json(Object object) {
        return new JsonBody(toJson(object));
    }

    public static JsonBody json(Object object, MatchType matchType) {
        return new JsonBody(toJson(object), matchType);
    }

    public static JsonBody json(Object object, Charset charset) {
        return new JsonBody(toJson(object), null, (charset != null ? DEFAULT_CONTENT_TYPE.withCharset(charset) : null), DEFAULT_MATCH_TYPE);
    }

    public static JsonBody json(Object object, Charset charset, MatchType matchType) {
        return new JsonBody(toJson(object), null, (charset != null ? DEFAULT_CONTENT_TYPE.withCharset(charset) : null), matchType);
    }

    public static JsonBody json(Object object, MediaType contentType) {
        return new JsonBody(toJson(object), null, contentType, DEFAULT_MATCH_TYPE);
    }

    public static JsonBody json(Object object, MediaType contentType, MatchType matchType) {
        return new JsonBody(toJson(object), null, contentType, matchType);
    }

    public String getValue() {
        return json;
    }

    public byte[] getRawBytes() {
        return rawBytes;
    }

    public MatchType getMatchType() {
        return matchType;
    }

    @Override
    public String toString() {
        return json;
    }

}
