package com.example.cert.infra.ca;

import java.time.LocalDateTime;

import lombok.Data;

@Data
public class IssueResult {

    private String signerId;
    private Integer certType;
    private String serialNumber;
    private String certSubject;
    private LocalDateTime validFrom;
    private LocalDateTime validTo;
    private byte[] p12Data;

    public IssueResult() {
    }

    public IssueResult(String signerId, Integer certType, String serialNumber, String certSubject,
                       LocalDateTime validFrom, LocalDateTime validTo, byte[] p12Data) {
        this.signerId = signerId;
        this.certType = certType;
        this.serialNumber = serialNumber;
        this.certSubject = certSubject;
        this.validFrom = validFrom;
        this.validTo = validTo;
        this.p12Data = p12Data;
    }
}
