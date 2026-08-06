package com.example.cert.infra.seal;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

public class SealImageGenerator {

    public static final int CERT_TYPE_ENTERPRISE = 1;
    public static final int CERT_TYPE_INDIVIDUAL = 2;

    public static class SealImageResult {
        private byte[] imageBytes;
        private int width;
        private int height;

        public SealImageResult(byte[] imageBytes, int width, int height) {
            this.imageBytes = imageBytes;
            this.width = width;
            this.height = height;
        }

        public byte[] getImageBytes() {
            return imageBytes;
        }

        public int getWidth() {
            return width;
        }

        public int getHeight() {
            return height;
        }
    }

    public static SealImageResult generateSealImage(String name, int certType) throws IOException {
        // 超采样因子：放大生成图片的像素分辨率以提升清晰度。
        // PDF 上的物理尺寸由调用方按固定 pt 值控制，此处放大像素不影响盖章大小。
        int S = 3;

        int width, height;
        double cx, cy, bottomY;

        if (certType == CERT_TYPE_ENTERPRISE) {
            // 1. 企业圆章基准画布 160 x 190，放大 S 倍
            width = 160 * S;
            height = 190 * S;
            cx = width / 2.0; // 80 * S
            cy = 80.0 * S;
        } else {
            width = 220 * S;
            height = 90 * S;
            cx = width / 2.0;
            cy = 36.0 * S;
        }

        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2d = image.createGraphics();

        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        if (certType == CERT_TYPE_ENTERPRISE) {
            Color redColor = new Color(255, 0, 0);
            g2d.setColor(redColor);

            double radius = 69.0 * S; // 外圆半径
            bottomY = cy + radius;

            // 外圆 (lineWidth = 3.0f * S)
            g2d.setStroke(new BasicStroke(3.0f * S));
            g2d.draw(new Ellipse2D.Double(cx - radius, cy - radius, radius * 2, radius * 2));

            // 内圆 (lineWidth = 1.2f * S, radius - 4 * S)
            double innerRadius = radius - 4.0 * S;
            g2d.setStroke(new BasicStroke(1.2f * S));
            g2d.draw(new Ellipse2D.Double(cx - innerRadius, cy - innerRadius, innerRadius * 2, innerRadius * 2));

            g2d.setStroke(new BasicStroke(3.0f * S));

            // 五角星 (半径 22 * S)
            drawStar(g2d, cx, cy + 6.0 * S, 22.0 * S);

            // 弧形文字 (字号 14 * S, textRadius = radius - 15 * S)
            g2d.setFont(new Font("SimSun", Font.BOLD, 14 * S));
            double textRadius = radius - 15.0 * S;
            double totalAngle = Math.PI * 1.1;
            double startAngle = -Math.PI / 2.0 - totalAngle / 2.0;

            int nameLen = name.length();
            for (int i = 0; i < nameLen; i++) {
                double angle;
                if (nameLen == 1) {
                    angle = -Math.PI / 2.0;
                } else {
                    angle = startAngle + ((double) i / (nameLen - 1)) * totalAngle;
                }

                double charX = cx + Math.cos(angle) * textRadius;
                double charY = cy + Math.sin(angle) * textRadius;

                Graphics2D g2dCopy = (Graphics2D) g2d.create();
                g2dCopy.translate(charX, charY);
                g2dCopy.rotate(angle + Math.PI / 2.0);

                String charStr = String.valueOf(name.charAt(i));
                FontMetrics fm = g2dCopy.getFontMetrics();
                int charWidth = fm.stringWidth(charStr);
                int charAscent = fm.getAscent();
                int charHeight = fm.getHeight();
                float drawX = -charWidth / 2.0f;
                float drawY = charAscent - (charHeight / 2.0f);

                g2dCopy.drawString(charStr, drawX, drawY);
                g2dCopy.dispose();
            }

        } else {
            // 个人章逻辑不变
            g2d.setColor(Color.BLACK);
            int fontSize;
            if (name.length() <= 2) {
                fontSize = 44 * S;
            } else if (name.length() == 3) {
                fontSize = 40 * S;
            } else {
                fontSize = 34 * S;
            }

            g2d.setFont(new Font("KaiTi", Font.BOLD, fontSize));

            FontMetrics fm = g2d.getFontMetrics();
            int textWidth = fm.stringWidth(name);
            int charAscent = fm.getAscent();
            int charHeight = fm.getHeight();

            float drawX = (float) (cx - (textWidth / 2.0));
            float drawY = (float) (cy + charAscent - (charHeight / 2.0));

            g2d.drawString(name, drawX, drawY);
            bottomY = cy + 20.0 * S;
        }

        // 2. 底部蓝色日期 (保持 16 * S MONOSPACED)
        String dateStr = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
        g2d.setColor(new Color(0, 0, 255));
        g2d.setFont(new Font(Font.MONOSPACED, Font.BOLD, 16 * S));

        FontMetrics dateFm = g2d.getFontMetrics();
        int dateWidth = dateFm.stringWidth(dateStr);
        int dateAscent = dateFm.getAscent();

        float dateX = (float) (cx - (dateWidth / 2.0));
        float dateY = (float) (bottomY + 8.0 * S + dateAscent);

        g2d.drawString(dateStr, dateX, dateY);

        g2d.dispose();

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(image, "PNG", baos);

        return new SealImageResult(baos.toByteArray(), width, height);
    }

    private static void drawStar(Graphics2D g2d, double cx, double cy, double outerRadius) {
        double innerRadius = outerRadius * 0.382;
        double rot = Math.PI / 2.0 * 3.0;
        double step = Math.PI / 5.0;

        Path2D path = new Path2D.Double();
        path.moveTo(cx, cy - outerRadius);

        for (int i = 0; i < 5; i++) {
            path.lineTo(cx + Math.cos(rot) * outerRadius, cy + Math.sin(rot) * outerRadius);
            rot += step;
            path.lineTo(cx + Math.cos(rot) * innerRadius, cy + Math.sin(rot) * innerRadius);
            rot += step;
        }
        path.lineTo(cx, cy - outerRadius);
        path.closePath();

        g2d.fill(path);
    }
}