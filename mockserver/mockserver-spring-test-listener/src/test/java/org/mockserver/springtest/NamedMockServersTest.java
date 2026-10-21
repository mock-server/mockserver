package org.mockserver.springtest;

import org.junit.Test;
import org.mockserver.client.MockServerClient;
import org.mockserver.integration.ClientAndServer;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;

import java.io.File;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.Assert.assertThrows;

public class NamedMockServersTest {

    static class NotAnnotated {
    }

    @MockServerInstance(name = "a", portProperties = "a.port")
    @MockServerInstance(name = "b", baseUrlProperties = "b.url", properties = "mockserver.logLevel=WARN")
    static class TwoInstances {
    }

    @MockServerInstance(name = "a", portProperties = "a.port")
    @MockServerInstance(name = "b", baseUrlProperties = "b.url", properties = "mockserver.logLevel=WARN")
    static class SameTwoInstances {
    }

    static class InheritsTwoInstances extends TwoInstances {
    }

    @MockServerInstance(name = "a")
    @MockServerInstance(name = "a", portProperties = "a.port")
    static class DuplicateName {
    }

    @MockServerInstance(name = "a")
    @MockServerInstance(name = "a")
    static class IdenticalDuplicateName {
    }

    @MockServerInstance(name = "a", portProperties = "other.port")
    static class RedeclaresASuperclassName extends TwoInstances {
    }

    @MockServerInstance(name = "a", portProperties = "mockServerPort")
    static class SetsAMockServerTestProperty {
    }

    @MockServerInstance(name = "nested")
    static class Outer {
        @InjectMockServer("nested")
        MockServerClient client;

        class Inner {
        }
    }

    @MockServerInstance(name = "a.b")
    static class InvalidName {
    }

    @MockServerInstance(name = "a", portProperties = "shared")
    @MockServerInstance(name = "b", portProperties = "shared")
    static class CollidingProperty {
    }

    @MockServerInstance(name = "a", properties = "server.url=http://localhost")
    static class NotAMockServerProperty {
    }

    @MockServerInstance(name = "a")
    static class InjectsUndeclaredName {
        @InjectMockServer("missing")
        MockServerClient client;
    }

    @MockServerInstance(name = "a")
    static class InjectsWrongType {
        @InjectMockServer("a")
        String client;
    }

    @Test
    public void readsEveryInstanceInDeclarationOrder() {
        List<NamedMockServers.Definition> definitions = NamedMockServers.definitionsFor(TwoInstances.class);

        assertThat(definitions.size(), is(2));
        assertThat(definitions.get(0).name, is("a"));
        assertThat(definitions.get(0).springPropertyNames(), contains("mockserver.a.port", "mockserver.a.baseUrl", "a.port"));
        assertThat(definitions.get(1).name, is("b"));
        assertThat(definitions.get(1).springPropertyNames(), contains("mockserver.b.port", "mockserver.b.baseUrl", "b.url"));
    }

    @Test
    public void inheritsInstancesFromASuperclass() {
        assertThat(NamedMockServers.definitionsFor(InheritsTwoInstances.class), is(NamedMockServers.definitionsFor(TwoInstances.class)));
    }

    @Test
    public void factoryCreatesNoCustomizerWithoutInstances() {
        assertThat(new MockServerInstancesCustomizerFactory().createContextCustomizer(NotAnnotated.class, Collections.emptyList()), is(nullValue()));
    }

    @Test
    public void classesDeclaringTheSameInstancesShareAContext() {
        MockServerInstancesCustomizerFactory factory = new MockServerInstancesCustomizerFactory();

        assertThat(factory.createContextCustomizer(TwoInstances.class, Collections.emptyList()),
            is(factory.createContextCustomizer(SameTwoInstances.class, Collections.emptyList())));
        assertThat(factory.createContextCustomizer(TwoInstances.class, Collections.emptyList()),
            is(not(factory.createContextCustomizer(InjectsWrongType.class, Collections.emptyList()))));
    }

    @Test
    public void rejectsADuplicateName() {
        IllegalStateException exception = assertThrows(IllegalStateException.class, () -> NamedMockServers.definitionsFor(DuplicateName.class));
        assertThat(exception.getMessage(), containsString("\"a\" is declared more than once"));
    }

