package com.example.cert.domain.dto;

import lombok.Data;

/**
 * PDF 签章响应体。
 * signedPdfUrl 为已签章 PDF 的存储地址（可直接下载/预览），
 * certSubject 与 signTime 用于前端展示签章证书信息。
 */
@Data
public class SignResponse {

    /** 已签章 PDF 的下载/访问地址 */
    private String signedPdfUrl;
    /** 签署所用数字证书的主题信息 */
    private String certSubject;
    /** 签章时间（ISO_LOCAL_DATE_TIME 格式） */
    private String signTime;
}
