package org.mockserver.serialization.deserializers.condition;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import org.mockserver.serialization.model.VerificationTimesDTO;
import org.mockserver.verify.VerificationTimes;

import java.io.IOException;

/**
 * @author jamesdbloom
 */
public class VerificationTimesDTODeserializer extends StdDeserializer<VerificationTimesDTO> {

    public VerificationTimesDTODeserializer() {
        super(VerificationTimesDTO.class);
    }

    @Override
    public VerificationTimesDTO deserialize(JsonParser jsonParser, DeserializationContext ctxt) throws IOException {
        VerificationTimesDTO verificationTimesDTO = null;

        Integer count = null;
        Boolean exact = null;
        Integer atLeast = null;
        Integer atMost = null;

        while (jsonParser.nextToken() != JsonToken.END_OBJECT) {
            String fieldName = jsonParser.getCurrentName();
            if ("count".equals(fieldName)) {
                jsonParser.nextToken();
                count = jsonParser.getIntValue();
            } else if ("exact".equals(fieldName)) {
                jsonParser.nextToken();
                exact = jsonParser.getBooleanValue();
            } else if ("atLeast".equals(fieldName)) {
                jsonParser.nextToken();
                atLeast = jsonParser.getIntValue();
            } else if ("atMost".equals(fieldName)) {
                jsonParser.nextToken();
                atMost = jsonParser.getIntValue();
            }

            if (atLeast != null || atMost != null) {
                verificationTimesDTO = new VerificationTimesDTO(VerificationTimes.between(atLeast != null ? atLeast : -1, atMost != null ? atMost : -1));
            } else if (count != null) {
                if (exact != null && exact) {
                    verificationTimesDTO = new VerificationTimesDTO(VerificationTimes.exactly(count));
                } else {
                    verificationTimesDTO = new VerificationTimesDTO(VerificationTimes.atLeast(count));
                }
            }
        }

        return verificationTimesDTO;
    }
}
