package com.tgzjdv.chat.screen;

import com.tgzjdv.chat.render.SideChatRenderer;
import com.tgzjdv.chat.update.UpdateChecker;
import com.tgzjdv.chat.update.UpdateDownloader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * 游戏内更新界面
 * 流程：确认 -> 下载（进度条）-> 应用替换 jar -> 关闭游戏
 */
public class UpdateScreen extends Screen {

    private enum State { CONFIRM, DOWNLOADING, FAILED }

    private static final int PANEL_W = 380;
    private static final int PANEL_H = 150;

    private State state = State.CONFIRM;
    private volatile float progress = 0f;
    private volatile long downloaded = 0;
    private volatile long total = 0;
    private String error = null;
    private boolean applying = false;
    private Thread downloadThread = null;

    private record Rect(int x, int y, int w, int h) {
    }

    public UpdateScreen() {
        super(Component.literal("TGZJDV 模组更新"));
    }

    private int left() {
        return (this.width - PANEL_W) / 2;
    }

    private int top() {
        return (this.height - PANEL_H) / 2;
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        graphics.fill(0, 0, this.width, this.height, 0xAA101318);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        int left = left();
        int top = top();
        // 面板背景
        graphics.fill(left, top, left + PANEL_W, top + PANEL_H, 0xF02A2F38);
        graphics.horizontalLine(left, left + PANEL_W, top, 0xFF5A6068);
        graphics.horizontalLine(left, left + PANEL_W, top + PANEL_H, 0xFF5A6068);
        graphics.verticalLine(left, top, top + PANEL_H, 0xFF5A6068);
        graphics.verticalLine(left + PANEL_W, top, top + PANEL_H, 0xFF5A6068);

        switch (state) {
            case CONFIRM -> renderConfirm(graphics, left, top, mouseX, mouseY);
            case DOWNLOADING -> renderDownloading(graphics, left, top);
            case FAILED -> renderFailed(graphics, left, top, mouseX, mouseY);
        }
        super.extractRenderState(graphics, mouseX, mouseY, delta);
    }

    private void renderConfirm(GuiGraphicsExtractor g, int left, int top, int mouseX, int mouseY) {
        String ver = UpdateChecker.getLatestVersion();
        String src = UpdateChecker.getSource() != null ? UpdateChecker.getSource().displayName : "?";
        g.centeredText(this.font, "\u00a76\u53d1\u73b0\u65b0\u7248\u672c \u00a7b" + ver, left + PANEL_W / 2, top + 16, 0xFFFFFFFF);
        g.centeredText(this.font, "\u00a77\u5f53\u524d\u7248\u672c\uff1a\u00a7f" + UpdateChecker.getCurrentVersion()
                + "   \u00a77\u6765\u81ea\uff1a\u00a7f" + src, left + PANEL_W / 2, top + 34, 0xFFFFFFFF);
        g.centeredText(this.font, "\u00a7c\u66f4\u65b0\u5b8c\u6210\u540e\u5c06\u81ea\u52a8\u5173\u95ed\u6e38\u620f\uff0c\u786e\u8ba4\u540e\u65e0\u6cd5\u53d6\u6d88\uff01",
                left + PANEL_W / 2, top + 52, 0xFFFFFFFF);
        int btnY = top + 104;
        drawButton(g, cancelRect(left, btnY), "\u53d6\u6d88", false, mouseX, mouseY);
        drawButton(g, okRect(left, btnY), "\u786e\u8ba4\u66f4\u65b0", true, mouseX, mouseY);
    }

    private void renderDownloading(GuiGraphicsExtractor g, int left, int top) {
        g.centeredText(this.font, "\u6b63\u5728\u4e0b\u8f7d\u66f4\u65b0...", left + PANEL_W / 2, top + 18, 0xFFFFFFFF);
        // 进度条
        int barX = left + 40;
        int barY = top + 56;
        int barW = PANEL_W - 80;
        int barH = 12;
        g.fill(barX, barY, barX + barW, barY + barH, 0xFF1E2229);
        g.fill(barX, barY, barX + (int) (barW * progress), barY + barH, 0xFF3A9B4E);
        g.horizontalLine(barX, barX + barW, barY, 0x66AAAAAA);
        g.horizontalLine(barX, barX + barW, barY + barH, 0x66AAAAAA);
        g.verticalLine(barX, barY, barY + barH, 0x66AAAAAA);
        g.verticalLine(barX + barW, barY, barY + barH, 0x66AAAAAA);
        g.centeredText(this.font, (int) (progress * 100) + "%", left + PANEL_W / 2, barY + 18, 0xFF9AA0A6);
        g.centeredText(this.font, humanSize(downloaded) + " / " + humanSize(total), left + PANEL_W / 2, barY + 32, 0xFF9AA0A6);
    }

