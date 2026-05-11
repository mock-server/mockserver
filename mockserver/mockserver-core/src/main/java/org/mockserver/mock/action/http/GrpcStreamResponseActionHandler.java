package org.mockserver.mock.action.http;

import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.*;
import org.mockserver.grpc.GrpcFrameCodec;
import org.mockserver.grpc.GrpcJsonMessageConverter;
import org.mockserver.grpc.GrpcProtoDescriptorStore;
import org.mockserver.grpc.GrpcStatusMapper;
import org.mockserver.log.model.LogEntry;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.model.Delay;
import org.mockserver.model.GrpcStreamMessage;
import org.mockserver.model.GrpcStreamResponse;
import org.mockserver.model.HttpResponse;
import org.mockserver.scheduler.Scheduler;
import org.slf4j.event.Level;

import java.util.List;

import static org.mockserver.log.model.LogEntry.LogMessageType.EXPECTATION_RESPONSE;

public class GrpcStreamResponseActionHandler {

    private final MockServerLogger mockServerLogger;
    private final Scheduler scheduler;
    private final GrpcProtoDescriptorStore descriptorStore;

    public GrpcStreamResponseActionHandler(MockServerLogger mockServerLogger, Scheduler scheduler, GrpcProtoDescriptorStore descriptorStore) {
        this.mockServerLogger = mockServerLogger;
        this.scheduler = scheduler;
        this.descriptorStore = descriptorStore;
    }

    public void handle(GrpcStreamResponse grpcStreamResponse, ChannelHandlerContext ctx, org.mockserver.model.HttpRequest request) {
        String serviceName = request.getFirstHeader("x-grpc-service");
        String methodName = request.getFirstHeader("x-grpc-method");

        com.google.protobuf.Descriptors.MethodDescriptor methodDescriptor = null;
        if (serviceName != null && !serviceName.isEmpty() && methodName != null && !methodName.isEmpty()) {
            methodDescriptor = descriptorStore.getMethod(serviceName, methodName);
        }

        DefaultHttpResponse initialResponse = new DefaultHttpResponse(
            HttpVersion.HTTP_1_1,
            HttpResponseStatus.OK
        );

        initialResponse.headers().set(HttpHeaderNames.CONTENT_TYPE, GrpcStatusMapper.GRPC_CONTENT_TYPE);
        initialResponse.headers().set(HttpHeaderNames.TRANSFER_ENCODING, "chunked");

        if (grpcStreamResponse.getHeaders() != null) {
            grpcStreamResponse.getHeaders().getEntries().forEach(header ->
                header.getValues().forEach(value ->
                    initialResponse.headers().add(header.getName().getValue(), value.getValue())
                )
            );
        }

        ctx.writeAndFlush(initialResponse);

        List<GrpcStreamMessage> messages = grpcStreamResponse.getMessages();
        if (messages != null && !messages.isEmpty()) {
            scheduleMessages(messages, 0, ctx, grpcStreamResponse, request, methodDescriptor);
        } else {
            finishStream(ctx, grpcStreamResponse);
        }
    }

