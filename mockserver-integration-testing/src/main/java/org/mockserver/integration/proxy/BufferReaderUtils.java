package org.mockserver.integration.proxy;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

/**
 * @author jamesdbloom
 */
public class BufferReaderUtils {

    public static String readerToString(InputStream inputStream) throws IOException {
        BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(inputStream));
        StringBuilder result = new StringBuilder();
        String line;
        Integer contentLength = null;
        while ((line = bufferedReader.readLine()) != null) {
            if (line.startsWith("Content-Length")) {
                contentLength = Integer.parseInt(line.split(":")[1].trim());
            }
            if (line.length() == 0) {
                if (contentLength != null) {
                    result.append('\n');
                    for (int position = 0; position < contentLength; position++) {
                        result.append((char) bufferedReader.read());
                    }
                }
                break;
            }
            result.append(line).append('\n');
        }
        return result.toString();
    }
}
