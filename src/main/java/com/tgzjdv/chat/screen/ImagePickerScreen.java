package com.tgzjdv.chat.screen;

import com.tgzjdv.chat.image.ImageCache;
import com.tgzjdv.chat.image.ImageUploader;
import com.tgzjdv.chat.render.SideChatRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

/**
 * 文件管理器式图片选择界面
 * - 顶部：地址栏 + 后退/前进/返回上层按钮
 * - 左侧：驱动器 + 功能文件夹（图片、视频、下载等）
 * - 右侧：当前目录的文件/文件夹列表（带滚动条）
 */
public class ImagePickerScreen extends Screen {

    private static final Component TITLE = Component.literal("选择图片");

    // ===== 布局 =====
    private static final int SIDE_W = 130;       // 侧边栏宽
    private static final int TOP_H = 26;         // 顶部控制栏高
    private static final int ENTRY_H = 40;       // 条目高
    private static final int GRID_COLS = 4;      // 网格列数

    // ===== 导航状态 =====
    private File currentDir;
    private final List<File> history = new ArrayList<>();
    private int historyIndex = -1;
    private List<File> entries = new ArrayList<>();
    private final List<Boolean> entryIsImage = new ArrayList<>();
    private final List<Boolean> entryIsDir = new ArrayList<>();
    private final List<Boolean> entryLoaded = new ArrayList<>();
    private final List<Identifier> entryTextures = new ArrayList<>();
    private final List<String> entryPaths = new ArrayList<>();
    private int scrollOffset = 0;

    // ===== 侧边栏 =====
    private final List<String> sideLabels = new ArrayList<>();
    private final List<String> sidePaths = new ArrayList<>();
    // PowerShell 获取的真实路径（volatile，渲染线程懒应用）
    private volatile String[] realFolderPaths = null;
    private boolean realPathsApplied = false;

    public ImagePickerScreen() {
        super(TITLE);
    }

    @Override
    protected void init() {
        // 侧边栏：驱动器 + 功能文件夹
        sideLabels.clear();
        sidePaths.clear();
        for (File root : File.listRoots()) {
            sideLabels.add(root.getPath());
            sidePaths.add(root.getPath());
        }
        String home = System.getProperty("user.home", ".");
        // 先按默认路径（user.home）
        addSidebarFolder("图片", home + "/Pictures");
        addSidebarFolder("视频", home + "/Videos");
        addSidebarFolder("下载", home + "/Downloads");
        addSidebarFolder("桌面", home + "/Desktop");
        addSidebarFolder("音乐", home + "/Music");
        addSidebarFolder("文档", home + "/Documents");
        // 异步获取系统真实路径（用户可能更改了保存位置），更新侧边栏
        loadRealFolderPaths();

        // 初始目录：images 文件夹（不存在则主目录）
        File images = new File(Minecraft.getInstance().gameDirectory, "images");
        navigateTo(images.exists() ? images : new File(home));
    }

