package com.example.cert.infra.signing;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.security.MessageDigest;

@Component
public class TsaClient {

    private static final Logger log = LoggerFactory.getLogger(TsaClient.class);

    @Value("${cert.tsa.url:https://freetsa.org/tsr}")
    private String tsaUrl;

    @Value("${cert.tsa.timeout-seconds:10}")
    private int timeoutSeconds;

    @Value("${cert.tsa.fail-on-error:false}")
    private boolean failOnError;

    public byte[] getTimestampToken(byte[] documentHash) {
        if (tsaUrl == null || tsaUrl.isBlank()) {
            log.info("TSA not configured, skipping");
            return null;
        }
        try {
            log.info("Requesting timestamp from: {}", tsaUrl);
            byte[] request = buildTimestampRequest(documentHash);
            HttpURLConnection conn = (HttpURLConnection) URI.create(tsaUrl).toURL().openConnection();
            conn.setDoOutput(true);
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/timestamp-query");
            conn.setConnectTimeout(timeoutSeconds * 1000);
            conn.setReadTimeout(timeoutSeconds * 1000);

            try (OutputStream os = conn.getOutputStream()) {
                os.write(request);
            }

            int responseCode = conn.getResponseCode();
            log.info("TSA response: HTTP {}", responseCode);
            if (responseCode == 200) {
                try (InputStream is = conn.getInputStream()) {
                    ByteArrayOutputStream baos = new ByteArrayOutputStream();
                    byte[] buf = new byte[4096];
                    int n;
                    while ((n = is.read(buf)) != -1) {
                        baos.write(buf, 0, n);
                    }
                    byte[] token = baos.toByteArray();
                    log.info("Timestamp token received: {} bytes", token.length);
                    return token;
                }
            } else {
                log.warn("TSA returned non-200: {}", responseCode);
            }
        } catch (Exception e) {
            log.error("TSA request failed", e);
            if (failOnError) {
                throw new RuntimeException("时间戳请求失败: " + e.getMessage(), e);
            }
        }
        return null;
    }

    private byte[] buildTimestampRequest(byte[] documentHash) throws Exception {
        org.bouncycastle.tsp.TimeStampRequestGenerator generator =
                new org.bouncycastle.tsp.TimeStampRequestGenerator();
        generator.setCertReq(true);
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hash = digest.digest(documentHash);
        return generator.generate(
                new org.bouncycastle.asn1.ASN1ObjectIdentifier("2.16.840.1.101.3.4.2.1"),
                hash).getEncoded();
    }
}
