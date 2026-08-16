package com.tgzjdv.chat.update;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 模组更新下载器
 * 从 Modrinth/GitHub CDN 下载 jar 到目标文件，支持进度回调
 */
public final class UpdateDownloader {

    /** 下载进度回调（在下载线程中调用） */
    public interface Progress {
        void onProgress(float progress, long downloaded, long total);
    }

    private UpdateDownloader() {
    }

    /**
     * 下载文件（阻塞，需在后台线程调用）
     *
     * @param url      下载地址
     * @param target   保存路径
     * @param progress 进度回调（可为 null）
     * @throws Exception 网络/IO 错误或 HTTP 非 200
     */
    public static void download(String url, Path target, Progress progress) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        conn.setConnectTimeout(15000);
        conn.setReadTimeout(60000);
        conn.setRequestProperty("User-Agent", "TGZJDV-Chat/" + UpdateChecker.getCurrentVersion());
        conn.setRequestProperty("Accept", "application/octet-stream");
        // GitHub 下载地址会重定向到对象存储，需跟随
        conn.setInstanceFollowRedirects(true);
        int code = conn.getResponseCode();
        if (code != 200) {
            throw new java.io.IOException("HTTP " + code);
        }
        long total = conn.getContentLengthLong();
        try (InputStream in = conn.getInputStream();
             OutputStream out = Files.newOutputStream(target)) {
            byte[] buf = new byte[8192];
            long done = 0;
            int n;
            while ((n = in.read(buf)) != -1) {
                out.write(buf, 0, n);
                done += n;
                if (progress != null) {
                    float p = total > 0 ? Math.min(1.0f, done / (float) total) : 0.0f;
                    progress.onProgress(p, done, total);
                }
            }
        } finally {
            conn.disconnect();
        }
    }
}
