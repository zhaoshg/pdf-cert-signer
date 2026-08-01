package com.example.cert.domain.repository;

import com.example.cert.domain.entity.Certificate;
import com.example.cert.domain.enums.CertStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.Optional;

public interface CertificateRepository extends JpaRepository<Certificate, Long>,
        JpaSpecificationExecutor<Certificate> {

    Optional<Certificate> findBySignerIdAndStatus(String signerId, CertStatus status);

    Optional<Certificate> findBySignerId(String signerId);

    List<Certificate> findByCreditCodeAndStatus(String creditCode, CertStatus status);

    boolean existsByCreditCodeAndStatus(String creditCode, CertStatus status);
}
