package org.mockserver.netty.grpc;

import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import org.mockserver.grpc.GrpcException;
import org.mockserver.grpc.GrpcFrameCodec;
import org.mockserver.grpc.GrpcJsonMessageConverter;
import org.mockserver.grpc.GrpcProtoDescriptorStore;
import org.mockserver.grpc.GrpcStatusMapper;
import com.google.protobuf.Descriptors;
import org.mockserver.log.model.LogEntry;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.model.HttpRequest;
import org.slf4j.event.Level;

import java.util.List;

@ChannelHandler.Sharable
public class GrpcToHttpRequestHandler extends SimpleChannelInboundHandler<HttpRequest> {

    private final MockServerLogger mockServerLogger;
    private final GrpcProtoDescriptorStore descriptorStore;

    public GrpcToHttpRequestHandler(MockServerLogger mockServerLogger, GrpcProtoDescriptorStore descriptorStore) {
        this.mockServerLogger = mockServerLogger;
        this.descriptorStore = descriptorStore;
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, HttpRequest request) {
        String contentType = request.getFirstHeader("content-type");
        if (GrpcStatusMapper.isGrpcContentType(contentType) && descriptorStore.hasServices()) {
            try {
                HttpRequest converted = convertGrpcRequest(request);
                ctx.fireChannelRead(converted);
            } catch (GrpcException e) {
                mockServerLogger.logEvent(
                    new LogEntry()
                        .setLogLevel(Level.WARN)
                        .setMessageFormat("gRPC request error:{}:{}")
                        .setArguments(request.getPath(), e.getMessage())
                );
                GrpcStatusMapper.GrpcStatusCode statusCode = e.getMessage() != null && e.getMessage().startsWith("unknown gRPC method")
                    ? GrpcStatusMapper.GrpcStatusCode.UNIMPLEMENTED
                    : GrpcStatusMapper.GrpcStatusCode.INTERNAL;
                org.mockserver.model.HttpResponse errorResponse = org.mockserver.model.HttpResponse.response()
                    .withStatusCode(200)
                    .withHeader("content-type", GrpcStatusMapper.GRPC_CONTENT_TYPE)
                    .withHeader(GrpcStatusMapper.GRPC_STATUS_HEADER, String.valueOf(statusCode.getCode()))
                    .withHeader(GrpcStatusMapper.GRPC_MESSAGE_HEADER, e.getMessage());
                ctx.writeAndFlush(errorResponse);
            } catch (Exception e) {
                mockServerLogger.logEvent(
                    new LogEntry()
                        .setLogLevel(Level.WARN)
                        .setMessageFormat("failed to convert gRPC request to JSON:{}:{}")
                        .setArguments(request.getPath(), e.getMessage())
                );
                org.mockserver.model.HttpResponse errorResponse = org.mockserver.model.HttpResponse.response()
                    .withStatusCode(200)
                    .withHeader("content-type", GrpcStatusMapper.GRPC_CONTENT_TYPE)
                    .withHeader(GrpcStatusMapper.GRPC_STATUS_HEADER, String.valueOf(GrpcStatusMapper.GrpcStatusCode.INTERNAL.getCode()))
                    .withHeader(GrpcStatusMapper.GRPC_MESSAGE_HEADER, "failed to decode gRPC request: " + e.getMessage());
                ctx.writeAndFlush(errorResponse);
            }
        } else {
            ctx.fireChannelRead(request);
        }
    }

    private HttpRequest convertGrpcRequest(HttpRequest request) {
        String path = request.getPath() != null ? request.getPath().getValue() : "";
        String[] parts = parseGrpcPath(path);
        String serviceName = parts[0];
        String methodName = parts[1];

        Descriptors.MethodDescriptor methodDescriptor = descriptorStore.getMethod(serviceName, methodName);
        if (methodDescriptor == null) {
            throw new GrpcException("unknown gRPC method: " + serviceName + "/" + methodName);
        }

        byte[] bodyBytes = request.getBodyAsRawBytes();
        if (bodyBytes == null || bodyBytes.length == 0) {
            return request;
        }

        List<byte[]> messages = GrpcFrameCodec.decode(bodyBytes);
        if (messages.isEmpty()) {
            throw new GrpcException("failed to decode gRPC frame from request body");
        }
        GrpcJsonMessageConverter converter = descriptorStore.getConverter();

        if (messages.size() == 1) {
            String json = converter.toJson(messages.get(0), methodDescriptor.getInputType());
            return request
                .clone()
                .withBody(json)
                .withHeader("x-grpc-service", serviceName)
                .withHeader("x-grpc-method", methodName)
                .withHeader("x-grpc-original-content-type", request.getFirstHeader("content-type"));
        } else if (messages.size() > 1) {
            StringBuilder jsonArray = new StringBuilder("[");
            for (int i = 0; i < messages.size(); i++) {
                if (i > 0) {
                    jsonArray.append(",");
                }
                jsonArray.append(converter.toJson(messages.get(i), methodDescriptor.getInputType()));
            }
            jsonArray.append("]");
            return request
                .clone()
                .withBody(jsonArray.toString())
                .withHeader("x-grpc-service", serviceName)
                .withHeader("x-grpc-method", methodName)
                .withHeader("x-grpc-original-content-type", request.getFirstHeader("content-type"))
                .withHeader("x-grpc-client-streaming", "true");
        }

        return request;
    }

    static String[] parseGrpcPath(String path) {
        if (path == null || path.isEmpty()) {
            return new String[]{"", ""};
        }
        if (path.startsWith("/")) {
            path = path.substring(1);
        }
        int slashIndex = path.lastIndexOf('/');
        if (slashIndex < 1 || slashIndex == path.length() - 1) {
            return new String[]{path, ""};
        }
        return new String[]{path.substring(0, slashIndex), path.substring(slashIndex + 1)};
    }
}