    private void renderFailed(GuiGraphicsExtractor g, int left, int top, int mouseX, int mouseY) {
        g.centeredText(this.font, "\u00a7c\u66f4\u65b0\u5931\u8d25", left + PANEL_W / 2, top + 24, 0xFFFFFFFF);
        String msg = error != null ? error : "\u672a\u77e5\u9519\u8bef";
        String line1 = msg.length() > 34 ? msg.substring(0, 34) : msg;
        String line2 = msg.length() > 34 ? msg.substring(34, Math.min(68, msg.length())) : "";
        g.centeredText(this.font, "\u00a77" + line1, left + PANEL_W / 2, top + 46, 0xFFFFFFFF);
        if (!line2.isEmpty()) {
            g.centeredText(this.font, "\u00a77" + line2, left + PANEL_W / 2, top + 60, 0xFFFFFFFF);
        }
        int btnY = top + 104;
        drawButton(g, closeRect(left, btnY), "\u5173\u95ed", false, mouseX, mouseY);
    }

    private void drawButton(GuiGraphicsExtractor g, Rect r, String text, boolean primary, int mouseX, int mouseY) {
        boolean hover = mouseX >= r.x && mouseX <= r.x + r.w && mouseY >= r.y && mouseY <= r.y + r.h;
        int bg = primary ? (hover ? 0xFF2E6B47 : 0xFF245A3A) : (hover ? 0xFF3A4048 : 0xFF2A2F38);
        g.fill(r.x, r.y, r.x + r.w, r.y + r.h, bg);
        g.horizontalLine(r.x, r.x + r.w, r.y, 0x66AAAAAA);
        g.horizontalLine(r.x, r.x + r.w, r.y + r.h, 0x66AAAAAA);
        g.verticalLine(r.x, r.y, r.y + r.h, 0x66AAAAAA);
        g.verticalLine(r.x + r.w, r.y, r.y + r.h, 0x66AAAAAA);
        g.centeredText(this.font, text, r.x + r.w / 2, r.y + 5, 0xFFFFFFFF);
    }

    private Rect cancelRect(int left, int btnY) {
        return new Rect(left + 40, btnY, 120, 22);
    }

    private Rect okRect(int left, int btnY) {
        return new Rect(left + PANEL_W - 40 - 120, btnY, 120, 22);
    }

    private Rect closeRect(int left, int btnY) {
        return new Rect(left + PANEL_W - 40 - 120, btnY, 120, 22);
    }

    private static String humanSize(long bytes) {
        if (bytes <= 0) {
            return "?";
        }
        if (bytes < 1024 * 1024) {
            return String.format("%.0f KB", bytes / 1024.0);
        }
        return String.format("%.1f MB", bytes / (1024.0 * 1024.0));
    }

