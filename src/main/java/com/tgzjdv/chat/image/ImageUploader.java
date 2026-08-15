package com.tgzjdv.chat.image;

import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * Telegraph-Image 图床上传工具
 * 上传图片文件，返回可访问的图片 URL
 */
public final class ImageUploader {

    /** 图床域名（用户部署的 Telegraph-Image） */
    public static final String IMAGE_HOST = "img.famousmusic.asia";
    private static final String UPLOAD_URL = "https://" + IMAGE_HOST + "/upload";

    private ImageUploader() {
    }

    /**
     * 上传图片文件到图床
     *
     * @param file      本地图片文件
     * @param progress  上传进度回调（0.0 - 1.0），可为 null
     * @param onError   失败回调（错误文本），可为 null
     * @return 完整图片 URL；失败返回 null
     */
    public static String upload(File file, java.util.function.Consumer<Float> progress, java.util.function.Consumer<String> onError) {
        if (file == null || !file.exists()) {
            if (onError != null) {
                onError.accept("文件不存在");
            }
            return null;
        }
        try {
            String boundary = "----tgzjdv" + UUID.randomUUID();
            HttpURLConnection conn = (HttpURLConnection) new URL(UPLOAD_URL).openConnection();
            conn.setRequestMethod("POST");
            conn.setDoOutput(true);
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(60000);
            conn.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);
            conn.setRequestProperty("User-Agent", "TGZJDV-Chat/1.3.5");

            String fileName = file.getName();
            long totalBytes = file.length();
            long writtenBytes = 0;
            try (DataOutputStream out = new DataOutputStream(conn.getOutputStream())) {
                // 文件头
                out.writeBytes("--" + boundary + "\r\n");
                out.writeBytes("Content-Disposition: form-data; name=\"file\"; filename=\"" + fileName + "\"\r\n");
                out.writeBytes("Content-Type: application/octet-stream\r\n\r\n");
                // 文件内容（统计进度）
                try (FileInputStream fis = new FileInputStream(file)) {
                    byte[] buffer = new byte[8192];
                    int read;
                    while ((read = fis.read(buffer)) != -1) {
                        out.write(buffer, 0, read);
                        writtenBytes += read;
                        if (progress != null && totalBytes > 0) {
                            progress.accept(Math.min(0.95f, (float) writtenBytes / (float) totalBytes));
                        }
                    }
                }
                out.writeBytes("\r\n");
                out.writeBytes("--" + boundary + "--\r\n");
                out.flush();
            }

            int code = conn.getResponseCode();
            if (code != 200) {
                // 读取错误响应（可能是 Cloudflare KV 配额等）
                String errBody = "";
                try {
                    try (InputStream in = conn.getErrorStream()) {
                        if (in != null) {
                            errBody = new String(in.readAllBytes(), StandardCharsets.UTF_8);
                        }
                    }
                } catch (Exception ignored) {
                }
                String errText = extractErrorMessage(errBody);
                if (onError != null) {
                    onError.accept(errText);
                }
                return null;
            }
            // 读取响应 JSON: [{"src":"/file/xxx.png"}]
            String response;
            try (InputStream in = conn.getInputStream()) {
                response = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            }
            // 解析 src
            int srcIdx = response.indexOf("\"src\":\"");
            if (srcIdx < 0) {
                if (onError != null) {
                    onError.accept(extractErrorMessage(response));
                }
                return null;
            }
            int start = srcIdx + "\"src\":\"".length();
            int end = response.indexOf("\"", start);
            if (end < 0) {
                return null;
            }
            String src = response.substring(start, end);
            if (progress != null) {
                progress.accept(1.0f);
            }
            if (src.startsWith("http")) {
                return src;
            }
            return "https://" + IMAGE_HOST + src;
        } catch (Exception e) {
            if (onError != null) {
                onError.accept("网络错误: " + e.getClass().getSimpleName());
            }
            return null;
        }
    }

    /** 从响应 JSON 中提取错误消息（如 KV 配额等） */
    private static String extractErrorMessage(String body) {
        if (body == null || body.isEmpty()) {
            return "上传失败";
        }
        try {
            int idx = body.indexOf("\"error\":\"");
            if (idx >= 0) {
                int start = idx + "\"error\":\"".length();
                int end = body.indexOf("\"", start);
                if (end > start) {
                    return "上传失败: " + body.substring(start, end);
                }
            }
        } catch (Exception ignored) {
        }
        return "上传失败";
    }

    /**
     * 上传图片文件（无进度/错误回调）
     */
    public static String upload(File file) {
        return upload(file, null, null);
    }
}
