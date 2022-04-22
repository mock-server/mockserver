package org.mockserver.cli;

import com.google.common.base.Joiner;
import com.google.common.base.Strings;
import org.mockserver.configuration.ConfigurationProperties;
import org.mockserver.configuration.IntegerStringListParser;
import org.mockserver.log.model.LogEntry;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.netty.MockServer;
import org.mockserver.version.Version;

import java.io.PrintStream;
import java.util.*;

import static org.apache.commons.lang3.StringUtils.*;
import static org.mockserver.character.Character.NEW_LINE;
import static org.mockserver.cli.Main.Arguments.*;
import static org.mockserver.log.model.LogEntry.LogMessageType.SERVER_CONFIGURATION;
import static org.mockserver.mock.HttpState.setPort;
import static org.slf4j.event.Level.*;

/**
 * @author jamesdbloom
 */
public class Main {
    static final String USAGE = "" +
        "   version: " + Version.getVersion() + NEW_LINE +
        "    " + NEW_LINE +
        "   java -jar <path to mockserver-netty-jar-with-dependencies.jar> -serverPort <port> [-proxyRemotePort <port>] [-proxyRemoteHost <hostname>] [-logLevel <level>] " + NEW_LINE +
        "                                                                                                                                                                 " + NEW_LINE +
        "     valid options are:                                                                                                                                          " + NEW_LINE +
        "        -serverPort <port>           The HTTP, HTTPS, SOCKS and HTTP CONNECT                                                                                     " + NEW_LINE +
        "                                     port(s) for both mocking and proxying                                                                                       " + NEW_LINE +
        "                                     requests.  Port unification is used to                                                                                      " + NEW_LINE +
        "                                     support all protocols for proxying and                                                                                      " + NEW_LINE +
        "                                     mocking on the same port(s). Supports                                                                                       " + NEW_LINE +
        "                                     comma separated list for binding to                                                                                         " + NEW_LINE +
        "                                     multiple ports.                                                                                                             " + NEW_LINE +
        "                                                                                                                                                                 " + NEW_LINE +
        "        -proxyRemotePort <port>      Optionally enables port forwarding mode.                                                                                    " + NEW_LINE +
        "                                     When specified all requests received will                                                                                   " + NEW_LINE +
        "                                     be forwarded to the specified port, unless                                                                                  " + NEW_LINE +
        "                                     they match an expectation.                                                                                                  " + NEW_LINE +
        "                                                                                                                                                                 " + NEW_LINE +
        "        -proxyRemoteHost <hostname>  Specified the host to forward all proxy                                                                                     " + NEW_LINE +
        "                                     requests to when port forwarding mode has                                                                                   " + NEW_LINE +
        "                                     been enabled using the proxyRemotePort                                                                                      " + NEW_LINE +
        "                                     option.  This setting is ignored unless                                                                                     " + NEW_LINE +
        "                                     proxyRemotePort has been specified. If no                                                                                   " + NEW_LINE +
        "                                     value is provided for proxyRemoteHost when                                                                                  " + NEW_LINE +
        "                                     proxyRemotePort has been specified,                                                                                         " + NEW_LINE +
        "                                     proxyRemoteHost will default to \"localhost\".                                                                              " + NEW_LINE +
        "                                                                                                                                                                 " + NEW_LINE +
        "        -logLevel <level>            Optionally specify log level using SLF4J levels:                                                                            " + NEW_LINE +
        "                                     TRACE, DEBUG, INFO, WARN, ERROR, OFF or Java                                                                                " + NEW_LINE +
        "                                     Logger levels: FINEST, FINE, INFO, WARNING,                                                                                 " + NEW_LINE +
        "                                     SEVERE or OFF. If not specified default is INFO                                                                             " + NEW_LINE +
        "                                                                                                                                                                 " + NEW_LINE +
        "   i.e. java -jar ./mockserver-netty-jar-with-dependencies.jar -serverPort 1080 -proxyRemotePort 80 -proxyRemoteHost www.mock-server.com -logLevel WARN                         " + NEW_LINE +
        "                                                                                                                                                                 " + NEW_LINE;
    private static final MockServerLogger MOCK_SERVER_LOGGER = new MockServerLogger(Main.class);
    private static final IntegerStringListParser INTEGER_STRING_LIST_PARSER = new IntegerStringListParser();
    static PrintStream systemErr = System.err;
    static PrintStream systemOut = System.out;
    static boolean usageShown = false;

