package com.example.cert.infra.http;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.net.http.HttpClient;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.time.Duration;

public final class HttpClients {

    private static final Logger log = LoggerFactory.getLogger(HttpClients.class);

    public static final HttpClient TRUST_ALL;

    static {
        TRUST_ALL = buildTrustAllClient();
        log.info("Trust-all HTTP client initialized");
    }

    private static HttpClient buildTrustAllClient() {
        try {
            TrustManager[] trustAll = new TrustManager[]{
                new X509TrustManager() {
                    public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[0]; }
                    public void checkClientTrusted(X509Certificate[] certs, String authType) {}
                    public void checkServerTrusted(X509Certificate[] certs, String authType) {}
                }
            };
            SSLContext sc = SSLContext.getInstance("TLS");
            sc.init(null, trustAll, new SecureRandom());
            return HttpClient.newBuilder()
                    .sslContext(sc)
                    .connectTimeout(Duration.ofSeconds(30))
                    .build();
        } catch (Exception e) {
            throw new RuntimeException("Failed to init trust-all HTTP client", e);
        }
    }

    private HttpClients() {}
}
