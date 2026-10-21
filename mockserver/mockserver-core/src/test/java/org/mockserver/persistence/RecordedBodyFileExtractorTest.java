package org.mockserver.persistence;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.matchers.TimeToLive;
import org.mockserver.matchers.Times;
import org.mockserver.mock.Expectation;
import org.mockserver.mock.ResponseMode;
import org.mockserver.model.BinaryBody;
import org.mockserver.model.FileBody;
import org.mockserver.model.HttpResponse;
import org.mockserver.model.MediaType;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.sameInstance;
import static org.junit.Assert.assertThrows;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;

public class RecordedBodyFileExtractorTest {

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    private static Expectation recorded(HttpResponse response) {
        return new Expectation(request("/report"), Times.once(), TimeToLive.unlimited(), 0).thenRespond(response);
    }

    @Test
    public void shouldWriteBodiesLargerThanTheThresholdToHashNamedFiles() throws Exception {
        String directory = new File(temporaryFolder.getRoot(), "bodies").getPath();
        String large = "{\"rows\":[" + "1,".repeat(100) + "1]}";
        Expectation original = recorded(response().withBody(large, MediaType.APPLICATION_JSON));

        List<Expectation> result = new RecordedBodyFileExtractor(new MockServerLogger(), directory, 64, 60_000).extract(Collections.singletonList(original));

        FileBody fileBody = (FileBody) result.get(0).getHttpResponse().getBody();
        Path written = Path.of(fileBody.getFilePath());
        assertThat(written.getParent().toString(), is(directory));
        assertThat(written.getFileName().toString(), matchesPattern("[0-9a-f]{64}\\.json"));
        assertThat(new String(Files.readAllBytes(written), StandardCharsets.UTF_8), is(large));
        assertThat(fileBody.getContentType(), is(MediaType.APPLICATION_JSON.toString()));
        assertThat(original.getHttpResponse().getBodyAsString(), is(large));
    }

    @Test
    public void shouldLeaveBodiesAtOrBelowTheThresholdInline() {
        String directory = new File(temporaryFolder.getRoot(), "bodies").getPath();
        Expectation original = recorded(response().withBody("small"));

        List<Expectation> result = new RecordedBodyFileExtractor(new MockServerLogger(), directory, 5, 60_000).extract(Collections.singletonList(original));

        assertThat(result.get(0), sameInstance(original));
        assertThat(new File(directory).exists(), is(false));
    }

    @Test
    public void shouldExtractEachResponseOfASequenceAndShareIdenticalBodies() throws Exception {
        String directory = temporaryFolder.getRoot().getPath();
        byte[] binary = new byte[]{0, 1, 2, (byte) 0xFF, 4, 5, 6, 7, 8, 9};
        Expectation sequence = new Expectation(request("/report"), Times.unlimited(), TimeToLive.unlimited(), 0)
            .withResponseMode(ResponseMode.SWITCH)
            .thenRespond(Arrays.asList(
                response().withBody(binary),
                response().withBody(binary),
                response().withBody("tiny")
            ));

        List<Expectation> result = new RecordedBodyFileExtractor(new MockServerLogger(), directory, 4, 60_000).extract(Collections.singletonList(sequence));

        List<HttpResponse> responses = result.get(0).getHttpResponses();
        assertThat(result.get(0).getResponseMode(), is(ResponseMode.SWITCH));
        FileBody first = (FileBody) responses.get(0).getBody();
        FileBody second = (FileBody) responses.get(1).getBody();
        assertThat(first.getFilePath(), is(second.getFilePath()));
        assertThat(first.getFilePath(), matchesPattern(".*[0-9a-f]{64}\\.bin"));
        assertThat(Files.readAllBytes(Path.of(first.getFilePath())), is(binary));
        assertThat(responses.get(2).getBody(), instanceOf(org.mockserver.model.StringBody.class));
        try (Stream<Path> files = Files.list(temporaryFolder.getRoot().toPath())) {
            assertThat(files.count(), is(1L));
        }
    }

