package com.example.cert.api;

import com.example.cert.core.common.R;
import com.example.cert.core.exception.BizException;
import com.example.cert.domain.dto.LoginRequest;
import com.example.cert.domain.dto.LoginResponse;
import com.example.cert.domain.entity.Admin;
import com.example.cert.domain.repository.AdminRepository;
import com.example.cert.infra.security.JwtUtil;
import jakarta.validation.Valid;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AdminRepository adminRepo;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

    public AuthController(AdminRepository adminRepo, PasswordEncoder passwordEncoder, JwtUtil jwtUtil) {
        this.adminRepo = adminRepo;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtil = jwtUtil;
    }

    @PostMapping("/login")
    public R<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        Admin admin = adminRepo.findByUsername(request.getUsername())
                .orElseThrow(() -> new BizException("用户名或密码错误"));

        if (!passwordEncoder.matches(request.getPassword(), admin.getPassword())) {
            throw new BizException("用户名或密码错误");
        }

        String token = jwtUtil.generateToken(admin.getUsername());

        LoginResponse resp = new LoginResponse();
        resp.setToken(token);
        resp.setUsername(admin.getUsername());
        return R.ok("登录成功", resp);
    }
}
