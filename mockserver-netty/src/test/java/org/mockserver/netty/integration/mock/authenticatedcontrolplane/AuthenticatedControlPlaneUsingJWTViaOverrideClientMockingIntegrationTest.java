package org.mockserver.netty.integration.mock.authenticatedcontrolplane;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.mockserver.keys.AsymmetricKeyGenerator;
import org.mockserver.keys.AsymmetricKeyPair;
import org.mockserver.authentication.jwt.JWKGenerator;
import org.mockserver.authentication.jwt.JWTGenerator;
import org.mockserver.cli.Main;
import org.mockserver.client.MockServerClient;
import org.mockserver.keys.AsymmetricKeyPairAlgorithm;
import org.mockserver.model.Header;
import org.mockserver.socket.PortFactory;
import org.mockserver.test.TempFileWriter;
import org.mockserver.testing.integration.mock.AbstractBasicMockingSameJVMIntegrationTest;

import static io.netty.handler.codec.http.HttpHeaderNames.AUTHORIZATION;
import static org.mockserver.configuration.ConfigurationProperties.controlPlaneJWTAuthenticationJWKSource;
import static org.mockserver.configuration.ConfigurationProperties.controlPlaneJWTAuthenticationRequired;
import static org.mockserver.model.Header.header;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.stop.Stop.stopQuietly;

/**
 * @author jamesdbloom
 */
public class AuthenticatedControlPlaneUsingJWTViaOverrideClientMockingIntegrationTest extends AbstractBasicMockingSameJVMIntegrationTest {

    private static final int severHttpPort = PortFactory.findFreePort();
    private static String originalControlPlaneJWTAuthenticationJWKSource;
    private static boolean originalControlPlaneJWTAuthenticationRequired;
    private static Header authorisationHeader;

    @BeforeClass
    public static void startServer() {
        // save original value
        originalControlPlaneJWTAuthenticationJWKSource = controlPlaneJWTAuthenticationJWKSource();
        originalControlPlaneJWTAuthenticationRequired = controlPlaneJWTAuthenticationRequired();

        // set new certificate authority values
        AsymmetricKeyPair asymmetricKeyPair = AsymmetricKeyGenerator.createAsymmetricKeyPair(AsymmetricKeyPairAlgorithm.RSA2048_SHA256);
        String jwkFile = TempFileWriter.write(new JWKGenerator().generateJWK(asymmetricKeyPair));
        String jwt = new JWTGenerator(asymmetricKeyPair).generateJWT();
        controlPlaneJWTAuthenticationJWKSource(jwkFile);
        controlPlaneJWTAuthenticationRequired(true);
        authorisationHeader = header(AUTHORIZATION.toString(), "Bearer " + jwt);

        Main.main("-serverPort", "" + severHttpPort);

        mockServerClient = new MockServerClient("localhost", severHttpPort).setRequestOverride(request().withHeader(authorisationHeader)).withSecure(true);
    }

    @AfterClass
    public static void stopServer() {
        stopQuietly(mockServerClient);

        // set back to original value
        controlPlaneJWTAuthenticationJWKSource(originalControlPlaneJWTAuthenticationJWKSource);
        controlPlaneJWTAuthenticationRequired(originalControlPlaneJWTAuthenticationRequired);
    }

    @Override
    public int getServerPort() {
        return severHttpPort;
    }

    @Override
    protected boolean isSecureControlPlane() {
        return true;
    }

    protected Header authorisationHeader() {
        return authorisationHeader;
    }

}
