package com.tgzjdv.chat.image;

import com.mojang.blaze3d.platform.NativeImage;
import com.tgzjdv.chat.TgzjdvChatMod;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * 图片下载与纹理缓存
 * 从 URL 异步下载图片 → 注册为纹理 → 回调 Identifier
 */
public final class ImageCache {

    private static final Map<String, Identifier> CACHE = new ConcurrentHashMap<>();
    private static final Map<String, int[]> IMAGE_DIMENSIONS = new ConcurrentHashMap<>();

    // 下载进度（url → 0.0-1.0）
    private static final Map<String, Float> DOWNLOAD_PROGRESS = new ConcurrentHashMap<>();

    private ImageCache() {
    }

    // 进行中的下载：url → 等待回调列表（同一 URL 只下载一次，后续请求等待）
    private static final Map<String, java.util.List<java.util.function.Consumer<Identifier>>> PENDING_DOWNLOADS = new ConcurrentHashMap<>();

    /**
     * 获取图片纹理（已缓存直接返回，未缓存则异步下载）
     * 同一 URL 只启动一次下载，后续请求进入等待队列
     *
     * @param url      图片 URL
     * @param progress 下载进度回调（0.0-1.0），可为 null
     * @param callback 纹理就绪时回调（可能为 null 表示失败）
     */
    public static void requestImage(String url, Consumer<Float> progress, Consumer<Identifier> callback) {
        Identifier cached = CACHE.get(url);
        if (cached != null) {
            callback.accept(cached);
            return;
        }
        // 是否已在下载（去重，避免每帧新开下载线程）
        boolean startNew;
        synchronized (PENDING_DOWNLOADS) {
            if (PENDING_DOWNLOADS.containsKey(url)) {
                PENDING_DOWNLOADS.get(url).add(callback);
                startNew = false;
            } else {
                java.util.List<Consumer<Identifier>> waiters = new java.util.concurrent.CopyOnWriteArrayList<>();
                waiters.add(callback);
                PENDING_DOWNLOADS.put(url, waiters);
                startNew = true;
            }
        }
        if (!startNew) {
            return; // 已有下载任务，等待其完成
        }
        // 启动唯一下载线程
        Thread thread = new Thread(() -> {
            Identifier texture = downloadAndRegister(url, progress);
            // 取出等待队列
            java.util.List<Consumer<Identifier>> waiters;
            synchronized (PENDING_DOWNLOADS) {
                waiters = PENDING_DOWNLOADS.remove(url);
            }
            final Identifier finalTexture = texture;
            // 立即写入缓存（渲染线程可读，避免"下载完成但未注册"期间被误判为未缓存而重新下载）
            if (finalTexture != null) {
                CACHE.put(url, finalTexture);
            }
            Minecraft.getInstance().execute(() -> {
                if (waiters != null) {
                    for (Consumer<Identifier> w : waiters) {
                        try {
                            w.accept(finalTexture);
                        } catch (Exception ignored) {
                        }
                    }
                }
            });
        }, "TGC-ImageLoader");
        thread.setDaemon(true);
        thread.start();
    }

    /** 简化调用（无进度） */
    public static void requestImage(String url, Consumer<Identifier> callback) {
        requestImage(url, null, callback);
    }

    /** 获取图片下载进度（0.0-1.0），未在下载返回 null */
    public static Float getDownloadProgress(String url) {
        return DOWNLOAD_PROGRESS.get(url);
    }

    /** 同步检查是否已缓存 */
    public static Identifier getCached(String url) {
        return CACHE.get(url);
    }

    /** 获取图片宽高 [w, h]，未加载返回 null */
    public static int[] getDimensions(String url) {
        return IMAGE_DIMENSIONS.get(url);
    }

    /**
     * 解码图片字节为 NativeImage
     * 优先用 NativeImage.read（PNG 等）；失败时用 Java ImageIO 解码（JPG/JPEG/GIF/BMP 等）
     */
    private static NativeImage decodeImage(byte[] bytes) throws Exception {
        try {
            return NativeImage.read(bytes);
        } catch (Exception e) {
            // 用 Java ImageIO 解码（支持 JPEG 等）
            java.awt.image.BufferedImage img = javax.imageio.ImageIO.read(new java.io.ByteArrayInputStream(bytes));
            if (img == null) {
                throw new Exception("ImageIO 无法解码图片");
            }
            int w = img.getWidth();
            int h = img.getHeight();
            NativeImage out = new NativeImage(w, h, true);
            int[] pixels = img.getRGB(0, 0, w, h, null, 0, w);
            for (int i = 0; i < pixels.length; i++) {
                int rgb = pixels[i];
                int a = (rgb >>> 24) & 0xFF;
                int r = (rgb >>> 16) & 0xFF;
                int g = (rgb >>> 8) & 0xFF;
                int b = rgb & 0xFF;
                // NativeImage 内部 ABGR
                out.setPixelABGR(i % w, i / w, (a << 24) | (b << 16) | (g << 8) | r);
            }
            return out;
        }
    }

