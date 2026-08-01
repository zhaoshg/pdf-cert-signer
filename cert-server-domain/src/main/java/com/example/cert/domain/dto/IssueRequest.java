package com.example.cert.domain.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class IssueRequest {

    @NotNull(message = "证书类型不能为空")
    private Integer certType;

    @NotBlank(message = "统一信用代码不能为空")
    private String creditCode;

    @NotBlank(message = "姓名不能为空")
    private String name;

    private String department;

    private String email;

    private Integer validDays;
}
