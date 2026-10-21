package org.mockserver.mock.action.http;

import org.junit.Assume;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.mockserver.configuration.Configuration;
import org.mockserver.file.FileBodyException;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.model.BodyWithContentType;
import org.mockserver.model.FileBody;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpTemplate;
import org.mockserver.model.MediaType;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.junit.Assert.assertThrows;
import static org.mockserver.model.HttpRequest.request;

/**
 * Behaviour of a {@link FileBody} whose PATH is rendered as a template ({@code templatedFilePath}), with
 * emphasis on request-controlled values trying to escape the directory the path's literal prefix names.
 */
public class FileBodyMaterialiserTemplatedPathTest {

    private static final String MUSTACHE_NAME = "{{ request.queryStringParameters.name.0 }}";
    private static final String VELOCITY_NAME = "$!{request.queryStringParameters['name'][0]}";

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    private final FileBodyMaterialiser materialiser = new FileBodyMaterialiser(new MockServerLogger(), new Configuration());
    private String mocksDirectory;
    private String secretFile;

    @Before
    public void createFiles() throws IOException {
        File root = temporaryFolder.getRoot();
        File mocks = new File(root, "mocks");
        assertThat(new File(mocks, "sub").mkdirs(), is(true));
        write(new File(mocks, "alice.json"), "{\"user\":\"alice\",\"method\":\"{{ request.method }}\"}");
        write(new File(mocks, "sub/bob.json"), "{\"user\":\"bob\"}");
        write(new File(mocks, MUSTACHE_NAME + ".json"), "{\"user\":\"literal\"}");
        File secret = new File(root, "secret.json");
        write(secret, "{\"secret\":true}");
        mocksDirectory = mocks.getAbsolutePath() + "/";
        secretFile = secret.getAbsolutePath();
    }

    @Test
    public void shouldSelectFileFromRequestWithMustachePath() {
        FileBody fileBody = mustacheBody(mocksDirectory + MUSTACHE_NAME + ".json");

        BodyWithContentType body = materialiser.materialise(fileBody, requestFor("alice").withMethod("PUT"));

        // the path picks the file, and the file contents are still rendered as a template
        assertThat(String.valueOf(body.getValue()), is("{\"user\":\"alice\",\"method\":\"PUT\"}"));
    }

    @Test
    public void shouldSelectFileFromRequestWithVelocityPath() {
        FileBody fileBody = new FileBody(mocksDirectory + "sub/" + VELOCITY_NAME + ".json", MediaType.APPLICATION_JSON, HttpTemplate.TemplateType.VELOCITY)
            .withTemplatedFilePath(true);

        BodyWithContentType body = materialiser.materialise(fileBody, requestFor("bob"));

        assertThat(String.valueOf(body.getValue()), is("{\"user\":\"bob\"}"));
    }

    @Test
    public void shouldAllowRequestValueToNameAFileInASubdirectory() {
        FileBody fileBody = mustacheBody(mocksDirectory + MUSTACHE_NAME + ".json");

        BodyWithContentType body = materialiser.materialise(fileBody, requestFor("sub/bob"));

        assertThat(String.valueOf(body.getValue()), is("{\"user\":\"bob\"}"));
    }

    @Test
    public void shouldUsePathLiterallyWhenTemplatedFilePathNotSet() {
        // without the opt-in, template syntax in the path is part of the file name, exactly as before
        FileBody fileBody = new FileBody(mocksDirectory + MUSTACHE_NAME + ".json", MediaType.APPLICATION_JSON, HttpTemplate.TemplateType.MUSTACHE);

        BodyWithContentType body = materialiser.materialise(fileBody, requestFor("alice"));

        assertThat(String.valueOf(body.getValue()), is("{\"user\":\"literal\"}"));
    }

    @Test
    public void shouldResolvePathWithoutTemplateSyntaxExactlyAsBefore() {
        // a path with no template syntax is never rendered or confined, so a literal ".." still works
        FileBody fileBody = mustacheBody(mocksDirectory + "sub/../alice.json");

        BodyWithContentType body = materialiser.materialise(fileBody, requestFor("ignored"));

        assertThat(String.valueOf(body.getValue()), containsString("\"user\":\"alice\""));
    }

