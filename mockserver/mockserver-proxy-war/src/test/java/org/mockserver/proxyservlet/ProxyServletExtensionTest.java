package org.mockserver.proxyservlet;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockserver.configuration.ConfigurationProperties;
import org.mockserver.extension.Extension;
import org.mockserver.extension.ExtensionContext;
import org.mockserver.extension.ExtensionException;
import org.mockserver.mock.Expectation;
import org.mockserver.server.initialize.ExpectationInitializer;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThrows;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;

/**
 * Extensions in a WAR: configured by system property, started before the servlet builds anything, told it has started
 * (with no ports) before the constructor returns, and stopped in reverse when the servlet is destroyed or its start is
 * refused.
 */
public class ProxyServletExtensionTest {

    static final List<String> EVENTS = new CopyOnWriteArrayList<>();

    public abstract static class Recording implements Extension {
        @Override
        public void start(ExtensionContext context) throws Exception {
            EVENTS.add("start:" + name());
        }

        @Override
        public void onStarted(List<Integer> ports) throws Exception {
            EVENTS.add("onStarted:" + name() + ":" + ports);
        }

        @Override
        public void stop() {
            EVENTS.add("stop:" + name());
        }
    }

    public static class First extends Recording implements ExpectationInitializer {
        @Override
        public String name() {
            return "proxy-war-first";
        }

        @Override
        public Expectation[] initializeExpectations() {
            return new Expectation[]{new Expectation(request("/seeded-by-extension")).thenRespond(response("seeded"))};
        }
    }

    public static class Second extends Recording {
        @Override
        public String name() {
            return "proxy-war-second";
        }
    }

    public static class FailsOnStarted extends Recording {
        @Override
        public String name() {
            return "proxy-war-fails-on-started";
        }

        @Override
        public int priority() {
            return 1;
        }

        @Override
        public void onStarted(List<Integer> ports) {
            throw new IllegalStateException("refusing to finish starting");
        }
    }

    private String originalExtensions;

    @Before
    public void rememberExtensions() {
        EVENTS.clear();
        originalExtensions = ConfigurationProperties.extensions();
    }

    @After
    public void restoreExtensions() {
        ConfigurationProperties.extensions(originalExtensions);
    }

    @Test
    public void shouldStartExtensionsNamedInASystemPropertyAndStopThemInReverseWhenDestroyed() {
        ConfigurationProperties.extensions(First.class.getName() + "," + Second.class.getName());

        ProxyServlet servlet = new ProxyServlet();
        try {
            assertThat(EVENTS, contains("start:proxy-war-first", "start:proxy-war-second", "onStarted:proxy-war-first:[]", "onStarted:proxy-war-second:[]"));
            MockHttpServletRequest retrieve = new MockHttpServletRequest("PUT", "/mockserver/retrieve");
            retrieve.setQueryString("type=ACTIVE_EXPECTATIONS");
            retrieve.addParameter("type", "ACTIVE_EXPECTATIONS");
            MockHttpServletResponse seeded = new MockHttpServletResponse();
            servlet.service(retrieve, seeded);
            assertThat(seeded.getStatus(), is(200));
            assertThat(seeded.getContentAsString().contains("/seeded-by-extension"), is(true));
        } catch (java.io.UnsupportedEncodingException e) {
            throw new AssertionError(e);
        } finally {
            servlet.destroy();
        }

        assertThat(EVENTS.subList(4, EVENTS.size()), contains("stop:proxy-war-second", "stop:proxy-war-first"));
    }

    @Test
    public void shouldRefuseToConstructAndStopTheStartedExtensionsInReverseWhenOnStartedFails() {
        ConfigurationProperties.extensions(First.class.getName() + "," + FailsOnStarted.class.getName() + "," + Second.class.getName());

        ExtensionException failure = assertThrows(ExtensionException.class, ProxyServlet::new);

        assertThat(failure.getMessage(), containsString("proxy-war-fails-on-started failed in onStarted"));
        assertThat(EVENTS, contains(
            "start:proxy-war-first", "start:proxy-war-second", "start:proxy-war-fails-on-started",
            "onStarted:proxy-war-first:[]", "onStarted:proxy-war-second:[]",
            "stop:proxy-war-fails-on-started", "stop:proxy-war-second", "stop:proxy-war-first"
        ));
    }

    @Test
    public void shouldLoadNoExtensionsWhenNoneAreConfigured() {
        ConfigurationProperties.extensions("");

        ProxyServlet servlet = new ProxyServlet();
        servlet.destroy();

        assertThat(EVENTS, is(empty()));
    }
}
