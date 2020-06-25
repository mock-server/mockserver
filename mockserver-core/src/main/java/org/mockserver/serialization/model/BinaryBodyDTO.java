package org.mockserver.serialization.model;

import org.mockserver.model.BinaryBody;

/**
 * @author jamesdbloom
 */
public class BinaryBodyDTO extends BodyWithContentTypeDTO {

    private final byte[] base64Bytes;

    public BinaryBodyDTO(BinaryBody binaryBody) {
        this(binaryBody, null);
    }

    public BinaryBodyDTO(BinaryBody binaryBody, Boolean not) {
        super(binaryBody.getType(), not, binaryBody);
        if (binaryBody.getRawBytes() != null) {
            base64Bytes = binaryBody.getRawBytes();
        } else {
            base64Bytes = new byte[0];
        }
    }

    public byte[] getBase64Bytes() {
        return base64Bytes;
    }

    public BinaryBody buildObject() {
        return new BinaryBody(getBase64Bytes(), getMediaType());
    }
}
