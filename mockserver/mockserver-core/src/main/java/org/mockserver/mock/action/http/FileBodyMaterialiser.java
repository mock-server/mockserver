package org.mockserver.mock.action.http;

import org.mockserver.configuration.Configuration;
import org.mockserver.file.FileBodyException;
import org.mockserver.file.FileReader;
import org.mockserver.file.TemplatedFilePathGuard;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.model.BinaryBody;
import org.mockserver.model.BodyWithContentType;
import org.mockserver.model.FileBody;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpTemplate;
import org.mockserver.model.MediaType;
import org.mockserver.model.StringBody;
import org.mockserver.templates.engine.TemplateEngine;
import org.mockserver.templates.engine.mustache.MustacheTemplateEngine;
import org.mockserver.templates.engine.velocity.VelocityTemplateEngine;

import java.io.IOException;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

/**
 * Materialises a {@link FileBody} into a concrete {@link BodyWithContentType} (a {@link StringBody} or a
 * {@link BinaryBody}) by reading the referenced file, so the file CONTENTS - not the file path string -
 * reach the wire.
 * <p>
 * This is the single shared implementation of that logic, invoked from every response-producing funnel
 * (static response, object callback, class callback, response template, and forward responseOverride)
 * as well as from request-body matching against a {@link FileBody}. Keeping it here (the action-handler
 * layer) rather than in the codec keeps file I/O out of {@code BodyDecoderEncoder} while still giving the
 * caller access to the request (for templating) and the {@link Configuration} (for template engines).
 * <ul>
 *   <li>A supported {@code templateType} ({@code VELOCITY}/{@code MUSTACHE}) with a request present -&gt;
 *       the file is rendered through that engine against the request, preserving the declared content type.
 *       When {@code templatedFilePath} is also {@code true} and the path contains template syntax, the path is
 *       rendered first and must pass {@link TemplatedFilePathGuard} before the file is read.</li>
 *   <li>No {@code templateType}, an unsupported one (e.g. JavaScript), or no request available -&gt; the file
 *       is served verbatim: a text content type yields a {@link StringBody}; a binary or absent content type
 *       yields a {@link BinaryBody} of the raw bytes so binary files (images, PDFs, archives) are not
 *       charset-corrupted.</li>
 *   <li>A missing / unreadable file -&gt; {@link FileBodyException} carrying the path for server-side logging.</li>
 * </ul>
 *
 * @author jamesdbloom
 */
public class FileBodyMaterialiser {

    private final MockServerLogger mockServerLogger;
    private final Configuration configuration;
    private VelocityTemplateEngine velocityTemplateEngine;
    private MustacheTemplateEngine mustacheTemplateEngine;

    public FileBodyMaterialiser(MockServerLogger mockServerLogger, Configuration configuration) {
        this.mockServerLogger = mockServerLogger;
        this.configuration = configuration;
    }

    /**
     * Materialises the given {@link FileBody}. When {@code httpRequest} is {@code null} (or the template
     * type is unsupported/absent) the file is served verbatim; otherwise a supported template type renders
     * the file against the request.
     *
     * @throws FileBodyException when the referenced file cannot be read
     */
    public BodyWithContentType materialise(FileBody fileBody, HttpRequest httpRequest) {
        if (httpRequest != null && isFileTemplatingSupported(fileBody.getTemplateType())) {
            return renderTemplatedFileBody(fileBody, httpRequest);
        }
        return materialiseFileBodyVerbatim(fileBody);
    }

    /**
     * Only Velocity and Mustache are supported for templating a {@link FileBody}; any other type (e.g.
     * JavaScript) or {@code null} means the file is not template-rendered and is served verbatim.
     */
    public static boolean isFileTemplatingSupported(HttpTemplate.TemplateType templateType) {
        return templateType == HttpTemplate.TemplateType.VELOCITY
            || templateType == HttpTemplate.TemplateType.MUSTACHE;
    }