    /** 后台获取 Windows 已知文件夹真实路径（跟随用户更改的保存位置） */
    private void loadRealFolderPaths() {
        Thread t = new Thread(() -> {
            try {
                String script = "$ProgressPreference='SilentlyContinue';"
                        + "[Console]::OutputEncoding=[Text.Encoding]::UTF8;"
                        + "[Environment]::GetFolderPath('MyPictures');"
                        + "[Environment]::GetFolderPath('MyVideos');"
                        + "[Environment]::GetFolderPath('Desktop');"
                        + "[Environment]::GetFolderPath('MyMusic');"
                        + "[Environment]::GetFolderPath('MyDocuments');"
                        + "$u=[Environment]::GetFolderPath('UserProfile');"
                        + "Write-Output ($u+'\\Downloads')";
                // 用 -EncodedCommand 避免引号/编码问题（UTF-16LE + Base64）
                String encoded = java.util.Base64.getEncoder().encodeToString(
                        script.getBytes(java.nio.charset.StandardCharsets.UTF_16LE));
                Process p = new ProcessBuilder("powershell", "-NoProfile", "-EncodedCommand", encoded)
                        .redirectErrorStream(true).start();
                byte[] bytes = p.getInputStream().readAllBytes();
                String out = new String(bytes, java.nio.charset.StandardCharsets.UTF_8).trim();
                if (out.contains("\uFFFD")) {
                    try {
                        out = new String(bytes, java.nio.charset.Charset.forName("GBK")).trim();
                    } catch (Exception ignored) {
                    }
                }
                // 用正则提取所有盘符路径（忽略 CLIXML 头 / XML 进度尾等杂质）
                java.util.List<String> paths = new java.util.ArrayList<>();
                java.util.regex.Matcher m = java.util.regex.Pattern
                        .compile("([A-Za-z]:\\\\[^\\r\\n<|]+)")
                        .matcher(out);
                while (m.find() && paths.size() < 6) {
                    paths.add(m.group(1).trim());
                }
                com.tgzjdv.chat.TgzjdvChatMod.LOGGER.info("[TGC文件夹] 解析到 {} 个路径: {}", paths.size(), paths);
                if (paths.size() >= 6) {
                    // 存储真实路径（渲染线程懒应用，避免 execute 延迟/并发问题）
                    realFolderPaths = new String[]{paths.get(0), paths.get(1), paths.get(2), paths.get(3), paths.get(4), paths.get(5)};
                    com.tgzjdv.chat.TgzjdvChatMod.LOGGER.info("[TGC文件夹] 真实路径已就绪");
                }
            } catch (Exception e) {
                com.tgzjdv.chat.TgzjdvChatMod.LOGGER.info("[TGC文件夹] 获取异常 {}", e.getMessage());
            }
        }, "TGC-Folders");
        t.setDaemon(true);
        t.start();
    }

    /** 渲染时应用真实路径（仅一次，渲染线程执行，避免并发修改） */
    private void applyRealPathsIfReady() {
        if (realPathsApplied || realFolderPaths == null) {
            return;
        }
        realPathsApplied = true;
        String[] paths = realFolderPaths;
        if (paths.length >= 6) {
            updateSidebarRealPaths(paths[0], paths[1], paths[2], paths[3], paths[4], paths[5]);
            com.tgzjdv.chat.TgzjdvChatMod.LOGGER.info("[TGC文件夹] 已应用真实路径到侧边栏，当前标签={}", sideLabels);
        }
    }

    /** 用真实路径更新（或添加）侧边栏功能文件夹 */
    private void updateSidebarRealPaths(String img, String vid, String dl, String desk, String mus, String doc) {
        String[] labels = {"图片", "视频", "下载", "桌面", "音乐", "文档"};
        String[] paths = {img, vid, dl, desk, mus, doc};
        for (int i = 0; i < labels.length; i++) {
            if (paths[i] == null || paths[i].isEmpty()) {
                continue;
            }
            File f;
            try {
                f = new File(paths[i]);
                if (!f.isDirectory()) {
                    continue;
                }
            } catch (Exception e) {
                continue;
            }
            int idx = sideLabels.indexOf(labels[i]);
            if (idx >= 0) {
                // 已存在：更新路径
                sidePaths.set(idx, paths[i]);
            } else {
                // 不存在：添加到侧边栏（驱动器之后）
                int insertIdx = File.listRoots().length;
                if (insertIdx > sideLabels.size()) {
                    insertIdx = sideLabels.size();
                }
                sideLabels.add(insertIdx, labels[i]);
                sidePaths.add(insertIdx, paths[i]);
            }
        }
    }

    private void addSidebarFolder(String label, String path) {
        File f = new File(path);
        if (f.isDirectory()) {
            sideLabels.add(label);
            sidePaths.add(path);
        }
    }

    /** 导航到目录（记录历史） */
    private void navigateTo(File dir) {
        if (dir == null || !dir.isDirectory()) {
            return;
        }
        try {
            // 前进历史截断
            while (history.size() > historyIndex + 1 && !history.isEmpty()) {
                history.remove(history.size() - 1);
            }
            history.add(dir);
            historyIndex++;
            loadEntries(dir);
        } catch (Exception e) {
            // 访问失败：回退历史
            if (historyIndex > 0) {
                historyIndex--;
                history.remove(history.size() - 1);
            }
        }
    }

