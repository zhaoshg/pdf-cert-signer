package com.example.cert.api;

import com.example.cert.core.common.R;
import com.example.cert.core.exception.BizException;
import com.example.cert.domain.dto.SignRequest;
import com.example.cert.domain.dto.SignResponse;
import com.example.cert.infra.http.HttpClients;
import com.example.cert.service.SigningService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

@RestController
@RequestMapping("/api/v1/pdf")
public class PdfSignController {

    private static final Logger log = LoggerFactory.getLogger(PdfSignController.class);

    private static final HttpClient HTTP_CLIENT = HttpClients.TRUST_ALL;

    private final SigningService signingService;

    public PdfSignController(SigningService signingService) {
        this.signingService = signingService;
    }

    @PostMapping("/sign")
    public R<SignResponse> sign(@Valid @RequestBody SignRequest request) {
        SignResponse resp = signingService.sign(request);
        return R.ok("签署成功", resp);
    }

    @GetMapping("/fetch")
    public ResponseEntity<byte[]> fetchPdf(@RequestParam String url) {
        log.info("Fetching PDF from: {}", url);
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(60))
                    .GET()
                    .build();

            HttpResponse<byte[]> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofByteArray());
            int code = response.statusCode();
            log.info("PDF fetch response: HTTP {} {} bytes", code, response.body().length);

            if (code != 200) {
                throw new BizException("远程服务器返回 HTTP " + code);
            }

            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_PDF)
                    .body(response.body());

        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            log.error("PDF fetch failed", e);
            throw new BizException("PDF加载失败: " + e.getMessage());
        }
    }
}
