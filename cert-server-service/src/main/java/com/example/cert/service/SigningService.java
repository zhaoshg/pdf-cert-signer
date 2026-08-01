package com.example.cert.service;

import com.example.cert.domain.dto.SignResponse;
import com.example.cert.domain.dto.SignRequest;

public interface SigningService {

    SignResponse sign(SignRequest request);
}