    /** 加载目录条目 */
    private void loadEntries(File dir) {
        currentDir = dir;
        entries.clear();
        entryIsImage.clear();
        entryIsDir.clear();
        entryLoaded.clear();
        entryTextures.clear();
        entryPaths.clear();

        File[] files = null;
        try {
            files = dir.listFiles();
        } catch (Exception ignored) {
            files = null;
        }
        if (files != null) {
            try {
                Arrays.sort(files, (a, b) -> {
                    boolean aDir = a.isDirectory();
                    boolean bDir = b.isDirectory();
                    if (aDir != bDir) {
                        return aDir ? -1 : 1;
                    }
                    return a.getName().compareToIgnoreCase(b.getName());
                });
            } catch (Exception ignored) {
            }
            int loadCount = 0;
            for (File f : files) {
                // 跳过不可访问/系统特殊文件
                boolean isDir;
                try {
                    isDir = f.isDirectory();
                } catch (Exception e) {
                    continue;
                }
                if (!isDir && !isImageFile(f)) {
                    // 非图片非文件夹也显示（文件名）
                }
                if (!isDir && !isReadableFile(f)) {
                    continue;
                }
                entries.add(f);
                boolean img = !isDir && isImageFile(f);
                entryIsImage.add(img);
                entryIsDir.add(isDir);
                entryLoaded.add(false);
                entryTextures.add(null);
                entryPaths.add(f.getAbsolutePath());
                // 限制预加载缩略图数量（避免大量图片导致卡顿）
                if (img && loadCount < 60) {
                    loadCount++;
                }
            }
        }
        scrollOffset = 0;
        // 预加载图片缩略图（限制数量）
        int loaded = 0;
        for (int i = 0; i < entries.size() && loaded < 60; i++) {
            if (entryIsImage.get(i)) {
                final int idx = i;
                final String path = entryPaths.get(i);
                ImageCache.requestLocalImage(path, id -> {
                    if (id != null && idx < entryLoaded.size()) {
                        entryLoaded.set(idx, true);
                        entryTextures.set(idx, id);
                    }
                });
                loaded++;
            }
        }
    }

    /** 判断文件是否可读（避免系统保护文件导致异常） */
    private static boolean isReadableFile(File f) {
        try {
            return f.exists() && f.canRead();
        } catch (Exception e) {
            return false;
        }
    }

    private static boolean isImageFile(File f) {
        if (!f.isFile()) {
            return false;
        }
        String n = f.getName().toLowerCase();
        return n.endsWith(".png") || n.endsWith(".jpg") || n.endsWith(".jpeg")
                || n.endsWith(".gif") || n.endsWith(".bmp") || n.endsWith(".webp");
    }

    // ===== 渲染 =====

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        graphics.fill(0, 0, this.width, this.height, 0xD0101318);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        // ===== 顶部控制栏 =====
        graphics.fill(0, 0, this.width, TOP_H, 0xFF252A33);
        int btnY = 3;
        int btnH = 20;
        // 后退
        renderNavButton(graphics, mouseX, mouseY, 4, btnY, 22, btnH, "\u2190", canGoBack());
        // 前进
        renderNavButton(graphics, mouseX, mouseY, 28, btnY, 22, btnH, "\u2192", canGoForward());
        // 返回上层
        renderNavButton(graphics, mouseX, mouseY, 52, btnY, 22, btnH, "\u2191", currentDir != null && currentDir.getParentFile() != null);
        // 地址栏
        int addrX = 80;
        int addrW = this.width - addrX - 34;
        graphics.fill(addrX, btnY, addrX + addrW, btnY + btnH, 0xFF2E343E);
        graphics.horizontalLine(addrX, addrX + addrW, btnY, 0x66AAAAAA);
        graphics.horizontalLine(addrX, addrX + addrW, btnY + btnH, 0x66AAAAAA);
        graphics.verticalLine(addrX, btnY, btnY + btnH, 0x66AAAAAA);
        graphics.verticalLine(addrX + addrW, btnY, btnY + btnH, 0x66AAAAAA);
        String path = currentDir == null ? "" : currentDir.getAbsolutePath();
        graphics.text(this.font, path, addrX + 4, btnY + 5, 0xFFFFFFFF, false);

        // 关闭按钮（右上角 X）
        int closeX = this.width - 24;
        int closeY = 2;
        boolean closeHover = mouseX >= closeX && mouseX <= closeX + 22 && mouseY >= closeY && mouseY <= closeY + 22;
        graphics.fill(closeX, closeY, closeX + 22, closeY + 22, closeHover ? 0xFF8B3D3D : 0xFF6B2E2E);
        graphics.centeredText(this.font, "\u2715", closeX + 11, closeY + (22 - this.font.lineHeight) / 2, 0xFFFFFFFF);

