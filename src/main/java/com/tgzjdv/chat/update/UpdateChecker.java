package com.tgzjdv.chat.update;

import com.tgzjdv.chat.TgzjdvChatMod;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/**
 * 模组更新检查器
 * 通过 Modrinth API 获取最新版本，与当前版本对比
 */
public final class UpdateChecker {

    /** Modrinth 项目 ID（模组主页） */
    public static final String MODRINTH_PROJECT = "TIwmn59c";
    private static final String MODRINTH_API_URL = "https://api.modrinth.com/v2/project/" + MODRINTH_PROJECT + "/version";
    /** GitHub 仓库（Modrinth 不可用时的回退源） */
    public static final String GITHUB_REPO = "TGZJDV/TGZJDVchat";
    private static final String GITHUB_API_URL = "https://api.github.com/repos/" + GITHUB_REPO + "/releases/latest";
    /** 更新页面链接 */
    public static final String MODRINTH_URL = "https://modrinth.com/project/" + MODRINTH_PROJECT;
    public static final String GITHUB_RELEASES_URL = "https://github.com/" + GITHUB_REPO + "/releases";

    /** 更新来源 */
    public enum Source {
        MODRINTH("Modrinth"),
        GITHUB("GitHub");
        public final String displayName;

        Source(String displayName) {
            this.displayName = displayName;
        }
    }

    private static volatile String latestVersion = null;
    private static volatile String latestChangelog = null;
    /** 匹配当前游戏版本的最新版 jar 下载地址 */
    private static volatile String latestDownloadUrl = null;
    /** 下载文件名（如 tgzjdvchat-mc26.1.2-1.4.1.jar） */
    private static volatile String latestFileName = null;
    private static volatile Source source = null;
    private static volatile boolean checked = false;
    private static volatile boolean checking = false;
    private static volatile String error = null;

    private UpdateChecker() {
    }

    /** 获取当前模组版本（从 Fabric 元数据） */
    public static String getCurrentVersion() {
        try {
            return net.fabricmc.loader.api.FabricLoader.getInstance()
                    .getModContainer(TgzjdvChatMod.MOD_ID).get()
                    .getMetadata().getVersion().getFriendlyString();
        } catch (Exception e) {
            return "unknown";
        }
    }

    /** 后台自动检查更新（无回调，完成时如有更新自动在游戏内提示） */
    public static void checkUpdate() {
        checkUpdate(null);
    }

    /**
     * 后台检查更新：优先从 Modrinth 获取，Modrinth 不可用时回退到 GitHub Releases。
     *
     * @param done 检查完成后的回调（在客户端渲染线程执行，可为 null；用于设置页刷新 UI）
     */
    public static void checkUpdate(Runnable done) {
        if (checking) {
            return; // 已在检查中，避免重复发起
        }
        checking = true;
        error = null;
        Thread t = new Thread(() -> {
            boolean ok = false;
            // 1) 优先 Modrinth
            try {
                String resp = httpGet(MODRINTH_API_URL);
                if (parseModrinth(resp)) {
                    source = Source.MODRINTH;
                    ok = true;
                }
            } catch (Exception ignored) {
                // Modrinth 不可用，回退 GitHub
            }
            // 2) 回退 GitHub Releases
            if (!ok) {
                try {
                    String resp = httpGet(GITHUB_API_URL);
                    if (parseGithub(resp)) {
                        source = Source.GITHUB;
                        ok = true;
                    }
                } catch (Exception ignored) {
                }
            }
            checked = ok;
            checking = false;
            if (!ok) {
                error = "无法连接更新服务器";
                TgzjdvChatMod.LOGGER.info("[TGZJDV's Chat] 更新检查失败: {}", error);
            } else {
                TgzjdvChatMod.LOGGER.info("[TGZJDV's Chat] 更新检查完成，来源: {}, 最新版本: {}", source, latestVersion);
            }
            // 完成后在客户端线程执行回调 + 自动提示
            Minecraft mc = Minecraft.getInstance();
            final boolean success = ok;
            mc.execute(() -> {
                if (done != null) {
                    done.run();
                }
                if (success && hasUpdate() && mc.player != null) {
                    mc.player.sendSystemMessage(Component.literal(
                            "\u00a7e[TGZJDV's Chat] \u00a7a\u53d1\u73b0\u65b0\u7248\u672c \u00a7b" + latestVersion
                                    + "\u00a7a\uff08\u5f53\u524d " + getCurrentVersion() + "\uff09\uff0c"
                                    + "\u00a77\u6765\u81ea " + source.displayName + "\uff0c\u8bf7\u5230\u8bbe\u7f6e\u9875\u66f4\u65b0"));
                }
            });
        }, "TGC-UpdateCheck");
        t.setDaemon(true);
        t.start();
    }

