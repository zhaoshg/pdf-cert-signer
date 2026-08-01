package com.example.cert.service;

import com.example.cert.domain.dto.IssueResponse;
import com.example.cert.domain.vo.CertVO;
import org.springframework.data.domain.Page;

public interface CertService {

    IssueResponse issue(int certType, String creditCode, String name, String department, String email, Integer validDays);

    CertVO lookupBySignerId(String signerId);

    Page<CertVO> list(String creditCode, String name, String status, int page, int size);

    void revoke(Long certId);

    byte[] downloadP12(Long certId);

    byte[] downloadRootCaCert();
}
