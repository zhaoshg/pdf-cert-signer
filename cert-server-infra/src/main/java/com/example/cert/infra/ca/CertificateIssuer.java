package com.example.cert.infra.ca;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.bouncycastle.asn1.ASN1ObjectIdentifier;
import org.bouncycastle.asn1.DEROctetString;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.math.BigInteger;
import java.security.*;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Date;

@Component
public class CertificateIssuer {

    private static final Logger log = LoggerFactory.getLogger(CertificateIssuer.class);
    private static final String OID_SIGNER_ID = "1.3.6.1.4.1.99999.1.1";

    private final RootCaManager rootCaManager;
    private final SignerIdGenerator signerIdGenerator;

    public CertificateIssuer(RootCaManager rootCaManager, SignerIdGenerator signerIdGenerator) {
        this.rootCaManager = rootCaManager;
        this.signerIdGenerator = signerIdGenerator;
    }

    @Value("${cert.key-secret:cert-secret-key-32bytes!!}")
    private String keySecret;

    public IssueResult issue(String creditCode, String name, String department, String email, int validDays)
            throws Exception {

        String signerId = signerIdGenerator.generate();

        KeyPairGenerator keyPairGen = KeyPairGenerator.getInstance("RSA", "BC");
        keyPairGen.initialize(2048);
        KeyPair keyPair = keyPairGen.generateKeyPair();

        String cn = name + "(" + department + ")";
        String dn = "CN=" + cn + ",OU=" + creditCode + ",O=PDFSigner,C=CN";
        X500Name subject = new X500Name(dn);

        BigInteger serial = BigInteger.valueOf(System.currentTimeMillis());
        Instant now = Instant.now();
        Date notBefore = Date.from(now);
        Date notAfter = Date.from(now.plus(validDays, ChronoUnit.DAYS));

        X500Name issuer = new X500Name(rootCaManager.getRootCert().getSubjectX500Principal().getName());
        JcaX509v3CertificateBuilder certBuilder = new JcaX509v3CertificateBuilder(
                issuer, serial, notBefore, notAfter, subject, keyPair.getPublic());

        certBuilder.addExtension(
                new ASN1ObjectIdentifier(OID_SIGNER_ID),
                false,
                new DEROctetString(signerId.getBytes()));

        ContentSigner signer = new JcaContentSignerBuilder("SHA256WithRSA")
                .setProvider("BC")
                .build(rootCaManager.getRootPrivateKey());

        X509CertificateHolder certHolder = certBuilder.build(signer);
        X509Certificate cert = new JcaX509CertificateConverter()
                .setProvider("BC")
                .getCertificate(certHolder);

        KeyStore p12 = KeyStore.getInstance("PKCS12");
        p12.load(null, null);
        char[] password = keySecret.toCharArray();
        p12.setKeyEntry("user-cert", keyPair.getPrivate(), password,
                new X509Certificate[]{cert, rootCaManager.getRootCert()});

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        p12.store(baos, password);
        byte[] p12Data = baos.toByteArray();

        Security.addProvider(new org.bouncycastle.jce.provider.BouncyCastleProvider());

        LocalDateTime validFrom = LocalDateTime.ofInstant(notBefore.toInstant(), ZoneId.systemDefault());
        LocalDateTime validTo = LocalDateTime.ofInstant(notAfter.toInstant(), ZoneId.systemDefault());

        log.info("Certificate issued: signerId={}, subject={}, serial={}", signerId, dn, serial.toString(16));

        return new IssueResult(signerId, serial.toString(16), dn, validFrom, validTo, p12Data);
    }
}
