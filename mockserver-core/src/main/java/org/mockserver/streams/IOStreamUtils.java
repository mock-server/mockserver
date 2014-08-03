package org.mockserver.streams;

import com.google.common.base.Charsets;
import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.ByteBuffer;

/**
 * @author jamesdbloom
 */
public class IOStreamUtils {
    private static final Logger logger = LoggerFactory.getLogger(IOStreamUtils.class);

    public static String readInputStreamToString(Socket socket) throws IOException {
        BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
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

    public static String readInputStreamToString(ServletRequest request) {
        try {
            return IOUtils.toString(request.getInputStream(), Charsets.UTF_8);
        } catch (IOException ioe) {
            logger.error("IOException while reading HttpServletRequest input stream", ioe);
            throw new RuntimeException("IOException while reading HttpServletRequest input stream", ioe);
        }
    }

    public static byte[] readInputStreamToByteArray(ServletRequest request) {
        try {
            return IOUtils.toByteArray(request.getInputStream());
        } catch (IOException ioe) {
            logger.error("IOException while reading HttpServletRequest input stream", ioe);
            throw new RuntimeException("IOException while reading HttpServletRequest input stream", ioe);
        }
    }

    public static void writeToOutputStream(byte[] data, ServletResponse response) {
        try {
            OutputStream output = response.getOutputStream();
            output.write(data);
            output.close();
        } catch (IOException ioe) {
            logger.error(String.format("IOException while writing [%s] to HttpServletResponse output stream", new String(data)), ioe);
            throw new RuntimeException(String.format("IOException while writing [%s] to HttpServletResponse output stream", new String(data)), ioe);
        }
    }

    public static ByteBuffer createBasicByteBuffer(String input) {
        ByteBuffer byteBuffer = ByteBuffer.allocateDirect(input.length()).put(input.getBytes());
        byteBuffer.flip();
        return byteBuffer;
    }
}
