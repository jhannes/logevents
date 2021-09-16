package org.logevents.observers.web;

import sun.security.x509.AlgorithmId;
import sun.security.x509.CertificateAlgorithmId;
import sun.security.x509.CertificateExtensions;
import sun.security.x509.CertificateSerialNumber;
import sun.security.x509.CertificateValidity;
import sun.security.x509.CertificateVersion;
import sun.security.x509.CertificateX509Key;
import sun.security.x509.DNSName;
import sun.security.x509.GeneralName;
import sun.security.x509.GeneralNames;
import sun.security.x509.KeyUsageExtension;
import sun.security.x509.SubjectAlternativeNameExtension;
import sun.security.x509.X500Name;
import sun.security.x509.X509CertImpl;
import sun.security.x509.X509CertInfo;

import javax.net.ssl.KeyManager;
import javax.net.ssl.KeyManagerFactory;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.math.BigInteger;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.cert.Certificate;
import java.security.cert.CertificateEncodingException;
import java.security.cert.X509Certificate;
import java.util.Base64;
import java.util.Date;
import java.util.Optional;

public class HostKeyStore {
    private final KeyStore keyStore;
    private final File file;
    private String hostName;
    private String keyPassword;
    private char[] storePassword;

    public HostKeyStore(File file, String storePasswordString) throws GeneralSecurityException, IOException {
        keyStore = KeyStore.getInstance("pkcs12");
        this.file = file;
        this.storePassword = storePasswordString.toCharArray();
        if (this.file.exists()) {
            keyStore.load(new FileInputStream(this.file), storePassword);
        } else {
            keyStore.load(null, storePassword);
        }
    }

    public void setHostName(String hostName) {
        this.hostName = hostName;
    }

    public void setKeyPassword(String keyPassword) {
        this.keyPassword = keyPassword;
    }

    public boolean isKeyPresent() throws KeyStoreException {
        return keyStore.isKeyEntry("key-" + hostName);
    }

    public void generateKey() throws IOException, GeneralSecurityException {
        generateCertificateAndKey();
        keyStore.store(new FileOutputStream(file), storePassword);
    }

    private void generateCertificateAndKey() throws GeneralSecurityException, IOException {
        KeyPair keyPair = KeyPairGenerator.getInstance("RSA").generateKeyPair();

        X509Certificate certificateImpl = generatedSignedCertificate(keyPair.getPublic(), keyPair.getPrivate(),
                Optional.ofNullable(hostName).orElseThrow(() -> new IllegalStateException("Call setHostName() before generating certicate")));

        keyStore.setKeyEntry(
                "key-" + hostName,
                keyPair.getPrivate(),
                Optional.ofNullable(keyPassword).orElseThrow(() -> new IllegalArgumentException("Call setKeyPassword() before generating certicate")).toCharArray(),
                new Certificate[]{ certificateImpl });
    }

    private X509Certificate generatedSignedCertificate(PublicKey publicKey, PrivateKey privateKey, String hostName) throws GeneralSecurityException, IOException {
        X509CertInfo certInfo = new X509CertInfo();
        certInfo.set(X509CertInfo.VERSION, new CertificateVersion(CertificateVersion.V3));
        certInfo.set(X509CertInfo.SERIAL_NUMBER, new CertificateSerialNumber(new BigInteger(64, new SecureRandom())));
        Date validFrom = new Date();
        long validityDays = 90;
        Date validTo = new Date(validFrom.getTime() + validityDays * 86400000L);
        certInfo.set(X509CertInfo.VALIDITY, new CertificateValidity(validFrom, validTo));
        certInfo.set(X509CertInfo.ALGORITHM_ID, new CertificateAlgorithmId(
                new AlgorithmId(AlgorithmId.sha512WithRSAEncryption_oid)));
        certInfo.set(X509CertInfo.KEY, new CertificateX509Key(publicKey));
        certInfo.set(X509CertInfo.ISSUER, new X500Name("CN=" + hostName));
        certInfo.set(X509CertInfo.SUBJECT, new X500Name("CN=" + hostName));

        CertificateExtensions extensions = new CertificateExtensions();

        KeyUsageExtension keyUsageExtension = new KeyUsageExtension();
        keyUsageExtension.set(KeyUsageExtension.KEY_CERTSIGN, true);
        keyUsageExtension.set(KeyUsageExtension.CRL_SIGN, true);
        extensions.set(KeyUsageExtension.NAME, keyUsageExtension);

        GeneralNames names = new GeneralNames();
        names.add(new GeneralName(new DNSName(hostName)));
        extensions.set(SubjectAlternativeNameExtension.NAME, new SubjectAlternativeNameExtension(names));

        certInfo.set(X509CertInfo.EXTENSIONS, extensions);

        X509CertImpl certificateImpl = new X509CertImpl(certInfo);
        certificateImpl.sign(privateKey, "SHA512withRSA");
        return certificateImpl;
    }

    public void writeCertificate(File file) throws IOException, GeneralSecurityException {
        try (FileWriter writer = new FileWriter(file)) {
            writeCertificate(writer);
        }
    }

    public void writeCertificate(Writer writer) throws KeyStoreException, IOException, CertificateEncodingException {
        Certificate certificate = getCertificate();
        writer.write("-----BEGIN CERTIFICATE-----\n");
        writer.write(Base64.getEncoder().encodeToString(certificate.getEncoded()));
        writer.write("\n-----END CERTIFICATE-----");
    }

    public X509Certificate getCertificate() throws KeyStoreException {
        return (X509Certificate) keyStore.getCertificate("key-" + hostName);
    }

    public KeyManager[] getKeyManagers() throws GeneralSecurityException {
        KeyManagerFactory keyManagerFactory = KeyManagerFactory.getInstance("SunX509");
        keyManagerFactory.init(keyStore, keyPassword.toCharArray());
        return keyManagerFactory.getKeyManagers();
    }
}
