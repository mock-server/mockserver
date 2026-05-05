package org.mockserver.springtest;

import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.MethodSorters;
import org.mockserver.client.MockServerClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.web.client.RestTemplate;

import javax.annotation.Nullable;
import java.net.URI;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;

@MockServerTest("server.url=http://localhost:${mockServerPort}")
@RunWith(SpringRunner.class)
@ContextConfiguration(classes = MockServerTestFullSampleTest.Config.class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class MockServerTestFullSampleTest {

    static class Config {
        @Bean
        public Client client() {
            return new Client();
        }
    }

    private MockServerClient mockServerClient;

    @Autowired
    private Client client;

    @Test
    public void _0_testClientAgainstMock() {
        mockServerClient
            .when(
                request()
                    .withPath("/some/path")
            )
            .respond(
                response()
                    .withStatusCode(200)
                    .withBody("some_body")
            );

        String result = client.getResult("/some/path", String.class);

        assertThat(result, is("some_body"));
    }

    @Test
    public void _1_mockServerIsResettedAfterTest() {
        mockServerClient.verifyZeroInteractions();
    }

    @SuppressWarnings("SameParameterValue")
    static class Client {
        @Value("${server.url}")
        private URI serverUrl;

        private final RestTemplate restTemplate = new RestTemplate();

        @Nullable
        public <T> T getResult(String path, Class<T> responseType) {
            return restTemplate.getForObject(serverUrl + path, responseType);
        }
    }
}