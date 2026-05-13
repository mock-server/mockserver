package org.mockserver.serialization.model;

import org.mockserver.model.FileBody;

public class FileBodyDTO extends BodyWithContentTypeDTO {

    private final String filePath;

    public FileBodyDTO(FileBody fileBody) {
        this(fileBody, fileBody.getNot());
    }

    public FileBodyDTO(FileBody fileBody, Boolean not) {
        super(fileBody.getType(), not, fileBody);
        filePath = fileBody.getFilePath();
    }

    public String getFilePath() {
        return filePath;
    }

    public FileBody buildObject() {
        return (FileBody) new FileBody(getFilePath(), getMediaType()).withOptional(getOptional());
    }
}
