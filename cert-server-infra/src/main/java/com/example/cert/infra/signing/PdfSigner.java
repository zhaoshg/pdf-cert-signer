package com.example.cert.infra.signing;

import com.example.cert.infra.ca.RootCaManager;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.pdmodel.interactive.digitalsignature.PDSignature;
import org.apache.pdfbox.pdmodel.interactive.digitalsignature.SignatureInterface;
import org.apache.pdfbox.pdmodel.interactive.digitalsignature.SignatureOptions;
import org.bouncycastle.cert.jcajce.JcaCertStore;
import org.bouncycastle.cms.*;
import org.bouncycastle.cms.jcajce.JcaSignerInfoGeneratorBuilder;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.bouncycastle.operator.jcajce.JcaDigestCalculatorProviderBuilder;
import org.bouncycastle.util.Store;
import org.springframework.stereotype.Component;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URI;
import java.security.KeyStore;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.*;

@Component
public class PdfSigner {

    private final RootCaManager rootCaManager;
    private final TsaClient tsaClient;

    public PdfSigner(RootCaManager rootCaManager, TsaClient tsaClient) {
        this.rootCaManager = rootCaManager;
        this.tsaClient = tsaClient;
    }

    public byte[] sign(byte[] pdfData, byte[] p12Data, String password,
                       Map<Integer, List<SignPosition>> sealsByPage) throws Exception {

        KeyStore ks = KeyStore.getInstance("PKCS12");
        ks.load(new ByteArrayInputStream(p12Data), password.toCharArray());
        String alias = ks.aliases().nextElement();
        PrivateKey privateKey = (PrivateKey) ks.getKey(alias, password.toCharArray());
        Certificate[] certChain = ks.getCertificateChain(alias);
        X509Certificate cert = (X509Certificate) certChain[0];

        PDDocument document = Loader.loadPDF(pdfData);
        ByteArrayOutputStream signedOutput = new ByteArrayOutputStream();

        document.addSignature(createSignature(cert), new PdfBoxSignature(
                privateKey, certChain, tsaClient, rootCaManager), createSignatureOptions(document, sealsByPage));
        document.saveIncremental(signedOutput);
        document.close();

        return signedOutput.toByteArray();
    }

    private PDSignature createSignature(X509Certificate cert) {
        PDSignature signature = new PDSignature();
        signature.setFilter(PDSignature.FILTER_ADOBE_PPKLITE);
        signature.setSubFilter(PDSignature.SUBFILTER_ADBE_PKCS7_DETACHED);
        signature.setName(cert.getSubjectX500Principal().getName());
        signature.setSignDate(Calendar.getInstance());
        return signature;
    }

    private SignatureOptions createSignatureOptions(PDDocument document, Map<Integer, List<SignPosition>> sealsByPage) {
        SignatureOptions options = new SignatureOptions();
        for (Map.Entry<Integer, List<SignPosition>> entry : sealsByPage.entrySet()) {
            int pageIndex = entry.getKey();
            if (pageIndex < document.getNumberOfPages()) {
                for (SignPosition pos : entry.getValue()) {
                    try {
                        options.setVisualSignature(createAppearance(document, pos));
                        options.setPage(pageIndex);
                    } catch (Exception ignored) {
                    }
                }
            }
        }
        return options;
    }

    private InputStream createAppearance(PDDocument document, SignPosition pos) throws Exception {
        PDDocument tempDoc = new PDDocument();
        float w = pos.width > 0 ? pos.width : 120;
        float h = pos.height > 0 ? pos.height : 120;
        PDPage page = new PDPage(new PDRectangle(w, h));
        tempDoc.addPage(page);

        try (PDPageContentStream cs = new PDPageContentStream(tempDoc, page)) {
            byte[] imageData = loadImage(pos.sealUrl);
            if (imageData != null) {
                PDImageXObject img = PDImageXObject.createFromByteArray(tempDoc, imageData, "seal");
                cs.drawImage(img, 0, 0, w, h);
            }
        }

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        tempDoc.save(baos);
        tempDoc.close();
        return new ByteArrayInputStream(baos.toByteArray());
    }

    private byte[] loadImage(String url) {
        if (url == null || url.isBlank()) {
            return null;
        }
        if (url.startsWith("data:")) {
            return decodeBase64DataUrl(url);
        }
        return downloadHttpImage(url);
    }

    private byte[] decodeBase64DataUrl(String url) {
        try {
            int commaIdx = url.indexOf(',');
            if (commaIdx < 0) return null;
            String base64 = url.substring(commaIdx + 1);
            return java.util.Base64.getDecoder().decode(base64);
        } catch (Exception e) {
            return null;
        }
    }

    private byte[] downloadHttpImage(String url) {
        try {
            HttpURLConnection conn = (HttpURLConnection) URI.create(url).toURL().openConnection();
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(10000);
            try (InputStream is = conn.getInputStream();
                 ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
                byte[] buf = new byte[4096];
                int n;
                while ((n = is.read(buf)) != -1) {
                    baos.write(buf, 0, n);
                }
                return baos.toByteArray();
            }
        } catch (Exception e) {
            return null;
        }
    }

    public static class SignPosition {
        public String sealUrl;
        public float x;
        public float y;
        public float width;
        public float height;

        public SignPosition(String sealUrl, float x, float y, float width, float height) {
            this.sealUrl = sealUrl;
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
        }
    }

    private static class PdfBoxSignature implements SignatureInterface {
        private final PrivateKey privateKey;
        private final Certificate[] certChain;
        private final TsaClient tsaClient;
        private final RootCaManager rootCaManager;

        PdfBoxSignature(PrivateKey privateKey, Certificate[] certChain, TsaClient tsaClient, RootCaManager rootCaManager) {
            this.privateKey = privateKey;
            this.certChain = certChain;
            this.tsaClient = tsaClient;
            this.rootCaManager = rootCaManager;
        }

        @Override
        public byte[] sign(InputStream content) throws IOException {
            try {
                List<X509Certificate> certList = new ArrayList<>();
                for (Certificate c : certChain) {
                    certList.add((X509Certificate) c);
                }
                Store certStore = new JcaCertStore(certList);

                CMSSignedDataGenerator generator = new CMSSignedDataGenerator();
                ContentSigner contentSigner = new JcaContentSignerBuilder("SHA256WithRSA")
                        .setProvider("BC")
                        .build(privateKey);

                generator.addSignerInfoGenerator(
                        new JcaSignerInfoGeneratorBuilder(
                                new JcaDigestCalculatorProviderBuilder().setProvider("BC").build())
                                .build(contentSigner, (X509Certificate) certChain[0]));

                generator.addCertificates(certStore);

                CMSTypedData msg = new CMSProcessableByteArray(readAllBytes(content));
                CMSSignedData signedData = generator.generate(msg, false);
                return signedData.getEncoded();
            } catch (Exception e) {
                throw new IOException("签名失败", e);
            }
        }

        private byte[] readAllBytes(InputStream is) throws IOException {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            int n;
            while ((n = is.read(buf)) != -1) {
                baos.write(buf, 0, n);
            }
            return baos.toByteArray();
        }
    }
}