    /**
     * Reads the file referenced by a {@link FileBody} and renders its contents through the configured
     * template engine against the request, so an externally stored response body can contain template
     * placeholders. The content type declared on the FileBody (when any) is preserved on the result.
     */
    private BodyWithContentType renderTemplatedFileBody(FileBody fileBody, HttpRequest httpRequest) {
        TemplateEngine templateEngine;
        switch (fileBody.getTemplateType()) {
            case VELOCITY:
                templateEngine = getVelocityTemplateEngine();
                break;
            case MUSTACHE:
                templateEngine = getMustacheTemplateEngine();
                break;
            default:
                // JavaScript (and any future type) is not supported for file body templating (see
                // TemplateEngine.renderTemplate); serve the raw file contents rather than the filePath.
                // This branch is defensive - materialise() only routes supported template types here.
                return materialiseFileBodyVerbatim(fileBody);
        }
        String fileTemplate = readFileTemplate(fileBody, templateEngine, httpRequest);
        String rendered = templateEngine.renderTemplate(fileTemplate, httpRequest);
        String contentType = fileBody.getContentType();
        return isNotBlank(contentType)
            ? new StringBody(rendered, MediaType.parse(contentType))
            : new StringBody(rendered);
    }

    /**
     * Reads the literal {@code filePath}, unless the body opted in with {@code templatedFilePath} and the path
     * contains template syntax: then the path is rendered, confined and read by {@link TemplatedFilePathGuard}.
     * A path without template syntax never reaches the template engine.
     */
    private static String readFileTemplate(FileBody fileBody, TemplateEngine templateEngine, HttpRequest httpRequest) {
        String filePath = fileBody.getFilePath();
        if (!Boolean.TRUE.equals(fileBody.getTemplatedFilePath())) {
            return readFileOrThrow(filePath);
        }
        int firstTemplateMarker = TemplatedFilePathGuard.firstTemplateMarker(filePath, fileBody.getTemplateType());
        if (firstTemplateMarker < 0) {
            return readFileOrThrow(filePath);
        }
        String renderedPath = templateEngine.renderTemplate(filePath, httpRequest);
        try {
            return new String(TemplatedFilePathGuard.readConfined(filePath, firstTemplateMarker, renderedPath), UTF_8);
        } catch (IOException | RuntimeException failure) {
            throw new FileBodyException(TemplatedFilePathGuard.printable(renderedPath), failure);
        }
    }

    /**
     * Reads the file referenced by a {@link FileBody} that is served verbatim (no template engine) and
     * returns its CONTENTS as the response body, preserving the declared content type. A text /
     * known-charset content type is served as a {@link StringBody}; a binary content type - or an
     * absent/unknown content type - is served as a {@link BinaryBody} of the raw file bytes so binary
     * files (images, PDFs, archives) are not corrupted by charset decoding.
     */
    private BodyWithContentType materialiseFileBodyVerbatim(FileBody fileBody) {
        String contentType = fileBody.getContentType();
        MediaType mediaType = isNotBlank(contentType) ? MediaType.parse(contentType) : null;
        if (mediaType != null && mediaType.isString()) {
            String fileContents = readFileOrThrow(fileBody.getFilePath());
            return new StringBody(fileContents, mediaType);
        }
        byte[] fileBytes = readBytesOrThrow(fileBody.getFilePath());
        return mediaType != null
            ? new BinaryBody(fileBytes, mediaType)
            : new BinaryBody(fileBytes);
    }

    private static String readFileOrThrow(String filePath) {
        try {
            return FileReader.readFileFromClassPathOrPath(filePath);
        } catch (RuntimeException throwable) {
            throw new FileBodyException(filePath, throwable);
        }
    }

    private static byte[] readBytesOrThrow(String filePath) {
        try {
            return FileReader.readBytesFromClassPathOrPath(filePath);
        } catch (RuntimeException throwable) {
            throw new FileBodyException(filePath, throwable);
        }
    }

    private VelocityTemplateEngine getVelocityTemplateEngine() {
        if (velocityTemplateEngine == null) {
            velocityTemplateEngine = new VelocityTemplateEngine(mockServerLogger, configuration);
        }
        return velocityTemplateEngine;
    }

    private MustacheTemplateEngine getMustacheTemplateEngine() {
        if (mustacheTemplateEngine == null) {
            mustacheTemplateEngine = new MustacheTemplateEngine(mockServerLogger, configuration);
        }
        return mustacheTemplateEngine;
    }
}
