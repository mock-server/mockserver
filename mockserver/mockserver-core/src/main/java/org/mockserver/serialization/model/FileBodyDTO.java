package org.mockserver.serialization.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.mockserver.model.FileBody;
import org.mockserver.model.HttpTemplate;

public class FileBodyDTO extends BodyWithContentTypeDTO {

    private final String filePath;
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private final HttpTemplate.TemplateType templateType;
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private final Boolean templatedFilePath;

    public FileBodyDTO(FileBody fileBody) {
        this(fileBody, fileBody.getNot());
    }

    public FileBodyDTO(FileBody fileBody, Boolean not) {
        super(fileBody.getType(), not, fileBody);
        filePath = fileBody.getFilePath();
        templateType = fileBody.getTemplateType();
        templatedFilePath = fileBody.getTemplatedFilePath();
    }

    public String getFilePath() {
        return filePath;
    }

    public HttpTemplate.TemplateType getTemplateType() {
        return templateType;
    }

    public Boolean getTemplatedFilePath() {
        return templatedFilePath;
    }

    public FileBody buildObject() {
        return (FileBody) new FileBody(getFilePath(), getMediaType(), getTemplateType()).withTemplatedFilePath(getTemplatedFilePath()).withOptional(getOptional());
    }
}
