package org.logevents.observers;

import org.junit.Rule;
import org.junit.Test;
import org.logevents.LogEvent;
import org.logevents.extend.junit.LogEventStatusRule;
import org.logevents.extend.servlets.LogEventSampler;
import org.logevents.status.StatusEvent;
import org.logevents.util.JsonParser;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
import java.io.IOException;
import java.net.URL;
import java.security.GeneralSecurityException;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import static org.junit.Assert.assertEquals;

public class WebLogEventObserverTest {

    @Rule
    public LogEventStatusRule logEventStatusRule = new LogEventStatusRule(StatusEvent.StatusLevel.ERROR);

    @Test
    public void shouldFetchLogEvents() throws IOException, GeneralSecurityException {
        Properties properties = new Properties();
        properties.setProperty("observer.web.httpsPort", "0");
        properties.setProperty("observer.web.openIdIssuer", "https://accounts.google.com");
        properties.setProperty("observer.web.clientId", "dummy");
        properties.setProperty("observer.web.clientSecret", "dummy");

        WebLogEventObserver observer = new WebLogEventObserver(properties, "observer.web");
        LogEvent logEvent = new LogEventSampler().build();
        observer.logEvent(logEvent);

        HttpsURLConnection connection = (HttpsURLConnection) new URL(observer.getServerUrl() + "/events").openConnection();
        connection.setSSLSocketFactory(createSSLContext(observer.getCertificate()).getSocketFactory());
        connection.setRequestProperty("Cookie", observer.createSessionCookie("jhannes"));

        Map<String, Object> objects = (Map<String, Object>)JsonParser.parse(connection);
        String loggedMessage = ((List<Map<String, Object>>) objects.get("events")).get(0).get("messageTemplate").toString();
        assertEquals(logEvent.getMessage(), loggedMessage);
    }

    SSLContext createSSLContext(Certificate certificate) throws KeyStoreException, IOException, NoSuchAlgorithmException, CertificateException, KeyManagementException {
        KeyStore keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
        keyStore.load(null, null);
        keyStore.setCertificateEntry("server", certificate);

        TrustManagerFactory trustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        trustManagerFactory.init(keyStore);

        SSLContext sslContext = SSLContext.getInstance("TLS");
        sslContext.init(null, trustManagerFactory.getTrustManagers(), null);
        return sslContext;
    }
}