        // ===== 左侧边栏 =====
        // 渲染前检查：真实路径就绪则应用（渲染线程安全）
        applyRealPathsIfReady();
        int sideRight = SIDE_W;
        graphics.fill(0, TOP_H, sideRight, this.height, 0xFF1E232B);
        int sideY = TOP_H + 4;
        for (int i = 0; i < sideLabels.size(); i++) {
            String label = sideLabels.get(i);
            String path2 = sidePaths.get(i);
            boolean hover = mouseX >= 0 && mouseX <= sideRight && mouseY >= sideY && mouseY <= sideY + 18;
            boolean selected = currentDir != null && currentDir.getAbsolutePath().equals(new File(path2).getAbsolutePath());
            if (hover || selected) {
                graphics.fill(0, sideY, sideRight, sideY + 18, 0x50222222);
            }
            graphics.text(this.font, label, 8, sideY + 5, selected ? 0xFF4FC3F7 : 0xFFCCCCCC, false);
            sideY += 19;
        }

        // ===== 右侧文件列表 =====
        int listLeft = SIDE_W + 4;
        int listTop = TOP_H + 4;
        int listRight = this.width - 8;
        int listBottom = this.height - 8;
        int listW = listRight - listLeft;

        if (entries.isEmpty()) {
            graphics.centeredText(this.font, "（空文件夹）", (listLeft + listRight) / 2, this.height / 2, 0xFF8A9199);
            super.extractRenderState(graphics, mouseX, mouseY, delta);
            return;
        }

        // 网格布局
        int cellW = listW / GRID_COLS;
        int cellH = ENTRY_H + 24;
        int rows = (entries.size() + GRID_COLS - 1) / GRID_COLS;
        int gridH = rows * cellH;
        int viewportH = listBottom - listTop;
        scrollOffset = Math.max(0, Math.min(scrollOffset, Math.max(0, gridH - viewportH)));

        // 滚动条
        if (gridH > viewportH) {
            int sbX = listRight - 6;
            int sbW = 4;
            int trackH = viewportH;
            int thumbH = Math.max(14, (int) (trackH * (viewportH / (float) gridH)));
            float ratio = gridH <= viewportH ? 0 : scrollOffset / (float) (gridH - viewportH);
            int thumbY = listTop + (int) ((trackH - thumbH) * ratio);
            graphics.fill(sbX - sbW, listTop, sbX, listBottom, 0x1FFFFFFF);
            graphics.fill(sbX - sbW, thumbY, sbX, thumbY + thumbH, 0x7FFFFFFF);
        }

        graphics.enableScissor(listLeft, listTop, listRight, listBottom);

