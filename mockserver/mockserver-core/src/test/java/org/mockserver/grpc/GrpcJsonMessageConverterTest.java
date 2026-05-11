package org.mockserver.grpc;

import com.google.protobuf.Descriptors;
import org.junit.Before;
import org.junit.Test;
import org.mockserver.logging.MockServerLogger;

import java.nio.file.Paths;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

public class GrpcJsonMessageConverterTest {

    private GrpcProtoDescriptorStore store;
    private GrpcJsonMessageConverter converter;
    private Descriptors.MethodDescriptor greetingMethod;

    @Before
    public void setUp() {
        store = new GrpcProtoDescriptorStore(new MockServerLogger());
        store.loadDescriptorSetFromPath(Paths.get("src/test/resources/grpc/greeting.dsc"));
        converter = store.getConverter();
        greetingMethod = store.getMethod("com.example.grpc.GreetingService", "Greeting");
    }

    @Test
    public void shouldConvertProtobufToJson() {
        byte[] protobuf = converter.toProtobuf("{\"name\": \"Tom\"}", greetingMethod.getInputType());
        String json = converter.toJson(protobuf, greetingMethod.getInputType());

        assertThat(json, containsString("\"name\""));
        assertThat(json, containsString("Tom"));
    }

    @Test
    public void shouldConvertJsonToProtobuf() {
        byte[] protobuf = converter.toProtobuf("{\"name\": \"Tom\"}", greetingMethod.getInputType());

        assertThat(protobuf, is(notNullValue()));
        assertThat(protobuf.length, is(greaterThan(0)));
    }

    @Test
    public void shouldRoundTripConversion() {
        String originalJson = "{\"name\":\"Alice\"}";
        byte[] protobuf = converter.toProtobuf(originalJson, greetingMethod.getInputType());
        String resultJson = converter.toJson(protobuf, greetingMethod.getInputType());

        assertThat(resultJson, containsString("Alice"));
    }

    @Test
    public void shouldHandleResponseMessageType() {
        String json = "{\"greeting\":\"Hello World\"}";
        byte[] protobuf = converter.toProtobuf(json, greetingMethod.getOutputType());
        String resultJson = converter.toJson(protobuf, greetingMethod.getOutputType());

        assertThat(resultJson, containsString("Hello World"));
    }

    @Test
    public void shouldHandleEmptyMessage() {
        String json = "{}";
        byte[] protobuf = converter.toProtobuf(json, greetingMethod.getInputType());
        String resultJson = converter.toJson(protobuf, greetingMethod.getInputType());

        assertThat(resultJson, is(notNullValue()));
    }

    @Test(expected = GrpcException.class)
    public void shouldThrowOnInvalidJson() {
        converter.toProtobuf("not valid json", greetingMethod.getInputType());
    }
}