    @Test
    public void shouldReplaceASymbolicLinkAtTheTargetNameRatherThanWriteThroughIt() throws Exception {
        File directory = temporaryFolder.newFolder("bodies");
        File outside = temporaryFolder.newFile("outside.txt");
        Files.write(outside.toPath(), "untouched".getBytes(StandardCharsets.UTF_8));
        byte[] body = "0123456789abcdef".getBytes(StandardCharsets.UTF_8);
        Expectation original = recorded(response().withBody(new BinaryBody(body)));
        String fileName = Path.of(((FileBody) new RecordedBodyFileExtractor(new MockServerLogger(), temporaryFolder.newFolder("probe").getPath(), 0, 60_000)
            .extract(Collections.singletonList(original)).get(0).getHttpResponse().getBody()).getFilePath()).getFileName().toString();
        Files.createSymbolicLink(directory.toPath().resolve(fileName), outside.toPath());

        new RecordedBodyFileExtractor(new MockServerLogger(), directory.getPath(), 0, 60_000).extract(Collections.singletonList(original));

        assertThat(new String(Files.readAllBytes(outside.toPath()), StandardCharsets.UTF_8), is("untouched"));
        assertThat(Files.isSymbolicLink(directory.toPath().resolve(fileName)), is(false));
        assertThat(Files.readAllBytes(directory.toPath().resolve(fileName)), is(body));
    }

    @Test
    public void shouldKeepTheBodyInlineWhenTheDirectoryCannotBeWritten() throws Exception {
        File notADirectory = temporaryFolder.newFile("file");
        Expectation original = recorded(response().withBody("a body that is long enough"));

        List<Expectation> result = new RecordedBodyFileExtractor(new MockServerLogger(), notADirectory.getPath(), 1, 60_000).extract(Collections.singletonList(original));

        assertThat(result.get(0), sameInstance(original));
    }

    @Test
    public void shouldKeepBodiesInlineOnceTheFileOrByteLimitIsReached() throws Exception {
        String directory = temporaryFolder.getRoot().getPath();
        List<Expectation> recorded = Arrays.asList(
            recorded(response().withBody("first body")),
            recorded(response().withBody("first body")),
            recorded(response().withBody("second body")),
            recorded(response().withBody("third body"))
        );

        List<Expectation> byFiles = new RecordedBodyFileExtractor(new MockServerLogger(), directory, 0, 2, Long.MAX_VALUE, 60_000).extract(recorded);
        List<Expectation> byBytes = new RecordedBodyFileExtractor(new MockServerLogger(), temporaryFolder.newFolder("bytes").getPath(), 0, 100, 15, 60_000).extract(recorded);

        assertThat(byFiles.get(0).getHttpResponse().getBody(), instanceOf(FileBody.class));
        assertThat(byFiles.get(1).getHttpResponse().getBody(), instanceOf(FileBody.class));
        assertThat(byFiles.get(2).getHttpResponse().getBody(), instanceOf(FileBody.class));
        assertThat(byFiles.get(3), sameInstance(recorded.get(3)));
        assertThat(byBytes.get(1).getHttpResponse().getBody(), instanceOf(FileBody.class));
        assertThat(byBytes.get(2), sameInstance(recorded.get(2)));
        try (Stream<Path> files = Files.list(temporaryFolder.getRoot().toPath())) {
            assertThat(files.filter(Files::isRegularFile).count(), is(2L));
        }
    }

    @Test
    public void shouldStartNoNewWriteOnceTheTimeBudgetIsSpent() {
        String directory = temporaryFolder.getRoot().getPath();
        Expectation original = recorded(response().withBody("a body that is long enough"));

        List<Expectation> result = new RecordedBodyFileExtractor(new MockServerLogger(), directory, 1, 0).extract(Collections.singletonList(original));

        assertThat(result.get(0), sameInstance(original));
        assertThat(temporaryFolder.getRoot().list().length, is(0));
    }

    @Test
    public void shouldRejectAMissingDirectoryOrNegativeThreshold() {
        assertThrows(IllegalArgumentException.class, () -> new RecordedBodyFileExtractor(new MockServerLogger(), " ", 1, 60_000));
        assertThrows(IllegalArgumentException.class, () -> new RecordedBodyFileExtractor(new MockServerLogger(), "dir", -1, 60_000));
    }

    @Test
    public void shouldChooseTheExtensionFromTheContentTypeOnly() {
        assertThat(RecordedBodyFileExtractor.extension("application/problem+json; charset=utf-8"), is(".json"));
        assertThat(RecordedBodyFileExtractor.extension("text/xml"), is(".xml"));
        assertThat(RecordedBodyFileExtractor.extension("text/html"), is(".html"));
        assertThat(RecordedBodyFileExtractor.extension("text/plain"), is(".txt"));
        assertThat(RecordedBodyFileExtractor.extension("application/../../etc"), is(".bin"));
        assertThat(RecordedBodyFileExtractor.extension(null), is(".bin"));
    }
}
