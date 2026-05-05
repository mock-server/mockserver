package org.mockserver.test;

import java.io.File;
import java.io.FileWriter;

public class TempFileWriter {

    public static String write(String data) {
        try {
            File tempFile = File.createTempFile("prefix", "suffix");
            FileWriter fileWriter = new FileWriter(tempFile);
            fileWriter.write(data);
            fileWriter.flush();
            fileWriter.close();
            return tempFile.getAbsolutePath();
        } catch (Throwable throwable) {
            throw new RuntimeException("Exception writing temporary file", throwable);
        }
    }

}
