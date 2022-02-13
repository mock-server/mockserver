package org.mockserver.netty.integration.mock;

import com.google.common.base.Joiner;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.mockserver.client.MockServerClient;
import org.mockserver.socket.PortFactory;
import org.mockserver.testing.integration.mock.AbstractBasicMockingIntegrationTest;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.mockserver.stop.Stop.stopQuietly;

/**
 * @author jamesdbloom
 */
@Ignore
public class ExtendedShadedJarMockingIntegrationTest extends AbstractBasicMockingIntegrationTest {

    private static final int mockServerPort = PortFactory.findFreePort();

    @BeforeClass
    public static void startServerUsingShadedJar() throws Exception {
        List<String> arguments = new ArrayList<>(Collections.singletonList(getJavaBin()));
        arguments.add("-Dfile.encoding=UTF-8");
        arguments.add("-jar");
        arguments.add(System.getProperty("project.basedir", "..") + "/mockserver-netty/target/mockserver-netty-" + System.getProperty("project.version", "5.12.1-SNAPSHOT") + "-shaded.jar");
        arguments.add("-serverPort");
        arguments.add("" + mockServerPort);
        String message = Joiner.on(" ").join(arguments);
        System.out.println("message = " + message);
        ProcessBuilder processBuilder = new ProcessBuilder(arguments);
        processBuilder.inheritIO();
        processBuilder.start();
        mockServerClient = new MockServerClient("localhost", mockServerPort, servletContext);
        mockServerClient.hasStarted();
    }

    private static String getJavaBin() {
        String javaBinary = "java";

        File javaHomeDirectory = new File(System.getProperty("java.home"));
        for (String javaExecutable : new String[]{"java", "java.exe"}) {
            File javaExeLocation = new File(javaHomeDirectory, fileSeparators("bin/" + javaExecutable));
            if (javaExeLocation.exists() && javaExeLocation.isFile()) {
                javaBinary = javaExeLocation.getAbsolutePath();
                break;
            }
        }

        return javaBinary;
    }

    private static String fileSeparators(String path) {
        StringBuilder ret = new StringBuilder();
        for (char c : path.toCharArray()) {
            if ((c == '/') || (c == '\\')) {
                ret.append(File.separatorChar);
            } else {
                ret.append(c);
            }
        }
        return ret.toString();
    }

    @AfterClass
    public static void stopServer() {
        stopQuietly(mockServerClient);
    }

    @Override
    public int getServerPort() {
        return mockServerPort;
    }

}
