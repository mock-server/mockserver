package org.mockserver.file;

import com.google.common.io.ByteStreams;
import org.mockserver.log.model.LogEntry;
import org.mockserver.logging.MockServerLogger;

import java.io.*;
import java.net.MalformedURLException;
import java.net.URL;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.slf4j.event.Level.ERROR;
import static org.slf4j.event.Level.TRACE;

/**
 * @author jamesdbloom
 */
public class FileReader {

    public static String readFileFromClassPathOrPath(String filePath) {
        try (InputStream inputStream = openStreamToFileFromClassPathOrPath(filePath)) {
            return new String(ByteStreams.toByteArray(inputStream), UTF_8.name());
        } catch (IOException ioe) {
            throw new RuntimeException("Exception while loading \"" + filePath + "\"", ioe);
        }
    }

    public static InputStream openStreamToFileFromClassPathOrPath(String filename) throws FileNotFoundException {
        InputStream inputStream = FileReader.class.getClassLoader().getResourceAsStream(filename);
        if (inputStream == null) {
            // load from path if not found in classpath
            inputStream = new FileInputStream(filename);
        }
        return inputStream;
    }

    public static Reader openReaderToFileFromClassPathOrPath(String filename) throws FileNotFoundException {
        return new InputStreamReader(openStreamToFileFromClassPathOrPath(filename));
    }

    public static URL getURL(String filepath) {
        File file = new File(filepath);
        if (file.exists()) {
            try {
                return file.toURI().toURL();
            } catch (MalformedURLException murle) {
                new MockServerLogger(FileReader.class).logEvent(
                    new LogEntry()
                        .setLogLevel(ERROR)
                        .setMessageFormat("exception while build file URL " + murle.getMessage())
                        .setThrowable(murle)
                );
            }
        }
        return FileReader.class.getClassLoader().getResource(filepath);
    }

}
