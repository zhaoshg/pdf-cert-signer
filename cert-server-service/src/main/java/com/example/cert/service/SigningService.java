package com.example.cert.service;

import com.example.cert.domain.dto.SignResponse;
import com.example.cert.domain.dto.SignRequest;
import com.example.cert.domain.dto.ScenarioSignRequest;

public interface SigningService {

    SignResponse sign(SignRequest request);

    /**
     * 场景证书签章：临时签发短期证书 → 签章 → 立即吊销，p12 仅内存使用。
     */
    SignResponse signScenario(ScenarioSignRequest request);
}
