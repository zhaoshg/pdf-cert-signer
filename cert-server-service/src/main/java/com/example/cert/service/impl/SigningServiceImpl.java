package com.example.cert.service.impl;

import com.example.cert.core.exception.BizException;
import com.example.cert.domain.dto.SignRequest;
import com.example.cert.domain.dto.SignResponse;
import com.example.cert.domain.entity.AuditLog;
import com.example.cert.domain.entity.Certificate;
import com.example.cert.domain.enums.CertStatus;
import com.example.cert.domain.repository.AuditLogRepository;
import com.example.cert.domain.repository.CertificateRepository;
import com.example.cert.infra.signing.PdfSigner;
import com.example.cert.infra.storage.FileStorageService;
import com.example.cert.service.SigningService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.URI;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
public class SigningServiceImpl implements SigningService {

    private final CertificateRepository certRepo;
    private final AuditLogRepository auditRepo;
    private final PdfSigner pdfSigner;
    private final FileStorageService storageService;

    public SigningServiceImpl(CertificateRepository certRepo, AuditLogRepository auditRepo,
                              PdfSigner pdfSigner, FileStorageService storageService) {
        this.certRepo = certRepo;
        this.auditRepo = auditRepo;
        this.pdfSigner = pdfSigner;
        this.storageService = storageService;
    }

    @Override
    @Transactional
    public SignResponse sign(SignRequest request) {
        Certificate cert = certRepo.findBySignerIdAndStatus(request.getSignerId(), CertStatus.ACTIVE)
                .orElseThrow(() -> new BizException("未找到有效证书"));

        if (cert.getStatus() == CertStatus.REVOKED) {
            throw new BizException("您的数字证书已被吊销");
        }
        if (cert.getValidTo().isBefore(LocalDateTime.now())) {
            throw new BizException("您的数字证书已过期，请先重新申请/更新证书");
        }

        byte[] pdfData = downloadPdf(request.getPdfUrl());
        String pdfHash = sha256(pdfData);

        Map<Integer, List<PdfSigner.SignPosition>> seals = new HashMap<>();
        for (SignRequest.SignPosition sp : request.getSignatures()) {
            seals.computeIfAbsent(sp.getPageIndex(), k -> new ArrayList<>())
                    .add(new PdfSigner.SignPosition(sp.getSealUrl(), sp.getX(), sp.getY(), sp.getWidth(), sp.getHeight()));
        }

        try {
            String p12Password = "cert-secret-key-32bytes!!";
            byte[] signedPdf = pdfSigner.sign(pdfData, cert.getP12Data(), p12Password, seals);

            String signedPdfHash = sha256(signedPdf);
            String fileName = "signed_" + request.getSignerId() + "_" + System.currentTimeMillis() + ".pdf";
            String signedUrl = storageService.upload(signedPdf, fileName);

            AuditLog log = new AuditLog();
            log.setSignerId(request.getSignerId());
            log.setCreditCode(cert.getCreditCode());
            log.setCertSerialNumber(cert.getSerialNumber());
            log.setPdfUrl(request.getPdfUrl());
            log.setPdfHash(pdfHash);
            log.setSignedPdfHash(signedPdfHash);
            log.setSignTime(LocalDateTime.now());
            auditRepo.save(log);

            SignResponse resp = new SignResponse();
            resp.setSignedPdfUrl(signedUrl);
            resp.setCertSubject(cert.getCertSubject());
            resp.setSignTime(LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
            return resp;

        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            throw new BizException("签章失败: " + e.getMessage());
        }
    }

    private byte[] downloadPdf(String url) {
        try {
            java.net.HttpURLConnection conn = (java.net.HttpURLConnection) URI.create(url).toURL().openConnection();
            conn.setConnectTimeout(30000);
            conn.setReadTimeout(60000);
            try (InputStream is = conn.getInputStream();
                 ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
                byte[] buf = new byte[8192];
                int n;
                while ((n = is.read(buf)) != -1) {
                    baos.write(buf, 0, n);
                }
                return baos.toByteArray();
            }
        } catch (Exception e) {
            throw new BizException("PDF文件下载失败: " + e.getMessage());
        }
    }

    private String sha256(byte[] data) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(data);
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            return "";
        }
    }
}