    /**
     * Run the MockServer directly providing the arguments as specified below.
     *
     * @param arguments the entries are in pairs:
     *                  - "-serverPort"       followed by the mandatory server local port,
     *                  - "-proxyRemotePort"  followed by the optional proxyRemotePort port that enabled port forwarding mode,
     *                  - "-proxyRemoteHost"  followed by the optional proxyRemoteHost port (ignored unless proxyRemotePort is specified)
     *                  - "-logLevel"         followed by the log level
     */
    public static void main(String... arguments) {
        try {
            Map<String, String> parsedArguments = parseArguments(arguments);
            Map<String, String> commandLineArguments = new HashMap<>(parsedArguments);
            Map<String, String> environmentVariableArguments = new HashMap<>();
            Map<String, String> systemPropertyArguments = new HashMap<>();

            System.getenv().forEach((key, value) -> {
                if (key.startsWith("MOCKSERVER_") && isNotBlank(value)) {
                    environmentVariableArguments.put(key, value);
                }
            });
            System.getProperties().forEach((key, value) -> {
                if (key instanceof String && value instanceof String) {
                    if (((String) key).startsWith("mockserver") && isNotBlank((String) value)) {
                        systemPropertyArguments.put((String) key, (String) value);
                    }
                }
            });

            for (Arguments parsedArgument : Arrays.asList(serverPort, proxyRemoteHost, proxyRemotePort)) {
                if (!parsedArguments.containsKey(parsedArgument.name())) {
                    if (systemPropertyArguments.containsKey(parsedArgument.systemPropertyName())) {
                        parsedArguments.put(parsedArgument.name(), systemPropertyArguments.get(parsedArgument.systemPropertyName()));
                        environmentVariableArguments.remove(parsedArgument.longEnvironmentVariableName());
                        environmentVariableArguments.remove(parsedArgument.shortEnvironmentVariableName());
                    } else {
                        if (environmentVariableArguments.containsKey(parsedArgument.longEnvironmentVariableName())) {
                            environmentVariableArguments.remove(parsedArgument.shortEnvironmentVariableName());
                            parsedArguments.put(parsedArgument.name(), environmentVariableArguments.get(parsedArgument.longEnvironmentVariableName()));
                        } else if (isNotBlank(System.getenv(parsedArgument.shortEnvironmentVariableName()))) {
                            if (!(parsedArgument == serverPort && "1080".equals(System.getenv(serverPort.shortEnvironmentVariableName())) && ConfigurationProperties.PROPERTIES.containsKey(serverPort.systemPropertyName()))) {
                                environmentVariableArguments.put(parsedArgument.shortEnvironmentVariableName(), System.getenv(parsedArgument.shortEnvironmentVariableName()));
                                parsedArguments.put(parsedArgument.name(), environmentVariableArguments.get(parsedArgument.shortEnvironmentVariableName()));
                            }
                        }
                    }
                } else {
                    systemPropertyArguments.remove(parsedArgument.systemPropertyName());
                    environmentVariableArguments.remove(parsedArgument.longEnvironmentVariableName());
                    environmentVariableArguments.remove(parsedArgument.shortEnvironmentVariableName());
                }
                if (!parsedArguments.containsKey(parsedArgument.name()) && ConfigurationProperties.PROPERTIES.containsKey(parsedArgument.systemPropertyName())) {
                    parsedArguments.put(parsedArgument.name(), String.valueOf(ConfigurationProperties.PROPERTIES.get(parsedArgument.systemPropertyName())));
                }
            }

            if (MockServerLogger.isEnabled(INFO)) {
                MOCK_SERVER_LOGGER.logEvent(
                    new LogEntry()
                        .setType(SERVER_CONFIGURATION)
                        .setLogLevel(INFO)
                        .setMessageFormat("using environment variables:{}and system properties:{}and command line options:{}")
                        .setArguments(
                            "[\n\t" + Joiner.on(",\n\t").withKeyValueSeparator("=").join(environmentVariableArguments) + "\n]",
                            "[\n\t" + Joiner.on(",\n\t").withKeyValueSeparator("=").join(systemPropertyArguments) + "\n]",
                            "[\n\t" + Joiner.on(",\n\t").withKeyValueSeparator("=").join(commandLineArguments) + "\n]"
                        )
                );
            }

            if (parsedArguments.size() > 0 && parsedArguments.containsKey(serverPort.name())) {
                if (parsedArguments.containsKey(logLevel.name())) {
                    ConfigurationProperties.logLevel(parsedArguments.get(logLevel.name()));
                }
                Integer[] localPorts = INTEGER_STRING_LIST_PARSER.toArray(parsedArguments.get(serverPort.name()));
                if (parsedArguments.containsKey(proxyRemotePort.name())) {
                    String remoteHost = parsedArguments.get(proxyRemoteHost.name());
                    if (isBlank(remoteHost)) {
                        remoteHost = "localhost";
                    }
                    new MockServer(Integer.parseInt(parsedArguments.get(proxyRemotePort.name())), remoteHost, localPorts);
                } else {
                    new MockServer(localPorts);
                }
                setPort(localPorts);

                if (ConfigurationProperties.logLevel() != null) {
                    MOCK_SERVER_LOGGER.logEvent(
                        new LogEntry()
                            .setType(SERVER_CONFIGURATION)
                            .setLogLevel(ConfigurationProperties.logLevel())
                            .setMessageFormat("logger level is " + ConfigurationProperties.logLevel() + ", change using:\n - 'ConfigurationProperties.logLevel(String level)' in Java code,\n - '-logLevel' command line argument,\n - 'mockserver.logLevel' JVM system property or,\n - 'mockserver.logLevel' property value in 'mockserver.properties'")
                    );
                }
            } else {
                showUsage("\"" + serverPort.name() + "\" not specified");
            }

        } catch (Throwable throwable) {
            MOCK_SERVER_LOGGER.logEvent(
                new LogEntry()
                    .setType(SERVER_CONFIGURATION)
                    .setLogLevel(ERROR)
                    .setMessageFormat("exception while starting:{}")
                    .setThrowable(throwable)
            );
            showUsage(null);
            if (ConfigurationProperties.disableSystemOut()) {
                new RuntimeException("exception while starting: " + throwable.getMessage()).printStackTrace(System.err);
            }
        }
    }

