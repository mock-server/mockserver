package org.mockserver.serialization.deserializers.collections;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import org.mockserver.model.KeyMatchStyle;
import org.mockserver.model.KeysToMultiValues;
import org.mockserver.model.NottableString;
import org.mockserver.model.ParameterStyle;

import java.io.IOException;

import static org.mockserver.model.NottableString.string;

/**
 * @author jamesdbloom
 */
public abstract class KeysToMultiValuesDeserializer<T extends KeysToMultiValues<?, ?>> extends StdDeserializer<T> {

    KeysToMultiValuesDeserializer(Class<T> valueClass) {
        super(valueClass);
    }

    public abstract T build();

    @Override
    public T deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
        if (p.isExpectedStartArrayToken()) {
            return deserializeArray(p, ctxt);
        } else if (p.isExpectedStartObjectToken()) {
            return deserializeObject(p, ctxt);
        } else {
            return null;
        }
    }

    private T deserializeObject(JsonParser jsonParser, DeserializationContext ctxt) throws IOException {
        T entries = build();
        NottableString key = string("");
        while (true) {
            JsonToken token = jsonParser.nextToken();
            switch (token) {
                case FIELD_NAME:
                    key = string(jsonParser.getText());
                    if ("keyMatchStyle".equals(key.getValue())) {
                        jsonParser.nextToken();
                        entries.withKeyMatchStyle(ctxt.readValue(jsonParser, KeyMatchStyle.class));
                    }
                    break;
                case START_OBJECT:
                    // parse parameterStyle and value
                    jsonParser.nextToken();
                    ParameterStyle parameterStyle = ParameterStyle.FORM_EXPLODED;
                    NottableString[] values = null;
                    while (token != JsonToken.END_OBJECT) {
                        String fieldName = jsonParser.getCurrentName();
                        if ("values".equals(fieldName)) {
                            jsonParser.nextToken();
                            values = ctxt.readValue(jsonParser, NottableString[].class);
                        } else if ("parameterStyle".equals(fieldName)) {
                            jsonParser.nextToken();
                            parameterStyle = ctxt.readValue(jsonParser, ParameterStyle.class);
                        }
                        token = jsonParser.nextToken();
                    }
                    entries.withEntry(key.withStyle(parameterStyle), values);
                    break;
                case START_ARRAY:
                    entries.withEntry(key, ctxt.readValue(jsonParser, NottableString[].class));
                    break;
                case VALUE_STRING:
                    entries.withEntry(key, ctxt.readValue(jsonParser, NottableString.class));
                    break;
                case END_OBJECT:
                    return entries;
                default:
                    throw new RuntimeException("Unexpected token: \"" + token + "\" id: \"" + token.id() + "\" text: \"" + jsonParser.getText());
            }
        }
    }

    private T deserializeArray(JsonParser jsonParser, DeserializationContext ctxt) throws IOException {
        T entries = build();
        NottableString key = string("");
        NottableString[] values = null;
        while (true) {
            JsonToken token = jsonParser.nextToken();
            switch (token) {
                case START_ARRAY:
                    values = ctxt.readValue(jsonParser, NottableString[].class);
                    break;
                case END_ARRAY:
                    return entries;
                case START_OBJECT:
                    if (key != null) {
                        key = null;
                    } else {
                        key = ctxt.readValue(jsonParser, NottableString.class);
                    }
                    values = null;
                    break;
                case END_OBJECT:
                    entries.withEntry(key, values);
                    break;
                case FIELD_NAME:
                    break;
                case VALUE_STRING:
                    key = ctxt.readValue(jsonParser, NottableString.class);
                    break;
                default:
                    throw new RuntimeException("Unexpected token: \"" + token + "\" id: \"" + token.id() + "\" text: \"" + jsonParser.getText());
            }
        }
    }
}