    @Test
    public void shouldReturnSameNotFoundErrorWhenRenderedFileMissing() {
        FileBody fileBody = mustacheBody(mocksDirectory + MUSTACHE_NAME + ".json");

        FileBodyException exception = assertThrows(FileBodyException.class, () -> materialiser.materialise(fileBody, requestFor("carol")));

        assertThat(exception.getFilePath(), is(mocksDirectory + "carol.json"));
    }

    @Test
    public void shouldRejectParentDirectoryTraversal() {
        assertRejected("../secret");
        assertRejected("sub/../../secret");
        assertRejectedWithoutSuffix("..");
        assertRejectedWithoutSuffix("sub/..");
    }

    @Test
    public void shouldRejectAbsolutePath() {
        assertRejectedWithoutSuffix(secretFile);
        assertRejectedWithoutSuffix("/" + secretFile);
    }

    @Test
    public void shouldRejectUrlEncodedTraversal() {
        assertRejected("%2e%2e%2fsecret");
        assertRejected("..%2Fsecret");
        assertRejected("%252e%252e%252fsecret");
    }

    @Test
    public void shouldRejectNullByteAndControlCharacters() {
        assertRejected("alice.json\u0000");
        assertRejected("alice\n");
    }

    @Test
    public void shouldRejectWindowsSeparatorsAndDrives() {
        assertRejected("..\\secret");
        assertRejected("sub\\bob");
        assertRejected("C:\\secret");
        assertRejected("C:secret");
    }

    @Test
    public void shouldRejectEmptyDotAndDoubledSegments() {
        assertRejectedWithoutSuffix("");
        assertRejected("./alice");
        assertRejected("sub//bob");
    }

    @Test
    public void shouldRejectTemplatedPathWhoseLiteralPrefixNamesNoDirectory() {
        // the working directory, classpath root or a filesystem root would let a request read far too much
        for (String pathTemplate : new String[]{MUSTACHE_NAME, "/" + MUSTACHE_NAME, "./" + MUSTACHE_NAME, "C:/" + MUSTACHE_NAME, "C:\\" + MUSTACHE_NAME}) {
            assertGuardRejects(mustacheBody(pathTemplate), "alice");
        }
    }

    @Test
    public void shouldSelectClasspathResourceFromRequest() {
        FileBody fileBody = mustacheBody("org/mockserver/templates/" + MUSTACHE_NAME + ".json");

        BodyWithContentType body = materialiser.materialise(fileBody, requestFor("sample_mustache_body").withMethod("PUT"));

        assertThat(String.valueOf(body.getValue()), containsString("\"method\": \"PUT\""));
    }

    @Test
    public void shouldRejectSymlinkThatLeavesBaseDirectory() throws IOException {
        Path link = new File(mocksDirectory, "escape").toPath();
        try {
            Files.createSymbolicLink(link, temporaryFolder.getRoot().toPath());
        } catch (IOException | UnsupportedOperationException | SecurityException e) {
            Assume.assumeNoException("symbolic links not supported", e);
        }

        assertRejected("escape/secret");
    }

    @Test
    public void shouldServeSymlinkThatStaysInsideBaseDirectory() throws IOException {
        Path link = new File(mocksDirectory, "alias.json").toPath();
        try {
            Files.createSymbolicLink(link, new File(mocksDirectory, "sub/bob.json").toPath());
        } catch (IOException | UnsupportedOperationException | SecurityException e) {
            Assume.assumeNoException("symbolic links not supported", e);
        }
        FileBody fileBody = mustacheBody(mocksDirectory + MUSTACHE_NAME + ".json");

        BodyWithContentType body = materialiser.materialise(fileBody, requestFor("alias"));

        assertThat(String.valueOf(body.getValue()), is("{\"user\":\"bob\"}"));
    }

