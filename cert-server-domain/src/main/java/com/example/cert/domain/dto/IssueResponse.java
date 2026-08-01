package com.example.cert.domain.dto;

import lombok.Data;

@Data
public class IssueResponse {

    private String signerId;
    private String serialNumber;
    private String certSubject;
    private String validFrom;
    private String validTo;
}
