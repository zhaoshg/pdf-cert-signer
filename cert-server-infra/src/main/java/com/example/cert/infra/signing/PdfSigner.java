package com.example.cert.infra.signing;

import com.example.cert.infra.ca.RootCaManager;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.cos.COSDictionary;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.interactive.digitalsignature.PDSignature;
import org.apache.pdfbox.pdmodel.interactive.digitalsignature.SignatureInterface;
import org.apache.pdfbox.pdmodel.interactive.digitalsignature.SignatureOptions;
import org.apache.pdfbox.pdmodel.interactive.digitalsignature.visible.PDVisibleSignDesigner;
import org.apache.pdfbox.pdmodel.interactive.digitalsignature.visible.PDVisibleSigProperties;
import org.bouncycastle.cert.jcajce.JcaCertStore;
import org.bouncycastle.cms.*;
import org.bouncycastle.cms.jcajce.JcaSignerInfoGeneratorBuilder;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.bouncycastle.operator.jcajce.JcaDigestCalculatorProviderBuilder;
import org.bouncycastle.asn1.ASN1ObjectIdentifier;
import org.bouncycastle.tsp.TimeStampToken;
import org.bouncycastle.util.Store;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URI;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.*;

@Component
public class PdfSigner {

    private static final Logger log = LoggerFactory.getLogger(PdfSigner.class);

    /** Reason 字段最大长度，避免过长文本被原样写入签名内容 */
    private static final int MAX_REASON_LENGTH = 128;

    private final RootCaManager rootCaManager;
    private final TsaClient tsaClient;

    public PdfSigner(RootCaManager rootCaManager, TsaClient tsaClient) {
        this.rootCaManager = rootCaManager;
        this.tsaClient = tsaClient;
    }

    public byte[] sign(byte[] pdfData, byte[] p12Data, String password, String reason,
                       Map<Integer, List<SignPosition>> sealsByPage) throws Exception {

        log.info("Signing PDF: {} bytes, {} seal pages", pdfData.length, sealsByPage.size());
        KeyStore ks = KeyStore.getInstance("PKCS12");
        ks.load(new ByteArrayInputStream(p12Data), password.toCharArray());
        String alias = ks.aliases().nextElement();
        PrivateKey privateKey = (PrivateKey) ks.getKey(alias, password.toCharArray());
        Certificate[] certChain = ks.getCertificateChain(alias);
        X509Certificate cert = (X509Certificate) certChain[0];
        String signerName = extractCN(cert.getSubjectX500Principal().getName());

        PDDocument document = Loader.loadPDF(pdfData);
        ByteArrayOutputStream signedOutput = new ByteArrayOutputStream();

        PDSignature signature = createSignature(cert, reason);
        SignatureOptions options = createSignatureOptions(document, sealsByPage, signerName, reason);

        log.info("Adding signature to document...");
        document.addSignature(signature, new PdfBoxSignature(
                privateKey, certChain, tsaClient, rootCaManager), options);
        log.info("Saving incremental...");
        document.saveIncremental(signedOutput);
        document.close();
        log.info("Signing complete: {} bytes output", signedOutput.size());

        return signedOutput.toByteArray();
    }

    private PDSignature createSignature(X509Certificate cert, String reason) {
        PDSignature signature = new PDSignature();
        signature.setFilter(PDSignature.FILTER_ADOBE_PPKLITE);
        signature.setSubFilter(PDSignature.SUBFILTER_ADBE_PKCS7_DETACHED);
        signature.setName(extractCN(cert.getSubjectX500Principal().getName()));
        String effectiveReason = reason == null ? "" : reason.trim();
        if (effectiveReason.length() > MAX_REASON_LENGTH) {
            effectiveReason = effectiveReason.substring(0, MAX_REASON_LENGTH);
        }
        signature.setReason(effectiveReason.isEmpty() ? "PDF电子签章" : effectiveReason);
        signature.setLocation("CN");
        signature.setSignDate(Calendar.getInstance());

        COSDictionary sigDict = signature.getCOSObject();
        COSDictionary propBuild = new COSDictionary();

        COSDictionary appDict = new COSDictionary();
        appDict.setName(COSName.NAME, "PDFCertSigner");
        propBuild.setItem(COSName.getPDFName("App"), appDict);

        COSDictionary filterDict = new COSDictionary();
        filterDict.setName(COSName.NAME, "Adobe.PPKLite");
        filterDict.setItem(COSName.getPDFName("SubFilter"),
                COSName.getPDFName("adbe.pkcs7.detached"));
        filterDict.setInt(COSName.R, 0x20000);
        propBuild.setItem(COSName.FILTER, filterDict);

        sigDict.setItem(COSName.getPDFName("Prop_Build"), propBuild);

        return signature;
    }

