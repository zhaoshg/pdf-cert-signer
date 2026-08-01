package com.example.cert.domain.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class CertVO {

    private Long id;
    private String signerId;
    private Integer certType;
    private String creditCode;
    private String name;
    private String department;
    private String email;
    private String serialNumber;
    private String certSubject;
    private String status;
    private LocalDateTime validFrom;
    private LocalDateTime validTo;
    private LocalDateTime createdAt;
}
