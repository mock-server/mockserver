package org.mockserver.file;

import com.google.common.base.Charsets;
import org.apache.commons.io.IOUtils;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;

/**
 * @author jamesdbloom
 */
public class FileReader {

    public static String readFileFromClassPathOrPath(String filePath) {
        try {
            return IOUtils.toString(openStreamToFileFromClassPathOrPath(filePath), Charsets.UTF_8.name());
        } catch (IOException e) {
            throw new RuntimeException("Exception while loading \"" + filePath + "\"");
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


}