        for (int i = 0; i < entries.size(); i++) {
            int row = i / GRID_COLS;
            int col = i % GRID_COLS;
            int x = listLeft + col * cellW;
            int y = listTop + row * cellH - scrollOffset;
            if (y + cellH < listTop || y > listBottom) {
                continue;
            }
            File f = entries.get(i);
            boolean hover = mouseX >= x && mouseX <= x + cellW - 4 && mouseY >= y && mouseY <= y + cellH;

            // 条目背景
            if (hover) {
                graphics.fill(x, y, x + cellW - 4, y + cellH, 0x50222222);
            }

            if (i < entryIsDir.size() && entryIsDir.get(i)) {
                // 文件夹图标
                graphics.fill(x + 6, y + 6, x + 30, y + 30, 0xFFE8B339);
                graphics.fill(x + 6, y + 6, x + 16, y + 12, 0xFFF1C40F);
                graphics.horizontalLine(x + 6, x + 30, y + 6, 0x33FFFFFF);
                graphics.horizontalLine(x + 6, x + 30, y + 30, 0x33FFFFFF);
                graphics.verticalLine(x + 6, y + 6, y + 30, 0x33FFFFFF);
                graphics.verticalLine(x + 30, y + 6, y + 30, 0x33FFFFFF);
                graphics.text(this.font, truncate(f.getName(), cellW - 40), x + 34, y + 12, 0xFFE8D9A0, false);
            } else if (entryIsImage.get(i)) {
                // 图片缩略图
                int thumbX = x + 4;
                int thumbY = y + 4;
                int thumbW = 36;
                int thumbH = 36;
                graphics.fill(thumbX, thumbY, thumbX + thumbW, thumbY + thumbH, 0xFF252A33);
                if (entryLoaded.get(i) && entryTextures.get(i) != null) {
                    int[] dims = ImageCache.getDimensions(entryPaths.get(i));
                    int tw = dims != null && dims[0] > 0 ? dims[0] : 16;
                    int th = dims != null && dims[1] > 0 ? dims[1] : 9;
                    float scale = Math.min((thumbW - 2) / (float) tw, (thumbH - 2) / (float) th);
                    int dw = Math.max(1, (int) (tw * scale));
                    int dh = Math.max(1, (int) (th * scale));
                    int dx = thumbX + (thumbW - dw) / 2;
                    int dy = thumbY + (thumbH - dh) / 2;
                    graphics.blit(RenderPipelines.GUI_TEXTURED, entryTextures.get(i), dx, dy, 0.0f, 0.0f, dw, dh, tw, th, tw, th);
                } else {
                    graphics.centeredText(this.font, "...", thumbX + thumbW / 2, thumbY + thumbH / 2 - 4, 0xFF9AA0A6);
                }
                graphics.text(this.font, truncate(f.getName(), cellW - 48), x + 44, y + 12, 0xFFCCCCCC, false);
            } else {
                // 其他文件
                graphics.fill(x + 8, y + 8, x + 32, y + 32, 0xFF444A54);
                graphics.horizontalLine(x + 8, x + 32, y + 8, 0x33FFFFFF);
                graphics.horizontalLine(x + 8, x + 32, y + 32, 0x33FFFFFF);
                graphics.verticalLine(x + 8, y + 8, y + 32, 0x33FFFFFF);
                graphics.verticalLine(x + 32, y + 8, y + 32, 0x33FFFFFF);
                graphics.text(this.font, truncate(f.getName(), cellW - 40), x + 36, y + 12, 0xFF777777, false);
            }
        }

