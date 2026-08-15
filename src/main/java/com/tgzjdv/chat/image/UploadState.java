package com.tgzjdv.chat.image;

/**
 * 图片上传状态（供聊天栏进度条渲染）
 */
public final class UploadState {

    private static volatile boolean uploading = false;
    private static volatile String fileName = "";
    private static volatile float progress = 0.0f;      // 0.0-1.0
    private static volatile boolean error = false;
    private static volatile String errorText = "";
    private static volatile long lastUpdateTime = 0;

    private UploadState() {
    }

    /** 开始上传 */
    public static void start(String name) {
        uploading = true;
        fileName = name == null ? "" : name;
        progress = 0.0f;
        error = false;
        errorText = "";
        lastUpdateTime = System.currentTimeMillis();
        // 平滑进度：文件读完后（95%）缓慢推进到 99%（等待服务器响应阶段）
        Thread smooth = new Thread(() -> {
            while (uploading && !error) {
                if (progress >= 0.95f && progress < 0.99f) {
                    progress = Math.min(0.99f, progress + 0.008f);
                    lastUpdateTime = System.currentTimeMillis();
                }
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    break;
                }
            }
        }, "TGC-UploadSmooth");
        smooth.setDaemon(true);
        smooth.start();
    }

    /** 更新进度 */
    public static void setProgress(float p) {
        progress = Math.max(0, Math.min(1.0f, p));
        lastUpdateTime = System.currentTimeMillis();
    }

    /** 上传失败 */
    public static void fail(String text) {
        uploading = false;
        error = true;
        errorText = text == null ? "上传失败" : text;
        lastUpdateTime = System.currentTimeMillis();
    }

    /** 上传成功完成 */
    public static void finish() {
        uploading = false;
        error = false;
        progress = 1.0f;
        lastUpdateTime = System.currentTimeMillis();
    }

    public static boolean isUploading() {
        return uploading;
    }

    public static boolean isError() {
        return error;
    }

    public static String getFileName() {
        return fileName;
    }

    public static float getProgress() {
        return progress;
    }

    public static String getErrorText() {
        return errorText;
    }

    public static long getLastUpdateTime() {
        return lastUpdateTime;
    }
}