    /** 下载图片并注册为纹理（在后台线程执行，注册提交到渲染线程） */
    private static Identifier downloadAndRegister(String url, Consumer<Float> progress) {
        try {
            HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(30000);
            conn.setRequestProperty("User-Agent", "TGZJDV-Chat/1.3.5");
            int code = conn.getResponseCode();
            if (code != 200) {
                TgzjdvChatMod.LOGGER.info("[TGC图片] 下载失败 HTTP={} url={}", code, url);
                return null;
            }
            int total = conn.getContentLength();
            byte[] bytes;
            try (InputStream in = conn.getInputStream()) {
                java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
                byte[] buffer = new byte[8192];
                int read;
                int totalRead = 0;
                while ((read = in.read(buffer)) != -1) {
                    bos.write(buffer, 0, read);
                    totalRead += read;
                    if (progress != null && total > 0) {
                        progress.accept(Math.min(0.9f, totalRead / (float) total));
                    }
                    DOWNLOAD_PROGRESS.put(url, Math.min(0.9f, total > 0 ? totalRead / (float) total : 0.9f));
                }
                bytes = bos.toByteArray();
            }
            if (bytes == null || bytes.length == 0) {
                TgzjdvChatMod.LOGGER.info("[TGC图片] 下载内容为空 url={}", url);
                return null;
            }
            NativeImage image;
            try {
                image = decodeImage(bytes);
            } catch (Exception e) {
                TgzjdvChatMod.LOGGER.info("[TGC图片] 图片解析失败 url={} 字节={} 错误={}", url, bytes.length, e.getMessage());
                return null;
            }
            if (image == null) {
                TgzjdvChatMod.LOGGER.info("[TGC图片] NativeImage 为空 url={}", url);
                return null;
            }
            int w = image.getWidth();
            int h = image.getHeight();

            // 唯一纹理 ID（含随机后缀，避免重复注册冲突）
            String id = "tgzjdvchat:img_" + Integer.toHexString(url.hashCode()) + "_" + UUID.randomUUID().toString().substring(0, 6);
            Identifier identifier = Identifier.tryParse(id);
            if (identifier == null) {
                identifier = Identifier.withDefaultNamespace("img_" + Math.abs(url.hashCode()) + "_" + UUID.randomUUID().toString().substring(0, 6));
            }

            // 注册纹理（必须在渲染线程）；缓存写入移到下载线程（避免时序竞态）
            final Identifier finalId = identifier;
            final int fw = w, fh = h;
            final float done = 1.0f;
            // 立即写入缓存（下载线程，ConcurrentHashMap 线程安全）
            CACHE.put(url, finalId);
            IMAGE_DIMENSIONS.put(url, new int[]{fw, fh});
            DOWNLOAD_PROGRESS.put(url, done);
            Minecraft.getInstance().execute(() -> {
                try {
                    DynamicTexture dynamic = new DynamicTexture(() -> "TGZJDV-Chat Image", image);
                    dynamic.upload();
                    Minecraft.getInstance().getTextureManager().register(finalId, dynamic);
                    TgzjdvChatMod.LOGGER.info("[TGC图片] 注册成功 url={} 尺寸={}x{} id={}", url, fw, fh, finalId);
                } catch (Exception e) {
                    TgzjdvChatMod.LOGGER.info("[TGC图片] 注册异常 url={} 错误={}", url, e.getMessage());
                }
            });
            return finalId;
        } catch (Exception e) {
            TgzjdvChatMod.LOGGER.info("[TGC图片] 下载异常 url={} 错误={}", url, e.getMessage());
            return null;
        }
    }

    // ================= 本地图片 =================

    /** 本地文件加载缓存（路径 → 纹理） */
    private static final Map<String, Identifier> LOCAL_CACHE = new ConcurrentHashMap<>();

    /**
     * 加载本地图片为缩略图（降采样到最大 256px，避免大图内存爆炸）
     */
    private static NativeImage loadThumbnail(byte[] bytes) throws Exception {
        java.awt.image.BufferedImage img = javax.imageio.ImageIO.read(new java.io.ByteArrayInputStream(bytes));
        if (img == null) {
            return decodeImage(bytes); // 回退
        }
        int maxSize = 256;
        int w = img.getWidth();
        int h = img.getHeight();
        float scale = Math.min(1.0f, maxSize / (float) Math.max(w, h));
        if (scale < 1.0f) {
            int nw = Math.max(1, (int) (w * scale));
            int nh = Math.max(1, (int) (h * scale));
            java.awt.image.BufferedImage scaled = new java.awt.image.BufferedImage(nw, nh, java.awt.image.BufferedImage.TYPE_INT_ARGB);
            java.awt.Graphics2D g = scaled.createGraphics();
            g.drawImage(img, 0, 0, nw, nh, null);
            g.dispose();
            img = scaled;
        }
        int iw = img.getWidth();
        int ih = img.getHeight();
        NativeImage out = new NativeImage(iw, ih, true);
        int[] pixels = img.getRGB(0, 0, iw, ih, null, 0, iw);
        for (int i = 0; i < pixels.length; i++) {
            int rgb = pixels[i];
            out.setPixelABGR(i % iw, i / iw,
                    (((rgb >>> 24) & 0xFF) << 24) | ((rgb & 0xFF) << 16) | (((rgb >>> 8) & 0xFF) << 8) | ((rgb >>> 16) & 0xFF));
        }
        return out;
    }

