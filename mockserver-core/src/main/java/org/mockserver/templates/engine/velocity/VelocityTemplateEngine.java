package org.mockserver.templates.engine.velocity;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.velocity.VelocityContext;
import org.apache.velocity.app.VelocityEngine;
import org.apache.velocity.runtime.RuntimeConstants;
import org.apache.velocity.tools.ToolContext;
import org.apache.velocity.tools.ToolManager;
import org.apache.velocity.tools.config.ToolConfiguration;
import org.apache.velocity.tools.config.ToolboxConfiguration;
import org.apache.velocity.tools.config.XmlFactoryConfiguration;
import org.apache.velocity.util.introspection.SecureUberspector;
import org.mockserver.log.model.LogEntry;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.model.HttpRequest;
import org.mockserver.serialization.ObjectMapperFactory;
import org.mockserver.serialization.model.DTO;
import org.mockserver.templates.engine.TemplateEngine;
import org.mockserver.templates.engine.TemplateFunctions;
import org.mockserver.templates.engine.model.HttpRequestTemplateObject;
import org.mockserver.templates.engine.serializer.HttpTemplateOutputDeserializer;
import org.mockserver.templates.engine.velocity.directives.Ifnull;
import org.slf4j.event.Level;

import java.io.StringWriter;
import java.io.Writer;
import java.util.Properties;

import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static org.mockserver.configuration.ConfigurationProperties.velocityDenyClasses;
import static org.mockserver.formatting.StringFormatter.formatLogMessage;
import static org.mockserver.log.model.LogEntry.LogMessageType.TEMPLATE_GENERATED;
import static org.mockserver.log.model.LogEntryMessages.TEMPLATE_GENERATED_MESSAGE_FORMAT;

/**
 * @author jamesdbloom
 */
@SuppressWarnings("FieldMayBeFinal")
public class VelocityTemplateEngine implements TemplateEngine {

    private static final VelocityEngine velocityEngine;
    private static final ToolContext toolContext;
    private static ObjectMapper objectMapper;
    private final MockServerLogger mockServerLogger;
    private HttpTemplateOutputDeserializer httpTemplateOutputDeserializer;

    static {
        // See: https://velocity.apache.org/engine/2.0/configuration.html
        Properties velocityProperties = new Properties();
        velocityProperties.put(RuntimeConstants.RUNTIME_LOG_REFERENCE_LOG_INVALID, "true");
        velocityProperties.put(RuntimeConstants.RUNTIME_STRING_INTERNING, "true");
        velocityProperties.put(RuntimeConstants.MAX_NUMBER_LOOPS, "-1");
        velocityProperties.put(RuntimeConstants.CHECK_EMPTY_OBJECTS, "true");
        velocityProperties.put(RuntimeConstants.PARSE_DIRECTIVE_MAXDEPTH, "10");
        velocityProperties.put(RuntimeConstants.RUNTIME_REFERENCES_STRICT, "false");
        velocityProperties.put("context.scope_control.template", "false");
        velocityProperties.put("context.scope_control.evaluate", "false");
        velocityProperties.put("context.scope_control.foreach", "true");
        velocityProperties.put("context.scope_control.macro", "false");
        velocityProperties.put("context.scope_control.define", "false");
        velocityProperties.put("directive.set.null.allowed", "true");
        velocityProperties.put(RuntimeConstants.INTERPOLATE_STRINGLITERALS, "true");
        velocityProperties.put(RuntimeConstants.INPUT_ENCODING, "UTF-8");
        velocityProperties.put(RuntimeConstants.PARSER_POOL_CLASS, org.apache.velocity.runtime.ParserPoolImpl.class.getName());
        velocityProperties.put(RuntimeConstants.PARSER_POOL_SIZE, "50");
        velocityProperties.put(RuntimeConstants.SPACE_GOBBLING, "lines");
        velocityProperties.put(RuntimeConstants.PARSER_HYPHEN_ALLOWED, "true");
        velocityProperties.put(RuntimeConstants.CUSTOM_DIRECTIVES, Ifnull.class.getName());
        velocityProperties.put(RuntimeConstants.RESOURCE_MANAGER_CLASS, org.apache.velocity.runtime.resource.ResourceManagerImpl.class.getName());
        velocityProperties.put(RuntimeConstants.RESOURCE_MANAGER_CACHE_CLASS, org.apache.velocity.runtime.resource.ResourceCacheImpl.class.getName());
        velocityProperties.put("resource.loader.file.class", org.apache.velocity.runtime.resource.loader.FileResourceLoader.class.getName());
        if (velocityDenyClasses()) {
            velocityProperties.put(RuntimeConstants.UBERSPECT_CLASSNAME, SecureUberspector.class.getName());
        }
        velocityEngine = new VelocityEngine();
        velocityEngine.init(velocityProperties);

        ToolManager manager = new ToolManager();

        ToolboxConfiguration applicationToolboxConfiguration = new ToolboxConfiguration();
        applicationToolboxConfiguration.setScope("application");
        ToolConfiguration collectionTool = new ToolConfiguration();
        collectionTool.setClass(org.apache.velocity.tools.generic.CollectionTool.class.getName());
        applicationToolboxConfiguration.addTool(collectionTool);
        ToolConfiguration comparisonDateTool = new ToolConfiguration();
        comparisonDateTool.setClass(org.apache.velocity.tools.generic.ComparisonDateTool.class.getName());
        applicationToolboxConfiguration.addTool(comparisonDateTool);
        ToolConfiguration displayTool = new ToolConfiguration();
        displayTool.setClass(org.apache.velocity.tools.generic.DisplayTool.class.getName());
        applicationToolboxConfiguration.addTool(displayTool);
        ToolConfiguration escapeTool = new ToolConfiguration();
        escapeTool.setClass(org.apache.velocity.tools.generic.EscapeTool.class.getName());
        applicationToolboxConfiguration.addTool(escapeTool);
        ToolConfiguration mathTool = new ToolConfiguration();
        mathTool.setClass(org.apache.velocity.tools.generic.MathTool.class.getName());
        applicationToolboxConfiguration.addTool(mathTool);
        ToolConfiguration numberTool = new ToolConfiguration();
        numberTool.setClass(org.apache.velocity.tools.generic.NumberTool.class.getName());
        applicationToolboxConfiguration.addTool(numberTool);
        ToolboxConfiguration requestToolboxConfiguration = new ToolboxConfiguration();
        requestToolboxConfiguration.setScope("request");
        ToolConfiguration jsonTool = new ToolConfiguration();
        jsonTool.setClass(org.apache.velocity.tools.generic.JsonTool.class.getName());
        requestToolboxConfiguration.addTool(jsonTool);
        ToolConfiguration xmlTool = new ToolConfiguration();
        xmlTool.setClass(org.apache.velocity.tools.generic.XmlTool.class.getName());
        requestToolboxConfiguration.addTool(xmlTool);
        XmlFactoryConfiguration xmlFactoryConfiguration = new XmlFactoryConfiguration();
        xmlFactoryConfiguration.addToolbox(applicationToolboxConfiguration);
        xmlFactoryConfiguration.addToolbox(requestToolboxConfiguration);
        manager.configure(xmlFactoryConfiguration);
        manager.setVelocityEngine(velocityEngine);
        toolContext = manager.createContext();
    }

