package org.mockserver.streams;

import com.google.common.io.ByteStreams;
import org.mockserver.log.model.LogEntry;
import org.mockserver.logging.MockServerLogger;
import org.slf4j.event.Level;

import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import java.io.*;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.mockserver.character.Character.NEW_LINE;

/**
 * @author jamesdbloom
 */
public class IOStreamUtils {
    private final MockServerLogger mockServerLogger;

    public IOStreamUtils(MockServerLogger mockServerLogger) {
        this.mockServerLogger = mockServerLogger;
    }

    public static String readHttpInputStreamToString(Socket socket) {
        try {
            BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            StringBuilder result = new StringBuilder();
            String line;
            Integer contentLength = null;
            while ((line = bufferedReader.readLine()) != null) {
                if (line.startsWith("content-length") || line.startsWith("Content-Length")) {
                    contentLength = Integer.parseInt(line.split(":")[1].trim());
                }
                if (line.length() == 0) {
                    if (contentLength != null) {
                        result.append(NEW_LINE);
                        for (int position = 0; position < contentLength; position++) {
                            result.append((char) bufferedReader.read());
                        }
                    }
                    break;
                }
                result.append(line).append(NEW_LINE);
            }
            return result.toString();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static String readSocketToString(Socket socket) {
        StringBuilder result = new StringBuilder();
        try {
            InputStream inputStream = socket.getInputStream();
            do {
                final byte[] buffer = new byte[10000];
                final int readBytes = inputStream.read(buffer);
                result.append(new String(
                    Arrays.copyOfRange(buffer, 0, readBytes),
                    StandardCharsets.UTF_8
                ));
            } while (inputStream.available() > 0);
            return result.toString();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public String readHttpInputStreamToString(ServletRequest request) {
        try {
            return new String(ByteStreams.toByteArray(request.getInputStream()), request.getCharacterEncoding() != null ? request.getCharacterEncoding() : UTF_8.name());
        } catch (IOException ioe) {
            mockServerLogger.logEvent(
                new LogEntry()
                    .setLogLevel(Level.ERROR)
                    .setMessageFormat("IOException while reading HttpServletRequest input stream")
                    .setThrowable(ioe)
            );
            throw new RuntimeException("IOException while reading HttpServletRequest input stream", ioe);
        }
    }

    public void writeToOutputStream(byte[] data, ServletResponse response) {
        try {
            OutputStream output = response.getOutputStream();
            output.write(data);
            output.close();
        } catch (IOException ioe) {
            mockServerLogger.logEvent(
                new LogEntry()
                    .setLogLevel(Level.ERROR)
                    .setMessageFormat("IOException while writing [" + new String(data) + "] to HttpServletResponse output stream")
                    .setThrowable(ioe)
            );
            throw new RuntimeException("IOException while writing [" + new String(data) + "] to HttpServletResponse output stream", ioe);
        }
    }

}
