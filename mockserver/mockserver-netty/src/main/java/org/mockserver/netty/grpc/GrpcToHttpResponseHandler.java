package org.mockserver.netty.grpc;

import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToMessageEncoder;
import com.google.protobuf.Descriptors;
import org.mockserver.grpc.GrpcFrameCodec;
import org.mockserver.grpc.GrpcJsonMessageConverter;
import org.mockserver.grpc.GrpcProtoDescriptorStore;
import org.mockserver.grpc.GrpcStatusMapper;
import org.mockserver.log.model.LogEntry;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.model.HttpResponse;
import org.slf4j.event.Level;

import java.util.List;

@ChannelHandler.Sharable
public class GrpcToHttpResponseHandler extends MessageToMessageEncoder<HttpResponse> {

    private final MockServerLogger mockServerLogger;
    private final GrpcProtoDescriptorStore descriptorStore;

    public GrpcToHttpResponseHandler(MockServerLogger mockServerLogger, GrpcProtoDescriptorStore descriptorStore) {
        this.mockServerLogger = mockServerLogger;
        this.descriptorStore = descriptorStore;
    }

    @Override
    protected void encode(ChannelHandlerContext ctx, HttpResponse response, List<Object> out) {
        String grpcService = response.getFirstHeader("x-grpc-service");
        String grpcMethod = response.getFirstHeader("x-grpc-method");

        if (grpcService != null && !grpcService.isEmpty() && grpcMethod != null && !grpcMethod.isEmpty()) {
            try {
                HttpResponse converted = convertToGrpcResponse(response, grpcService, grpcMethod);
                out.add(converted);
            } catch (Exception e) {
                mockServerLogger.logEvent(
                    new LogEntry()
                        .setLogLevel(Level.WARN)
                        .setMessageFormat("failed to convert response to gRPC for {}/{}:{}")
                        .setArguments(grpcService, grpcMethod, e.getMessage())
                );
                out.add(response.clone()
                    .withStatusCode(200)
                    .withHeader("content-type", GrpcStatusMapper.GRPC_CONTENT_TYPE)
                    .withHeader(GrpcStatusMapper.GRPC_STATUS_HEADER, String.valueOf(GrpcStatusMapper.GrpcStatusCode.INTERNAL.getCode()))
                    .withHeader(GrpcStatusMapper.GRPC_MESSAGE_HEADER, "failed to encode gRPC response: " + e.getMessage())
                    .removeHeader("x-grpc-service")
                    .removeHeader("x-grpc-method"));
            }
        } else {
            out.add(response);
        }
    }

    private HttpResponse convertToGrpcResponse(HttpResponse response, String serviceName, String methodName) {
        Descriptors.MethodDescriptor methodDescriptor = descriptorStore.getMethod(serviceName, methodName);
        if (methodDescriptor == null) {
            return response.clone()
                .removeHeader("x-grpc-service")
                .removeHeader("x-grpc-method");
        }

        String statusName = response.getFirstHeader(GrpcStatusMapper.GRPC_STATUS_NAME_HEADER);
        GrpcStatusMapper.GrpcStatusCode statusCode;
        if (statusName != null && !statusName.isEmpty()) {
            statusCode = GrpcStatusMapper.fromName(statusName);
        } else {
            statusCode = GrpcStatusMapper.GrpcStatusCode.OK;
        }

        String bodyString = response.getBodyAsString();
        if (bodyString == null || bodyString.isEmpty()) {
            return response.clone()
                .withStatusCode(200)
                .withHeader("content-type", GrpcStatusMapper.GRPC_CONTENT_TYPE)
                .withHeader(GrpcStatusMapper.GRPC_STATUS_HEADER, String.valueOf(statusCode.getCode()))
                .removeHeader("x-grpc-service")
                .removeHeader("x-grpc-method");
        }

        GrpcJsonMessageConverter converter = descriptorStore.getConverter();
        byte[] protobufBytes = converter.toProtobuf(bodyString, methodDescriptor.getOutputType());
        byte[] grpcFrame = GrpcFrameCodec.encode(protobufBytes);

        return response.clone()
            .withStatusCode(200)
            .withBody(new org.mockserver.model.BinaryBody(grpcFrame))
            .withHeader("content-type", GrpcStatusMapper.GRPC_CONTENT_TYPE)
            .withHeader(GrpcStatusMapper.GRPC_STATUS_HEADER, String.valueOf(statusCode.getCode()))
            .removeHeader("x-grpc-service")
            .removeHeader("x-grpc-method")
            .removeHeader(GrpcStatusMapper.GRPC_STATUS_NAME_HEADER);
    }
}
