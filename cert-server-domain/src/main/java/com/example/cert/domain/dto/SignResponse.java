package com.example.cert.domain.dto;

import lombok.Data;

@Data
public class SignResponse {

    private String signedPdfUrl;
    private String certSubject;
    private String signTime;
}