        graphics.disableScissor();
        super.extractRenderState(graphics, mouseX, mouseY, delta);
    }

    private void renderNavButton(GuiGraphicsExtractor graphics, int mouseX, int mouseY, int x, int y, int w, int h, String text, boolean enabled) {
        boolean hover = enabled && mouseX >= x && mouseX <= x + w && mouseY >= y && mouseY <= y + h;
        graphics.fill(x, y, x + w, y + h, hover ? 0xFF3A4048 : 0xFF2A2F38);
        graphics.horizontalLine(x, x + w, y, 0x66AAAAAA);
        graphics.horizontalLine(x, x + w, y + h, 0x66AAAAAA);
        graphics.verticalLine(x, y, y + h, 0x66AAAAAA);
        graphics.verticalLine(x + w, y, y + h, 0x66AAAAAA);
        graphics.centeredText(this.font, text, x + w / 2, y + 4, enabled ? 0xFFFFFFFF : 0xFF555555);
    }

    private String truncate(String s, int maxW) {
        while (this.font.width(s) > maxW && s.length() > 1) {
            s = s.substring(0, s.length() - 1);
        }
        if (this.font.width(s) > maxW) {
            return s;
        }
        return s;
    }

    private boolean canGoBack() {
        return historyIndex > 0;
    }

    private boolean canGoForward() {
        return historyIndex >= 0 && historyIndex < history.size() - 1;
    }

    // ===== 交互 =====

    @Override
    public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event, boolean doubleClick) {
        if (event.button() != 0) {
            return super.mouseClicked(event, doubleClick);
        }
        int mx = (int) event.x();
        int my = (int) event.y();

        // 关闭按钮（右上角 X）
        if (mx >= this.width - 24 && mx <= this.width - 2 && my >= 2 && my <= 24) {
            SideChatRenderer.setScreenCompat(null);
            return true;
        }

        // 顶部按钮
        int btnY = 3;
        int btnH = 20;
        if (my >= btnY && my <= btnY + btnH) {
            if (mx >= 4 && mx <= 26 && canGoBack()) {
                historyIndex--;
                loadEntries(history.get(historyIndex));
                return true;
            }
            if (mx >= 28 && mx <= 50 && canGoForward()) {
                historyIndex++;
                loadEntries(history.get(historyIndex));
                return true;
            }
            if (mx >= 52 && mx <= 74 && currentDir != null && currentDir.getParentFile() != null) {
                navigateTo(currentDir.getParentFile());
                return true;
            }
        }

        // 侧边栏
        if (mx <= SIDE_W && my > TOP_H) {
            int sideY = TOP_H + 4;
            for (int i = 0; i < sideLabels.size(); i++) {
                if (my >= sideY && my <= sideY + 18) {
                    navigateTo(new File(sidePaths.get(i)));
                    return true;
                }
                sideY += 19;
            }
        }

        // 文件列表
        int listLeft = SIDE_W + 4;
        int listTop = TOP_H + 4;
        int listRight = this.width - 8;
        int listW = listRight - listLeft;
        int cellW = listW / GRID_COLS;
        int cellH = ENTRY_H + 24;
        if (mx > listLeft && mx < listRight && my > listTop) {
            int row = (my - listTop + scrollOffset) / cellH;
            int col = (mx - listLeft) / cellW;
            int idx = row * GRID_COLS + col;
            if (idx >= 0 && idx < entries.size()) {
                File f = entries.get(idx);
                if (f.isDirectory()) {
                    navigateTo(f);
                    return true;
                }
                if (isImageFile(f)) {
                    uploadAndSend(f);
                    return true;
                }
            }
        }

        // 滚动条点击
        int listBottom = this.height - 8;
        int viewportH = listBottom - listTop;
        int rows = (entries.size() + GRID_COLS - 1) / GRID_COLS;
        int gridH = rows * cellH;
        if (gridH > viewportH) {
            int sbX = listRight - 6;
            if (mx >= sbX - 6 && mx <= sbX + 2 && my >= listTop && my <= listBottom) {
                float t = (my - listTop) / (float) viewportH;
                scrollOffset = (int) ((gridH - viewportH) * t);
                return true;
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        scrollOffset -= (int) (verticalAmount * 30);
        return true;
    }

    /** 选择图片：后台上传并发送 */
    private void uploadAndSend(File file) {
        Minecraft mc = Minecraft.getInstance();
        final boolean privateChannel = SideChatRenderer.isPrivateChannel();
        final String privateTarget = SideChatRenderer.getPrivateTarget();
        SideChatRenderer.setScreenCompat(null);
        SideChatRenderer.resetPanelFade();
        SideChatRenderer.setScreenCompat(new net.minecraft.client.gui.screens.ChatScreen("", false));
        com.tgzjdv.chat.image.UploadState.start(file.getName());
        Thread t = new Thread(() -> {
            String url = ImageUploader.upload(file,
                    com.tgzjdv.chat.image.UploadState::setProgress,
                    com.tgzjdv.chat.image.UploadState::fail);
            mc.execute(() -> {
                if (url != null) {
                    if (mc.player != null) {
                        String playerName = mc.player.getGameProfile().name();
                        String urlText;
                        if (com.tgzjdv.chat.config.ChatAuthConfig.isImageSafeMode()) {
                            urlText = com.tgzjdv.chat.image.ImageCodec.encodeUrlSafe(url);
                        } else {
                            urlText = url;
                        }
                        String msg = "[\u56fe\u7247] <" + playerName + "> \u9700\u8981\u5b89\u88c5TGZJDV's Chat\u6a21\u7ec4\u624d\u80fd\u67e5\u770b\u6b64\u56fe\u7247: " + urlText;
                        if (privateChannel && privateTarget != null) {
                            mc.player.connection.sendCommand("tell " + privateTarget + " " + msg);
                        } else {
                            mc.player.connection.sendChat(msg);
                        }
                    }
                    com.tgzjdv.chat.image.UploadState.finish();
                } else if (!com.tgzjdv.chat.image.UploadState.isError()) {
                    com.tgzjdv.chat.image.UploadState.fail("上传失败");
                }
            });
        }, "TGC-Upload");
        t.setDaemon(true);
        t.start();
    }

    /** 打开图片选择器（首次需同意上传协议） */
    public static void open() {
        if (com.tgzjdv.chat.config.ChatAuthConfig.isImageUploadAgreed()) {
            SideChatRenderer.setScreenCompat(new ImagePickerScreen());
        } else {
            SideChatRenderer.setScreenCompat(new ImageAgreementScreen());
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public boolean isAllowedInPortal() {
        return false;
    }
}
