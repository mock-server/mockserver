package org.mockserver.streams;

import org.apache.commons.io.IOUtils;
import org.mockserver.log.model.LogEntry;
import org.mockserver.logging.MockServerLogger;
import org.slf4j.event.Level;

import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.ByteBuffer;

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

    public static String readInputStreamToString(Socket socket) throws IOException {
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
    }

    public String readInputStreamToString(ServletRequest request) {
        try {
            return IOUtils.toString(request.getInputStream(), UTF_8.name());
        } catch (IOException ioe) {
            mockServerLogger.logEvent(
                new LogEntry()
                    .setType(LogEntry.LogMessageType.EXCEPTION)
                    .setLogLevel(Level.ERROR)
                    .setMessageFormat("IOException while reading HttpServletRequest input stream")
                    .setThrowable(ioe)
            );
            throw new RuntimeException("IOException while reading HttpServletRequest input stream", ioe);
        }
    }

    public byte[] readInputStreamToByteArray(ServletRequest request) {
        try {
            return IOUtils.toByteArray(request.getInputStream());
        } catch (IOException ioe) {
            mockServerLogger.logEvent(
                new LogEntry()
                    .setType(LogEntry.LogMessageType.EXCEPTION)
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
                    .setType(LogEntry.LogMessageType.EXCEPTION)
                    .setLogLevel(Level.ERROR)
                    .setMessageFormat("IOException while writing [" + new String(data) + "] to HttpServletResponse output stream")
                    .setThrowable(ioe)
            );
            throw new RuntimeException("IOException while writing [" + new String(data) + "] to HttpServletResponse output stream", ioe);
        }
    }

}
