package com.example.cert.domain.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class IssueRequest {

    @NotBlank(message = "统一信用代码不能为空")
    private String creditCode;

    @NotBlank(message = "姓名不能为空")
    private String name;

    @NotBlank(message = "部门不能为空")
    private String department;

    private String email;

    private Integer validDays;
}
