package com.example.cert.service.impl;

import com.example.cert.core.exception.BizException;
import com.example.cert.domain.dto.IssueResponse;
import com.example.cert.domain.entity.Certificate;
import com.example.cert.domain.enums.CertStatus;
import com.example.cert.domain.repository.CertificateRepository;
import com.example.cert.domain.vo.CertVO;
import com.example.cert.infra.ca.CertificateIssuer;
import com.example.cert.infra.ca.IssueResult;
import com.example.cert.infra.ca.RootCaManager;
import com.example.cert.service.CertService;
import jakarta.persistence.criteria.Predicate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.io.ByteArrayOutputStream;
import java.security.KeyStore;
import java.security.cert.X509Certificate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

@Service
public class CertServiceImpl implements CertService {

    private static final Logger log = LoggerFactory.getLogger(CertServiceImpl.class);

    private final CertificateRepository certRepo;
    private final CertificateIssuer issuer;
    private final RootCaManager rootCaManager;

    @Value("${cert.default-valid-days:365}")
    private int defaultValidDays;

    public CertServiceImpl(CertificateRepository certRepo, CertificateIssuer issuer, RootCaManager rootCaManager) {
        this.certRepo = certRepo;
        this.issuer = issuer;
        this.rootCaManager = rootCaManager;
    }

    @Override
    @Transactional
    public IssueResponse issue(int certType, String creditCode, String name, String department, String email, Integer validDays) {
        if (certType != 1 && certType != 2) {
            throw new BizException("证书类型无效，1=企业，2=个人");
        }
        if (creditCode == null || creditCode.isBlank()) {
            throw new BizException("统一信用代码/身份证号不能为空");
        }
        if (name == null || name.isBlank()) {
            throw new BizException("姓名不能为空");
        }

        int days = validDays != null && validDays > 0 ? validDays : defaultValidDays;

        List<Certificate> oldCerts = certRepo.findByCreditCodeAndStatus(creditCode, CertStatus.ACTIVE);
        for (Certificate c : oldCerts) {
            c.setStatus(CertStatus.REVOKED);
            certRepo.save(c);
        }

        try {
            IssueResult result = issuer.issue(certType, creditCode, name, department, email, days);

            Certificate cert = new Certificate();
            cert.setSignerId(result.getSignerId());
            cert.setCertType(certType);
            cert.setCreditCode(creditCode);
            cert.setName(name);
            cert.setDepartment(department);
            cert.setEmail(email);
            cert.setSerialNumber(result.getSerialNumber());
            cert.setCertSubject(result.getCertSubject());
            cert.setStatus(CertStatus.ACTIVE);
            cert.setValidFrom(result.getValidFrom());
            cert.setValidTo(result.getValidTo());
            cert.setP12Data(result.getP12Data());

            certRepo.save(cert);

            IssueResponse resp = new IssueResponse();
            resp.setSignerId(result.getSignerId());
            resp.setCertType(certType);
            resp.setSerialNumber(result.getSerialNumber());
            resp.setCertSubject(result.getCertSubject());
            DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
            resp.setValidFrom(result.getValidFrom().format(fmt));
            resp.setValidTo(result.getValidTo().format(fmt));
            return resp;

        } catch (Exception e) {
            log.error("证书签发失败", e);
            throw new BizException("证书签发失败: " + e.getMessage());
        }
    }

    @Override
    public CertVO lookupBySignerId(String signerId) {
        Certificate cert = certRepo.findBySignerId(signerId)
                .orElseThrow(() -> new BizException("证书不存在"));
        return toVO(cert);
    }

    @Override
    public Page<CertVO> list(String creditCode, String name, String status, int page, int size) {
        Specification<Certificate> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (StringUtils.hasText(creditCode)) {
                predicates.add(cb.like(root.get("creditCode"), "%" + creditCode + "%"));
            }
            if (StringUtils.hasText(name)) {
                predicates.add(cb.like(root.get("name"), "%" + name + "%"));
            }
            if (StringUtils.hasText(status)) {
                predicates.add(cb.equal(root.get("status"), CertStatus.valueOf(status)));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };

        PageRequest pr = PageRequest.of(page - 1, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        return certRepo.findAll(spec, pr).map(this::toVO);
    }

    @Override
    @Transactional
    public void revoke(Long certId) {
        Certificate cert = certRepo.findById(certId)
                .orElseThrow(() -> new BizException("证书不存在"));
        if (cert.getStatus() == CertStatus.REVOKED) {
            throw new BizException("证书已被吊销");
        }
        cert.setStatus(CertStatus.REVOKED);
        certRepo.save(cert);
    }

    @Override
    public byte[] downloadP12(Long certId) {
        Certificate cert = certRepo.findById(certId)
                .orElseThrow(() -> new BizException("证书不存在"));
        if (cert.getP12Data() == null || cert.getP12Data().length == 0) {
            throw new BizException("证书私钥数据不存在");
        }
        return cert.getP12Data();
    }

    @Override
    public byte[] downloadRootCaCert() {
        try {
            X509Certificate rootCert = rootCaManager.getRootCert();
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            KeyStore ks = KeyStore.getInstance("PKCS12");
            ks.load(null, null);
            ks.setCertificateEntry("root-ca", rootCert);

            KeyStore trustStore = KeyStore.getInstance("PKCS12");
            trustStore.load(null, null);
            trustStore.setEntry("root-ca",
                    new KeyStore.TrustedCertificateEntry(rootCert), null);

            return rootCert.getEncoded();
        } catch (Exception e) {
            throw new BizException("下载根证书失败: " + e.getMessage());
        }
    }

    private CertVO toVO(Certificate c) {
        CertVO vo = new CertVO();
        vo.setId(c.getId());
        vo.setCertType(c.getCertType());
        vo.setSignerId(c.getSignerId());
        vo.setCreditCode(c.getCreditCode());
        vo.setName(c.getName());
        vo.setDepartment(c.getDepartment());
        vo.setEmail(c.getEmail());
        vo.setSerialNumber(c.getSerialNumber());
        vo.setCertSubject(c.getCertSubject());
        vo.setStatus(c.getStatus().name());
        vo.setValidFrom(c.getValidFrom());
        vo.setValidTo(c.getValidTo());
        vo.setCreatedAt(c.getCreatedAt());
        return vo;
    }
}
