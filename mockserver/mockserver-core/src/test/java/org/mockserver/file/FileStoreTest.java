package org.mockserver.file;

import org.junit.Before;
import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.util.Set;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.hamcrest.core.Is.is;

public class FileStoreTest {

    private FileStore fileStore;

    @Before
    public void setUp() {
        fileStore = new FileStore();
    }

    @Test
    public void shouldStoreAndRetrieveFile() {
        byte[] content = "hello world".getBytes(StandardCharsets.UTF_8);
        fileStore.store("test.txt", content);
        assertThat(fileStore.retrieve("test.txt"), is(content));
    }

    @Test
    public void shouldReturnNullForNonExistentFile() {
        assertThat(fileStore.retrieve("nonexistent.txt"), is(nullValue()));
    }

    @Test
    public void shouldCheckFileExists() {
        fileStore.store("test.txt", "data".getBytes(StandardCharsets.UTF_8));
        assertThat(fileStore.exists("test.txt"), is(true));
        assertThat(fileStore.exists("other.txt"), is(false));
    }

    @Test
    public void shouldDeleteExistingFile() {
        fileStore.store("test.txt", "data".getBytes(StandardCharsets.UTF_8));
        assertThat(fileStore.delete("test.txt"), is(true));
        assertThat(fileStore.exists("test.txt"), is(false));
        assertThat(fileStore.retrieve("test.txt"), is(nullValue()));
    }

    @Test
    public void shouldReturnFalseWhenDeletingNonExistentFile() {
        assertThat(fileStore.delete("nonexistent.txt"), is(false));
    }

    @Test
    public void shouldListFiles() {
        fileStore.store("file1.txt", "data1".getBytes(StandardCharsets.UTF_8));
        fileStore.store("file2.txt", "data2".getBytes(StandardCharsets.UTF_8));
        Set<String> files = fileStore.listFiles();
        assertThat(files, containsInAnyOrder("file1.txt", "file2.txt"));
    }

    @Test
    public void shouldReturnEmptySetWhenNoFiles() {
        assertThat(fileStore.listFiles(), is(empty()));
    }

    @Test
    public void shouldResetAllFiles() {
        fileStore.store("file1.txt", "data1".getBytes(StandardCharsets.UTF_8));
        fileStore.store("file2.txt", "data2".getBytes(StandardCharsets.UTF_8));
        fileStore.reset();
        assertThat(fileStore.size(), is(0));
        assertThat(fileStore.listFiles(), is(empty()));
    }

    @Test
    public void shouldReturnCorrectSize() {
        assertThat(fileStore.size(), is(0));
        fileStore.store("file1.txt", "data1".getBytes(StandardCharsets.UTF_8));
        assertThat(fileStore.size(), is(1));
        fileStore.store("file2.txt", "data2".getBytes(StandardCharsets.UTF_8));
        assertThat(fileStore.size(), is(2));
    }

    @Test
    public void shouldOverwriteExistingFile() {
        fileStore.store("test.txt", "original".getBytes(StandardCharsets.UTF_8));
        fileStore.store("test.txt", "updated".getBytes(StandardCharsets.UTF_8));
        assertThat(new String(fileStore.retrieve("test.txt"), StandardCharsets.UTF_8), is("updated"));
        assertThat(fileStore.size(), is(1));
    }

    @Test
    public void shouldStoreBinaryContent() {
        byte[] binaryContent = new byte[]{0x00, 0x01, 0x02, (byte) 0xFF};
        fileStore.store("binary.dat", binaryContent);
        assertThat(fileStore.retrieve("binary.dat"), is(binaryContent));
    }
}
