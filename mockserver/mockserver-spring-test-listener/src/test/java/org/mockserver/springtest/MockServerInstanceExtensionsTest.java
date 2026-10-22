package org.mockserver.springtest;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockserver.client.MockServerClient;
import org.mockserver.extension.Extension;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringRunner;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;

/**
 * Each named instance builds its own Configuration from its own properties, so each loads only the extensions it
 * names.
 */
@RunWith(SpringRunner.class)
@ContextConfiguration(classes = MockServerInstanceExtensionsTest.Config.class)
@MockServerInstance(
    name = "orders",
    properties = "mockserver.extensions=org.mockserver.springtest.MockServerInstanceExtensionsTest$OrdersExtension"
)
@MockServerInstance(
    name = "billing",
    properties = "mockserver.extensions=org.mockserver.springtest.MockServerInstanceExtensionsTest$BillingExtension"
)
public class MockServerInstanceExtensionsTest {

    static class Config {
    }

    static final Map<String, List<Integer>> PORTS_SEEN = new ConcurrentHashMap<>();

    public abstract static class PortRecording implements Extension {
        @Override
        public void onStarted(List<Integer> ports) {
            PORTS_SEEN.computeIfAbsent(name(), key -> new CopyOnWriteArrayList<>()).addAll(ports);
        }
    }

    public static class OrdersExtension extends PortRecording {
        @Override
        public String name() {
            return "spring-orders";
        }
    }

    public static class BillingExtension extends PortRecording {
        @Override
        public String name() {
            return "spring-billing";
        }
    }

    @InjectMockServer("orders")
    private MockServerClient ordersClient;

    @InjectMockServer("billing")
    private MockServerClient billingClient;

    @Test
    public void eachInstanceLoadsOnlyTheExtensionsItsOwnPropertiesName() {
        assertThat(PORTS_SEEN.get("spring-orders"), contains(ordersClient.getPort()));
        assertThat(PORTS_SEEN.get("spring-billing"), contains(billingClient.getPort()));
    }
}
