package com.example.cert.infra.signing;

public class CoordinateConverter {

    public static PdfPoint toPdfPoint(double displayX, double displayY,
                                       double renderWidth, double renderHeight,
                                       double pdfWidth, double pdfHeight) {
        double scaleX = pdfWidth / renderWidth;
        double scaleY = pdfHeight / renderHeight;
        double pdfX = displayX * scaleX;
        double pdfY = pdfHeight - (displayY * scaleY);
        return new PdfPoint((float) pdfX, (float) pdfY);
    }

    public record PdfPoint(float x, float y) {}
}
