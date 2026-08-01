package com.example.cert.domain.entity;

import com.example.cert.domain.enums.CertStatus;
import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "cert_certificate", indexes = {
        @Index(name = "idx_cert_sign_id", columnList = "signerId"),
        @Index(name = "idx_cert_credit_code", columnList = "creditCode")
})
public class Certificate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "signer_id", nullable = false, unique = true, length = 32)
    private String signerId;

    @Column(name = "cert_type", nullable = false)
    private Integer certType;

    @Column(name = "credit_code", nullable = false, length = 64)
    private String creditCode;

    @Column(nullable = false, length = 64)
    private String name;

    @Column(length = 128)
    private String department;

    @Column(length = 128)
    private String email;

    @Column(name = "serial_number", nullable = false, unique = true, length = 64)
    private String serialNumber;

    @Column(name = "cert_subject", nullable = false, length = 256)
    private String certSubject;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private CertStatus status;

    @Column(name = "valid_from", nullable = false)
    private LocalDateTime validFrom;

    @Column(name = "valid_to", nullable = false)
    private LocalDateTime validTo;

    @Column(name = "p12_data", columnDefinition = "MEDIUMBLOB")
    private byte[] p12Data;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    void onCreate() {
        this.createdAt = LocalDateTime.now();
        if (this.status == null) {
            this.status = CertStatus.ACTIVE;
        }
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
