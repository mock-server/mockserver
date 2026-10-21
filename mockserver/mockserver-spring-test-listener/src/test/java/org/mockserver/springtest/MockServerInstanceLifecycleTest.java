package org.mockserver.springtest;

import org.junit.Test;
import org.junit.runner.JUnitCore;
import org.junit.runner.Result;
import org.junit.runner.RunWith;
import org.mockserver.client.MockServerClient;
import org.mockserver.integration.ClientAndServer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringRunner;

import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.Assert.assertThrows;

public class MockServerInstanceLifecycleTest {

    @MockServerInstance(name = "lifecycle-shared")
    static class SharedDeclaration {
    }

    @MockServerInstance(name = "lifecycle-stopped")
    static class StoppedDeclaration {
        @InjectMockServer("lifecycle-stopped")
        MockServerClient client;
    }

    @Test
    public void contextsWithTheSameDeclarationShareOneServerThatStopsWhenTheLastCloses() {
        List<NamedMockServers.Definition> definitions = NamedMockServers.definitionsFor(SharedDeclaration.class);
        NamedMockServers.Definition definition = definitions.get(0);
        GenericApplicationContext first = refreshedContext(definitions);
        GenericApplicationContext second = refreshedContext(definitions);
        ClientAndServer server = NamedMockServers.running(definition);

        assertThat(server, is(notNullValue()));
        assertThat(NamedMockServers.references(definition), is(2));
        assertThat(first.getEnvironment().getProperty("mockserver.lifecycle-shared.port", Integer.class), is(server.getPort()));
        assertThat(second.getEnvironment().getProperty("mockserver.lifecycle-shared.port", Integer.class), is(server.getPort()));

        first.close();
        assertThat(NamedMockServers.running(definition), is(sameInstance(server)));
        assertThat(server.isRunning(), is(true));

        second.close();
        assertThat(NamedMockServers.running(definition), is(nullValue()));
        assertThat(NamedMockServers.references(definition), is(0));
        assertThat(server.isRunning(), is(false));
    }

    @Test
    public void refusesToInjectAnInstanceStoppedWhileItsContextStillUsesIt() {
        List<NamedMockServers.Definition> definitions = NamedMockServers.definitionsFor(StoppedDeclaration.class);
        GenericApplicationContext context = refreshedContext(definitions);
        try {
            NamedMockServers.running(definitions.get(0)).stop();

            IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> MockServerTestExecutionListener.injectNamedMockServerClients(StoppedDeclaration.class, new StoppedDeclaration(), context::getEnvironment));
            assertThat(exception.getMessage(), containsString("\"lifecycle-stopped\" is not running on port"));
        } finally {
            context.close();
        }
    }

    @Test
    public void springTestClassesShareContextsAndServersOnlyWhenTheirDeclarationsMatch() {
        Result result = JUnitCore.runClasses(SameA.class, SameB.class, Different.class, Dirtied.class);
        assertThat(result.getFailures().toString(), result.wasSuccessful(), is(true));
        assertThat(result.getRunCount(), is(4));

        assertThat(SameB.context, is(sameInstance(SameA.context)));
        assertThat(SameB.port, is(SameA.port));
        assertThat(Different.context, is(not(sameInstance(SameA.context))));
        assertThat(Different.port, is(not(SameA.port)));
        assertThat(NamedMockServers.running(NamedMockServers.definitionsFor(SameA.class).get(0)).getPort(), is(SameA.port));

        // @DirtiesContext closes the class's context, which was the only one using its instance
        assertThat(Dirtied.server.isRunning(), is(false));
        assertThat(NamedMockServers.running(NamedMockServers.definitionsFor(Dirtied.class).get(0)), is(nullValue()));
    }

    private static GenericApplicationContext refreshedContext(List<NamedMockServers.Definition> definitions) {
        GenericApplicationContext context = new GenericApplicationContext();
        context.setEnvironment(new StandardEnvironment());
        new MockServerInstancesCustomizer(definitions).customizeContext(context, null);
        context.refresh();
        return context;
    }

    static class EmptyConfig {
    }

    @RunWith(SpringRunner.class)
    @ContextConfiguration(classes = EmptyConfig.class)
    @MockServerInstance(name = "lifecycle-same")
    public static class SameA {
        static ApplicationContext context;
        static int port;

        @Autowired
        private ApplicationContext applicationContext;

        @InjectMockServer("lifecycle-same")
        private MockServerClient client;

        @Test
        public void record() {
            context = applicationContext;
            port = client.getPort();
        }
    }

    @RunWith(SpringRunner.class)
    @ContextConfiguration(classes = EmptyConfig.class)
    @MockServerInstance(name = "lifecycle-same")
    public static class SameB {
        static ApplicationContext context;
        static int port;

        @Autowired
        private ApplicationContext applicationContext;

        @InjectMockServer("lifecycle-same")
        private MockServerClient client;

        @Test
        public void record() {
            context = applicationContext;
            port = client.getPort();
        }
    }

    @RunWith(SpringRunner.class)
    @ContextConfiguration(classes = EmptyConfig.class)
    @MockServerInstance(name = "lifecycle-same", portProperties = "different.port")
    public static class Different {
        static ApplicationContext context;
        static int port;

        @Autowired
        private ApplicationContext applicationContext;

        @InjectMockServer("lifecycle-same")
        private MockServerClient client;

        @Test
        public void record() {
            context = applicationContext;
            port = client.getPort();
        }
    }

    @RunWith(SpringRunner.class)
    @ContextConfiguration(classes = EmptyConfig.class)
    @MockServerInstance(name = "lifecycle-dirtied")
    @DirtiesContext
    public static class Dirtied {
        static ClientAndServer server;

        @InjectMockServer("lifecycle-dirtied")
        private ClientAndServer injected;

        @Test
        public void record() {
            server = injected;
            assertThat(server.isRunning(), is(true));
        }
    }
}
