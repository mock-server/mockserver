package org.mockserver.client;

import org.junit.Test;
import org.mockserver.configuration.ClientConfiguration;
import org.mockserver.configuration.Configuration;
import org.mockserver.model.HttpRequest;
import org.mockserver.proxyconfiguration.ProxyConfiguration;

import java.lang.reflect.Field;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

import static org.hamcrest.CoreMatchers.*;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertThrows;
import static org.mockserver.configuration.ClientConfiguration.clientConfiguration;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.proxyconfiguration.ProxyConfiguration.proxyConfiguration;

/**
 * Verifies that {@link MockServerClient#builder()} produces clients equivalent to those produced
 * by the equivalent constructors and {@code with...} setters, that defaults are correct, and that
 * misconfiguration is loud in a way consistent with the constructors.
 *
 * @author jamesdbloom
 */
public class MockServerClientBuilderTest {

    @SuppressWarnings("unchecked")
    private static <T> T field(MockServerClient client, String name) {
        try {
            Field field = MockServerClient.class.getDeclaredField(name);
            field.setAccessible(true);
            return (T) field.get(client);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    private static void assertSameField(MockServerClient expected, MockServerClient actual, String name) {
        Object expectedValue = field(expected, name);
        Object actualValue = field(actual, name);
        assertThat("field '" + name + "'", actualValue, is(expectedValue));
    }

    private static void assertEquivalent(MockServerClient expected, MockServerClient actual) {
        assertSameField(expected, actual, "host");
        assertSameField(expected, actual, "port");
        assertSameField(expected, actual, "contextPath");
        assertSameField(expected, actual, "secure");
        assertSameField(expected, actual, "proxyConfiguration");
        assertSameField(expected, actual, "requestOverride");
        // configuration is never null after construction
        assertThat(field(actual, "configuration"), is(notNullValue()));
        assertThat(field(expected, "configuration"), is(notNullValue()));
    }

    // ---------- defaults ----------

    @Test
    public void shouldUseSensibleDefaults() {
        // when
        MockServerClient client = MockServerClient.builder().build();

        // then
        assertThat(field(client, "host"), is("localhost"));
        assertThat(field(client, "port"), is(1080));
        assertThat(field(client, "contextPath"), is(""));
        assertThat(field(client, "secure"), is(nullValue()));
        assertThat(client.isSecure(), is(false));
        assertThat(field(client, "configuration"), is(notNullValue()));
        assertThat(field(client, "proxyConfiguration"), is(nullValue()));
        assertThat(field(client, "controlPlaneJWTSupplier"), is(nullValue()));
        assertThat(field(client, "requestOverride"), is(nullValue()));
    }

    // ---------- equivalence to each constructor overload ----------

    @Test
    public void shouldEquateToHostPortConstructor() {
        assertEquivalent(
            new MockServerClient("example.com", 1090),
            MockServerClient.builder().host("example.com").port(1090).build()
        );
    }

    @Test
    public void shouldEquateToHostPortContextPathConstructor() {
        assertEquivalent(
            new MockServerClient("example.com", 1090, "/mockserver"),
            MockServerClient.builder().host("example.com").port(1090).contextPath("/mockserver").build()
        );
    }

    @Test
    public void shouldEquateToConfigurationHostPortConstructor() {
        Configuration configuration = Configuration.configuration();
        assertEquivalent(
            new MockServerClient(configuration, "example.com", 1090),
            MockServerClient.builder().configuration(configuration).host("example.com").port(1090).build()
        );
    }

    @Test
    public void shouldEquateToConfigurationHostPortContextPathConstructor() {
        Configuration configuration = Configuration.configuration();
        assertEquivalent(
            new MockServerClient(configuration, "example.com", 1090, "/mockserver"),
            MockServerClient.builder().configuration(configuration).host("example.com").port(1090).contextPath("/mockserver").build()
        );
    }

    @Test
    public void shouldEquateToClientConfigurationHostPortContextPathConstructor() {
        ClientConfiguration configuration = clientConfiguration();
        MockServerClient constructed = new MockServerClient(configuration, "example.com", 1090, "/mockserver");
        MockServerClient built = MockServerClient.builder().configuration(configuration).host("example.com").port(1090).contextPath("/mockserver").build();

        assertEquivalent(constructed, built);
        // same ClientConfiguration instance is passed straight through in both cases
        assertThat(field(built, "configuration"), is(sameInstance(configuration)));
        assertThat(field(constructed, "configuration"), is(sameInstance(configuration)));
    }

    @Test
    public void shouldEquateToPortFutureConstructor() {
        CompletableFuture<Integer> portFuture = new CompletableFuture<>();

        MockServerClient constructed = new MockServerClient((ClientConfiguration) null, portFuture);
        MockServerClient built = MockServerClient.builder().portFuture(portFuture).build();

        // the future-based constructor always uses 127.0.0.1 and an empty context path
        assertThat(field(built, "host"), is("127.0.0.1"));
        assertThat(field(built, "contextPath"), is(""));
        assertEquivalent(constructed, built);
        assertThat(field(built, "portFuture"), is(sameInstance(portFuture)));

        portFuture.complete(1234);
        assertThat(built.getPort(), is(1234));
    }

    // ---------- setter-dimension coverage ----------

    @Test
    public void shouldApplySecure() {
        MockServerClient built = MockServerClient.builder().secure(true).build();
        assertThat(field(built, "secure"), is(true));
        assertThat(built.isSecure(), is(true));

        assertEquivalent(new MockServerClient("localhost", 1080).withSecure(true), built);
    }

    @Test
    public void shouldApplyProxyConfiguration() {
        ProxyConfiguration proxy = proxyConfiguration(ProxyConfiguration.Type.HTTP, "localhost:8080");
        MockServerClient built = MockServerClient.builder().proxyConfiguration(proxy).build();

        assertThat(field(built, "proxyConfiguration"), is(sameInstance(proxy)));
        assertEquivalent(new MockServerClient("localhost", 1080).withProxyConfiguration(proxy), built);
    }

    @Test
    public void shouldApplyControlPlaneJWTString() {
        MockServerClient built = MockServerClient.builder().controlPlaneJWT("some-token").build();

        Supplier<String> supplier = field(built, "controlPlaneJWTSupplier");
        assertThat(supplier, is(notNullValue()));
        assertThat(supplier.get(), is("some-token"));
    }

    @Test
    public void shouldApplyControlPlaneJWTSupplier() {
        Supplier<String> supplier = () -> "supplied-token";
        MockServerClient built = MockServerClient.builder().controlPlaneJWT(supplier).build();

        assertThat(field(built, "controlPlaneJWTSupplier"), is(sameInstance(supplier)));
    }

    @Test
    public void shouldApplyControlPlaneBasicAuthentication() {
        MockServerClient built = MockServerClient.builder().controlPlaneBasicAuthentication("admin", "pa:ss word").build();

        assertThat(built.webSocketAuthorization(), is("Basic " + java.util.Base64.getEncoder().encodeToString("admin:pa:ss word".getBytes(java.nio.charset.StandardCharsets.UTF_8))));
    }

    @Test
    public void shouldPreferTheControlPlaneJWTOverBasicCredentials() {
        MockServerClient built = MockServerClient.builder().controlPlaneBasicAuthentication("admin", "secret").controlPlaneJWT("some-token").build();

        assertThat(built.webSocketAuthorization(), is("Bearer some-token"));
    }

    @Test
    public void shouldRefuseBasicCredentialsWhoseUsernameCannotBeSent() {
        assertThrows(IllegalArgumentException.class, () -> MockServerClient.builder().controlPlaneBasicAuthentication("a:b", "secret").build());
        assertThrows(IllegalArgumentException.class, () -> MockServerClient.builder().controlPlaneBasicAuthentication("", "secret").build());
    }

    @Test
    public void shouldApplyRequestOverride() {
        HttpRequest override = request().withHeader("X-Test", "value");
        MockServerClient built = MockServerClient.builder().requestOverride(override).build();

        assertThat(field(built, "requestOverride"), is(sameInstance(override)));
        assertEquivalent(new MockServerClient("localhost", 1080).withRequestOverride(override), built);
    }

    @Test
    public void shouldApplyConfigurationFromConfiguration() {
        Configuration configuration = Configuration.configuration();
        MockServerClient built = MockServerClient.builder().configuration(configuration).build();

        ClientConfiguration clientConfiguration = field(built, "configuration");
        assertThat(clientConfiguration, is(notNullValue()));
    }

    @Test
    public void shouldTreatNullConfigurationAsDefault() {
        MockServerClient built = MockServerClient.builder().configuration((ClientConfiguration) null).build();
        assertThat(field(built, "configuration"), is(notNullValue()));
    }

    // ---------- misconfiguration is loud ----------

    @Test
    public void shouldRejectNullHostConsistentlyWithConstructor() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
            () -> MockServerClient.builder().host(null).build());
        assertThat(exception.getMessage(), containsString("Host can not be null or empty"));
    }