    /** 发送 GET 请求，返回响应体（非 200 或网络错误时抛出异常） */
    private static String httpGet(String url) throws Exception {
        java.net.HttpURLConnection conn = (java.net.HttpURLConnection) new java.net.URL(url).openConnection();
        conn.setConnectTimeout(10000);
        conn.setReadTimeout(20000);
        conn.setRequestProperty("User-Agent", "TGZJDV-Chat/" + getCurrentVersion());
        conn.setRequestProperty("Accept", "application/json");
        int code = conn.getResponseCode();
        if (code != 200) {
            throw new java.io.IOException("HTTP " + code);
        }
        return new String(conn.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
    }

    /**
     * 解析 Modrinth API 响应（JSON 数组，多个版本按日期倒序）
     * 找到 game_versions 匹配当前游戏版本的最新版本，提取其主文件下载地址
     */
    private static boolean parseModrinth(String resp) {
        String mcVer = getMinecraftVersion();
        int pos = 0;
        while (true) {
            int vnIdx = resp.indexOf("\"version_number\":", pos);
            if (vnIdx < 0) {
                break;
            }
            int next = resp.indexOf("\"version_number\":", vnIdx + 1);
            if (next < 0) {
                next = resp.length();
            }
            String block = resp.substring(vnIdx, next);
            String v = normalizeVersion(extractJsonString(block, "version_number"));
            // 该版本是否支持当前游戏版本（game_versions 数组中包含）
            boolean matched = mcVer.isEmpty() || block.contains("\"" + mcVer + "\"");
            if (matched && v != null) {
                String url = extractModrinthPrimaryUrl(block);
                if (url != null) {
                    latestVersion = v;
                    latestDownloadUrl = url;
                    latestFileName = fileNameFromUrl(url);
                    latestChangelog = truncate(cleanChangelog(extractJsonString(block, "changelog")));
                    return true;
                }
            }
            pos = next;
        }
        return false;
    }

    /** 从 Modrinth 版本块中提取 primary=true 文件的下载地址 */
    private static String extractModrinthPrimaryUrl(String block) {
        int primIdx = block.indexOf("\"primary\":true");
        if (primIdx < 0) {
            return null;
        }
        int urlIdx = block.lastIndexOf("\"url\":\"", primIdx);
        if (urlIdx < 0) {
            return null;
        }
        int start = urlIdx + "\"url\":\"".length();
        int end = block.indexOf('"', start);
        return end > start ? block.substring(start, end) : null;
    }

    /**
     * 解析 GitHub Releases API 响应（tag_name 形如 v1.4.0，body 为更新日志）
     * 在 assets 中寻找文件名匹配当前游戏版本的 jar，提取其下载地址
     */
    private static boolean parseGithub(String resp) {
        String tag = extractJsonString(resp, "tag_name");
        String body = extractJsonString(resp, "body");
        String v = normalizeVersion(tag);
        if (v == null) {
            return false;
        }
        latestVersion = v;
        latestChangelog = truncate(cleanChangelog(body));
        // 匹配当前游戏版本的资产文件（如 tgzjdvchat-mc26.1.2-1.4.0.jar）
        String mcVer = getMinecraftVersion();
        String prefix = "tgzjdvchat-mc" + mcVer + "-";
        String url = findGithubAssetUrl(resp, prefix);
        if (url == null && !mcVer.isEmpty()) {
            // 精确版本未匹配时，尝试带 "mc" 前缀的任意匹配（文件名含 mc 版本）
            url = findGithubAssetUrl(resp, "tgzjdvchat-mc");
        }
        if (url != null) {
            latestDownloadUrl = url;
            latestFileName = fileNameFromUrl(url);
        }
        return true;
    }

    /** 在 GitHub releases 响应中查找 name 以 prefix 开头的 asset 的下载地址 */
    private static String findGithubAssetUrl(String resp, String prefix) {
        int idx = 0;
        while (true) {
            int nameIdx = resp.indexOf("\"name\":", idx);
            if (nameIdx < 0) {
                break;
            }
            int nameStart = resp.indexOf('"', nameIdx + "\"name\":".length()) + 1;
            if (nameStart <= 0) {
                break;
            }
            int nameEnd = resp.indexOf('"', nameStart);
            if (nameEnd < 0) {
                break;
            }
            String name = resp.substring(nameStart, nameEnd);
            int urlIdx = resp.indexOf("\"browser_download_url\":", nameEnd);
            if (urlIdx < 0) {
                break;
            }
            int urlStart = resp.indexOf('"', urlIdx + "\"browser_download_url\":".length()) + 1;
            int urlEnd = resp.indexOf('"', urlStart);
            if (urlEnd > urlStart && name.startsWith(prefix) && name.endsWith(".jar")) {
                return resp.substring(urlStart, urlEnd);
            }
            idx = nameEnd;
        }
        return null;
    }

    /** 从下载地址中提取文件名 */
    private static String fileNameFromUrl(String url) {
        int idx = url.lastIndexOf('/');
        String name = idx >= 0 ? url.substring(idx + 1) : url;
        // 去除可能的查询参数
        int q = name.indexOf('?');
        return q >= 0 ? name.substring(0, q) : name;
    }

    /** 获取当前 Minecraft 版本（如 26.1.2） */
    public static String getMinecraftVersion() {
        try {
            return Minecraft.getInstance().getLaunchedVersion();
        } catch (Exception e) {
            return "";
        }
    }

    /** 获取当前模组 jar 文件的路径（运行环境下的 mods 目录 jar） */
    public static java.nio.file.Path getModJarPath() {
        try {
            return net.fabricmc.loader.api.FabricLoader.getInstance()
                    .getModContainer(TgzjdvChatMod.MOD_ID).get()
                    .getOrigin().getPaths().get(0);
        } catch (Exception e) {
            return null;
        }
    }

    /** 从 JSON 字符串中提取指定 key 的字符串值（未找到返回 null） */
    private static String extractJsonString(String resp, String key) {
        String k = "\"" + key + "\":\"";
        int idx = resp.indexOf(k);
        if (idx < 0) {
            return null;
        }
        int start = idx + k.length();
        int end = resp.indexOf("\"", start);
        if (end <= start) {
            return null;
        }
        return resp.substring(start, end);
    }

    /** 规范化版本号：去除前导非数字字符（如 v 前缀）与构建元数据/预发布后缀 */
    private static String normalizeVersion(String raw) {
        if (raw == null) {
            return null;
        }
        String ver = raw.trim();
        int i = 0;
        while (i < ver.length() && !Character.isDigit(ver.charAt(i))) {
            i++;
        }
        if (i > 0) {
            ver = ver.substring(i);
        }
        if (ver.isEmpty()) {
            return null;
        }
        // 截断到纯数字点号段（去掉 +build / -prerelease 后缀）
        int cut = ver.indexOf('+');
        if (cut < 0) {
            cut = ver.indexOf('-');
        }
        if (cut >= 0) {
            ver = ver.substring(0, cut);
        }
        return ver.isEmpty() ? null : ver;
    }

    /** 清理更新日志：去除 Markdown 标记与转义换行，压缩空白 */
    private static String cleanChangelog(String raw) {
        if (raw == null) {
            return null;
        }
        String s = raw.replaceAll("(?s)```.*?```", " ")
                .replaceAll("[#*>`|]", " ")
                .replace("\\n", " ").replace("\\r", "")
                .replaceAll("\\s+", " ").trim();
        return s.isEmpty() ? null : s;
    }

    /** 截断过长文本 */
    private static String truncate(String s) {
        if (s == null || s.length() <= 300) {
            return s;
        }
        return s.substring(0, 300);
    }

    /** 是否有可用更新 */
    public static boolean hasUpdate() {
        if (!checked || latestVersion == null || latestVersion.isEmpty()) {
            return false;
        }
        return compareVersions(latestVersion, getCurrentVersion()) > 0;
    }

    /** 最新版本号 */
    public static String getLatestVersion() {
        return latestVersion;
    }

    /** 最新版本更新日志 */
    public static String getLatestChangelog() {
        return latestChangelog;
    }

    /** 匹配当前游戏版本的最新版下载地址（无则 null） */
    public static String getLatestDownloadUrl() {
        return latestDownloadUrl;
    }

    /** 下载文件名（如 tgzjdvchat-mc26.1.2-1.4.1.jar，无则 null） */
    public static String getLatestFileName() {
        return latestFileName;
    }

    /** 是否有可用的下载地址 */
    public static boolean hasDownload() {
        return latestDownloadUrl != null && !latestDownloadUrl.isEmpty();
    }

    /** 是否已检查过（无论成功与否） */
    public static boolean isChecked() {
        return checked;
    }

    /** 是否正在检查中 */
    public static boolean isChecking() {
        return checking;
    }

    /** 更新来源（检查成功时非空） */
    public static Source getSource() {
        return source;
    }

    /** 检查失败原因（失败时非空） */
    public static String getError() {
        return error;
    }

    /** 打开更新页面：按来源跳转（GitHub 回退时打开 GitHub Releases，否则打开 Modrinth） */
    public static void openUpdatePage() {
        String url = source == Source.GITHUB ? GITHUB_RELEASES_URL : MODRINTH_URL;
        try {
            net.minecraft.util.Util.getPlatform().openUri(java.net.URI.create(url));
        } catch (Exception e) {
            TgzjdvChatMod.LOGGER.warn("[TGZJDV's Chat] 打开更新页面失败: {}", e.getMessage());
        }
    }

    /** 比较版本号，返回正数表示 a 更新 */
    private static int compareVersions(String a, String b) {
        String[] pa = a.split("[+\\-]")[0].split("\\.");
        String[] pb = b.split("[+\\-]")[0].split("\\.");
        int len = Math.max(pa.length, pb.length);
        for (int i = 0; i < len; i++) {
            int na = i < pa.length ? parseIntSafe(pa[i]) : 0;
            int nb = i < pb.length ? parseIntSafe(pb[i]) : 0;
            if (na != nb) {
                return na > nb ? 1 : -1;
            }
        }
        return 0;
    }

    private static int parseIntSafe(String s) {
        try {
            return Integer.parseInt(s.trim());
        } catch (Exception e) {
            return 0;
        }
    }
}