    @Test
    public void rejectsAnIdenticalDuplicateName() {
        IllegalStateException exception = assertThrows(IllegalStateException.class, () -> NamedMockServers.definitionsFor(IdenticalDuplicateName.class));
        assertThat(exception.getMessage(), containsString("\"a\" is declared more than once"));
    }

    @Test
    public void rejectsASubclassRedeclaringASuperclassName() {
        IllegalStateException exception = assertThrows(IllegalStateException.class, () -> NamedMockServers.definitionsFor(RedeclaresASuperclassName.class));
        assertThat(exception.getMessage(), containsString("\"a\" is declared more than once on " + RedeclaresASuperclassName.class.getName() + " and its superclasses"));
    }

    @Test
    public void rejectsAPropertyThatMockServerTestSets() {
        IllegalStateException exception = assertThrows(IllegalStateException.class, () -> NamedMockServers.definitionsFor(SetsAMockServerTestProperty.class));
        assertThat(exception.getMessage(), containsString("cannot set \"mockServerPort\""));
    }

    @Test
    public void injectsAnOuterClassFieldIntoTheEnclosingInstanceOfAnInnerTestInstance() {
        NamedMockServers.Definition definition = NamedMockServers.definitionsFor(Outer.Inner.class).get(0);
        assertThat(definition.name, is("nested"));
        ClientAndServer server = NamedMockServers.acquire(definition);
        try {
            StandardEnvironment environment = new StandardEnvironment();
            environment.getPropertySources().addFirst(new MapPropertySource("test", Map.of(definition.portProperty(), server.getPort())));
            Outer outer = new Outer();

            MockServerTestExecutionListener.injectNamedMockServerClients(Outer.Inner.class, outer.new Inner(), () -> environment);

            assertThat(outer.client, is(sameInstance(server)));
        } finally {
            NamedMockServers.release(definition);
        }
    }

    @Test
    public void rejectsANameThatIsNotAPropertySegment() {
        IllegalStateException exception = assertThrows(IllegalStateException.class, () -> NamedMockServers.definitionsFor(InvalidName.class));
        assertThat(exception.getMessage(), containsString("must contain only letters, digits"));
    }

    @Test
    public void rejectsTwoInstancesSettingTheSameProperty() {
        IllegalStateException exception = assertThrows(IllegalStateException.class, () -> NamedMockServers.definitionsFor(CollidingProperty.class));
        assertThat(exception.getMessage(), containsString("\"shared\" is set by more than one"));
    }

    @Test
    public void rejectsAPropertyThatIsNotMockServerConfiguration() {
        IllegalStateException exception = assertThrows(IllegalStateException.class, () -> NamedMockServers.definitionsFor(NotAMockServerProperty.class));
        assertThat(exception.getMessage(), containsString("must be mockserver.<property>=<value>"));
    }

    @Test
    public void rejectsAnInjectionPointForAnUndeclaredName() {
        IllegalStateException exception = assertThrows(IllegalStateException.class,
            () -> MockServerTestExecutionListener.injectNamedMockServerClients(InjectsUndeclaredName.class, new InjectsUndeclaredName(), StandardEnvironment::new));
        assertThat(exception.getMessage(), containsString("has no matching @MockServerInstance(name = \"missing\")"));
    }

    @Test
    public void rejectsAnInjectionPointOfTheWrongType() {
        IllegalStateException exception = assertThrows(IllegalStateException.class,
            () -> MockServerTestExecutionListener.injectNamedMockServerClients(InjectsWrongType.class, new InjectsWrongType(), StandardEnvironment::new));
        assertThat(exception.getMessage(), containsString("must be a MockServerClient or ClientAndServer"));
    }

    @Test
    public void expandsADirectoryToTheJsonFilesInIt() {
        assertThat(NamedMockServers.Definition.expandDirectory("src/test/resources/named-instance-inventory"),
            is("src/test/resources/named-instance-inventory/*.json"));
        assertThat(NamedMockServers.Definition.expandDirectory("src/test/resources/named-instance-inventory/"),
            is("src/test/resources/named-instance-inventory/*.json"));
        assertThat(NamedMockServers.Definition.expandDirectory("org/mockserver/springtest/named-instance-payment.json"),
            is("org/mockserver/springtest/named-instance-payment.json"));
        assertThat(new File("src/test/resources/named-instance-inventory").isDirectory(), is(true));
    }
}