    private SignatureOptions createSignatureOptions(PDDocument document,
                                                     Map<Integer, List<SignPosition>> sealsByPage,
                                                     String signerName,
                                                     String reason) throws Exception {
        SignatureOptions options = new SignatureOptions();
        String visualReason = reason == null || reason.isBlank() ? "签章" : reason;
        for (Map.Entry<Integer, List<SignPosition>> entry : sealsByPage.entrySet()) {
            int pageIndex = entry.getKey();
            if (pageIndex < document.getNumberOfPages()) {
                for (SignPosition pos : entry.getValue()) {
                    byte[] imageData = loadImage(pos.sealUrl);
                    if (imageData == null || imageData.length == 0) {
                        log.warn("No seal image data, skipping visual");
                        continue;
                    }
                    log.info("Seal image: {} bytes, page {}", imageData.length, pageIndex);

                    PDVisibleSignDesigner designer = new PDVisibleSignDesigner(
                            document, new ByteArrayInputStream(imageData), pageIndex + 1);
                    designer.xAxis(pos.x).yAxis(pos.y)
                            .width(pos.width).height(pos.height)
                            .adjustForRotation();

                    PDVisibleSigProperties props = new PDVisibleSigProperties();
                    props.signerName(signerName)
                            .signatureReason(visualReason)
                            .preferredSize(0)
                            .page(pageIndex + 1)
                            .visualSignEnabled(true)
                            .setPdVisibleSignature(designer);
                    props.buildSignature();

                    options.setVisualSignature(props.getVisibleSignature());
                    options.setPage(pageIndex);
                }
            }
        }
        return options;
    }

    private String extractCN(String dn) {
        for (String part : dn.split(",")) {
            String trimmed = part.trim();
            if (trimmed.startsWith("CN=")) {
                String cn = trimmed.substring(3);
                if (cn.contains("(")) {
                    cn = cn.substring(0, cn.indexOf("("));
                }
                return cn;
            }
        }
        return dn;
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
        private static final Logger log = LoggerFactory.getLogger(PdfBoxSignature.class);
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
                log.info("Starting CMS signature generation...");
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
                log.info("CMS signed data generated, size: {} bytes", signedData.getEncoded().length);

                byte[] tsToken = tsaClient.getTimestampToken(
                        signedData.getSignerInfos().getSigners().iterator().next().getSignature());
                if (tsToken != null) {
                    log.info("TSA response: {} bytes", tsToken.length);
                    org.bouncycastle.tsp.TimeStampResponse tsResponse =
                            new org.bouncycastle.tsp.TimeStampResponse(tsToken);
                    TimeStampToken timeStampToken = tsResponse.getTimeStampToken();
                    log.info("Timestamp token created: {}", timeStampToken.getTimeStampInfo().getGenTime());

                    org.bouncycastle.cms.SignerInformation signer =
                            signedData.getSignerInfos().getSigners().iterator().next();

                    org.bouncycastle.asn1.cms.Attribute timestampAttr =
                            new org.bouncycastle.asn1.cms.Attribute(
                                    org.bouncycastle.asn1.pkcs.PKCSObjectIdentifiers.id_aa_signatureTimeStampToken,
                                    new org.bouncycastle.asn1.DERSet(
                                            timeStampToken.toCMSSignedData().toASN1Structure()));
                    log.info("Timestamp attribute created");

                    org.bouncycastle.cms.SignerInformation newSigner =
                            org.bouncycastle.cms.SignerInformation.replaceUnsignedAttributes(
                                    signer, new org.bouncycastle.asn1.cms.AttributeTable(
                                            new org.bouncycastle.asn1.ASN1EncodableVector()));
                    newSigner = org.bouncycastle.cms.SignerInformation.replaceUnsignedAttributes(
                            newSigner, new org.bouncycastle.asn1.cms.AttributeTable(timestampAttr));
                    log.info("Signer updated with timestamp attribute");

                    signedData = org.bouncycastle.cms.CMSSignedData.replaceSigners(
                            signedData, new org.bouncycastle.cms.SignerInformationStore(newSigner));
                    log.info("Timestamp added to signature, final size: {} bytes", signedData.getEncoded().length);
                } else {
                    log.info("No timestamp token (TSA not configured or request failed)");
                }

                return signedData.getEncoded();
            } catch (Exception e) {
                log.error("CMS signing failed", e);
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
