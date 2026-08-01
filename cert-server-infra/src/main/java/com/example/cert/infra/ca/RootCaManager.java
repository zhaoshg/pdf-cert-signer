package com.example.cert.infra.ca;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.BasicConstraints;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.KeyUsage;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.*;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;

@Component
public class RootCaManager {

    private static final Logger log = LoggerFactory.getLogger(RootCaManager.class);

    @Value("${cert.root-ca.keystore-path:conf/root-ca.p12}")
    private String keystorePath;

    @Value("${cert.root-ca.keystore-password:root-ca-secret}")
    private String keystorePassword;

    private KeyStore keyStore;

    private X509Certificate rootCert;

    private PrivateKey rootPrivateKey;

    static {
        Security.addProvider(new BouncyCastleProvider());
    }

    @PostConstruct
    public void init() throws Exception {
        Path path = Paths.get(keystorePath);
        if (Files.exists(path)) {
            load(path);
        } else {
            generate(path);
        }
    }

    private void load(Path path) throws Exception {
        keyStore = KeyStore.getInstance("PKCS12");
        try (InputStream is = new FileInputStream(path.toFile())) {
            keyStore.load(is, keystorePassword.toCharArray());
        }
        String alias = keyStore.aliases().nextElement();
        rootCert = (X509Certificate) keyStore.getCertificate(alias);
        rootPrivateKey = (PrivateKey) keyStore.getKey(alias, keystorePassword.toCharArray());
        log.info("Root CA loaded, subject: {}", rootCert.getSubjectX500Principal());
    }

    private void generate(Path path) throws Exception {
        KeyPairGenerator keyPairGen = KeyPairGenerator.getInstance("RSA", "BC");
        keyPairGen.initialize(4096);
        KeyPair keyPair = keyPairGen.generateKeyPair();

        X500Name issuer = new X500Name("CN=PDF Signer Root CA,OU=CertServer,O=Internal,C=CN");
        BigInteger serial = BigInteger.valueOf(System.currentTimeMillis());
        Instant now = Instant.now();
        Date notBefore = Date.from(now);
        Date notAfter = Date.from(now.plus(3650, ChronoUnit.DAYS));

        JcaX509v3CertificateBuilder certBuilder = new JcaX509v3CertificateBuilder(
                issuer, serial, notBefore, notAfter, issuer, keyPair.getPublic());

        certBuilder.addExtension(Extension.basicConstraints, true, new BasicConstraints(0));
        certBuilder.addExtension(Extension.keyUsage, true,
                new KeyUsage(KeyUsage.keyCertSign | KeyUsage.cRLSign));

        ContentSigner signer = new JcaContentSignerBuilder("SHA256WithRSA")
                .setProvider("BC")
                .build(keyPair.getPrivate());

        X509CertificateHolder certHolder = certBuilder.build(signer);
        X509Certificate cert = new JcaX509CertificateConverter()
                .setProvider("BC")
                .getCertificate(certHolder);

        keyStore = KeyStore.getInstance("PKCS12");
        keyStore.load(null, null);
        keyStore.setKeyEntry("root-ca", keyPair.getPrivate(), keystorePassword.toCharArray(),
                new X509Certificate[]{cert});

        Files.createDirectories(path.getParent());
        try (FileOutputStream fos = new FileOutputStream(path.toFile())) {
            keyStore.store(fos, keystorePassword.toCharArray());
        }

        rootCert = cert;
        rootPrivateKey = keyPair.getPrivate();
        log.info("Root CA generated and saved to {}", keystorePath);
    }

    public KeyStore getKeyStore() {
        return keyStore;
    }

    public X509Certificate getRootCert() {
        return rootCert;
    }

    public PrivateKey getRootPrivateKey() {
        return rootPrivateKey;
    }
}