    public VelocityTemplateEngine(MockServerLogger mockServerLogger) {
        this.mockServerLogger = mockServerLogger;
        this.httpTemplateOutputDeserializer = new HttpTemplateOutputDeserializer(mockServerLogger);
        if (objectMapper == null) {
            objectMapper = ObjectMapperFactory.createObjectMapper();
        }
    }

    @Override
    public <T> T executeTemplate(String template, HttpRequest request, Class<? extends DTO<T>> dtoClass) {
        T result;
        try {
            Writer writer = new StringWriter();
            VelocityContext context = new VelocityContext(toolContext);
            context.put("request", new HttpRequestTemplateObject(request));
            TemplateFunctions.BUILT_IN_FUNCTIONS.forEach(context::put);
            velocityEngine.evaluate(context, writer, "VelocityResponseTemplate", template);
            JsonNode generatedObject = null;
            try {
                generatedObject = objectMapper.readTree(writer.toString());
            } catch (Throwable throwable) {
                if (MockServerLogger.isEnabled(Level.INFO)) {
                    mockServerLogger.logEvent(
                        new LogEntry()
                            .setLogLevel(Level.INFO)
                            .setHttpRequest(request)
                            .setMessageFormat("exception deserialising generated content:{}into json node for request:{}")
                            .setArguments(writer.toString(), request)
                    );
                }
            }
            if (MockServerLogger.isEnabled(Level.INFO)) {
                mockServerLogger.logEvent(
                    new LogEntry()
                        .setType(TEMPLATE_GENERATED)
                        .setLogLevel(Level.INFO)
                        .setHttpRequest(request)
                        .setMessageFormat(TEMPLATE_GENERATED_MESSAGE_FORMAT)
                        .setArguments(generatedObject != null ? generatedObject : writer.toString(), template, request)
                );
            }
            result = httpTemplateOutputDeserializer.deserializer(request, writer.toString(), dtoClass);
        } catch (Exception e) {
            throw new RuntimeException(formatLogMessage("Exception:{}transforming template:{}for request:{}", isNotBlank(e.getMessage()) ? e.getMessage() : e.getClass().getSimpleName(), template, request), e);
        }
        return result;
    }
}
