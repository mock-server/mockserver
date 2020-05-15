package org.mockserver.codec;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToMessageDecoder;

import java.util.List;

import static org.mockserver.model.BinaryMessage.bytes;

public class NettyBinaryToMockServerResponseDecoder extends MessageToMessageDecoder<ByteBuf> {
    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf byteBuf, List<Object> out) {
        out.add(bytes(ByteBufUtil.getBytes(byteBuf)));
    }
}