    private void scheduleMessages(List<GrpcStreamMessage> messages, int index, ChannelHandlerContext ctx, GrpcStreamResponse grpcStreamResponse, org.mockserver.model.HttpRequest request, com.google.protobuf.Descriptors.MethodDescriptor methodDescriptor) {
        if (index >= messages.size() || !ctx.channel().isActive()) {
            finishStream(ctx, grpcStreamResponse);
            return;
        }

        GrpcStreamMessage message = messages.get(index);
        Delay delay = message.getDelay();

        Runnable writeMessage = () -> {
            try {
                if (!ctx.channel().isActive()) {
                    return;
                }
                byte[] frameBytes = encodeMessage(message, methodDescriptor);
                DefaultHttpContent content = new DefaultHttpContent(
                    Unpooled.wrappedBuffer(frameBytes)
                );
                ctx.writeAndFlush(content).addListener(future -> {
                    if (future.isSuccess()) {
                        if (mockServerLogger.isEnabledForInstance(Level.DEBUG)) {
                            mockServerLogger.logEvent(
                                new LogEntry()
                                    .setType(EXPECTATION_RESPONSE)
                                    .setLogLevel(Level.DEBUG)
                                    .setCorrelationId(request.getLogCorrelationId())
                                    .setHttpRequest(request)
                                    .setMessageFormat("sent gRPC stream message {} of {} for request:{}")
                                    .setArguments(index + 1, messages.size(), request)
                            );
                        }
                        scheduleMessages(messages, index + 1, ctx, grpcStreamResponse, request, methodDescriptor);
                    } else {
                        if (mockServerLogger.isEnabledForInstance(Level.WARN)) {
                            mockServerLogger.logEvent(
                                new LogEntry()
                                    .setLogLevel(Level.WARN)
                                    .setCorrelationId(request.getLogCorrelationId())
                                    .setHttpRequest(request)
                                    .setMessageFormat("async write failure for gRPC stream message {} for request:{}")
                                    .setArguments(index + 1, request)
                                    .setThrowable(future.cause())
                            );
                        }
                        finishStream(ctx, grpcStreamResponse);
                    }
                });
            } catch (Exception e) {
                if (mockServerLogger.isEnabledForInstance(Level.WARN)) {
                    mockServerLogger.logEvent(
                        new LogEntry()
                            .setLogLevel(Level.WARN)
                            .setCorrelationId(request.getLogCorrelationId())
                            .setHttpRequest(request)
                            .setMessageFormat("exception sending gRPC stream message {} for request:{}")
                            .setArguments(index + 1, request)
                            .setThrowable(e)
                    );
                }
                finishStream(ctx, grpcStreamResponse);
            }
        };

        if (delay != null) {
            scheduler.schedule(writeMessage, false, delay);
        } else {
            writeMessage.run();
        }
    }

    private byte[] encodeMessage(GrpcStreamMessage message, com.google.protobuf.Descriptors.MethodDescriptor methodDescriptor) {
        String json = message.getJson();
        if (json == null || json.isEmpty()) {
            return GrpcFrameCodec.encode(new byte[0]);
        }

        if (methodDescriptor != null) {
            GrpcJsonMessageConverter converter = descriptorStore.getConverter();
            byte[] protobufBytes = converter.toProtobuf(json, methodDescriptor.getOutputType());
            return GrpcFrameCodec.encode(protobufBytes);
        } else {
            return GrpcFrameCodec.encode(json.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        }
    }

    private void finishStream(ChannelHandlerContext ctx, GrpcStreamResponse grpcStreamResponse) {
        if (ctx.channel().isActive()) {
            GrpcStatusMapper.GrpcStatusCode statusCode = GrpcStatusMapper.GrpcStatusCode.OK;
            if (grpcStreamResponse.getStatusName() != null && !grpcStreamResponse.getStatusName().isEmpty()) {
                statusCode = GrpcStatusMapper.fromName(grpcStreamResponse.getStatusName());
            }

            DefaultLastHttpContent trailers = new DefaultLastHttpContent();
            trailers.trailingHeaders().set(GrpcStatusMapper.GRPC_STATUS_HEADER, String.valueOf(statusCode.getCode()));
            if (grpcStreamResponse.getStatusMessage() != null && !grpcStreamResponse.getStatusMessage().isEmpty()) {
                trailers.trailingHeaders().set(GrpcStatusMapper.GRPC_MESSAGE_HEADER, grpcStreamResponse.getStatusMessage());
            }

            ctx.writeAndFlush(trailers).addListener(future -> {
                if (grpcStreamResponse.getCloseConnection() != null && grpcStreamResponse.getCloseConnection()) {
                    ctx.close();
                }
            });
        }
    }
}
