package org.mockserver.file;

import org.mockserver.model.BinaryBody;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;
import org.mockserver.model.MediaType;

import java.io.IOException;
import java.io.InputStream;
import java.net.URLDecoder;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;
import java.util.Map;

import static io.netty.handler.codec.http.HttpHeaderNames.CONTENT_LENGTH;
import static io.netty.handler.codec.http.HttpHeaderNames.CONTENT_TYPE;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.mockserver.model.HttpResponse.response;

/**
 * Serves {@code GET} and {@code HEAD} requests from the {@code staticFilesDirectory}. The request path chooses the
 * file, so it is confined the same way as {@link TemplatedFilePathGuard} confines a templated file path:
 * <ul>
 *   <li>the path is percent-decoded once; it must then be a plain relative path with no empty segment and no segment
 *       starting with {@code .} (so no {@code .}, {@code ..} or hidden file), and no {@code \}, {@code :}, {@code %}
 *       (drive letters, NTFS streams, double encoding) or control character, including NUL;</li>
 *   <li>once symlinks are resolved the file must still be a regular, non-hidden file inside the directory's real path,
 *       which is resolved once when the server starts, and it is opened without following a symlink;</li>
 *   <li>a directory is never listed: a path ending in {@code /} names that directory's {@code index.html}.</li>
 * </ul>
 */
public final class StaticFileDirectory {

    public static final long MAX_FILE_SIZE_IN_BYTES = 50L * 1024 * 1024;
    private static final String INDEX_FILE = "index.html";
    private static final MediaType DEFAULT_CONTENT_TYPE = MediaType.APPLICATION_OCTET_STREAM;
    private static final Map<String, MediaType> CONTENT_TYPES = Map.ofEntries(
        Map.entry("json", MediaType.parse("application/json")),
        Map.entry("map", MediaType.parse("application/json")),
        Map.entry("html", MediaType.parse("text/html")),
        Map.entry("htm", MediaType.parse("text/html")),
        Map.entry("css", MediaType.parse("text/css")),
        Map.entry("js", MediaType.parse("text/javascript")),
        Map.entry("mjs", MediaType.parse("text/javascript")),
        Map.entry("txt", MediaType.parse("text/plain")),
        Map.entry("csv", MediaType.parse("text/csv")),
        Map.entry("md", MediaType.parse("text/markdown")),
        Map.entry("xml", MediaType.parse("application/xml")),
        Map.entry("yaml", MediaType.parse("application/yaml")),
        Map.entry("yml", MediaType.parse("application/yaml")),
        Map.entry("svg", MediaType.parse("image/svg+xml")),
        Map.entry("png", MediaType.parse("image/png")),
        Map.entry("jpg", MediaType.parse("image/jpeg")),
        Map.entry("jpeg", MediaType.parse("image/jpeg")),
        Map.entry("gif", MediaType.parse("image/gif")),
        Map.entry("webp", MediaType.parse("image/webp")),
        Map.entry("ico", MediaType.parse("image/x-icon")),
        Map.entry("pdf", MediaType.parse("application/pdf")),
        Map.entry("zip", MediaType.parse("application/zip")),
        Map.entry("gz", MediaType.parse("application/gzip")),
        Map.entry("wasm", MediaType.parse("application/wasm")),
        Map.entry("woff", MediaType.parse("font/woff")),
        Map.entry("woff2", MediaType.parse("font/woff2")),
        Map.entry("ttf", MediaType.parse("font/ttf")),
        Map.entry("mp3", MediaType.parse("audio/mpeg")),
        Map.entry("mp4", MediaType.parse("video/mp4"))
    );

    private final Path realDirectory;
    private final long maxFileSizeInBytes;

    StaticFileDirectory(Path realDirectory, long maxFileSizeInBytes) {
        this.realDirectory = realDirectory;
        this.maxFileSizeInBytes = maxFileSizeInBytes;
    }

    /**
     * @return the directory resolved to its real path, or {@code null} when {@code directory} is blank
     * @throws IOException when {@code directory} does not exist or is not a directory
     */
    public static StaticFileDirectory open(String directory) throws IOException {
        if (isBlank(directory)) {
            return null;
        }
        Path realDirectory;
        try {
            realDirectory = Paths.get(directory.trim()).toRealPath();
        } catch (InvalidPathException invalid) {
            throw new IOException("invalid directory path", invalid);
        }
        if (!Files.isDirectory(realDirectory)) {
            throw new IOException("not a directory");
        }
        return new StaticFileDirectory(realDirectory, MAX_FILE_SIZE_IN_BYTES);
    }

