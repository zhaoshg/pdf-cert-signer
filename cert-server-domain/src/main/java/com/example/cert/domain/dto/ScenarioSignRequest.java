package com.example.cert.domain.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

/**
 * 场景证书签章请求体（POST /api/v1/pdf/sign-scenario）。
 *
 * 一站式“临时签发短期证书 → 签章 → 立即吊销”：调用方一次传入证书签发字段 + PDF 与印章坐标，
 * 后端签发临时证书（默认有效期 1 天）完成签章后立即吊销，p12 私钥仅内存使用、不返回调用方。
 */
@Data
public class ScenarioSignRequest {

    @NotNull(message = "证书类型不能为空")
    private Integer certType;

    @NotBlank(message = "统一信用代码不能为空")
    private String creditCode;

    @NotBlank(message = "姓名不能为空")
    private String name;

    private String department;

    private String email;

    /** 临时证书有效天数，可空；非法值后端回退为 1 天 */
    private Integer validDays;

    /** 待签署 PDF 的下载地址（服务端自行下载），可为远程 URL */
    @NotBlank(message = "pdfUrl不能为空")
    private String pdfUrl;

    /** 签章位置列表，复用 SignRequest.SignPosition（页码/印章/坐标/reason） */
    @NotEmpty(message = "签章位置不能为空")
    @Valid
    private List<SignRequest.SignPosition> signatures;
}
