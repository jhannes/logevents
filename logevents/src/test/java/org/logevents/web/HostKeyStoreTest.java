package org.logevents.web;

import org.junit.Test;
import sun.security.x509.GeneralNameInterface;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.StringWriter;
import java.security.GeneralSecurityException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.Base64;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class HostKeyStoreTest {

    @Test
    public void shouldGenerateCertificateWithValidSubject() throws GeneralSecurityException, IOException {
        File file = new File("target/tmp/test.p12");
        file.getParentFile().mkdirs();
        file.delete();
        String storePassword = "dsngalbfakjgnajnf";
        HostKeyStore hostKeyStore = new HostKeyStore(file, storePassword);
        hostKeyStore.setHostName("myhost.local");
        hostKeyStore.setKeyPassword("esndlksdngln");
        hostKeyStore.generateKey();
        X509Certificate certificate = hostKeyStore.getCertificate();
        assertEquals("CN=myhost.local", certificate.getSubjectDN().getName());
        List<?> subjectAlternativeNames = certificate.getSubjectAlternativeNames().iterator().next();
        assertEquals(GeneralNameInterface.NAME_DNS, subjectAlternativeNames.get(0));
        assertEquals("myhost.local", subjectAlternativeNames.get(1));
    }

    @Test
    public void shouldWriteCertificateToWriter() throws GeneralSecurityException, IOException {
        File file = new File("target/tmp/test2.p12");
        file.getParentFile().mkdirs();
        file.delete();
        String storePassword = "dsngalbfakjgnajnfwfs";

        HostKeyStore hostKeyStore = new HostKeyStore(file, storePassword);
        hostKeyStore.setHostName("myhost2.local");
        hostKeyStore.setKeyPassword("esndlksdngln2");
        hostKeyStore.generateKey();

        StringWriter writer = new StringWriter();
        hostKeyStore.writeCertificate(writer);

        String certificateString = writer.toString()
                .replace("-----BEGIN CERTIFICATE-----", "")
                .replace("-----END CERTIFICATE-----", "")
                .replaceAll("\n", "");
        byte[] certificateData = Base64.getDecoder().decode(certificateString);
        X509Certificate certificate = (X509Certificate) CertificateFactory.getInstance("X509")
                .generateCertificate(new ByteArrayInputStream(certificateData));

        assertEquals(certificate, hostKeyStore.getCertificate());
    }

    @Test
    public void shouldStoreKeyToFile() throws GeneralSecurityException, IOException {
        File file = new File("target/tmp/test3.p12");
        file.getParentFile().mkdirs();
        file.delete();
        String storePassword = "sgmsklgsdklng";

        HostKeyStore hostKeyStore = new HostKeyStore(file, storePassword);
        hostKeyStore.setHostName("myhost3.local");
        hostKeyStore.setKeyPassword("esndlksdngln2");
        assertFalse(hostKeyStore.isKeyPresent());
        hostKeyStore.generateKey();

        HostKeyStore hostKeyStore2 = new HostKeyStore(file, storePassword);
        hostKeyStore2.setHostName("myhost3.local");
        hostKeyStore2.setKeyPassword("esndlksdngln2");
        assertTrue(hostKeyStore2.isKeyPresent());


        assertEquals(hostKeyStore.getCertificate(), hostKeyStore2.getCertificate());
    }
}