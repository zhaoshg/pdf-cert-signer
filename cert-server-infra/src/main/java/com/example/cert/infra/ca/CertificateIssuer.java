package com.example.cert.infra.ca;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.bouncycastle.asn1.ASN1ObjectIdentifier;
import org.bouncycastle.asn1.DEROctetString;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.BasicConstraints;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.KeyUsage;
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

    public IssueResult issue(int certType, String creditCode, String name, String department, String email, int validDays)
            throws Exception {

        log.info("Starting certificate issuance: certType={}, name={}, creditCode={}", certType, name, creditCode);
        String signerId = signerIdGenerator.generate();
        log.info("Generated signerId: {}", signerId);

        KeyPairGenerator keyPairGen = KeyPairGenerator.getInstance("RSA", "BC");
        keyPairGen.initialize(2048);
        KeyPair keyPair = keyPairGen.generateKeyPair();
        log.info("RSA-2048 key pair generated");

        String cn = department != null && !department.isBlank() ? name + "(" + department + ")" : name;
        String dn = "CN=" + cn + ",OU=" + creditCode + ",O=PDFSigner,C=CN";
        X500Name subject = new X500Name(dn);

        BigInteger serial = BigInteger.valueOf(System.currentTimeMillis());
        Instant now = Instant.now();
        Date notBefore = Date.from(now);
        Date notAfter = Date.from(now.plus(validDays, ChronoUnit.DAYS));

        X500Name issuer = new X500Name(rootCaManager.getRootCert().getSubjectX500Principal().getName());
        JcaX509v3CertificateBuilder certBuilder = new JcaX509v3CertificateBuilder(
                issuer, serial, notBefore, notAfter, subject, keyPair.getPublic());

        certBuilder.addExtension(Extension.basicConstraints, true, new BasicConstraints(false));
        certBuilder.addExtension(Extension.keyUsage, true,
                new KeyUsage(KeyUsage.digitalSignature | KeyUsage.nonRepudiation));

        certBuilder.addExtension(
                new ASN1ObjectIdentifier(OID_SIGNER_ID),
                false,
                new DEROctetString(signerId.getBytes()));

        log.info("Signing certificate with root CA key...");
        ContentSigner signer = new JcaContentSignerBuilder("SHA256WithRSA")
                .setProvider("BC")
                .build(rootCaManager.getRootPrivateKey());

        X509CertificateHolder certHolder = certBuilder.build(signer);
        X509Certificate cert = new JcaX509CertificateConverter()
                .setProvider("BC")
                .getCertificate(certHolder);
        log.info("Certificate signed and converted: {}", cert.getSubjectX500Principal());

        log.info("Storing into PKCS12 keystore...");
        KeyStore p12 = KeyStore.getInstance("PKCS12", "BC");
        p12.load(null, null);
        char[] password = keySecret.toCharArray();
        p12.setKeyEntry("user-cert", keyPair.getPrivate(), password,
                new X509Certificate[]{cert, rootCaManager.getRootCert()});

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        p12.store(baos, password);
        byte[] p12Data = baos.toByteArray();
        log.info("PKCS12 keystore created, size: {} bytes", p12Data.length);

        LocalDateTime validFrom = LocalDateTime.ofInstant(notBefore.toInstant(), ZoneId.systemDefault());
        LocalDateTime validTo = LocalDateTime.ofInstant(notAfter.toInstant(), ZoneId.systemDefault());

        log.info("Certificate issued: signerId={}, certType={}, subject={}, serial={}", signerId, certType, dn, serial.toString(16));

        return new IssueResult(signerId, certType, serial.toString(16), dn, validFrom, validTo, p12Data);
    }
}
