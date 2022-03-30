package org.mockserver.test;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

public class TempFileWriter {

    public static String write(String data) {
        try {
            File jwkTempFile = File.createTempFile("prefix", "suffix");
            FileWriter fileWriter = new FileWriter(jwkTempFile);
            fileWriter.write(data);
            fileWriter.flush();
            fileWriter.close();
            return jwkTempFile.getAbsolutePath();
        } catch (Throwable throwable) {
            throw new RuntimeException("Exception writing temporary file", throwable);
        }
    }

}
