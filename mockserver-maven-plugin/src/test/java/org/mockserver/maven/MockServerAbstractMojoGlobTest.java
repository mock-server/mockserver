package org.mockserver.maven;

import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.lang.reflect.Field;
import java.net.URL;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.Assert.assertEquals;

public class MockServerAbstractMojoGlobTest {

    private MockServerAbstractMojo mojo;
    private String fixtureBasePath;

    @Before
    public void setUp() throws Exception {
        mojo = new MockServerAbstractMojo() {
            @Override
            public void execute() {
                throw new UnsupportedOperationException("method not implemented yet");
            }
        };

        URL fixtureUrl = getClass().getClassLoader().getResource("org/mockserver/maven/glob-fixtures/expectation_a.json");
        if (fixtureUrl != null) {
            fixtureBasePath = new File(fixtureUrl.toURI()).getParentFile().getParentFile().getParentFile().getParentFile().getParentFile().getAbsolutePath();
        }
    }

    private void setPrivateField(String fieldName, Object value) throws Exception {
        Field field = MockServerAbstractMojo.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(mojo, value);
    }

    @Test
    public void shouldReturnEmptyStringWhenInitializationJsonIsNull() {
        assertEquals("", mojo.createInitializerJson());
    }

    @Test
    public void shouldReturnEmptyStringWhenInitializationJsonIsBlank() {
        mojo.initializationJson = "   ";
        assertEquals("", mojo.createInitializerJson());
    }

    @Test
    public void shouldExpandGlobAndMergeMultipleJsonFiles() throws Exception {
        setPrivateField("compileResourcePath", fixtureBasePath);
        mojo.initializationJson = "org/mockserver/maven/glob-fixtures/*.json";

        String result = mojo.createInitializerJson();

        assertThat(result, startsWith("["));
        assertThat(result, endsWith("]"));
        assertThat(result, containsString("/path_a"));
        assertThat(result, containsString("/path_b"));
    }

    @Test
    public void shouldExpandGlobWithRecursivePattern() throws Exception {
        setPrivateField("compileResourcePath", fixtureBasePath);
        mojo.initializationJson = "org/mockserver/maven/glob-fixtures/{,**/}*.json";

        String result = mojo.createInitializerJson();

        assertThat(result, startsWith("["));
        assertThat(result, endsWith("]"));
        assertThat(result, containsString("/path_a"));
        assertThat(result, containsString("/path_b"));
        assertThat(result, containsString("/path_c"));
    }

    @Test
    public void shouldFallBackToTestResourcePathForGlob() throws Exception {
        setPrivateField("testResourcePath", fixtureBasePath);
        mojo.initializationJson = "org/mockserver/maven/glob-fixtures/*.json";

        String result = mojo.createInitializerJson();

        assertThat(result, startsWith("["));
        assertThat(result, endsWith("]"));
        assertThat(result, containsString("/path_a"));
        assertThat(result, containsString("/path_b"));
    }

    @Test
    public void shouldReturnEmptyStringWhenGlobMatchesNoFiles() throws Exception {
        setPrivateField("compileResourcePath", fixtureBasePath);
        mojo.initializationJson = "org/mockserver/maven/glob-fixtures/*.xml";

        String result = mojo.createInitializerJson();

        assertEquals("", result);
    }

    @Test
    public void shouldHandleSingleObjectJsonFilesInGlob() throws Exception {
        setPrivateField("compileResourcePath", fixtureBasePath);
        mojo.initializationJson = "org/mockserver/maven/glob-fixtures/subdir/*.json";

        String result = mojo.createInitializerJson();

        assertThat(result, startsWith("["));
        assertThat(result, endsWith("]"));
        assertThat(result, containsString("/path_c"));
    }

    @Test
    public void shouldUseLiteralPathWhenNoGlobCharactersPresent() throws Exception {
        setPrivateField("compileResourcePath", fixtureBasePath);
        mojo.initializationJson = "org/mockserver/maven/initializerJson.json";

        String result = mojo.createInitializerJson();

        assertThat(result, containsString("/test_initializer_path"));
        assertThat(result, containsString("test_initializer_request_body"));
    }

    @Test
    public void shouldMergeFromBothCompileAndTestResourcePaths() throws Exception {
        URL fixtureUrl = getClass().getClassLoader().getResource("org/mockserver/maven/glob-fixtures/subdir/expectation_c.json");
        String subdirPath = new File(fixtureUrl.toURI()).getParentFile().getParentFile().getParentFile().getParentFile().getParentFile().getParentFile().getAbsolutePath();

        setPrivateField("compileResourcePath", fixtureBasePath);
        setPrivateField("testResourcePath", subdirPath);
        mojo.initializationJson = "org/mockserver/maven/glob-fixtures/subdir/*.json";

        String result = mojo.createInitializerJson();

        assertThat(result, containsString("/path_c"));
    }

    @Test
    public void shouldDetectQuestionMarkAsGlobCharacter() throws Exception {
        setPrivateField("compileResourcePath", fixtureBasePath);
        mojo.initializationJson = "org/mockserver/maven/glob-fixtures/expectation_?.json";

        String result = mojo.createInitializerJson();

        assertThat(result, startsWith("["));
        assertThat(result, endsWith("]"));
        assertThat(result, containsString("/path_a"));
        assertThat(result, containsString("/path_b"));
    }
}
