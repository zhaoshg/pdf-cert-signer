package com.example.cert.infra.ca;

import java.time.LocalDateTime;

public class IssueResult {

    private String signerId;
    private String serialNumber;
    private String certSubject;
    private LocalDateTime validFrom;
    private LocalDateTime validTo;
    private byte[] p12Data;

    public IssueResult() {
    }

    public IssueResult(String signerId, String serialNumber, String certSubject,
                       LocalDateTime validFrom, LocalDateTime validTo, byte[] p12Data) {
        this.signerId = signerId;
        this.serialNumber = serialNumber;
        this.certSubject = certSubject;
        this.validFrom = validFrom;
        this.validTo = validTo;
        this.p12Data = p12Data;
    }

    public String getSignerId() { return signerId; }
    public void setSignerId(String signerId) { this.signerId = signerId; }

    public String getSerialNumber() { return serialNumber; }
    public void setSerialNumber(String serialNumber) { this.serialNumber = serialNumber; }

    public String getCertSubject() { return certSubject; }
    public void setCertSubject(String certSubject) { this.certSubject = certSubject; }

    public LocalDateTime getValidFrom() { return validFrom; }
    public void setValidFrom(LocalDateTime validFrom) { this.validFrom = validFrom; }

    public LocalDateTime getValidTo() { return validTo; }
    public void setValidTo(LocalDateTime validTo) { this.validTo = validTo; }

    public byte[] getP12Data() { return p12Data; }
    public void setP12Data(byte[] p12Data) { this.p12Data = p12Data; }
}
