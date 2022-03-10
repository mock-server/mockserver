package org.mockserver.file;

import org.mockserver.log.model.LogEntry;
import org.mockserver.logging.MockServerLogger;

import java.io.File;
import java.io.IOException;

import static org.slf4j.event.Level.ERROR;

public class FileCreator {

    private static final MockServerLogger MOCK_SERVER_LOGGER = new MockServerLogger(FileCreator.class);

    public static File createFileIfNotExists(String type, File file) {
        if (!file.exists()) {
            try {
                createParentDirs(file);
                if (!file.createNewFile()) {
                    MOCK_SERVER_LOGGER.logEvent(
                        new LogEntry()
                            .setLogLevel(ERROR)
                            .setMessageFormat("failed to create the file{}while attempting to save " + type + " file")
                            .setArguments(file.getAbsolutePath())
                    );

                }
            } catch (Throwable throwable) {
                MOCK_SERVER_LOGGER.logEvent(
                    new LogEntry()
                        .setLogLevel(ERROR)
                        .setMessageFormat("failed to create the file{}while attempting to save " + type + " file")
                        .setArguments(file.getAbsolutePath())
                        .setThrowable(throwable)
                );
            }
        }
        return file;
    }

    @SuppressWarnings("ResultOfMethodCallIgnored")
    public static void createParentDirs(File file) throws IOException {
        File parent = file.getCanonicalFile().getParentFile();
        if (parent == null) {
            /*
             * The given directory is a filesystem root. All zero of its ancestors exist. This doesn't
             * mean that the root itself exists -- consider x:\ on a Windows machine without such a drive
             * -- or even that the caller can create it, but this method makes no such guarantees even for
             * non-root files.
             */
            return;
        }
        createParentDirs(parent);
        if (!parent.exists()) {
            parent.mkdirs();
        }
        if (!parent.isDirectory()) {
            throw new IOException("Unable to create parent directories of " + file);
        }
    }

}