    private static Map<String, String> parseArguments(String... arguments) {
        Map<String, String> parsedArguments = new HashMap<>();
        List<String> errorMessages = new ArrayList<>();

        Iterator<String> argumentsIterator = Arrays.asList(arguments).iterator();
        while (argumentsIterator.hasNext()) {
            final String next = argumentsIterator.next();
            String argumentName = substringAfter(next, "-");
            if (argumentsIterator.hasNext()) {
                String argumentValue = argumentsIterator.next();
                if (!Arguments.names().containsIgnoreCase(argumentName)) {
                    showUsage("invalid argument \"" + argumentName + "\" found");
                    break;
                } else {
                    String errorMessage = "";
                    switch (Arguments.valueOf(argumentName)) {
                        case serverPort:
                            if (!argumentValue.matches("^\\d+(,\\d+)*$")) {
                                errorMessage = argumentName + " value \"" + argumentValue + "\" is invalid, please specify a comma separated list of ports i.e. \"1080,1081,1082\"";
                            }
                            break;
                        case proxyRemotePort:
                            if (!argumentValue.matches("^\\d+$")) {
                                errorMessage = argumentName + " value \"" + argumentValue + "\" is invalid, please specify a port i.e. \"1080\"";
                            }
                            break;
                        case proxyRemoteHost:
                            String validIpAddressRegex = "^(([0-9]|[1-9][0-9]|1[0-9]{2}|2[0-4][0-9]|25[0-5])\\.){3}([0-9]|[1-9][0-9]|1[0-9]{2}|2[0-4][0-9]|25[0-5])$";
                            String validHostnameRegex = "^(([a-zA-Z0-9]|[a-zA-Z0-9][a-zA-Z0-9\\-]*[a-zA-Z0-9])\\.)*([A-Za-z0-9]|[A-Za-z0-9][A-Za-z0-9\\-]*[A-Za-z0-9])$";
                            if (!(argumentValue.matches(validIpAddressRegex) || argumentValue.matches(validHostnameRegex))) {
                                errorMessage = argumentName + " value \"" + argumentValue + "\" is invalid, please specify a host name i.e. \"localhost\" or \"127.0.0.1\"";
                            }
                            break;
                        case logLevel:
                            if (!Arrays.asList("TRACE", "DEBUG", "INFO", "WARN", "ERROR", "OFF", "FINEST", "FINE", "INFO", "WARNING", "SEVERE").contains(argumentValue)) {
                                errorMessage = argumentName + " value \"" + argumentValue + "\" is invalid, please specify one of SL4J levels: \"TRACE\", \"DEBUG\", \"INFO\", \"WARN\", \"ERROR\", \"OFF\" or the Java Logger levels: \"FINEST\", \"FINE\", \"INFO\", \"WARNING\", \"SEVERE\", \"OFF\"";
                            }
                            break;
                    }
                    if (errorMessage.isEmpty()) {
                        parsedArguments.put(argumentName, argumentValue);
                    } else {
                        errorMessages.add(errorMessage);
                    }
                }
            } else {
                break;
            }
        }

        if (!errorMessages.isEmpty()) {
            printValidationEror(errorMessages);
            throw new IllegalArgumentException(errorMessages.toString());
        }
        return parsedArguments;
    }

