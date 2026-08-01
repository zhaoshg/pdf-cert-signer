package com.example.cert.domain.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

@Data
public class SignRequest {

    @NotBlank(message = "signerId不能为空")
    private String signerId;

    @NotBlank(message = "pdfUrl不能为空")
    private String pdfUrl;

    @NotEmpty(message = "签章位置不能为空")
    @Valid
    private List<SignPosition> signatures;

    @Data
    public static class SignPosition {

        private int pageIndex;

        private String sealUrl;

        private float x;
        private float y;
        private float width = 120;
        private float height = 120;
        private String reason;
    }
}
