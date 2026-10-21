package org.mockserver.springtest;

import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.MethodSorters;
import org.mockserver.client.MockServerClient;
import org.mockserver.integration.ClientAndServer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringRunner;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;
import static org.mockserver.verify.VerificationTimes.once;

@RunWith(SpringRunner.class)
@ContextConfiguration(classes = MockServerInstancesTest.Config.class)
@MockServerInstance(
    name = "payment",
    portProperties = "payment.port",
    baseUrlProperties = "payment.url",
    initializationJsonPath = "org/mockserver/springtest/named-instance-payment.json"
)
@MockServerInstance(
    name = "inventory",
    baseUrlProperties = "inventory.url",
    initializationJsonPath = "src/test/resources/named-instance-inventory"
)
@TestPropertySource(properties = {
    "payment.url=http://payment.example.com",
    "inventory.endpoint=${mockserver.inventory.baseUrl}/inventory"
})
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class MockServerInstancesTest {

    static class Config {
    }

    private static final HttpClient HTTP_CLIENT = HttpClient.newHttpClient();

    @InjectMockServer("payment")
    private MockServerClient paymentClient;

    @InjectMockServer("inventory")
    private ClientAndServer inventoryServer;

    @Value("${mockserver.payment.port}")
    private int paymentPort;

    @Value("${mockserver.payment.baseUrl}")
    private String paymentBaseUrl;

    @Value("${mockserver.inventory.port}")
    private int inventoryPort;

    @Value("${payment.port}")
    private int customPaymentPort;

    @Value("${payment.url}")
    private String paymentUrl;

    @Value("${inventory.url}")
    private String inventoryUrl;

    @Value("${inventory.endpoint}")
    private String inventoryEndpoint;

    @Test
    public void _0_propertiesResolveToEachInstancesOwnPort() {
        assertThat(paymentPort, is(not(inventoryPort)));
        assertThat(paymentPort, is(paymentClient.getPort()));
        assertThat(inventoryPort, is(inventoryServer.getPort()));
        assertThat(customPaymentPort, is(paymentPort));
        assertThat(paymentBaseUrl, is("http://localhost:" + paymentPort));
        // the instance's property overrides the @TestPropertySource value
        assertThat(paymentUrl, is("http://localhost:" + paymentPort));
        assertThat(inventoryUrl, is("http://localhost:" + inventoryPort));
        assertThat(inventoryEndpoint, is("http://localhost:" + inventoryPort + "/inventory"));
    }

    @Test
    public void _1_eachClientTalksToItsOwnServer() throws Exception {
        paymentClient.when(request().withPath("/greeting")).respond(response().withBody("payment"));
        inventoryServer.when(request().withPath("/greeting")).respond(response().withBody("inventory"));

        assertThat(get(paymentUrl + "/greeting").body(), is("payment"));
        assertThat(get(inventoryUrl + "/greeting").body(), is("inventory"));

        paymentClient.verify(request().withPath("/greeting"), once());
        inventoryServer.verify(request().withPath("/greeting"), once());
    }

    @Test
    public void _2_eachInstanceLoadsItsOwnInitializationFile() throws Exception {
        assertThat(get(paymentUrl + "/payment/status").body(), is("payment-from-file"));
        assertThat(get(inventoryUrl + "/inventory/stock").body(), is("inventory-from-directory"));
        assertThat(get(paymentUrl + "/inventory/stock").statusCode(), is(404));
        assertThat(get(inventoryUrl + "/payment/status").statusCode(), is(404));
    }

    @Test
    public void _3_eachInstanceIsResetBetweenTestsButKeepsItsInitialExpectations() throws Exception {
        paymentClient.verifyZeroInteractions();
        inventoryServer.verifyZeroInteractions();

        assertThat(get(paymentUrl + "/greeting").statusCode(), is(404));
        assertThat(get(inventoryUrl + "/greeting").statusCode(), is(404));
        assertThat(get(paymentUrl + "/payment/status").body(), is("payment-from-file"));
        assertThat(get(inventoryUrl + "/inventory/stock").body(), is("inventory-from-directory"));
    }

    private static HttpResponse<String> get(String url) throws Exception {
        return HTTP_CLIENT.send(HttpRequest.newBuilder(URI.create(url)).GET().build(), HttpResponse.BodyHandlers.ofString());
    }
}