    public Path getDirectory() {
        return realDirectory;
    }

    /**
     * Whether the request uses a method static files are served for, checked before any file system access.
     */
    public static boolean isServableMethod(HttpRequest request) {
        String method = request.getMethod() != null ? request.getMethod().getValue() : null;
        return "GET".equals(method) || "HEAD".equals(method);
    }

    /**
     * Answers a {@code GET} or {@code HEAD} request with the file its path names: a {@code HEAD} response has the
     * file's {@code Content-Length} and no body.
     *
     * @return the response, or {@code null} when the request names no file that may be served
     * @throws IOException when the file cannot be read or is larger than the size limit
     */
    public HttpResponse respond(HttpRequest request) throws IOException {
        if (!isServableMethod(request) || request.getPath() == null) {
            return null;
        }
        Path file = resolve(request.getPath().getValue());
        if (file == null) {
            return null;
        }
        long size = Files.size(file);
        if (size > maxFileSizeInBytes) {
            throw tooLarge();
        }
        MediaType contentType = contentType(file.getFileName().toString());
        HttpResponse response = response()
            .withStatusCode(200)
            .withHeader(CONTENT_TYPE.toString(), contentType.toString());
        if ("HEAD".equals(request.getMethod().getValue())) {
            return response.withHeader(CONTENT_LENGTH.toString(), Long.toString(size));
        }
        byte[] bytes;
        try (InputStream inputStream = Files.newInputStream(file, LinkOption.NOFOLLOW_LINKS)) {
            bytes = inputStream.readNBytes((int) maxFileSizeInBytes + 1);
        }
        if (bytes.length > maxFileSizeInBytes) {
            throw tooLarge();
        }
        return response.withBody(new BinaryBody(bytes, contentType));
    }

    /**
     * @return the real path of the regular, non-hidden file inside the directory that {@code requestPath} names, or
     * {@code null} when there is none or the path could leave the directory
     */
    Path resolve(String requestPath) {
        String relativePath = relativePath(requestPath);
        if (relativePath == null) {
            return null;
        }
        Path realFile;
        try {
            realFile = realDirectory.resolve(relativePath).toRealPath();
        } catch (IOException | InvalidPathException | SecurityException missingOrUnreadable) {
            return null;
        }
        if (!realFile.startsWith(realDirectory) || realFile.equals(realDirectory)) {
            return null;
        }
        for (Path segment : realDirectory.relativize(realFile)) {
            if (segment.toString().startsWith(".")) {
                return null;
            }
        }
        return Files.isRegularFile(realFile, LinkOption.NOFOLLOW_LINKS) ? realFile : null;
    }

    /**
     * @return the checked path relative to the directory, or {@code null} when it is not a plain relative path
     */
    static String relativePath(String requestPath) {
        if (requestPath == null || requestPath.isEmpty() || requestPath.charAt(0) != '/') {
            return null;
        }
        String decodedPath = requestPath;
        if (requestPath.indexOf('%') >= 0) {
            try {
                // decoded once: "+" is a literal plus in a path, and a "%" left after decoding is refused below
                decodedPath = URLDecoder.decode(requestPath.replace("+", "%2B"), UTF_8);
            } catch (IllegalArgumentException malformedEncoding) {
                return null;
            }
        }
        String relativePath = decodedPath.substring(1);
        if (relativePath.isEmpty() || relativePath.endsWith("/")) {
            relativePath = relativePath + INDEX_FILE;
        }
        int segmentStart = 0;
        for (int i = 0; i <= relativePath.length(); i++) {
            char c = i < relativePath.length() ? relativePath.charAt(i) : '/';
            if (c == '/') {
                if (i == segmentStart || relativePath.charAt(segmentStart) == '.') {
                    return null;
                }
                segmentStart = i + 1;
            } else if (c == '\\' || c == ':' || c == '%' || TemplatedFilePathGuard.isUnsafe(c)) {
                return null;
            }
        }
        return relativePath;
    }

    static MediaType contentType(String fileName) {
        int extensionStart = fileName.lastIndexOf('.');
        if (extensionStart < 0 || extensionStart == fileName.length() - 1) {
            return DEFAULT_CONTENT_TYPE;
        }
        return CONTENT_TYPES.getOrDefault(fileName.substring(extensionStart + 1).toLowerCase(Locale.ROOT), DEFAULT_CONTENT_TYPE);
    }

    private IOException tooLarge() {
        return new IOException("file is larger than the " + maxFileSizeInBytes + " byte limit for static files");
    }
}
