package org.mockserver.proxy.http;

import io.netty.channel.embedded.EmbeddedChannel;
import org.junit.After;
import org.junit.Before;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockserver.client.serialization.ExpectationSerializer;
import org.mockserver.client.serialization.HttpRequestSerializer;
import org.mockserver.client.serialization.VerificationSequenceSerializer;
import org.mockserver.client.serialization.VerificationSerializer;
import org.mockserver.filters.RequestLogFilter;
import org.mockserver.filters.RequestResponseLogFilter;
import org.mockserver.mock.Expectation;
import org.mockserver.mock.action.ActionHandler;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;
import org.mockserver.proxy.Proxy;
import org.mockserver.verify.Verification;
import org.mockserver.verify.VerificationSequence;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

/**
 * @author jamesdbloom
 */
public class HttpProxyHandlerTest {


    // model objects
    @Mock
    Expectation mockExpectation;
    @Mock
    HttpRequest mockHttpRequest;
    @Mock
    HttpResponse mockHttpResponse;
    @Mock
    Verification mockVerification;
    @Mock
    VerificationSequence mockVerificationSequence;
    // mockserver
    RequestResponseLogFilter mockRequestResponseLogFilter;
    Proxy mockHttpProxy;
    RequestLogFilter mockRequestLogFilter;
    @Mock
    ActionHandler mockActionHandler;
    // serializers
    @Mock
    ExpectationSerializer mockExpectationSerializer;
    @Mock
    HttpRequestSerializer mockHttpRequestSerializer;
    @Mock
    VerificationSerializer mockVerificationSerializer;
    @Mock
    VerificationSequenceSerializer mockVerificationSequenceSerializer;

    @InjectMocks
    HttpProxyHandler httpProxyHandler;
    EmbeddedChannel embeddedChannel;

    @Before
    public void setupFixture() {
        // given - a mock server handler
        mockRequestLogFilter = mock(RequestLogFilter.class);
        mockRequestResponseLogFilter = mock(RequestResponseLogFilter.class);
        mockHttpProxy = mock(Proxy.class);
        httpProxyHandler = new HttpProxyHandler(mockHttpProxy, mockRequestLogFilter, mockRequestResponseLogFilter);
        embeddedChannel = new EmbeddedChannel(httpProxyHandler);

        initMocks(this);

        // given - serializers
        when(mockExpectationSerializer.deserialize(anyString())).thenReturn(mockExpectation);
        when(mockHttpRequestSerializer.deserialize(anyString())).thenReturn(mockHttpRequest);
        when(mockVerificationSerializer.deserialize(anyString())).thenReturn(mockVerification);
        when(mockVerificationSequenceSerializer.deserialize(anyString())).thenReturn(mockVerificationSequence);
    }

    @After
    public void closeEmbeddedChanel() {
        assertThat(embeddedChannel.finish(), is(false));
    }
}
