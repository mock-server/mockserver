package org.mockserver.file;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.mockserver.model.BinaryBody;
import org.mockserver.model.HttpResponse;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.Assert.assertThrows;
import static org.mockserver.model.HttpRequest.request;

/**
 * Which request paths {@code staticFilesDirectory} serves, and that every way a request path could reach a file
 * outside the directory, a hidden file or a directory listing is refused.
 */
public class StaticFileDirectoryTest {

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    private File root;
    private File served;
    private StaticFileDirectory staticFiles;

    @Before
    public void createDirectory() throws IOException {
        root = temporaryFolder.getRoot();
        served = temporaryFolder.newFolder("served");
        write(new File(root, "secret.txt"), "outside");
        write(new File(served, "hello.json"), "{\"hello\":\"world\"}");
        write(new File(served, "nested/page.html"), "<p>nested</p>");
        write(new File(served, "nested/index.html"), "<p>index</p>");
        write(new File(served, "index.html"), "<p>root</p>");
        write(new File(served, "my file+1.txt"), "spaced");
        write(new File(served, "noextension"), "raw");
        write(new File(served, ".env"), "SECRET=1");
        write(new File(served, ".git/config"), "[core]");
        staticFiles = StaticFileDirectory.open(served.getPath());
    }

    @Test
    public void shouldServeFileWithContentTypeFromExtension() throws IOException {
        HttpResponse response = staticFiles.respond(request("/hello.json").withMethod("GET"));

        assertThat(response.getStatusCode(), is(200));
        assertThat(response.getFirstHeader("content-type"), is("application/json"));
        assertThat(new String(((BinaryBody) response.getBody()).getRawBytes(), StandardCharsets.UTF_8), is("{\"hello\":\"world\"}"));
    }

    @Test
    public void shouldAnswerHeadWithLengthAndNoBody() throws IOException {
        HttpResponse response = staticFiles.respond(request("/nested/page.html").withMethod("HEAD"));

        assertThat(response.getStatusCode(), is(200));
        assertThat(response.getFirstHeader("content-type"), is("text/html"));
        assertThat(response.getFirstHeader("content-length"), is("13"));
        assertThat(response.getBody(), nullValue());
    }

    @Test
    public void shouldServeIndexForPathEndingInSlash() throws IOException {
        assertThat(body("/"), is("<p>root</p>"));
        assertThat(body("/nested/"), is("<p>index</p>"));
    }

    @Test
    public void shouldDecodePathOnce() throws IOException {
        assertThat(body("/my%20file+1.txt"), is("spaced"));
        assertThat(body("/nested%2Fpage.html"), is("<p>nested</p>"));
    }

    @Test
    public void shouldServeUnknownExtensionAsOctetStream() throws IOException {
        assertThat(staticFiles.respond(request("/noextension").withMethod("GET")).getFirstHeader("content-type"), is("application/octet-stream"));
    }

    @Test
    public void shouldNotServeOtherMethods() throws IOException {
        assertThat(staticFiles.respond(request("/hello.json").withMethod("POST")), nullValue());
        assertThat(staticFiles.respond(request("/hello.json").withMethod("get")), nullValue());
    }

    @Test
    public void shouldNotServeMissingFileOrDirectory() throws IOException {
        assertThat(staticFiles.respond(request("/missing.json").withMethod("GET")), nullValue());
        assertThat(staticFiles.respond(request("/nested").withMethod("GET")), nullValue());
    }

    @Test
    public void shouldRefuseTraversal() throws IOException {
        for (String path : new String[]{
            "/../secret.txt",
            "/nested/../../secret.txt",
            "/nested/../hello.json",
            "/./hello.json",
            "/%2e%2e/secret.txt",
            "/%2E%2E%2Fsecret.txt",
            "/%252e%252e/secret.txt",
            "/..%5csecret.txt",
            "/..\\secret.txt",
            "/nested\\..\\..\\secret.txt",
            "//etc/passwd",
            "/nested//page.html",
            "/C:/Windows/win.ini",
            "/C:secret.txt",
            "/hello.json%00.txt",
            "/hello.json\u0000",
            "/hello.json::$DATA",
            "/%",
            "/%zz",
            "hello.json",
            "",
        }) {
            assertThat(path, staticFiles.respond(request(path).withMethod("GET")), nullValue());
        }
    }