    /**
     * 加载本地图片文件为纹理（异步，降采样缩略图）
     *
     * @param filePath 本地图片文件路径
     * @param callback 纹理就绪时回调（可能为 null）
     */
    public static void requestLocalImage(String filePath, java.util.function.Consumer<Identifier> callback) {
        Identifier cached = LOCAL_CACHE.get(filePath);
        if (cached != null) {
            callback.accept(cached);
            return;
        }
        Thread thread = new Thread(() -> {
            try {
                java.io.File file = new java.io.File(filePath);
                if (!file.exists()) {
                    Minecraft.getInstance().execute(() -> callback.accept(null));
                    return;
                }
                byte[] bytes = java.nio.file.Files.readAllBytes(file.toPath());
                NativeImage image = loadThumbnail(bytes);
                if (image == null) {
                    Minecraft.getInstance().execute(() -> callback.accept(null));
                    return;
                }
                int w = image.getWidth();
                int h = image.getHeight();
                String id = "tgzjdvchat:local_" + Math.abs(filePath.hashCode()) + "_" + UUID.randomUUID().toString().substring(0, 6);
                Identifier identifier = Identifier.tryParse(id);
                if (identifier == null) {
                    identifier = Identifier.withDefaultNamespace("local_" + Math.abs(filePath.hashCode()) + "_" + UUID.randomUUID().toString().substring(0, 6));
                }
                final Identifier finalId = identifier;
                final int fw = w, fh = h;
                // 立即写入缓存（下载线程，避免竞态导致重复加载）
                LOCAL_CACHE.put(filePath, finalId);
                IMAGE_DIMENSIONS.put(filePath, new int[]{fw, fh});
                Minecraft.getInstance().execute(() -> {
                    try {
                        DynamicTexture dynamic = new DynamicTexture(() -> "TGZJDV-Local Image", image);
                        dynamic.upload();
                        Minecraft.getInstance().getTextureManager().register(finalId, dynamic);
                        TgzjdvChatMod.LOGGER.info("[TGC图片] 本地注册成功 path={} 尺寸={}x{}", filePath, fw, fh);
                    } catch (Exception e) {
                        TgzjdvChatMod.LOGGER.info("[TGC图片] 本地注册异常 path={} 错误={}", filePath, e.getMessage());
                    }
                    try {
                        callback.accept(finalId);
                    } catch (Exception ignored) {
                    }
                });
            } catch (Exception e) {
                TgzjdvChatMod.LOGGER.info("[TGC图片] 本地加载异常 path={} 错误={}", filePath, e.getMessage());
                Minecraft.getInstance().execute(() -> callback.accept(null));
            }
        }, "TGC-LocalImage");
        thread.setDaemon(true);
        thread.start();
    }

    /** 获取本地图片纹理（已加载） */
    public static Identifier getLocalCached(String filePath) {
        return LOCAL_CACHE.get(filePath);
    }

    /**
     * 下载图片并保存到 images 目录，回调保存路径（失败回调 null）
     */
    public static void copyImageToFile(String url, java.util.function.Consumer<String> callback) {
        Thread t = new Thread(() -> {
            try {
                java.net.HttpURLConnection conn = (java.net.HttpURLConnection) new java.net.URL(url).openConnection();
                conn.setConnectTimeout(15000);
                conn.setReadTimeout(30000);
                conn.setRequestProperty("User-Agent", "TGZJDV-Chat/1.3.5");
                int code = conn.getResponseCode();
                if (code != 200) {
                    Minecraft.getInstance().execute(() -> callback.accept(null));
                    return;
                }
                byte[] bytes;
                try (InputStream in = conn.getInputStream()) {
                    bytes = in.readAllBytes();
                }
                if (bytes == null || bytes.length == 0) {
                    Minecraft.getInstance().execute(() -> callback.accept(null));
                    return;
                }
                String name = url.substring(url.lastIndexOf('/') + 1);
                if (name.isEmpty() || name.contains("?")) {
                    name = "image_" + System.currentTimeMillis() + ".png";
                }
                java.io.File dir = new java.io.File(Minecraft.getInstance().gameDirectory, "images");
                if (!dir.exists()) {
                    dir.mkdirs();
                }
                java.io.File out = new java.io.File(dir, name);
                java.nio.file.Files.write(out.toPath(), bytes);
                final String path = out.getAbsolutePath();
                Minecraft.getInstance().execute(() -> callback.accept(path));
            } catch (Exception e) {
                Minecraft.getInstance().execute(() -> callback.accept(null));
            }
        }, "TGC-CopyFile");
        t.setDaemon(true);
        t.start();
    }

    /** 清空缓存 */
    public static void clearCache() {
        CACHE.clear();
        IMAGE_DIMENSIONS.clear();
        LOCAL_CACHE.clear();
        DOWNLOAD_PROGRESS.clear();
    }
}
