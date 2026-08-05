package com.example.cert.service.impl;

import com.example.cert.core.exception.BizException;
import com.example.cert.domain.dto.SignRequest;
import com.example.cert.domain.dto.SignResponse;
import com.example.cert.domain.entity.AuditLog;
import com.example.cert.domain.entity.Certificate;
import com.example.cert.domain.enums.CertStatus;
import com.example.cert.domain.repository.AuditLogRepository;
import com.example.cert.domain.repository.CertificateRepository;
import com.example.cert.infra.http.HttpClients;
import com.example.cert.infra.signing.PdfSigner;
import com.example.cert.infra.storage.FileStorageService;
import com.example.cert.service.SigningService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
public class SigningServiceImpl implements SigningService {

    private static final Logger log = LoggerFactory.getLogger(SigningServiceImpl.class);

    private static final HttpClient HTTP_CLIENT = HttpClients.TRUST_ALL;

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
        // 1. 校验签署人证书：必须存在且状态为 ACTIVE、未吊销、未过期
        Certificate cert = certRepo.findBySignerIdAndStatus(request.getSignerId(), CertStatus.ACTIVE)
                .orElseThrow(() -> new BizException("未找到有效证书"));

        if (cert.getStatus() == CertStatus.REVOKED) {
            throw new BizException("您的数字证书已被吊销");
        }
        if (cert.getValidTo().isBefore(LocalDateTime.now())) {
            throw new BizException("您的数字证书已过期，请先重新申请/更新证书");
        }

        // 2. 下载待签 PDF 并计算原始哈希（用于审计追溯原始文件）
        byte[] pdfData = downloadPdf(request.getPdfUrl());
        String pdfHash = sha256(pdfData);

        // 3. 按页码归集签章位置，传递给 PdfSigner 逐页盖章；
        //    reason 取第一个非空签章原因，作为整份 PDF 数字签名的 Reason 字段
        Map<Integer, List<PdfSigner.SignPosition>> seals = new HashMap<>();
        String reason = null;
        for (SignRequest.SignPosition sp : request.getSignatures()) {
            seals.computeIfAbsent(sp.getPageIndex(), k -> new ArrayList<>())
                    .add(new PdfSigner.SignPosition(sp.getSealUrl(), sp.getX(), sp.getY(), sp.getWidth(), sp.getHeight()));
            if (reason == null && sp.getReason() != null && !sp.getReason().isBlank()) {
                reason = sp.getReason();
            }
        }

        try {
            // 4. 用签署人证书私钥执行视觉签章 + 数字签名，产出已签章 PDF
            String p12Password = "cert-secret-key-32bytes!!";
            byte[] signedPdf = pdfSigner.sign(pdfData, cert.getP12Data(), p12Password, reason, seals);

            // 5. 上传已签章 PDF 到文件存储，返回可访问 URL
            String signedPdfHash = sha256(signedPdf);
            String fileName = "signed_" + request.getSignerId() + "_" + System.currentTimeMillis() + ".pdf";
            String signedUrl = storageService.upload(signedPdf, fileName);

            // 6. 写审计日志：记录签署人、证书、PDF 哈希、签章时间，便于事后追溯
            AuditLog log = new AuditLog();
            log.setSignerId(request.getSignerId());
            log.setCreditCode(cert.getCreditCode());
            log.setCertSerialNumber(cert.getSerialNumber());
            log.setPdfUrl(request.getPdfUrl());
            log.setPdfHash(pdfHash);
            log.setSignedPdfHash(signedPdfHash);
            log.setSignTime(LocalDateTime.now());
            auditRepo.save(log);

            // 7. 组装响应返回给调用方
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
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(60))
                    .GET()
                    .build();
            HttpResponse<byte[]> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() != 200) {
                throw new BizException("远程服务器返回 HTTP " + response.statusCode());
            }
            return response.body();
        } catch (BizException e) {
            throw e;
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