    @Test
    public void shouldRefuseAbsolutePathOfFileInsideDirectory() throws IOException {
        String absolute = new File(served, "hello.json").getAbsolutePath();

        assertThat(staticFiles.respond(request("/" + absolute).withMethod("GET")), nullValue());
    }

    @Test
    public void shouldRefuseHiddenFiles() throws IOException {
        assertThat(staticFiles.respond(request("/.env").withMethod("GET")), nullValue());
        assertThat(staticFiles.respond(request("/.git/config").withMethod("GET")), nullValue());
        assertThat(staticFiles.respond(request("/%2Eenv").withMethod("GET")), nullValue());
    }

    @Test
    public void shouldRefuseSymlinkLeavingDirectory() throws IOException {
        Path link = new File(served, "escape.txt").toPath();
        Path directoryLink = new File(served, "outside").toPath();
        if (!createSymbolicLink(link, new File(root, "secret.txt").toPath()) || !createSymbolicLink(directoryLink, root.toPath())) {
            return;
        }

        assertThat(staticFiles.respond(request("/escape.txt").withMethod("GET")), nullValue());
        assertThat(staticFiles.respond(request("/outside/secret.txt").withMethod("GET")), nullValue());
    }

    @Test
    public void shouldRefuseSymlinkToHiddenFileAndServeSymlinkInsideDirectory() throws IOException {
        if (!createSymbolicLink(new File(served, "env.txt").toPath(), new File(served, ".env").toPath())
            || !createSymbolicLink(new File(served, "alias.json").toPath(), new File(served, "hello.json").toPath())) {
            return;
        }

        assertThat(staticFiles.respond(request("/env.txt").withMethod("GET")), nullValue());
        assertThat(body("/alias.json"), is("{\"hello\":\"world\"}"));
    }

    @Test
    public void shouldConfineDirectoryThatIsItselfASymlink() throws IOException {
        Path linkedDirectory = new File(root, "linked").toPath();
        if (!createSymbolicLink(linkedDirectory, served.toPath())) {
            return;
        }
        StaticFileDirectory linked = StaticFileDirectory.open(linkedDirectory.toString());

        assertThat(new String(((BinaryBody) linked.respond(request("/hello.json").withMethod("GET")).getBody()).getRawBytes(), StandardCharsets.UTF_8), is("{\"hello\":\"world\"}"));
        assertThat(linked.respond(request("/../secret.txt").withMethod("GET")), nullValue());
    }

    @Test
    public void shouldRefuseFileLargerThanLimit() throws IOException {
        StaticFileDirectory small = new StaticFileDirectory(served.toPath().toRealPath(), 4);

        IOException tooLarge = assertThrows(IOException.class, () -> small.respond(request("/hello.json").withMethod("GET")));
        assertThat(tooLarge.getMessage(), containsString("larger than the 4 byte limit"));
        assertThrows(IOException.class, () -> small.respond(request("/hello.json").withMethod("HEAD")));
        assertThat(new String(((BinaryBody) small.respond(request("/noextension").withMethod("GET")).getBody()).getRawBytes(), StandardCharsets.UTF_8), is("raw"));
    }

    @Test
    public void shouldNotOpenBlankDirectoryAndRejectMissingDirectory() {
        try {
            assertThat(StaticFileDirectory.open(""), nullValue());
            assertThat(StaticFileDirectory.open(null), nullValue());
        } catch (IOException e) {
            throw new AssertionError(e);
        }
        assertThrows(IOException.class, () -> StaticFileDirectory.open(new File(root, "missing").getPath()));
        assertThrows(IOException.class, () -> StaticFileDirectory.open(new File(root, "secret.txt").getPath()));
    }

    private String body(String path) throws IOException {
        HttpResponse response = staticFiles.respond(request(path).withMethod("GET"));
        assertThat(path, response.getStatusCode(), is(200));
        return new String(((BinaryBody) response.getBody()).getRawBytes(), StandardCharsets.UTF_8);
    }

    private static boolean createSymbolicLink(Path link, Path target) {
        try {
            Files.createSymbolicLink(link, target);
            return true;
        } catch (IOException | UnsupportedOperationException notSupported) {
            return false;
        }
    }

    private static void write(File file, String content) throws IOException {
        Files.createDirectories(file.getParentFile().toPath());
        Files.write(file.toPath(), content.getBytes(StandardCharsets.UTF_8));
    }
}
