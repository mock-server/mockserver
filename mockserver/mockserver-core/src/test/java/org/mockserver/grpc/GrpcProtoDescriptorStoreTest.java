package org.mockserver.grpc;

import com.google.protobuf.Descriptors;
import org.junit.Before;
import org.junit.Test;
import org.mockserver.logging.MockServerLogger;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

public class GrpcProtoDescriptorStoreTest {

    private GrpcProtoDescriptorStore store;

    @Before
    public void setUp() {
        store = new GrpcProtoDescriptorStore(new MockServerLogger());
    }

    @Test
    public void shouldLoadDescriptorFromPath() {
        Path descriptorPath = Paths.get("src/test/resources/grpc/greeting.dsc");
        store.loadDescriptorSetFromPath(descriptorPath);

        assertThat(store.hasServices(), is(true));
        assertThat(store.getService("com.example.grpc.GreetingService"), is(notNullValue()));
    }

    @Test
    public void shouldResolveServiceMethods() {
        Path descriptorPath = Paths.get("src/test/resources/grpc/greeting.dsc");
        store.loadDescriptorSetFromPath(descriptorPath);

        Descriptors.MethodDescriptor greeting = store.getMethod("com.example.grpc.GreetingService", "Greeting");
        assertThat(greeting, is(notNullValue()));
        assertThat(greeting.getName(), is("Greeting"));
        assertThat(greeting.getInputType().getName(), is("HelloRequest"));
        assertThat(greeting.getOutputType().getName(), is("HelloResponse"));
        assertThat(greeting.isClientStreaming(), is(false));
        assertThat(greeting.isServerStreaming(), is(false));
    }

    @Test
    public void shouldResolveStreamingMethods() {
        Path descriptorPath = Paths.get("src/test/resources/grpc/greeting.dsc");
        store.loadDescriptorSetFromPath(descriptorPath);

        Descriptors.MethodDescriptor serverStreaming = store.getMethod("com.example.grpc.GreetingService", "ListGreetings");
        assertThat(serverStreaming, is(notNullValue()));
        assertThat(serverStreaming.isServerStreaming(), is(true));
        assertThat(serverStreaming.isClientStreaming(), is(false));

        Descriptors.MethodDescriptor clientStreaming = store.getMethod("com.example.grpc.GreetingService", "CollectGreetings");
        assertThat(clientStreaming, is(notNullValue()));
        assertThat(clientStreaming.isClientStreaming(), is(true));
        assertThat(clientStreaming.isServerStreaming(), is(false));

        Descriptors.MethodDescriptor bidi = store.getMethod("com.example.grpc.GreetingService", "Chat");
        assertThat(bidi, is(notNullValue()));
        assertThat(bidi.isClientStreaming(), is(true));
        assertThat(bidi.isServerStreaming(), is(true));
    }

    @Test
    public void shouldListAllServices() {
        Path descriptorPath = Paths.get("src/test/resources/grpc/greeting.dsc");
        store.loadDescriptorSetFromPath(descriptorPath);

        Map<String, Descriptors.ServiceDescriptor> allServices = store.getAllServices();
        assertThat(allServices.size(), is(1));
        assertThat(allServices.containsKey("com.example.grpc.GreetingService"), is(true));
    }

    @Test
    public void shouldReturnNullForUnknownService() {
        assertThat(store.getService("com.example.Unknown"), is(nullValue()));
        assertThat(store.getMethod("com.example.Unknown", "foo"), is(nullValue()));
    }

    @Test
    public void shouldReset() {
        Path descriptorPath = Paths.get("src/test/resources/grpc/greeting.dsc");
        store.loadDescriptorSetFromPath(descriptorPath);
        assertThat(store.hasServices(), is(true));

        store.reset();
        assertThat(store.hasServices(), is(false));
    }

    @Test
    public void shouldProvideJsonConverter() {
        Path descriptorPath = Paths.get("src/test/resources/grpc/greeting.dsc");
        store.loadDescriptorSetFromPath(descriptorPath);

        GrpcJsonMessageConverter converter = store.getConverter();
        assertThat(converter, is(notNullValue()));
    }

    @Test
    public void shouldLoadDescriptorDirectory() {
        Path directory = Paths.get("src/test/resources/grpc");
        store.loadDescriptorDirectory(directory);

        assertThat(store.hasServices(), is(true));
        assertThat(store.getService("com.example.grpc.GreetingService"), is(notNullValue()));
    }

    @Test
    public void shouldHandleNullDirectory() {
        store.loadDescriptorDirectory(null);
        assertThat(store.hasServices(), is(false));
    }
}
