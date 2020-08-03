package org.mockserver.spring;

import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertThat;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;

import java.net.URI;

import javax.annotation.Nullable;

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

@MockServerTest("server.url=http://localhost:${mockServerPort}/dummy")
@RunWith(SpringRunner.class)
@ContextConfiguration(classes = MockServerTestFullSampleTest.Config.class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class MockServerTestFullSampleTest {

    static class Config {
        @Bean
        public DummyClient dummyClient() {
            return new DummyClient();
        }
    }

    private MockServerClient mockServerClient;

    @Autowired
    DummyClient dummyClient;

    @Test
    public void _0_testClientAgainstMock() {
        mockServerClient.when(
                request().withPath("/dummy")
        ).respond(
                response().withStatusCode(200).withBody("42")
        );

        String result = dummyClient.getResult();

        assertThat(result, is("42"));
    }

    @Test
    public void _1_mockServerIsResettedAfterTest() {
        mockServerClient.verifyZeroInteractions();
    }

    static class DummyClient {
        @Value("${server.url}")
        URI serverUrl;

        final RestTemplate restTemplate = new RestTemplate();

        @Nullable
        String getResult() {
            return restTemplate.getForObject(serverUrl, String.class);
        }
    }
}