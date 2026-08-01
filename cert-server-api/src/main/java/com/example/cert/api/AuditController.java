package com.example.cert.api;

import com.example.cert.core.common.R;
import com.example.cert.domain.entity.AuditLog;
import com.example.cert.domain.repository.AuditLogRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/audit")
public class AuditController {

    private final AuditLogRepository auditRepo;

    public AuditController(AuditLogRepository auditRepo) {
        this.auditRepo = auditRepo;
    }

    @GetMapping("/list")
    public R<Page<AuditLog>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        PageRequest pr = PageRequest.of(page - 1, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        return R.ok(auditRepo.findAll(pr));
    }
}
