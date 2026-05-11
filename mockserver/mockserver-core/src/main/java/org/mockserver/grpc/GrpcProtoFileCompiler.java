package org.mockserver.grpc;

import org.mockserver.logging.MockServerLogger;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class GrpcProtoFileCompiler {

    private final MockServerLogger mockServerLogger;
    private final String protocPath;

    public GrpcProtoFileCompiler(MockServerLogger mockServerLogger) {
        this(mockServerLogger, "protoc");
    }

    public GrpcProtoFileCompiler(MockServerLogger mockServerLogger, String protocPath) {
        this.mockServerLogger = mockServerLogger;
        this.protocPath = protocPath != null && !protocPath.isEmpty() ? protocPath : "protoc";
    }

    public byte[] compile(Path protoFile) {
        return compile(protoFile, protoFile.getParent());
    }

    public byte[] compile(Path protoFile, Path protoPath) {
        Path outputFile = null;
        try {
            outputFile = Files.createTempFile("mockserver-grpc-", ".desc");
            List<String> command = new ArrayList<>();
            command.add(protocPath);
            command.add("--descriptor_set_out=" + outputFile.toAbsolutePath());
            command.add("--include_imports");
            if (protoPath != null) {
                command.add("--proto_path=" + protoPath.toAbsolutePath());
            }
            command.add(protoFile.toAbsolutePath().toString());

            ProcessBuilder processBuilder = new ProcessBuilder(command);
            processBuilder.redirectErrorStream(true);
            Process process = processBuilder.start();

            String output;
            try (InputStream is = process.getInputStream()) {
                output = new String(is.readAllBytes(), StandardCharsets.UTF_8);
            }

            boolean completed = process.waitFor(30, TimeUnit.SECONDS);
            if (!completed) {
                process.destroyForcibly();
                throw new GrpcException("protoc timed out after 30 seconds");
            }

            if (process.exitValue() != 0) {
                throw new GrpcException("protoc failed (exit code " + process.exitValue() + "): " + output);
            }

            return Files.readAllBytes(outputFile);
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw new GrpcException("Failed to compile proto file: " + protoFile, e);
        } finally {
            if (outputFile != null) {
                try {
                    Files.deleteIfExists(outputFile);
                } catch (IOException ignored) {
                }
            }
        }
    }

    public byte[] compileSource(String protoSource) {
        Path tempProtoFile = null;
        try {
            tempProtoFile = Files.createTempFile("mockserver-grpc-", ".proto");
            Files.write(tempProtoFile, protoSource.getBytes(StandardCharsets.UTF_8));
            return compile(tempProtoFile, tempProtoFile.getParent());
        } catch (IOException e) {
            throw new GrpcException("Failed to compile proto source", e);
        } finally {
            if (tempProtoFile != null) {
                try {
                    Files.deleteIfExists(tempProtoFile);
                } catch (IOException ignored) {
                }
            }
        }
    }

    public void compileDirectory(Path directory, GrpcProtoDescriptorStore store) {
        if (directory == null || !Files.isDirectory(directory)) {
            return;
        }
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(directory, "*.proto")) {
            for (Path entry : stream) {
                try {
                    byte[] descriptorBytes = compile(entry, directory);
                    store.loadDescriptorSet(descriptorBytes);
                } catch (Exception e) {
                    mockServerLogger.logEvent(
                        new org.mockserver.log.model.LogEntry()
                            .setType(org.mockserver.log.model.LogEntry.LogMessageType.WARN)
                            .setMessageFormat("failed to compile proto file {}:{}")
                            .setArguments(entry, e.getMessage())
                    );
                }
            }
        } catch (IOException e) {
            throw new GrpcException("Failed to scan proto directory " + directory, e);
        }
    }

    public boolean isProtocAvailable() {
        try {
            ProcessBuilder processBuilder = new ProcessBuilder(protocPath, "--version");
            processBuilder.redirectErrorStream(true);
            Process process = processBuilder.start();
            try (InputStream is = process.getInputStream()) {
                is.readAllBytes();
            }
            boolean completed = process.waitFor(5, TimeUnit.SECONDS);
            if (!completed) {
                process.destroyForcibly();
                return false;
            }
            return process.exitValue() == 0;
        } catch (IOException e) {
            return false;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }
}
