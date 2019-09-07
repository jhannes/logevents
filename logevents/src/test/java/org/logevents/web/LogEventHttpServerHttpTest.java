package org.logevents.web;

import org.junit.Before;
import org.junit.Test;
import org.logevents.util.NetUtils;
import org.logevents.util.openid.OpenIdConfiguration;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.util.Map;
import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class LogEventHttpServerHttpTest {

    private LogEventHttpServer server;

    @Before
    public void setUp() throws Exception {
        server = new LogEventHttpServer();
    }

    @Test
    public void shouldFetchLoggingPageOnHttp() throws IOException {
        LogEventHttpServer server = new LogEventHttpServer();
        server.setHttpPort(java.util.Optional.of(0));
        server.start();

        String frontPage = readURL(server.getUrl());
        assertTrue(frontPage + " should contain " + "<title>LogEvents Dashboard</title>",
                frontPage.contains("<title>LogEvents Dashboard</title>"));

        String style = readURL(server.getUrl() + "/logevents.css");
        assertTrue(style + " should contain 'nav .closeDrawer {'",
                style.contains("nav .closeDrawer {"));

        String script = readURL(server.getUrl() + "/logevents-common.js");
        assertTrue(script + " should contain 'function createElementWithText'",
                script.contains("function createElementWithText"));
    }

    @Test
    public void shouldGenerateLoginRedirectOnHttps() throws IOException, GeneralSecurityException {
        server.setHttpsPort(java.util.Optional.of(0));
        server.setHostname("localhost");
        server.setKeyStore(Optional.of("target/test-cert.p12"));
        server.setOpenIdConfiguration(new OpenIdConfiguration(null, "the-client", null) {
            @Override
            protected String getAuthorizationEndpoint() {
                return "http://my-nice-idp/authorize";
            }
        });
        server.start();

        HttpsURLConnection connection = (HttpsURLConnection) new URL(server.getUrl() + "/login").openConnection();
        SSLContext sslContext = createSslContext("key-localhost.crt");
        connection.setSSLSocketFactory(sslContext.getSocketFactory());
        connection.setInstanceFollowRedirects(false);

        assertEquals(302, connection.getResponseCode());
        URL location = new URL(connection.getHeaderField("Location"));
        assertEquals("my-nice-idp", location.getAuthority());
        assertEquals("/authorize", location.getPath());

        Map<String, String[]> parameters = AbstractLogEventHttpServer.parseParameters(location.getQuery());
        assertEquals("the-client", parameters.get("client_id")[0]);
        assertEquals(server.getUrl() + "/oauth2callback", parameters.get("redirect_uri")[0]);
    }

    @Test
    public void shouldReturn404OnUnknownFile() throws IOException {
        LogEventHttpServer server = new LogEventHttpServer();
        server.setHttpPort(java.util.Optional.of(0));
        server.start();
        HttpURLConnection connection = (HttpURLConnection) new URL(server.getUrl() + "/logevents-missing.js").openConnection();
        assertEquals(404, connection.getResponseCode());
    }

    private String readURL(String url) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        return NetUtils.readAsString(connection.getInputStream());
    }

    SSLContext createSslContext(String rootCaFile) throws GeneralSecurityException, IOException {
        SSLContext sslContext = SSLContext.getInstance("TLS");
        sslContext.init(null, createTrustManagers(rootCaFile), null);
        return sslContext;
    }

    private TrustManager[] createTrustManagers(String rootCaFile) throws KeyStoreException, IOException, NoSuchAlgorithmException, CertificateException {
        KeyStore trustStore = KeyStore.getInstance(KeyStore.getDefaultType());
        trustStore.load(null, null);
        try (FileInputStream inStream = new FileInputStream(rootCaFile)) {
            trustStore.setCertificateEntry("ca",
                    CertificateFactory.getInstance("X.509").generateCertificate(inStream));
        }
        TrustManagerFactory trustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        trustManagerFactory.init(trustStore);
        return trustManagerFactory.getTrustManagers();
    }
}
