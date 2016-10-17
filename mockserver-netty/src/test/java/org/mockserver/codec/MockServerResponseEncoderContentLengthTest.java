package org.mockserver.codec;

import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpHeaders;
import org.junit.Before;
import org.junit.Test;
import org.mockserver.model.ConnectionOptions;
import org.mockserver.model.HttpResponse;

import java.util.ArrayList;
import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.core.Is.is;
import static org.mockserver.model.BinaryBody.binary;
import static org.mockserver.model.HttpResponse.response;
import static org.mockserver.model.JsonBody.json;
import static org.mockserver.model.StringBody.exact;

/**
 * @author jamesdbloom
 */
public class MockServerResponseEncoderContentLengthTest {

    private MockServerResponseEncoder mockServerResponseEncoder;
    private List<Object> output;
    private HttpResponse httpResponse;

    @Before
    public void setupFixture() {
        mockServerResponseEncoder = new MockServerResponseEncoder();
        output = new ArrayList<Object>();
        httpResponse = response();
    }

    @Test
    public void shouldSetContentLengthForStringBody() {
        // given - a request
        String body = "some_content";
        httpResponse = response().withBody(exact(body));

        // when
        mockServerResponseEncoder.encode(null, httpResponse, output);

        // then
        HttpHeaders headers = ((FullHttpResponse) output.get(0)).headers();
        assertThat(headers.getAll("Content-Length"), containsInAnyOrder("" + body.length()));
    }

    @Test
    public void shouldSetContentLengthForBinaryBody() {
        // given - a request
        byte[] body = "some_binary_content".getBytes();
        httpResponse = response().withBody(binary(body));

        // when
        mockServerResponseEncoder.encode(null, httpResponse, output);

        // then
        HttpHeaders headers = ((FullHttpResponse) output.get(0)).headers();
        assertThat(headers.getAll("Content-Length"), containsInAnyOrder("" + body.length));
    }

    @Test
    public void shouldSetContentLengthForJsonBody() {
        // given - a request
        String body = "{ \"message\": \"some_json_content\" }";
        httpResponse = response().withBody(json(body));

        // when
        mockServerResponseEncoder.encode(null, httpResponse, output);

        // then
        HttpHeaders headers = ((FullHttpResponse) output.get(0)).headers();
        assertThat(headers.getAll("Content-Length"), containsInAnyOrder("" + body.length()));
    }

    @Test
    public void shouldSetContentLengthForNullBody() {
        // given - a request
        httpResponse = response();

        // when
        mockServerResponseEncoder.encode(null, httpResponse, output);

        // then
        HttpHeaders headers = ((FullHttpResponse) output.get(0)).headers();
        assertThat(headers.getAll("Content-Length"), containsInAnyOrder("0"));
    }

    @Test
    public void shouldSetContentLengthForEmptyBody() {
        // given - a request
        httpResponse = response();

        // when
        mockServerResponseEncoder.encode(null, httpResponse, output);

        // then
        HttpHeaders headers = ((FullHttpResponse) output.get(0)).headers();
        assertThat(headers.getAll("Content-Length"), containsInAnyOrder("0"));
    }

    @Test
    public void shouldSuppressContentLengthViaConnectionOptions() {
        // given - a request
        httpResponse = response()
                .withBody("some_content")
                .withConnectionOptions(
                        new ConnectionOptions()
                                .withSuppressContentLengthHeader(true)
                );

        // when
        mockServerResponseEncoder.encode(null, httpResponse, output);

        // then
        HttpHeaders headers = ((FullHttpResponse) output.get(0)).headers();
        assertThat(headers.contains("Content-Length"), is(false));
    }

    @Test
    public void shouldOverrideContentLengthViaConnectionOptions() {
        // given - a request
        httpResponse = response()
                .withBody("some_content")
                .withConnectionOptions(
                        new ConnectionOptions()
                                .withContentLengthHeaderOverride(50)
                );

        // when
        mockServerResponseEncoder.encode(null, httpResponse, output);

        // then
        HttpHeaders headers = ((FullHttpResponse) output.get(0)).headers();
        assertThat(headers.getAll("Content-Length"), containsInAnyOrder("50"));
    }

}
