package org.mockserver.integration.proxy.http;

import org.apache.commons.codec.binary.Hex;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.entity.ByteArrayEntity;
import org.apache.http.util.EntityUtils;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mockserver.client.MockServerClient;
import org.mockserver.echo.http.EchoServer;
import org.mockserver.integration.proxy.AbstractClientProxyIntegrationTest;
import org.mockserver.mockserver.MockServer;
import org.mockserver.model.HttpStatusCode;

import static org.junit.Assert.assertEquals;
import static org.mockserver.model.BinaryBody.binary;
import static org.mockserver.model.Header.header;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.stop.Stop.stopQuietly;
import static org.mockserver.verify.VerificationTimes.exactly;

/**
 * @author jamesdbloom
 */
public class NettyHttpProxyIntegrationTest extends AbstractClientProxyIntegrationTest {

    private static int mockServerPort;
    private static EchoServer echoServer;
    private static MockServerClient mockServerClient;

    @BeforeClass
    public static void setupFixture() {
        servletContext = "";

        mockServerPort = new MockServer().getLocalPort();
        mockServerClient = new MockServerClient("localhost", mockServerPort);

        echoServer = new EchoServer(false);
    }

    @AfterClass
    public static void stopServer() {
        stopQuietly(echoServer);
        stopQuietly(mockServerClient);
    }

    @Before
    public void resetProxy() {
        mockServerClient.reset();
    }

    @Override
    public int getProxyPort() {
        return mockServerPort;
    }

    @Override
    public int getSecureProxyPort() {
        return mockServerPort;
    }

    @Override
    public MockServerClient getMockServerClient() {
        return mockServerClient;
    }

    @Override
    public int getServerPort() {
        return echoServer.getPort();
    }

