package org.mockserver.file;

import org.apache.commons.io.IOUtils;

import java.io.*;
import java.nio.charset.StandardCharsets;

/**
 * @author jamesdbloom
 */
public class FileReader {

    public static String readFileFromClassPathOrPath(String filePath) {
        try (InputStream inputStream = openStreamToFileFromClassPathOrPath(filePath)) {
            return IOUtils.toString(inputStream, StandardCharsets.UTF_8.name());
        } catch (IOException ioe) {
            throw new RuntimeException("Exception while loading \"" + filePath + "\"", ioe);
        }
    }

    // used by maven plugin
    public static String readFileFromClassPathOrPath(ClassLoader classLoader, String filePath) {
        try (InputStream inputStream = openStreamToFileFromClassPathOrPath(classLoader, filePath)) {
            return IOUtils.toString(inputStream, StandardCharsets.UTF_8.name());
        } catch (IOException ioe) {
            throw new RuntimeException("Exception while loading \"" + filePath + "\"", ioe);
        }
    }

    public static InputStream openStreamToFileFromClassPathOrPath(String filename) throws FileNotFoundException {
        return openStreamToFileFromClassPathOrPath(FileReader.class.getClassLoader(), filename);
    }

    public static InputStream openStreamToFileFromClassPathOrPath(ClassLoader classLoader, String filename) throws FileNotFoundException {
        InputStream inputStream = classLoader.getResourceAsStream(filename);
        if (inputStream == null) {
            // load from path if not found in classpath
            inputStream = new FileInputStream(filename);
        }
        return inputStream;
    }

    public static Reader openReaderToFileFromClassPathOrPath(String filename) throws FileNotFoundException {
        return new InputStreamReader(openStreamToFileFromClassPathOrPath(filename));
    }


}
