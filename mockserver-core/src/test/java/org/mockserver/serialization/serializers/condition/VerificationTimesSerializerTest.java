package org.mockserver.serialization.serializers.condition;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.junit.Test;
import org.mockserver.serialization.ObjectMapperFactory;

import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertThat;
import static org.mockserver.verify.VerificationTimes.*;

/**
 * @author jamesdbloom
 */
public class VerificationTimesSerializerTest {

    @Test
    public void shouldSerializeBetween() throws JsonProcessingException {
        assertThat(ObjectMapperFactory
                .createObjectMapper()
                .writerWithDefaultPrettyPrinter()
                .writeValueAsString(
                    between(1, 2)
                ),
            is("{\n" +
                "  \"atLeast\" : 1,\n" +
                "  \"atMost\" : 2\n" +
                "}"));
    }

    @Test
    public void shouldSerializeOnce() throws JsonProcessingException {
        assertThat(ObjectMapperFactory
                .createObjectMapper()
                .writerWithDefaultPrettyPrinter()
                .writeValueAsString(
                    once()
                ),
            is("{\n" +
                "  \"atLeast\" : 1,\n" +
                "  \"atMost\" : 1\n" +
                "}"));
    }

    @Test
    public void shouldSerializeExact() throws JsonProcessingException {
        assertThat(ObjectMapperFactory
                .createObjectMapper()
                .writerWithDefaultPrettyPrinter()
                .writeValueAsString(
                    exactly(2)
                ),
            is("{\n" +
                "  \"atLeast\" : 2,\n" +
                "  \"atMost\" : 2\n" +
                "}"));
    }

    @Test
    public void shouldSerializeAtLeast() throws JsonProcessingException {
        assertThat(ObjectMapperFactory
                .createObjectMapper()
                .writerWithDefaultPrettyPrinter()
                .writeValueAsString(
                    atLeast(2)
                ),
            is("{\n" +
                "  \"atLeast\" : 2\n" +
                "}"));
    }

    @Test
    public void shouldSerializeAtMost() throws JsonProcessingException {
        assertThat(ObjectMapperFactory
                .createObjectMapper()
                .writerWithDefaultPrettyPrinter()
                .writeValueAsString(
                    atMost(2)
                ),
            is("{\n" +
                "  \"atMost\" : 2\n" +
                "}"));
    }
}