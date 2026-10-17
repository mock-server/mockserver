package org.mockserver.file;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

public class FilePathTest {

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void shouldExpandGlobToClasspathResources() {
        List<String> result = FilePath.expandFilePathGlobs("org/mockserver/socket/*.pem");

        assertThat(result, hasItem("org/mockserver/socket/CertificateAuthorityCertificate.pem"));
    }

    // absolutePathFromClassPathOrPath

    @Test
    public void shouldReturnClasspathResourcePath() {
        // "org/mockserver/socket/CertificateAuthorityCertificate.pem" exists on the classpath in mockserver-core
        String result = FilePath.absolutePathFromClassPathOrPath("org/mockserver/socket/CertificateAuthorityCertificate.pem");
        assertThat(result, is(notNullValue()));
        assertThat(result, endsWith("org/mockserver/socket/CertificateAuthorityCertificate.pem"));
    }

    @Test
    public void shouldReturnAbsolutePathWhenNotOnClasspath() {
        String result = FilePath.absolutePathFromClassPathOrPath("/some/nonexistent/file.txt");
        assertThat(result, is("/some/nonexistent/file.txt"));
    }

    @Test
    public void shouldReturnAbsolutePathForRelativePath() {
        String result = FilePath.absolutePathFromClassPathOrPath("relative/path/file.txt");
        assertThat(result, containsString("relative/path/file.txt"));
        // should be absolute
        assertThat(new File(result).isAbsolute(), is(true));
    }

    // getURL

    @Test
    public void shouldReturnURLForExistingFile() throws IOException {
        File tempFile = temporaryFolder.newFile("test-url.txt");

        URL result = FilePath.getURL(tempFile.getAbsolutePath());

        assertThat(result, is(notNullValue()));
        assertThat(result.getProtocol(), is("file"));
        assertThat(result.getPath(), is(tempFile.getAbsolutePath()));
    }

    @Test
    public void shouldReturnClasspathURLForMissingFile() {
        // when file does not exist on disk, falls back to classpath
        URL result = FilePath.getURL("org/mockserver/socket/CertificateAuthorityCertificate.pem");
        assertThat(result, is(notNullValue()));
    }

    @Test
    public void shouldReturnNullWhenFileNotFoundAnywhere() {
        URL result = FilePath.getURL("completely-nonexistent-xyz-file.impossible");
        assertThat(result, is(nullValue()));
    }

    // expandFilePathGlobs

    @Test
    public void shouldReturnEmptyListForBlankPath() {
        List<String> result = FilePath.expandFilePathGlobs("");
        assertThat(result, is(empty()));
    }

    @Test
    public void shouldReturnEmptyListForNullPath() {
        List<String> result = FilePath.expandFilePathGlobs(null);
        assertThat(result, is(empty()));
    }

    @Test
    public void shouldReturnEmptyListForPathWithoutDot() {
        List<String> result = FilePath.expandFilePathGlobs("no-extension");
        assertThat(result, is(empty()));
    }

    @Test
    public void shouldReturnSingletonListForNonGlobPath() {
        List<String> result = FilePath.expandFilePathGlobs("some/path/file.json");
        assertThat(result, contains("some/path/file.json"));
    }

    @Test
    public void shouldExpandGlobToMatchFiles() throws IOException {
        File dir = temporaryFolder.newFolder("globtest");
        File file1 = new File(dir, "alpha.json");
        File file2 = new File(dir, "beta.json");
        File file3 = new File(dir, "gamma.txt");
        file1.createNewFile();
        file2.createNewFile();
        file3.createNewFile();

        String globPattern = dir.getAbsolutePath() + "/*.json";
        List<String> result = FilePath.expandFilePathGlobs(globPattern);

        assertThat(result, hasSize(2));
        assertThat(result, hasItem(containsString("alpha.json")));
        assertThat(result, hasItem(containsString("beta.json")));
    }

    @Test
    public void shouldReturnSortedResults() throws IOException {
        File dir = temporaryFolder.newFolder("sorttest");
        new File(dir, "c.json").createNewFile();
        new File(dir, "a.json").createNewFile();
        new File(dir, "b.json").createNewFile();

        String globPattern = dir.getAbsolutePath() + "/*.json";
        List<String> result = FilePath.expandFilePathGlobs(globPattern);

        assertThat(result, hasSize(3));
        // results should be sorted
        assertThat(result.get(0), containsString("a.json"));
        assertThat(result.get(1), containsString("b.json"));
        assertThat(result.get(2), containsString("c.json"));
    }

    @Test
    public void shouldHandleGlobWithQuestionMark() throws IOException {
        File dir = temporaryFolder.newFolder("qtest");
        new File(dir, "file1.txt").createNewFile();
        new File(dir, "file2.txt").createNewFile();
        new File(dir, "file10.txt").createNewFile();

        String globPattern = dir.getAbsolutePath() + "/file?.txt";
        List<String> result = FilePath.expandFilePathGlobs(globPattern);

        assertThat(result, hasSize(2));
        assertThat(result, hasItem(containsString("file1.txt")));
        assertThat(result, hasItem(containsString("file2.txt")));
    }

    @Test
    public void shouldHandleNonExistentGlobDirectory() {
        String globPattern = "/nonexistent/directory/path/*.json";
        List<String> result = FilePath.expandFilePathGlobs(globPattern);
        // should not throw, returns empty or classpath matches
        assertThat(result, is(notNullValue()));
    }
}
