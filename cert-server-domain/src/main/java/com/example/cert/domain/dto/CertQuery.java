package com.example.cert.domain.dto;

import lombok.Data;

@Data
public class CertQuery {

    private String creditCode;
    private String name;
    private String status;
    private Integer page = 1;
    private Integer size = 20;
}
