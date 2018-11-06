package org.mockserver.serialization.deserializers.collections;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.core.JsonTokenId;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import org.mockserver.model.Cookies;
import org.mockserver.model.NottableString;

import java.io.IOException;

import static org.mockserver.model.NottableString.string;

/**
 * @author jamesdbloom
 */
public class CookiesDeserializer extends StdDeserializer<Cookies> {

    public CookiesDeserializer() {
        super(Cookies.class);
    }

    @Override
    public Cookies deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
        if (p.isExpectedStartArrayToken()) {
            return deserializeArray(p, ctxt, ctxt.getNodeFactory());
        } else if (p.isExpectedStartObjectToken()) {
            return deserializeObject(p, ctxt, ctxt.getNodeFactory());
        } else {
            return (Cookies) ctxt.handleUnexpectedToken(Cookies.class, p);
        }
    }

    private Cookies deserializeObject(JsonParser jsonParser, DeserializationContext ctxt, JsonNodeFactory nodeFactory) throws IOException {
        Cookies cookies = new Cookies();
        NottableString key = string("");
        while (true) {
            JsonToken t = jsonParser.nextToken();
            switch (t.id()) {
                case JsonTokenId.ID_FIELD_NAME:
                    key = string(jsonParser.getText());
                    break;
                case JsonTokenId.ID_STRING:
                    cookies.withEntry(key, string(ctxt.readValue(jsonParser, String.class)));
                    break;
                case JsonTokenId.ID_END_OBJECT:
                    return cookies;
                default:
                    throw new RuntimeException("Unexpected token: \"" + t + "\" id: \"" + t.id() + "\" text: \"" + jsonParser.getText());
            }
        }
    }

    private Cookies deserializeArray(JsonParser jsonParser, DeserializationContext ctxt, JsonNodeFactory nodeFactory) throws IOException {
        Cookies headers = new Cookies();
        NottableString key = null;
        NottableString value = null;
        String fieldName = null;
        while (true) {
            JsonToken t = jsonParser.nextToken();
            switch (t.id()) {
                case JsonTokenId.ID_END_ARRAY:
                    return headers;
                case JsonTokenId.ID_START_OBJECT:
                    key = null;
                    value = null;
                    break;
                case JsonTokenId.ID_FIELD_NAME:
                    fieldName = jsonParser.getText();
                    break;
                case JsonTokenId.ID_STRING:
                    if ("name".equals(fieldName)) {
                        key = string(ctxt.readValue(jsonParser, String.class));
                    } else if ("value".equals(fieldName)) {
                        value = string(ctxt.readValue(jsonParser, String.class));
                    }
                    break;
                case JsonTokenId.ID_END_OBJECT:
                    headers.withEntry(key, value);
                    break;
                default:
                    throw new RuntimeException("Unexpected token: \"" + t + "\" id: \"" + t.id() + "\" text: \"" + jsonParser.getText());
            }
        }
    }
}
