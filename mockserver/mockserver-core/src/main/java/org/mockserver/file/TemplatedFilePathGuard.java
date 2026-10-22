package org.mockserver.file;

import org.mockserver.model.HttpTemplate;

import java.io.IOException;
import java.io.InputStream;
import java.net.JarURLConnection;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLConnection;
import java.nio.file.FileSystemNotFoundException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Confines a {@code FileBody} path that was rendered as a template against the request.
 * <p>
 * A literal file path comes from whoever created the expectation and is used as given. Once the path is
 * templated, part of it comes from the request, so a client could otherwise pick any file the server can
 * read. The literal text before the first template expression, cut back to its last path separator, is the
 * <em>base directory</em>. It must name a directory, not just the working directory, the classpath root or a
 * filesystem root. Everything after it is request-influenced and must be a plain relative path below it:
 * <ul>
 *   <li>no {@code .} or {@code ..} segment, no empty segment (so no leading, trailing or doubled {@code /});</li>
 *   <li>no {@code \}, {@code :} or {@code %} (Windows separators and drives, NTFS streams, URL-encoded
 *       traversal) and no control or line-separator characters, including NUL;</li>
 *   <li>a file read from the filesystem, or from a classpath directory, must once symlinks are resolved
 *       still be inside the resolved base directory.</li>
 * </ul>
 */
public final class TemplatedFilePathGuard {

    private TemplatedFilePathGuard() {
    }

    /**
     * Returns the index of the first template expression in {@code pathTemplate} for the given engine, or
     * {@code -1} when the path contains no template syntax and can be used literally. This is a plain
     * character scan, so a path without template syntax never reaches a template engine.
     */
    public static int firstTemplateMarker(String pathTemplate, HttpTemplate.TemplateType templateType) {
        if (pathTemplate == null || templateType == null) {
            return -1;
        }
        switch (templateType) {
            case VELOCITY: {
                int dollar = pathTemplate.indexOf('$');
                int hash = pathTemplate.indexOf('#');
                return dollar < 0 ? hash : hash < 0 ? dollar : Math.min(dollar, hash);
            }
            case MUSTACHE:
                return pathTemplate.indexOf("{{");
            default:
                return -1;
        }
    }

    /**
     * Returns the literal base directory of a templated path: the text before the first template
     * expression, up to and including its last {@code /} or {@code \}; empty when there is none.
     */
    static String baseDirectory(String pathTemplate, int firstTemplateMarker) {
        String literalPrefix = pathTemplate.substring(0, firstTemplateMarker);
        int lastSeparator = Math.max(literalPrefix.lastIndexOf('/'), literalPrefix.lastIndexOf('\\'));
        return lastSeparator < 0 ? "" : literalPrefix.substring(0, lastSeparator + 1);
    }

    /**
     * Confines a rendered path to the base directory of the template it was rendered from, then reads it.
     * Like {@link FileReader}, the classpath is tried before the filesystem. A {@code file:} resource or a
     * filesystem file is read through its resolved real path, which must lie under the resolved base, and is
     * opened without following a symlink, so the file read is the file that was checked.
     *
     * @throws IllegalArgumentException when the rendered path could escape the base directory
     * @throws IOException              when the file is missing or cannot be read
     */
    public static byte[] readConfined(String pathTemplate, int firstTemplateMarker, String renderedPath) throws IOException {
        return readConfined(FileReader.class.getClassLoader(), pathTemplate, firstTemplateMarker, renderedPath);
    }

    static byte[] readConfined(ClassLoader classLoader, String pathTemplate, int firstTemplateMarker, String renderedPath) throws IOException {
        String baseDirectory = baseDirectory(pathTemplate, firstTemplateMarker);
        int requestSegments = checkRenderedPath(pathTemplate, baseDirectory, renderedPath);
        URL resource = classLoader.getResource(renderedPath);
        if (resource == null) {
            return readContained(pathTemplate, baseDirectory, toPath(renderedPath), toPath(baseDirectory));
        }
        switch (resource.getProtocol()) {
            case "file": {
                Path resourcePath = fileUrlToPath(resource, pathTemplate, baseDirectory);
                Path classpathBase = resourcePath;
                for (int i = 0; i < requestSegments && classpathBase != null; i++) {
                    classpathBase = classpathBase.getParent();
                }
                if (classpathBase == null) {
                    throw outside(pathTemplate, baseDirectory);
                }
                return readContained(pathTemplate, baseDirectory, resourcePath, classpathBase);
            }
            case "jar": {
                // an archive entry cannot be a symlink; its name must be exactly the checked path
                URLConnection connection = resource.openConnection();
                if (!(connection instanceof JarURLConnection) || !renderedPath.equals(((JarURLConnection) connection).getEntryName())) {
                    throw outside(pathTemplate, baseDirectory);
                }
                try (InputStream inputStream = connection.getInputStream()) {
                    return inputStream.readAllBytes();
                }
            }
            default:
                // containment cannot be verified for other protocols (vfs:, war:, bundleresource:, ...)
                throw outside(pathTemplate, baseDirectory);
        }
    }

