package com.example.cert.api;

import com.example.cert.core.common.R;
import com.example.cert.domain.dto.SignRequest;
import com.example.cert.domain.dto.SignResponse;
import com.example.cert.service.SigningService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/pdf")
public class PdfSignController {

    private final SigningService signingService;

    public PdfSignController(SigningService signingService) {
        this.signingService = signingService;
    }

    @PostMapping("/sign")
    public R<SignResponse> sign(@Valid @RequestBody SignRequest request) {
        SignResponse resp = signingService.sign(request);
        return R.ok("签署成功", resp);
    }
}
