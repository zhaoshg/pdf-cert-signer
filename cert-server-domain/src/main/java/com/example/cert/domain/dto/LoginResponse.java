package com.example.cert.domain.dto;

import lombok.Data;

@Data
public class LoginResponse {

    private String token;
    private String username;
}