    @Test
    public void shouldRejectClasspathDirectorySymlinkThatLeavesBaseDirectory() throws Exception {
        // a symlink inside a classpath directory (target/test-classes here) must not lead out of the base either
        Path classpathBase = Paths.get(getClass().getClassLoader().getResource("org/mockserver/templates/sample_mustache_body.json").toURI()).getParent();
        Path link = classpathBase.resolve("u4-escape-" + System.nanoTime());
        try {
            Files.createSymbolicLink(link, temporaryFolder.getRoot().toPath());
        } catch (IOException | UnsupportedOperationException | SecurityException e) {
            Assume.assumeNoException("symbolic links not supported", e);
        }
        try {
            assertGuardRejects(mustacheBody("org/mockserver/templates/" + MUSTACHE_NAME + ".json"), link.getFileName() + "/secret");
        } finally {
            Files.deleteIfExists(link);
        }
    }

    @Test
    public void shouldRejectUnicodeLineSeparatorsAndC1Controls() {
        assertRejected("alice" + (char) 0x2028);
        assertRejected("alice" + (char) 0x2029);
        assertRejected("alice" + (char) 0x85);
    }

    @Test
    public void shouldTreatVelocityDirectiveAsTemplateSyntax() {
        FileBody fileBody = new FileBody(mocksDirectory + "#if($request.method == 'GET')alice#{else}sub/bob#end.json", MediaType.APPLICATION_JSON, HttpTemplate.TemplateType.VELOCITY)
            .withTemplatedFilePath(true);

        assertThat(String.valueOf(materialiser.materialise(fileBody, requestFor("x").withMethod("GET")).getValue()), containsString("\"user\":\"alice\""));
        assertThat(String.valueOf(materialiser.materialise(fileBody, requestFor("x").withMethod("POST")).getValue()), is("{\"user\":\"bob\"}"));
    }

    @Test
    public void shouldNotLeakRejectedValueUnescapedIntoTheError() {
        FileBody fileBody = mustacheBody(mocksDirectory + MUSTACHE_NAME + ".json");

        FileBodyException exception = assertThrows(FileBodyException.class, () -> materialiser.materialise(fileBody, requestFor("a\nb")));

        assertThat(exception.getFilePath(), not(containsString("\n")));
        assertThat(exception.getFilePath(), containsString("\\x0A"));
    }

    private void assertRejected(String requestValue) {
        assertGuardRejects(mustacheBody(mocksDirectory + MUSTACHE_NAME + ".json"), requestValue);
        assertGuardRejects(new FileBody(mocksDirectory + VELOCITY_NAME + ".json", MediaType.APPLICATION_JSON, HttpTemplate.TemplateType.VELOCITY).withTemplatedFilePath(true), requestValue);
    }

    private void assertRejectedWithoutSuffix(String requestValue) {
        assertGuardRejects(mustacheBody(mocksDirectory + MUSTACHE_NAME), requestValue);
    }

    // the same FileBodyException a bad literal path raises, but caused by the guard rather than a missing file
    private void assertGuardRejects(FileBody fileBody, String requestValue) {
        FileBodyException exception = assertThrows("expected \"" + requestValue + "\" to be rejected", FileBodyException.class, () -> materialiser.materialise(fileBody, requestFor(requestValue)));
        assertThat("expected \"" + requestValue + "\" to be rejected by the guard", exception.getCause(), is(instanceOf(IllegalArgumentException.class)));
        assertThat(exception.getCause().getMessage(), containsString("outside its base directory"));
    }

    private static FileBody mustacheBody(String filePath) {
        return new FileBody(filePath, MediaType.APPLICATION_JSON, HttpTemplate.TemplateType.MUSTACHE).withTemplatedFilePath(true);
    }

    private static HttpRequest requestFor(String name) {
        return request().withPath("/files").withQueryStringParameter("name", name);
    }

    private static void write(File file, String content) throws IOException {
        Files.write(file.toPath(), content.getBytes(StandardCharsets.UTF_8));
    }
}
