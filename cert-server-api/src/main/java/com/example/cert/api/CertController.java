package com.example.cert.api;

import com.example.cert.core.common.R;
import com.example.cert.domain.dto.IssueRequest;
import com.example.cert.domain.dto.IssueResponse;
import com.example.cert.domain.vo.CertVO;
import com.example.cert.service.CertService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/cert")
public class CertController {

    private final CertService certService;

    public CertController(CertService certService) {
        this.certService = certService;
    }

    @PostMapping("/issue")
    public R<IssueResponse> issue(@Valid @RequestBody IssueRequest request) {
        IssueResponse resp = certService.issue(
                request.getCertType(), request.getCreditCode(), request.getName(),
                request.getDepartment(), request.getEmail(), request.getValidDays());
        return R.ok("签发成功", resp);
    }

    @GetMapping("/info/{signerId}")
    public R<CertVO> getBySignerId(@PathVariable String signerId) {
        return R.ok(certService.lookupBySignerId(signerId));
    }

    @GetMapping("/list")
    public R<Page<CertVO>> list(
            @RequestParam(required = false) String creditCode,
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return R.ok(certService.list(creditCode, name, status, page, size));
    }

    @PostMapping("/revoke/{id}")
    public R<Void> revoke(@PathVariable Long id) {
        certService.revoke(id);
        return R.ok();
    }

    @GetMapping("/download/p12/{id}")
    public ResponseEntity<byte[]> downloadP12(@PathVariable Long id) {
        byte[] data = certService.downloadP12(id);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=certificate.p12")
                .body(data);
    }

    @GetMapping("/download/root-ca")
    public ResponseEntity<byte[]> downloadRootCa() {
        byte[] data = certService.downloadRootCaCert();
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=RootCA.crt")
                .body(data);
    }
}
