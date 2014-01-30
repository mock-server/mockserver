package org.mockserver.netty.proxy.direct;

import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.http.HttpHeaders;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author jamesdbloom
 */
public class BasicHttpDecoder {

    protected static final Logger logger = LoggerFactory.getLogger(BasicHttpDecoder.class);
    private final ByteBuf byteBuf;
    private Integer contentLength = null;
    private int contentStart;

    public BasicHttpDecoder(ByteBuf byteBuf) {
        this.byteBuf = byteBuf;
        readContentLength();
        byteBuf.release();
    }

    private String readLine() {
        StringBuilder stringBuffer = new StringBuilder();
        while (byteBuf.isReadable()) {
            char nextCharacter = (char) byteBuf.readByte();
            if (nextCharacter == '\r' || nextCharacter == '\n') {
                if(byteBuf.isReadable()) {
                    // swallow '\r' or '\n'
                    byteBuf.readByte();
                }
                break;
            } else {
                stringBuffer.append(nextCharacter);
            }
        }
        return stringBuffer.toString();
    }

    private void readContentLength() {
        while (byteBuf.isReadable()) {
            String line = readLine();
//            logger.warn("LINE: " + line);
            if (line.startsWith(HttpHeaders.Names.CONTENT_LENGTH)) {
                contentLength = Integer.parseInt(line.split(":")[1].trim());
                logger.warn("CONTENT-LENGTH: --- " + contentLength + " ----");
            }
            if (contentLength != null && line.isEmpty()) {
                contentStart = byteBuf.readerIndex();
                logger.warn("CONTENT-START: --- " + contentStart + " ----");
                break;
            }
        }
    }

    public Integer getContentLength() {
        return contentLength;
    }

    public int getContentStart() {
        return contentStart;
    }
}
