package org.mockserver.codec;

import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToMessageEncoder;
import org.mockserver.model.BinaryMessage;

import java.util.List;

public class MockServerBinaryToNettyRequestEncoder extends MessageToMessageEncoder<BinaryMessage> {
    @Override
    protected void encode(ChannelHandlerContext ctx, BinaryMessage binaryMessage, List<Object> out) {
        out.add(Unpooled.copiedBuffer(binaryMessage.getBytes()));
    }
}
