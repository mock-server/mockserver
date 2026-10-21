package org.mockserver.file;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.net.URLConnection;
import java.net.URLStreamHandler;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThrows;

/**
 * Classpath resolution of a templated {@code FileBody} path: which resource URLs can be confined, and that
 * anything that cannot be verified is refused rather than read.
 */
public class TemplatedFilePathGuardTest {

    private static final String TEMPLATE = "u4cp/{{ request.queryStringParameters.name.0 }}.json";
    private static final int MARKER = TEMPLATE.indexOf("{{");

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void shouldReadJarEntryUnderBaseDirectory() throws Exception {
        File jar = jarWith("u4cp/alice.json", "{\"user\":\"alice\"}");
        try (URLClassLoader classLoader = new URLClassLoader(new URL[]{jar.toURI().toURL()}, null)) {
            byte[] bytes = TemplatedFilePathGuard.readConfined(classLoader, TEMPLATE, MARKER, "u4cp/alice.json");

            assertThat(new String(bytes, StandardCharsets.UTF_8), is("{\"user\":\"alice\"}"));
        }
    }

    @Test
    public void shouldRefuseJarUrlWhoseEntryIsNotTheCheckedPath() throws Exception {
        File jar = jarWith("secret.json", "{\"secret\":true}");
        URL other = new URL("jar:" + jar.toURI() + "!/secret.json");

        assertRefused(resolvingTo(other), "u4cp/alice.json");
    }

    @Test
    public void shouldRefuseProtocolWhoseContainmentCannotBeVerified() throws Exception {
        URL vfs = new URL("vfs", "", -1, "/deployment/u4cp/alice.json", new URLStreamHandler() {
            @Override
            protected URLConnection openConnection(URL url) {
                throw new AssertionError("an unverifiable resource must not be opened");
            }
        });

        assertRefused(resolvingTo(vfs), "u4cp/alice.json");
    }

    @Test
    public void shouldReadFileUrlThatIsNotPercentEncoded() throws Exception {
        File directory = new File(temporaryFolder.getRoot(), "with space/u4cp");
        assertThat(directory.mkdirs(), is(true));
        File file = new File(directory, "alice.json");
        Files.write(file.toPath(), "{\"user\":\"alice\"}".getBytes(StandardCharsets.UTF_8));
        URL unencoded = new URL("file:" + file.getAbsolutePath());

        byte[] bytes = TemplatedFilePathGuard.readConfined(resolvingTo(unencoded), TEMPLATE, MARKER, "u4cp/alice.json");

        assertThat(new String(bytes, StandardCharsets.UTF_8), is("{\"user\":\"alice\"}"));
    }

    @Test
    public void shouldRefuseFileUrlThatCannotBeConvertedToAPath() throws Exception {
        URL remote = new URL("file://remote-host/share/u4cp/alice.json");

        assertRefused(resolvingTo(remote), "u4cp/alice.json");
    }

    private static void assertRefused(ClassLoader classLoader, String renderedPath) {
        IllegalArgumentException refused = assertThrows(IllegalArgumentException.class, () -> TemplatedFilePathGuard.readConfined(classLoader, TEMPLATE, MARKER, renderedPath));
        assertThat(refused.getMessage(), containsString("outside its base directory"));
    }

    private static ClassLoader resolvingTo(URL url) {
        return new ClassLoader(null) {
            @Override
            public URL getResource(String name) {
                return url;
            }
        };
    }

    private File jarWith(String entryName, String content) throws IOException {
        File jar = temporaryFolder.newFile("resources-" + System.nanoTime() + ".jar");
        try (JarOutputStream jarOutputStream = new JarOutputStream(new FileOutputStream(jar))) {
            jarOutputStream.putNextEntry(new JarEntry(entryName));
            jarOutputStream.write(content.getBytes(StandardCharsets.UTF_8));
            jarOutputStream.closeEntry();
        }
        return jar;
    }
}
