package com.example.cert.domain.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "cert_audit_log", indexes = {
        @Index(name = "idx_audit_sign_id", columnList = "signerId"),
        @Index(name = "idx_audit_sign_time", columnList = "signTime")
})
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "signer_id", nullable = false, length = 32)
    private String signerId;

    @Column(name = "credit_code", length = 64)
    private String creditCode;

    @Column(name = "cert_serial_number", length = 64)
    private String certSerialNumber;

    @Column(name = "pdf_url", length = 512)
    private String pdfUrl;

    @Column(name = "pdf_hash", length = 64)
    private String pdfHash;

    @Column(name = "signed_pdf_hash", length = 64)
    private String signedPdfHash;

    @Column(name = "sign_time")
    private LocalDateTime signTime;

    @Column(name = "client_ip", length = 45)
    private String clientIp;

    @Column(name = "tsa_response", columnDefinition = "TEXT")
    private String tsaResponse;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void onCreate() {
        this.createdAt = LocalDateTime.now();
    }
}
