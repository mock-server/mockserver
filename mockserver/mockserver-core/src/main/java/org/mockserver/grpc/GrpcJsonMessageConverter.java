package org.mockserver.grpc;

import com.google.protobuf.Descriptors;
import com.google.protobuf.DynamicMessage;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.TypeRegistry;
import com.google.protobuf.util.JsonFormat;

public class GrpcJsonMessageConverter {

    private final TypeRegistry typeRegistry;
    private final JsonFormat.Printer jsonPrinter;
    private final JsonFormat.Parser jsonParser;

    public GrpcJsonMessageConverter(TypeRegistry typeRegistry) {
        this.typeRegistry = typeRegistry;
        this.jsonPrinter = JsonFormat.printer().usingTypeRegistry(typeRegistry);
        this.jsonParser = JsonFormat.parser().ignoringUnknownFields().usingTypeRegistry(typeRegistry);
    }

    public String toJson(byte[] protobufBytes, Descriptors.Descriptor messageDescriptor) {
        try {
            DynamicMessage message = DynamicMessage.parseFrom(messageDescriptor, protobufBytes);
            return jsonPrinter.print(message);
        } catch (InvalidProtocolBufferException e) {
            throw new GrpcException("Failed to convert protobuf to JSON", e);
        }
    }

    public byte[] toProtobuf(String json, Descriptors.Descriptor messageDescriptor) {
        try {
            DynamicMessage.Builder builder = DynamicMessage.newBuilder(messageDescriptor);
            jsonParser.merge(json, builder);
            return builder.build().toByteArray();
        } catch (InvalidProtocolBufferException e) {
            throw new GrpcException("Failed to convert JSON to protobuf", e);
        }
    }

    public TypeRegistry getTypeRegistry() {
        return typeRegistry;
    }
}
