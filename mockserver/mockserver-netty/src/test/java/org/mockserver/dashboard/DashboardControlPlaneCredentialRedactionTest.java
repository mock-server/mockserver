package org.mockserver.dashboard;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.After;
import org.junit.Test;
import org.mockserver.authentication.basic.PasswordHash;
import org.mockserver.configuration.Configuration;
import org.mockserver.dashboard.model.DashboardLogEntryDTO;
import org.mockserver.dashboard.serializers.DashboardLogEntryDTOGroupSerializer;
import org.mockserver.dashboard.serializers.DashboardLogEntryDTOSerializer;
import org.mockserver.dashboard.serializers.DescriptionSerializer;
import org.mockserver.dashboard.serializers.ThrowableSerializer;
import org.mockserver.log.model.LogEntry;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.mock.HttpState;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;
import org.mockserver.responsewriter.ResponseWriter;
import org.mockserver.scheduler.Scheduler;
import org.mockserver.serialization.ObjectMapperFactory;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.mockserver.configuration.Configuration.configuration;
import static org.mockserver.model.HttpRequest.request;

/**
 * What the dashboard streams for a successful control-plane call: the serialized entries carry the request, but
 * never the control-plane credentials it was sent with, whatever {@code redactSecretsInLog} says.
 */
public class DashboardControlPlaneCredentialRedactionTest {

    private static final ObjectMapper DASHBOARD_MAPPER = ObjectMapperFactory.createObjectMapper(
        new DashboardLogEntryDTOSerializer(),
        new DashboardLogEntryDTOGroupSerializer(),
        new DescriptionSerializer(),
        new ThrowableSerializer()
    );

    private HttpState httpState;
    private Scheduler scheduler;

    @After
    public void stop() {
        if (httpState != null) {
            httpState.stop();
        }
        if (scheduler != null) {
            scheduler.shutdown();
        }
    }

    @Test
    public void shouldNotStreamAControlPlaneCallersBasicCredentials() throws Exception {
        Configuration configuration = configuration()
            .logLevel("INFO")
            .controlPlaneBasicAuthenticationRequired(true)
            .controlPlaneBasicAuthenticationCredentials("admin:" + PasswordHash.pbkdf2Sha256("Adm1n-S3cret", 10_000));
        MockServerLogger logger = new MockServerLogger(configuration, DashboardControlPlaneCredentialRedactionTest.class);
        scheduler = new Scheduler(configuration, logger, true);
        httpState = new HttpState(configuration, logger, scheduler);
        String credentials = Base64.getEncoder().encodeToString("admin:Adm1n-S3cret".getBytes(StandardCharsets.UTF_8));

        HttpRequest scenario = request("/mockserver/scenario/redaction-check").withMethod("PUT").withSecure(true)
            .withHeader("Authorization", "Basic " + credentials).withBody("{\"state\":\"Started\"}");
        CapturingResponseWriter responseWriter = new CapturingResponseWriter(configuration);
        httpState.handle(scenario, responseWriter, false);
        assertThat(responseWriter.response.getStatusCode(), is(200));

        CompletableFuture<List<LogEntry>> logged = new CompletableFuture<>();
        httpState.getMockServerLog().retrieveMessageLogEntries(null, logged::complete);
        StringBuilder streamed = new StringBuilder();
        for (LogEntry entry : logged.get(30, TimeUnit.SECONDS)) {
            DashboardLogEntryDTO dto = new DashboardLogEntryDTO(entry, configuration);
            // the frame, and the request the dashboard fetches for an entry's detail view
            streamed.append(DASHBOARD_MAPPER.writeValueAsString(dto)).append(java.util.Arrays.toString(dto.getHttpRequests()));
        }

        assertThat(streamed.toString(), containsString("redaction-check"));
        assertThat("the entry still carries the request, with its credentials masked", streamed.toString(), containsString("***REDACTED***"));
        assertThat(streamed.toString(), not(containsString(credentials)));
        assertThat(streamed.toString(), not(containsString("Adm1n-S3cret")));
    }

    private static class CapturingResponseWriter extends ResponseWriter {
        private HttpResponse response;

        CapturingResponseWriter(Configuration configuration) {
            super(configuration, new MockServerLogger());
        }

        @Override
        public void sendResponse(HttpRequest request, HttpResponse response) {
            this.response = response;
        }
    }
}
