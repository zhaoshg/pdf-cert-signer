package com.example.cert.service.impl;

import com.example.cert.core.exception.BizException;
import com.example.cert.domain.dto.ScenarioSignRequest;
import com.example.cert.domain.dto.SignRequest;
import com.example.cert.domain.dto.SignResponse;
import com.example.cert.domain.entity.AuditLog;
import com.example.cert.domain.entity.Certificate;
import com.example.cert.domain.enums.CertStatus;
import com.example.cert.domain.repository.AuditLogRepository;
import com.example.cert.domain.repository.CertificateRepository;
import com.example.cert.infra.ca.CertificateIssuer;
import com.example.cert.infra.ca.IssueResult;
import com.example.cert.infra.http.HttpClients;
import com.example.cert.infra.signing.PdfSigner;
import com.example.cert.infra.storage.FileStorageService;
import com.example.cert.service.SigningService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
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
    private final CertificateIssuer issuer;
    /** 自注入（代理对象），编排方法经它调用事务方法，避免同类自调用事务失效 */
    private final SigningServiceImpl self;

    @Value("${cert.key-secret:cert-secret-key-32bytes!!}")
    private String keySecret;

    public SigningServiceImpl(CertificateRepository certRepo, AuditLogRepository auditRepo,
                              PdfSigner pdfSigner, FileStorageService storageService,
                              CertificateIssuer issuer, @Lazy SigningServiceImpl self) {
        this.certRepo = certRepo;
        this.auditRepo = auditRepo;
        this.pdfSigner = pdfSigner;
        this.storageService = storageService;
        this.issuer = issuer;
        this.self = self;
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

        return self.signWithCert(cert, request.getPdfUrl(), request.getSignatures(), null);
    }

    /**
     * 场景证书签章编排（本身无事务）：签发临时证 → 签章 → finally 吊销。
     * 各步骤独立事务，签章失败 finally 仍提交吊销，不残留有效证书。
     * 注意：不用 CertServiceImpl.issue()，其“同 creditCode 旧证自动作废”会误伤长期证。
     */
    @Override
    public SignResponse signScenario(ScenarioSignRequest request) {
        Certificate temp = self.issueTempCert(request);
        boolean signed = false;
        boolean revokeFailed = false;
        try {
            SignResponse resp = self.signWithCert(temp, request.getPdfUrl(), request.getSignatures(), temp.getSignerId());
            signed = true;
            return resp;
        } finally {
            try {
                self.revokeTempCert(temp.getId());
            } catch (Exception e) {
                log.error("临时证书吊销失败 signerId={}", temp.getSignerId(), e);
                revokeFailed = true;
            }
            if (signed && revokeFailed) {
                throw new BizException("签章成功但临时证书吊销失败，需人工处理 signerId=" + temp.getSignerId());
            }
        }
    }

    /** 签发临时短期证书并落库（独立事务），p12 仅随实体在内存传递，不返回调用方 */
    @Transactional
    public Certificate issueTempCert(ScenarioSignRequest request) {
        if (request.getCertType() == null || (request.getCertType() != 1 && request.getCertType() != 2)) {
            throw new BizException("证书类型无效，1=企业，2=个人");
        }
        if (request.getCreditCode() == null || request.getCreditCode().isBlank()) {
            throw new BizException("统一信用代码/身份证号不能为空");
        }
        if (request.getName() == null || request.getName().isBlank()) {
            throw new BizException("姓名不能为空");
        }
        int days = request.getValidDays() != null && request.getValidDays() > 0 ? request.getValidDays() : 1;

        try {
            IssueResult result = issuer.issue(request.getCertType(), request.getCreditCode(),
                    request.getName(), request.getDepartment(), request.getEmail(), days);

            Certificate cert = new Certificate();
            cert.setSignerId(result.getSignerId());
            cert.setCertType(request.getCertType());
            cert.setCreditCode(request.getCreditCode());
            cert.setName(request.getName());
            cert.setDepartment(request.getDepartment());
            cert.setEmail(request.getEmail());
            cert.setSerialNumber(result.getSerialNumber());
            cert.setCertSubject(result.getCertSubject());
            cert.setStatus(CertStatus.ACTIVE);
            cert.setValidFrom(result.getValidFrom());
            cert.setValidTo(result.getValidTo());
            cert.setP12Data(result.getP12Data());
            return certRepo.save(cert);
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            throw new BizException("临时证书签发失败: " + e.getMessage());
        }
    }

    /** 吊销临时证书（独立新事务，finally 中必提交）；已吊销/不存在则静默跳过 */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void revokeTempCert(Long certId) {
        Certificate cert = certRepo.findById(certId).orElse(null);
        if (cert == null || cert.getStatus() == CertStatus.REVOKED) {
            return;
        }
        cert.setStatus(CertStatus.REVOKED);
        certRepo.save(cert);
    }

    /**
     * 签章核心（下载 PDF → 密码学签名 → 上传 → 审计 → 响应），供 sign / signScenario 复用。
     *
     * @param tempSignerId 场景签章时回填本次临时 signerId，老接口传 null
     */
    @Transactional
    public SignResponse signWithCert(Certificate cert, String pdfUrl,
                                     List<SignRequest.SignPosition> signatures, String tempSignerId) {
        // 2. 下载待签 PDF 并计算原始哈希（用于审计追溯原始文件）
        byte[] pdfData = downloadPdf(pdfUrl);
        String pdfHash = sha256(pdfData);

        // 3. 按页码归集签章位置，传递给 PdfSigner 逐页盖章；
        //    reason 取第一个非空签章原因，作为整份 PDF 数字签名的 Reason 字段
        Map<Integer, List<PdfSigner.SignPosition>> seals = new HashMap<>();
        String reason = null;
        for (SignRequest.SignPosition sp : signatures) {
            seals.computeIfAbsent(sp.getPageIndex(), k -> new ArrayList<>())
                    .add(new PdfSigner.SignPosition(sp.getSealUrl(), sp.getX(), sp.getY(), sp.getWidth(), sp.getHeight()));
            if (reason == null && sp.getReason() != null && !sp.getReason().isBlank()) {
                reason = sp.getReason();
            }
        }

        try {
            // 4. 用签署人证书私钥执行视觉签章 + 数字签名，产出已签章 PDF
            byte[] signedPdf = pdfSigner.sign(pdfData, cert.getP12Data(), keySecret, reason, seals);

            // 5. 上传已签章 PDF 到文件存储，返回可访问 URL
            String signedPdfHash = sha256(signedPdf);
            String fileName = "signed_" + cert.getSignerId() + "_" + System.currentTimeMillis() + ".pdf";
            String signedUrl = storageService.upload(signedPdf, fileName);

            // 6. 写审计日志：记录签署人、证书、PDF 哈希、签章时间，便于事后追溯
            AuditLog log = new AuditLog();
            log.setSignerId(cert.getSignerId());
            log.setCreditCode(cert.getCreditCode());
            log.setCertSerialNumber(cert.getSerialNumber());
            log.setPdfUrl(pdfUrl);
            log.setPdfHash(pdfHash);
            log.setSignedPdfHash(signedPdfHash);
            log.setSignTime(LocalDateTime.now());
            auditRepo.save(log);

            // 7. 组装响应返回给调用方
            SignResponse resp = new SignResponse();
            resp.setSignedPdfUrl(signedUrl);
            resp.setCertSubject(cert.getCertSubject());
            resp.setSignTime(LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
            resp.setTempSignerId(tempSignerId);
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
