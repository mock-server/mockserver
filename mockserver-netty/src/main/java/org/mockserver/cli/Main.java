package org.mockserver.cli;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Joiner;
import org.apache.commons.cli.*;
import org.apache.commons.io.FileUtils;
import org.mockserver.logging.Logging;
import org.mockserver.mockserver.MockServerBuilder;
import org.mockserver.proxy.ProxyBuilder;
import org.mockserver.socket.SSLFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * @author jamesdbloom
 */
public class Main {
    public static final String PROXY_PORT_KEY = "proxyPort";
    public static final String PROXY_PORT_KEY_DESCRIPTION = "specifies the HTTP and HTTPS port for the MockServer port unification is used to support HTTP and HTTPS on the same port";

    public static final String SERVER_PORT_KEY = "serverPort";
    public static final String SERVER_PORT_KEY_DESCRIPTION = "specifies the HTTP, HTTPS, SOCKS and HTTP CONNECT port for proxy, port unification supports for all protocols on the same port";

    public static final String FILE_LOCATION_KEY = "jksPath";
    public static final String FILE_LOCATION_KEY_DESCRIPTION = "Path to a Java KeyStore file containing your SSL Certificate. If jksPath is provided, keyPassword is also required";

    public static final String KEY_STORE_PASSWORD = "keyPassword";
    public static final String KEY_STORE_PASSWORD_DESCRIPTION = "KeyStore password";


    private static final Logger logger = LoggerFactory.getLogger(Main.class);
    @VisibleForTesting
    static ProxyBuilder httpProxyBuilder = new ProxyBuilder();
    @VisibleForTesting
    static MockServerBuilder mockServerBuilder = new MockServerBuilder();

    @VisibleForTesting
    static boolean shutdownOnUsage = true;
    private static boolean usagePrinted = false;

    /**
     * Run the MockServer directly providing the parseArguments for the server and httpProxyBuilder as the only input parameters (if not provided the server port defaults to 8080 and the httpProxyBuilder is not started).
     *
     * @param arguments the entries are in pairs:
     *                  - the first  pair is "-serverPort" followed by the server port if not provided the MockServer is not started,
     *                  - the second pair is "-proxyPort"  followed by the httpProxyBuilder  port if not provided the httpProxyBuilder      is not started
     */
    public static void main(String... arguments) {
        usagePrinted = false;

        Map<String, String> parseStringArguments = new HashMap<String, String>();
        Options options = new Options();
        options.addOption(OptionBuilder.withArgName("integer").hasArg().withDescription(SERVER_PORT_KEY_DESCRIPTION).create(SERVER_PORT_KEY));
        options.addOption(OptionBuilder.withArgName("integer").hasArg().withDescription(PROXY_PORT_KEY_DESCRIPTION).create(PROXY_PORT_KEY));
        options.addOption(OptionBuilder.withArgName("string").hasArg().withDescription(FILE_LOCATION_KEY_DESCRIPTION).create(FILE_LOCATION_KEY));
        options.addOption(OptionBuilder.withArgName("string").hasArg().withDescription(KEY_STORE_PASSWORD_DESCRIPTION).create(KEY_STORE_PASSWORD));

        CommandLineParser parser = new BasicParser();
        CommandLine cmd;
        try {
            cmd = parser.parse(options, arguments);
        } catch (ParseException e) {
            showUsage(options);
            return;
        }

        if (logger.isDebugEnabled()) {
            logger.debug(System.getProperty("line.separator") + System.getProperty("line.separator") + "Using command line options: " +
                    Joiner.on(", ").withKeyValueSeparator("=").join(parseStringArguments) + System.getProperty("line.separator"));
        }
        Logging.overrideLogLevel(System.getProperty("mockserver.logLevel"));

        if (!(cmd.hasOption(PROXY_PORT_KEY) || cmd.hasOption(SERVER_PORT_KEY))) {
            showUsage(options);
        }

        if (cmd.hasOption(PROXY_PORT_KEY)) {
            httpProxyBuilder.withLocalPort(Integer.parseInt(cmd.getOptionValue(PROXY_PORT_KEY))).build();
        }
        if (cmd.hasOption(SERVER_PORT_KEY)) {
            mockServerBuilder.withHTTPPort(Integer.parseInt(cmd.getOptionValue(SERVER_PORT_KEY))).build();
        }

        if ((cmd.hasOption(FILE_LOCATION_KEY) ^ cmd.hasOption(KEY_STORE_PASSWORD))) {
            showUsage(options);
        } else if (cmd.hasOption(FILE_LOCATION_KEY) && cmd.hasOption(KEY_STORE_PASSWORD)) {
            try {
                FileUtils.copyFile(new File(cmd.getOptionValue(FILE_LOCATION_KEY)), new File(SSLFactory.KEY_STORE_FILENAME));
            } catch (IOException e) {
                logger.error("Error copying keystore" + e.getMessage());
                showUsage(options);
            }

            SSLFactory.keyStorePassword = cmd.getOptionValue(KEY_STORE_PASSWORD);
        }
    }

    private static void showUsage(Options options) {
        HelpFormatter formatter = new HelpFormatter();
        formatter.printHelp("mockserver", options);
        if (shutdownOnUsage) {
            System.exit(1);
        }
    }



}