    @Test
    public void shouldRejectEmptyHostConsistentlyWithConstructor() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
            () -> MockServerClient.builder().host("").build());
        assertThat(exception.getMessage(), containsString("Host can not be null or empty"));
    }

    @Test
    public void shouldRejectNullContextPathConsistentlyWithConstructor() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
            () -> MockServerClient.builder().contextPath(null).build());
        assertThat(exception.getMessage(), containsString("ContextPath can not be null"));
    }

    @Test
    public void shouldRejectPortFutureCombinedWithHost() {
        CompletableFuture<Integer> portFuture = new CompletableFuture<>();
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
            () -> MockServerClient.builder().portFuture(portFuture).host("example.com").build());
        assertThat(exception.getMessage(), containsString("portFuture(...) can not be combined with"));
    }

    @Test
    public void shouldRejectPortFutureCombinedWithPort() {
        CompletableFuture<Integer> portFuture = new CompletableFuture<>();
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
            () -> MockServerClient.builder().portFuture(portFuture).port(1090).build());
        assertThat(exception.getMessage(), containsString("portFuture(...) can not be combined with"));
    }

    @Test
    public void shouldRejectPortFutureCombinedWithContextPath() {
        CompletableFuture<Integer> portFuture = new CompletableFuture<>();
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
            () -> MockServerClient.builder().portFuture(portFuture).contextPath("/mockserver").build());
        assertThat(exception.getMessage(), containsString("portFuture(...) can not be combined with"));
    }
}
