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
     * 坐标均为 PDF 左上角原点坐标系（x 向右、y 向下，单位 pt，1pt ≈ 0.3528mm）。
     * 前端在缩放视图中拖拽时，需将屏幕像素除以缩放因子换算回 PDF pt 后再提交；
     * 尺寸 width/height 建议与印章图片宽高比一致，避免盖章时被拉伸变形。
     */
    @Data
    public static class SignPosition {

        /** 页码（从 0 开始） */
        private int pageIndex;

        /** 印章图片下载地址，可为远程 URL */
        private String sealUrl;

        /** 印章矩形左上角 x 坐标（PDF pt） */
        private float x;
        /** 印章矩形左上角 y 坐标（PDF pt） */
        private float y;
        /** 印章宽度（PDF pt） */
        private float width = 120;
        /** 印章高度（PDF pt） */
        private float height = 120;
        /** 签章原因，写入数字签名的 Reason 字段；为空时后端回退为"PDF电子签章" */
        private String reason;
    }
}