    @Test
    public void shouldForwardRequestsAndFixContentType() throws Exception {
        // given
        getMockServerClient()
            .when(
                request()
                    .withHeader("Content-Type", "application/encrypted;charset=UTF-8")
            )
            .forward(
                httpRequest -> {
                    String hexBytes = Hex.encodeHexString(httpRequest.getBodyAsRawBytes()).toUpperCase();
                    System.out.println("hexBytes in callback = " + hexBytes);
                    return httpRequest
                        .replaceHeader(header("Content-Type", "application/encrypted"))
                        .withBody(binary(httpRequest.getBodyAsRawBytes()));
                }
            );

        // and
        HttpClient httpClient = createHttpClient();
        String hexString = "007C2C35913A4AF4B4E8969CEBDCF0D5613630E869B2CBF5C9B53BF94D706DA4810486D90556F79DDF547D8882D5A076CE75EC65F58B545FBD3AAC52460FF8E8DC65015E873C6745E8BE0B7ED8DA474D2E1CC33BC342ED8C5A2D7840365E743FE8E394A816CA8ECBE19D1905F72345B3082D1B080D29F2531E5CFF4A7914C31BEDB0CEC003A3652A68B587652BAF40F37B4B6BA62161CEB2FF2E5CE9C3E28E510E3A9EE2A64E217492ED7B75A740B9D662164A0D9D6EB8400171F068C68ADA89681F4AC32CFAA338D339F31AC3A2CC35E86EED96C2AEEBA08621A51141DFD6B8F90D04D16447343EF01B097495BE811FEE856D724C5CA1D33524162AD1FB9630F323175F4A4DCF821CB8CB416899CED9CF3A5F4736D8D39E37300A288ED9402DC5225144916F68EF93FAA2626CC3D56D10EF54DAE130A340C2080ED704D6B1FF1A11D3BD2D2229A6F07AE4BFF7C238F3FA87F67D29973E6716148411E652B6981A0DE431FCD5CC4734E2E8D8522361312DB1764AF05779D1813F9BD910515265FEE60060EBCF7FE1880384CF8F8CE204B84916DE920FB4EC864060358CFADFA793C86AB82F1A74D9BB19D85BFB5D8E191CDD18C0CE6A19DFA1C815A6DFB153836F3DA9DE5A3343E84F11E4994F5ACB77BAEC332DCBD35F922863AFB8089CCEE9620268C36F5BE090063ABC687E60D488F49EBC4E863EF7BAD1AD862BAB44AD1A1C8D5618EC9BA95510953C12CFD30CA781CF4FA8DA7DCD161B41C5615084EE27B429BBAC3106FC81BFADB1642439ACA869539E999D701D726DEA59A468070D6777E91A69446F255576143DBA2A4BD42028D0CFDA24D1B0365A96876D3F08493E13F46C6968CF4B0D2DB6B7023B7C8FB7FA8DE427AF8F0AF26FF021C9036378B9CD0B52FD07C46B12548D06FF0F810C76A0F450BA944801D4228DD0516504DE236FE48B5D70F235257429D031EE7D496E86A08C57B949DF26B053731A8A3B71C5FFAD9C7436B91F34FCD6C1F908854758503DC03AE2F7991038412CDE090203DFA8C14AD97DCD6ACD9EE8CF7A4D96B2F83D11AAC2F7FEBB06DE72C1A53BE21AD9BF26DD50798724F01C48E1A5CD1E5476E6956868ACE57E69A4CFA7ECE7CAD3E6AB226C4AD3E4A6F38369A56A2FBF1E0D112F31F516A1DF0CEB6B8242EFE80C46478CC87D77A2E2D323A166D399B86A2332175FFC137721BD3C0BB303D5FE23E696B2E3BC5EFAD5406119409E1F9D4418C517FD5D9A69E9CD24E421BF9BA839ECE572CF1762F5AECD20B5925C68A13A72EBD033710F268938B0D092E9467DD6D5109E29F25573F298AE452C45D9E8CFD64C8E6475C336484DB04164C33DB20A37045C945D50C0E93EB2605CDD461738C54DC837452FA8EA7280D2E48CC29EB3991A6906F17FA905C0A10AD9C3A9ED6B8A6602EB07903AB4FB158D15B5E54F27629D48D2E800A8BE50892E33F2B7750D437EC9FD642778B8276E8C6CF4E16DAF9E30DFBE3F48BD7FC995F4247AEB0662B2D9D00E02C6E5EC48F0AC9AF73AF87B6C89D8D9654C1735E2723BD94ACB7F17DB64884A1ACFF852F0D26ECF0736904547A71AAC2FD1C07F92BEBF5C4CD34A51A0C6A42E6AB2CC2AB176F8AA049B6F2804874A3FB2556F7A1D9BF2545B05B8FB813CE89EBA5E018E655F29F61280497312B6F85D397041E9EAF38579FEBD32CBDD902CFA5C33E1E1B9BD09829C2EEC6716736A8970ACCF003FD46831409B72FBCEBB22919E504E486D2DE55C19C0845A4B0E1CE5BEA0509E081C20E7E0DB6E937AB029C2C0BDAA49BF2E1E0EC75469EC4100CD8C9601";
        System.out.println("hexString in original request = " + hexString);
        byte[] hexBytes = Hex.decodeHex(hexString);

        // when
        HttpPost request = new HttpPost(
            new URIBuilder()
                .setScheme("http")
                .setHost("127.0.0.1")
                .setPort(getServerPort())
                .setPath(addContextToPath("test_headers_and_body"))
                .build()
        );
        request.setEntity(new ByteArrayEntity(hexBytes));
        request.setHeader("Content-Type", "application/encrypted;charset=utf-8");
        HttpResponse response = httpClient.execute(request);

        // then
        assertEquals(HttpStatusCode.OK_200.code(), response.getStatusLine().getStatusCode());
        assertEquals(hexString.toUpperCase(), Hex.encodeHexString(EntityUtils.toByteArray(response.getEntity())).toUpperCase());

        // and
        getMockServerClient().verify(
            request()
                .withMethod("POST")
                .withPath("/test_headers_and_body")
                .withBody(hexBytes),
            exactly(1)
        );
    }

}
