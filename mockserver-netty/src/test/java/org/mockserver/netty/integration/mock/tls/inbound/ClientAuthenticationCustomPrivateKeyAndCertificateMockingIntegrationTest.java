package org.mockserver.netty.integration.mock.tls.inbound;

import com.google.common.collect.Sets;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mockserver.cli.Main;
import org.mockserver.client.MockServerClient;
import org.mockserver.configuration.ConfigurationProperties;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.socket.PortFactory;
import org.mockserver.socket.tls.KeyStoreFactory;
import sun.security.util.Debug;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLPeerUnverifiedException;
import javax.net.ssl.SSLSession;
import java.nio.charset.StandardCharsets;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.mockserver.configuration.ConfigurationProperties.*;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;
import static org.mockserver.model.HttpStatusCode.OK_200;
import static org.mockserver.socket.tls.jdk.X509Generator.x509FromPEMFile;
import static org.mockserver.stop.Stop.stopQuietly;

/**
 * @author jamesdbloom
 */
public class ClientAuthenticationCustomPrivateKeyAndCertificateMockingIntegrationTest extends AbstractClientAuthenticationMockingIntegrationTest {

    private static final int severHttpPort = PortFactory.findFreePort();
    private static String originalCertificateAuthorityCertificate;
    private static String originalPrivateKeyPath;
    private static String originalX509CertificatePath;

    @BeforeClass
    public static void startServer() {
        // save original value
        originalCertificateAuthorityCertificate = certificateAuthorityCertificate();
        originalPrivateKeyPath = privateKeyPath();
        originalX509CertificatePath = x509CertificatePath();

        // set new certificate authority values
        certificateAuthorityCertificate("org/mockserver/netty/integration/tls/ca.pem");
        privateKeyPath("org/mockserver/netty/integration/tls/leaf-key-pkcs8.pem");
        x509CertificatePath("org/mockserver/netty/integration/tls/leaf-cert.pem");
        tlsMutualAuthenticationRequired(true);

        Main.main("-serverPort", "" + severHttpPort);

        mockServerClient = new MockServerClient("localhost", severHttpPort).withSecure(true);
    }

    @AfterClass
    public static void stopServer() {
        stopQuietly(mockServerClient);

        // set back to original value
        certificateAuthorityCertificate(originalCertificateAuthorityCertificate);
        privateKeyPath(originalPrivateKeyPath);
        x509CertificatePath(originalX509CertificatePath);
        tlsMutualAuthenticationRequired(false);
    }

    @Override
    public int getServerPort() {
        return severHttpPort;
    }


    @Test
    public void shouldReturnExpectedX509Certificate() throws Exception {
        // given
        mockServerClient
            .when(
                request()
                    .withMethod("POST")
                    .withPath(calculatePath("some_path"))
            )
            .respond(
                response()
                    .withStatusCode(200)
                    .withBody("some_body_response")
            );
        X509Certificate x509Certificate = x509FromPEMFile(ConfigurationProperties.x509CertificatePath());

        // when
        HttpClient httpClient = HttpClients
            .custom()
            .setSSLContext(new KeyStoreFactory(new MockServerLogger()).sslContext())
            .setSSLHostnameVerifier(new PinningHostnameVerifier(Sets.newHashSet(Debug.toHexString(x509Certificate.getSerialNumber()).replaceAll("\\W", ""))))
            .build();
        HttpResponse response = httpClient.execute(new HttpPost(new URIBuilder()
            .setScheme("https")
            .setHost("localhost")
            .setPort(getServerPort())
            .setPath(calculatePath("some_path"))
            .build()));
        String responseBody = new String(EntityUtils.toByteArray(response.getEntity()), StandardCharsets.UTF_8);

        // then
        assertThat(response.getStatusLine().getStatusCode(), is(OK_200.code()));
        assertThat(responseBody, is("some_body_response"));
    }

    public static class PinningHostnameVerifier implements HostnameVerifier {
        final Set<String> expectedSerialNumbers;

        public PinningHostnameVerifier(Set<String> expectedSerialNumbers) {
            this.expectedSerialNumbers = expectedSerialNumbers;
        }

        @Override
        public boolean verify(String host, SSLSession sslSession) {
            List<String> foundSerialNumbers = new ArrayList<>();
            try {
                for (Certificate certificate : sslSession.getPeerCertificates()) {
                    if (certificate instanceof X509Certificate) {
                        X509Certificate x509Certificate = (X509Certificate) certificate;
                        String serialNumbers = Debug.toHexString(x509Certificate.getSerialNumber()).replaceAll("\\W", "");
                        foundSerialNumbers.add(serialNumbers);
                        if (expectedSerialNumbers.contains(serialNumbers)) {
                            return true;
                        }
                    }
                }
            } catch (SSLPeerUnverifiedException e) {
                throw new RuntimeException(e);
            }
            throw new RuntimeException("Certificate pinning failure expected " + expectedSerialNumbers + " found " + foundSerialNumbers);
        }
    }

}