    /**
     * Some classloaders return {@code file:} URLs that are not percent-encoded, which {@link URL#toURI()}
     * rejects; the multi-argument {@link URI} constructor quotes such characters instead.
     */
    private static Path fileUrlToPath(URL resource, String pathTemplate, String baseDirectory) {
        try {
            return Paths.get(resource.toURI());
        } catch (URISyntaxException | IllegalArgumentException | FileSystemNotFoundException notEncoded) {
            try {
                String authority = resource.getAuthority() == null || resource.getAuthority().isEmpty() ? null : resource.getAuthority();
                return Paths.get(new URI("file", authority, resource.getPath(), null, null));
            } catch (URISyntaxException | IllegalArgumentException | FileSystemNotFoundException unresolvable) {
                throw outside(pathTemplate, baseDirectory);
            }
        }
    }

    /**
     * @return the number of request-influenced path segments
     */
    private static int checkRenderedPath(String pathTemplate, String baseDirectory, String renderedPath) {
        if (!namesADirectory(baseDirectory) || renderedPath == null || !renderedPath.startsWith(baseDirectory)) {
            throw outside(pathTemplate, baseDirectory);
        }
        int segments = 1;
        int segmentStart = baseDirectory.length();
        for (int i = segmentStart; i <= renderedPath.length(); i++) {
            char c = i < renderedPath.length() ? renderedPath.charAt(i) : '/';
            if (c == '/') {
                if (isDotOrEmpty(renderedPath, segmentStart, i)) {
                    throw outside(pathTemplate, baseDirectory);
                }
                if (i < renderedPath.length()) {
                    segments++;
                }
                segmentStart = i + 1;
            } else if (isUnsafe(c) || c == '\\' || c == ':' || c == '%') {
                throw outside(pathTemplate, baseDirectory);
            }
        }
        return segments;
    }

    private static boolean isDotOrEmpty(String path, int start, int end) {
        int length = end - start;
        return length == 0
            || length == 1 && path.charAt(start) == '.'
            || length == 2 && path.charAt(start) == '.' && path.charAt(start + 1) == '.';
    }

    private static boolean namesADirectory(String baseDirectory) {
        int segmentStart = 0;
        for (int i = 0; i <= baseDirectory.length(); i++) {
            if (i == baseDirectory.length() || baseDirectory.charAt(i) == '/' || baseDirectory.charAt(i) == '\\') {
                int length = i - segmentStart;
                boolean dot = length == 1 && baseDirectory.charAt(segmentStart) == '.';
                boolean drive = length == 2 && Character.isLetter(baseDirectory.charAt(segmentStart)) && baseDirectory.charAt(segmentStart + 1) == ':';
                if (length > 0 && !dot && !drive) {
                    return true;
                }
                segmentStart = i + 1;
            }
        }
        return false;
    }

    static boolean isUnsafe(char c) {
        int type = Character.getType(c);
        return type == Character.CONTROL || type == Character.LINE_SEPARATOR || type == Character.PARAGRAPH_SEPARATOR;
    }

    private static Path toPath(String path) throws IOException {
        try {
            return Paths.get(path.isEmpty() ? "." : path);
        } catch (InvalidPathException e) {
            throw new IOException("invalid file path", e);
        }
    }

    private static byte[] readContained(String pathTemplate, String baseDirectory, Path target, Path base) throws IOException {
        Path realBase = base.toRealPath();
        Path realTarget = target.toRealPath();
        if (!realTarget.startsWith(realBase) || realTarget.equals(realBase)) {
            throw outside(pathTemplate, baseDirectory);
        }
        try (InputStream inputStream = Files.newInputStream(realTarget, LinkOption.NOFOLLOW_LINKS)) {
            return inputStream.readAllBytes();
        }
    }

    /**
     * Makes a request-influenced path safe to write to a log line by escaping control characters.
     */
    public static String printable(String path) {
        if (path == null) {
            return null;
        }
        StringBuilder printable = new StringBuilder(path.length());
        for (int i = 0; i < path.length(); i++) {
            char c = path.charAt(i);
            if (isUnsafe(c)) {
                printable.append(String.format(c <= 0xFF ? "\\x%02X" : "\\x%04X", (int) c));
            } else {
                printable.append(c);
            }
        }
        return printable.toString();
    }

    private static IllegalArgumentException outside(String pathTemplate, String baseDirectory) {
        return new IllegalArgumentException("templated file path \"" + pathTemplate + "\" rendered to a path outside its base directory \"" + baseDirectory + "\"");
    }
}