    @Override
    public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event, boolean doubleClick) {
        if (event.button() == 0) {
            int mx = (int) event.x();
            int my = (int) event.y();
            int left = left();
            int top = top();
            if (state == State.CONFIRM) {
                int btnY = top + 104;
                if (hit(cancelRect(left, btnY), mx, my)) {
                    close();
                    return true;
                }
                if (hit(okRect(left, btnY), mx, my)) {
                    startDownload();
                    return true;
                }
            } else if (state == State.FAILED) {
                int btnY = top + 104;
                if (hit(closeRect(left, btnY), mx, my)) {
                    close();
                    return true;
                }
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean keyPressed(KeyEvent key) {
        if (key.key() == 256) { // Esc
            if (state != State.DOWNLOADING) {
                close();
            }
            return true;
        }
        return super.keyPressed(key);
    }

    private static boolean hit(Rect r, int mx, int my) {
        return mx >= r.x && mx <= r.x + r.w && my >= r.y && my <= r.y + r.h;
    }

    private void close() {
        SideChatRenderer.setScreenCompat(null);
    }

    /** 开始后台下载 */
    private void startDownload() {
        if (!UpdateChecker.hasDownload()) {
            state = State.FAILED;
            error = "\u6ca1\u6709\u53ef\u7528\u7684\u4e0b\u8f7d\u5730\u5740";
            return;
        }
        state = State.DOWNLOADING;
        progress = 0f;
        downloaded = 0;
        total = 0;
        String url = UpdateChecker.getLatestDownloadUrl();
        String fileName = UpdateChecker.getLatestFileName();
        Path tmpDir = Minecraft.getInstance().gameDirectory.toPath().resolve(".tgzjdvchat-update");
        Path target;
        try {
            Files.createDirectories(tmpDir);
            target = tmpDir.resolve(fileName != null && !fileName.isEmpty() ? fileName : "tgzjdvchat-update.jar");
        } catch (Exception e) {
            state = State.FAILED;
            error = "\u521b\u5efa\u4e34\u65f6\u76ee\u5f55\u5931\u8d25\uff1a" + e.getMessage();
            return;
        }
        final Path targetFile = target;
        downloadThread = new Thread(() -> {
            try {
                UpdateDownloader.download(url, targetFile, (p, done, totalBytes) -> {
                    progress = p;
                    downloaded = done;
                    total = totalBytes;
                });
                Minecraft.getInstance().execute(() -> applyUpdate(targetFile));
            } catch (Exception e) {
                Minecraft.getInstance().execute(() -> {
                    state = State.FAILED;
                    error = e.getMessage() != null ? e.getMessage() : "\u4e0b\u8f7d\u5931\u8d25";
                });
            }
        }, "TGC-Updater");
        downloadThread.setDaemon(true);
        downloadThread.start();
    }

    /** 下载完成后应用更新：替换 jar 并关闭游戏 */
    private void applyUpdate(Path downloaded) {
        if (applying) {
            return;
        }
        applying = true;
        try {
            Path modJar = UpdateChecker.getModJarPath();
            if (modJar == null || !Files.isRegularFile(modJar) || !modJar.getFileName().toString().endsWith(".jar")) {
                state = State.FAILED;
                error = "\u5f53\u524d\u4e3a\u5f00\u53d1\u73af\u5883\u6216\u65e0\u6cd5\u5b9a\u4f4d\u6a21\u7ec4\u6587\u4ef6\uff0c\u65e0\u6cd5\u81ea\u52a8\u66f4\u65b0";
                applying = false;
                return;
            }
            String oldName = modJar.getFileName().toString();
            String newName = UpdateChecker.getLatestFileName();
            if (newName == null || newName.isEmpty()) {
                newName = downloaded.getFileName().toString();
            }
            Path modsDir = modJar.getParent();
            Path tmpDir = Minecraft.getInstance().gameDirectory.toPath().resolve(".tgzjdvchat-update");
            // 临时目录内重命名为最终文件名
            Path finalTmp = tmpDir.resolve(newName);
            if (!finalTmp.equals(downloaded)) {
                Files.move(downloaded, finalTmp, StandardCopyOption.REPLACE_EXISTING);
            }
            boolean windows = System.getProperty("os.name", "").toLowerCase().contains("win");
            if (windows) {
                // Windows：jar 被 JVM 占用无法直接替换，写批处理脚本在游戏退出后执行
                Path script = tmpDir.resolve("apply.bat");
                Files.writeString(script, buildApplyBatch(oldName, newName));
                // 路径加引号以支持含空格的路径
                new ProcessBuilder("cmd.exe", "/c", "start", "", "/min", "\"" + script + "\"").start();
            } else {
                // 其他系统：文件未被占用，直接替换
                Files.copy(finalTmp, modsDir.resolve(newName), StandardCopyOption.REPLACE_EXISTING);
                Files.deleteIfExists(modJar);
            }
            // 关闭游戏
            Minecraft.getInstance().stop();
        } catch (Exception e) {
            state = State.FAILED;
            error = "\u5e94\u7528\u66f4\u65b0\u5931\u8d25\uff1a" + (e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName());
            applying = false;
        }
    }

    /** 生成 Windows 替换脚本（等待游戏退出后删除旧 jar、移入新 jar） */
    private static String buildApplyBatch(String oldName, String newName) {
        return "@echo off\r\n"
                + "rem TGZJDV Chat auto-update\r\n"
                + "cd /d \"%~dp0\"\r\n"
                + "rem wait for game exit then remove old jar (max ~30s)\r\n"
                + "for /l %%i in (1,1,15) do (\r\n"
                + "    ping 127.0.0.1 -n 2 >nul\r\n"
                + "    del /q \"..\\mods\\" + oldName + "\" 2>nul\r\n"
                + "    if not exist \"..\\mods\\" + oldName + "\" goto replaced\r\n"
                + ")\r\n"
                + ":replaced\r\n"
                + "move /y \"" + newName + "\" \"..\\mods\\" + newName + "\" >nul 2>nul\r\n"
                + "cd ..\r\n"
                + "rmdir /s /q \".tgzjdvchat-update\" 2>nul\r\n";
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
