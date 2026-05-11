package org.mockserver.serialization.model;

import org.junit.Test;
import org.mockserver.model.Delay;
import org.mockserver.model.GrpcStreamMessage;
import org.mockserver.model.GrpcStreamResponse;
import org.mockserver.serialization.ObjectMapperFactory;

import java.util.concurrent.TimeUnit;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.core.IsNull.notNullValue;
import static org.hamcrest.core.IsNull.nullValue;

public class GrpcStreamResponseDTOTest {

    @Test
    public void shouldBuildObjectFromDTO() {
        GrpcStreamResponse original = GrpcStreamResponse.grpcStreamResponse()
            .withStatusName("OK")
            .withStatusMessage("success")
            .withMessage("{\"greeting\": \"hello\"}")
            .withMessage(GrpcStreamMessage.grpcStreamMessage("{\"greeting\": \"world\"}")
                .withDelay(new Delay(TimeUnit.MILLISECONDS, 100)))
            .withCloseConnection(false);

        GrpcStreamResponseDTO dto = new GrpcStreamResponseDTO(original);
        GrpcStreamResponse rebuilt = dto.buildObject();

        assertThat(rebuilt.getStatusName(), is("OK"));
        assertThat(rebuilt.getStatusMessage(), is("success"));
        assertThat(rebuilt.getMessages().size(), is(2));
        assertThat(rebuilt.getMessages().get(0).getJson(), is("{\"greeting\": \"hello\"}"));
        assertThat(rebuilt.getMessages().get(1).getJson(), is("{\"greeting\": \"world\"}"));
        assertThat(rebuilt.getMessages().get(1).getDelay(), notNullValue());
        assertThat(rebuilt.getMessages().get(1).getDelay().getTimeUnit(), is(TimeUnit.MILLISECONDS));
        assertThat(rebuilt.getMessages().get(1).getDelay().getValue(), is(100L));
        assertThat(rebuilt.getCloseConnection(), is(false));
    }

    @Test
    public void shouldHandleNullInput() {
        GrpcStreamResponseDTO dto = new GrpcStreamResponseDTO(null);
        GrpcStreamResponse rebuilt = dto.buildObject();

        assertThat(rebuilt.getStatusName(), nullValue());
        assertThat(rebuilt.getMessages(), nullValue());
    }

    @Test
    public void shouldRoundTripThroughJson() throws Exception {
        GrpcStreamResponse original = GrpcStreamResponse.grpcStreamResponse()
            .withStatusName("NOT_FOUND")
            .withStatusMessage("resource not found")
            .withMessage("{\"error\": \"not found\"}")
            .withCloseConnection(true);

        GrpcStreamResponseDTO dto = new GrpcStreamResponseDTO(original);
        String json = ObjectMapperFactory.createObjectMapper().writeValueAsString(dto);
        GrpcStreamResponseDTO deserialized = ObjectMapperFactory.createObjectMapper().readValue(json, GrpcStreamResponseDTO.class);
        GrpcStreamResponse rebuilt = deserialized.buildObject();

        assertThat(rebuilt.getStatusName(), is("NOT_FOUND"));
        assertThat(rebuilt.getStatusMessage(), is("resource not found"));
        assertThat(rebuilt.getMessages().size(), is(1));
        assertThat(rebuilt.getCloseConnection(), is(true));
    }

    @Test
    public void shouldRoundTripEmptyMessages() throws Exception {
        GrpcStreamResponse original = GrpcStreamResponse.grpcStreamResponse()
            .withStatusName("OK");

        GrpcStreamResponseDTO dto = new GrpcStreamResponseDTO(original);
        String json = ObjectMapperFactory.createObjectMapper().writeValueAsString(dto);
        GrpcStreamResponseDTO deserialized = ObjectMapperFactory.createObjectMapper().readValue(json, GrpcStreamResponseDTO.class);
        GrpcStreamResponse rebuilt = deserialized.buildObject();

        assertThat(rebuilt.getStatusName(), is("OK"));
        assertThat(rebuilt.getMessages(), nullValue());
    }
}
