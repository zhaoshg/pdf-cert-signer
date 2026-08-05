package com.example.cert.domain.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

/**
 * PDF 签章请求体（POST /api/v1/pdf/sign）。
 *
 * 完整调用链路：前端签章页 → PdfSignController.sign() → SigningServiceImpl.sign()
 *            → PdfSigner.sign()（生成已签章 PDF）→ FileStorageService.upload()（保存并返回 URL）
 */
@Data
public class SignRequest {

    /** 签署人唯一标识，服务端据此查找其名下"有效状态"的数字证书用于签名 */
    @NotBlank(message = "signerId不能为空")
    private String signerId;

    /** 待签署 PDF 的下载地址（服务端自行下载），可为远程 URL */
    @NotBlank(message = "pdfUrl不能为空")
    private String pdfUrl;

    /** 签章位置列表，同一份 PDF 可一次性盖多个印章 */
    @NotEmpty(message = "签章位置不能为空")
    @Valid
    private List<SignPosition> signatures;

    /**
     * 单个签章位置（视觉签章定位信息）。
     * 注意：x/y 为 PDF 页面左上角原点坐标系，与 PDF 渲染时的缩放无关，
     * 前端若在缩放视图下拖拽，需自行按缩放比例换算后再提交。
     */
    @Data
    public static class SignPosition {

        /** 页码（从 0 开始） */
        private int pageIndex;

        /** 印章图片下载地址，可为远程 URL */
        private String sealUrl;

        /** 印章中心点 x 坐标（PDF 用户空间单位，默认以 pt 计） */
        private float x;
        /** 印章中心点 y 坐标（PDF 用户空间单位，默认以 pt 计） */
        private float y;
        /** 印章宽度，默认 120 */
        private float width = 120;
        /** 印章高度，默认 120 */
        private float height = 120;
        /** 签章原因，写入数字签名的 Reason 字段；为空时后端回退为"PDF电子签章" */
        private String reason;
    }
}