    private static void printValidationEror(List<String> errorMessages) {
        int maxLengthMessage = 0;
        for (String errorMessage : errorMessages) {
            if (errorMessage.length() > maxLengthMessage) {
                maxLengthMessage = errorMessage.length();
            }
        }
        systemOut.println(NEW_LINE + "   " + Strings.padEnd("", maxLengthMessage, '='));
        for (String errorMessage : errorMessages) {
            systemOut.println("   " + errorMessage);
        }
        systemOut.println("   " + Strings.padEnd("", maxLengthMessage, '=') + NEW_LINE);
    }

    private static void showUsage(String errorMessage) {
        if (!usageShown) {
            usageShown = true;
            systemOut.print(USAGE);
            systemOut.flush();
        }
        if (isNotBlank(errorMessage)) {
            systemErr.print("\nERROR:  " + errorMessage + "\n\n");
            systemErr.flush();
        }
    }

    public enum Arguments {
        serverPort("SERVER_PORT"),
        proxyRemoteHost("PROXY_REMOTE_HOST"),
        proxyRemotePort("PROXY_REMOTE_PORT"),
        logLevel("LOG_LEVEL");

        final static CaseInsensitiveList names = new CaseInsensitiveList();

        static {
            for (Arguments arguments : values()) {
                names.add(arguments.name());
            }
        }

        private final String shortEnvironmentVariableName;

        Arguments(String shortEnvironmentVariableName) {
            this.shortEnvironmentVariableName = shortEnvironmentVariableName;
        }

        public static CaseInsensitiveList names() {
            return names;
        }

        public String shortEnvironmentVariableName() {
            return shortEnvironmentVariableName;
        }

        public String longEnvironmentVariableName() {
            return "MOCKSERVER_" + shortEnvironmentVariableName;
        }

        public String systemPropertyName() {
            return "mockserver." + name();
        }
    }

    static class CaseInsensitiveList extends ArrayList<String> {

        CaseInsensitiveList() {
            super();
        }

        boolean containsIgnoreCase(String matcher) {
            for (String listItem : this) {
                if (listItem.equalsIgnoreCase(matcher)) {
                    return true;
                }
            }
            return false;
        }
    }

}
