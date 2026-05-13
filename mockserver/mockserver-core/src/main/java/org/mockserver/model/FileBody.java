package org.mockserver.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import org.mockserver.file.FileReader;

import java.util.Objects;

import static org.mockserver.model.MediaType.DEFAULT_TEXT_HTTP_CHARACTER_SET;

public class FileBody extends BodyWithContentType<String> {
    private int hashCode;
    private final String filePath;

    public FileBody(String filePath) {
        this(filePath, null);
    }

    public FileBody(String filePath, MediaType contentType) {
        super(Type.FILE, contentType);
        this.filePath = filePath;
    }

    public static FileBody file(String filePath) {
        return new FileBody(filePath);
    }

    public static FileBody file(String filePath, MediaType contentType) {
        return new FileBody(filePath, contentType);
    }

    public String getFilePath() {
        return filePath;
    }

    public String getValue() {
        return filePath;
    }

    @Override
    @JsonIgnore
    public byte[] getRawBytes() {
        try {
            String content = FileReader.readFileFromClassPathOrPath(filePath);
            return content.getBytes(determineCharacterSet(contentType, DEFAULT_TEXT_HTTP_CHARACTER_SET));
        } catch (Throwable t) {
            return new byte[0];
        }
    }

    @Override
    public String toString() {
        try {
            return FileReader.readFileFromClassPathOrPath(filePath);
        } catch (Throwable t) {
            return "";
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        if (hashCode() != o.hashCode()) {
            return false;
        }
        if (!super.equals(o)) {
            return false;
        }
        FileBody fileBody = (FileBody) o;
        return Objects.equals(filePath, fileBody.filePath);
    }

    @Override
    public int hashCode() {
        if (hashCode == 0) {
            hashCode = Objects.hash(super.hashCode(), filePath);
        }
        return hashCode;
    }
}
