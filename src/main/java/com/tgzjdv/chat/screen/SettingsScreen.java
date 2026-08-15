package com.tgzjdv.chat.screen;

import com.tgzjdv.chat.config.ChatAuthConfig;
import com.tgzjdv.chat.config.SideChatConfig;
import com.tgzjdv.chat.render.SideChatRenderer;
import com.tgzjdv.chat.update.UpdateChecker;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;

/**
 * 模组设置界面（独立设置页）
 * - 登录密码 / 自定义命令（登录、死亡点、返回、回家）
 * - autoLogin=true 时（首次使用）：保存后自动登录
 */
public class SettingsScreen extends Screen {

    private static final Component TITLE = Component.literal("TGZJDV 聊天设置");

    /** 内容顶部偏移（顶端对齐） */
    private static final int TOP_OFFSET = 60;

    private EditBox passwordInput;
    private EditBox loginCmdInput;
    private EditBox dbackCmdInput;
    private EditBox backCmdInput;
    private EditBox homeCmdInput;
    private EditBox tpaCmdInput;
    private EditBox tpahereCmdInput;
    private AutoHideDelaySlider hideDelaySlider;
    private final boolean autoLogin;

    /** 返回界面（模组菜单进入时设置，关闭时返回） */
    private static net.minecraft.client.gui.screens.Screen returnScreen = null;

    public static void setReturnScreen(net.minecraft.client.gui.screens.Screen screen) {
        returnScreen = screen;
    }

    public SettingsScreen(boolean autoLogin) {
        super(TITLE);
        this.autoLogin = autoLogin;
    }

    @Override
    protected void init() {
        int centerX = this.width / 2 - 130;
        int startY = TOP_OFFSET;
        // 登录密码
        this.passwordInput = new EditBox(this.font, centerX, startY, 260, 20, Component.literal("登录密码"));
        this.passwordInput.setMaxLength(64);
        this.passwordInput.setValue(ChatAuthConfig.getLoginPassword());
        this.passwordInput.setResponder(text -> ChatAuthConfig.setLoginPassword(text));
        this.addRenderableWidget(this.passwordInput);
        // 登录命令
        this.loginCmdInput = new EditBox(this.font, centerX, startY + 26, 260, 20, Component.literal("登录命令"));
        this.loginCmdInput.setMaxLength(32);
        this.loginCmdInput.setValue(ChatAuthConfig.getLoginCommand());
        this.loginCmdInput.setResponder(text -> ChatAuthConfig.setLoginCommand(text));
        this.addRenderableWidget(this.loginCmdInput);
        // 死亡点命令
        this.dbackCmdInput = new EditBox(this.font, centerX, startY + 52, 260, 20, Component.literal("死亡点命令"));
        this.dbackCmdInput.setMaxLength(32);
        this.dbackCmdInput.setValue(ChatAuthConfig.getDbackCommand());
        this.dbackCmdInput.setResponder(text -> ChatAuthConfig.setDbackCommand(text));
        this.addRenderableWidget(this.dbackCmdInput);
        // 返回命令
        this.backCmdInput = new EditBox(this.font, centerX, startY + 78, 260, 20, Component.literal("返回命令"));
        this.backCmdInput.setMaxLength(32);
        this.backCmdInput.setValue(ChatAuthConfig.getBackCommand());
        this.backCmdInput.setResponder(text -> ChatAuthConfig.setBackCommand(text));
        this.addRenderableWidget(this.backCmdInput);
        // 回家命令
        this.homeCmdInput = new EditBox(this.font, centerX, startY + 104, 260, 20, Component.literal("回家命令"));
        this.homeCmdInput.setMaxLength(32);
        this.homeCmdInput.setValue(ChatAuthConfig.getHomeCommand());
        this.homeCmdInput.setResponder(text -> ChatAuthConfig.setHomeCommand(text));
        this.addRenderableWidget(this.homeCmdInput);
        // tpa 命令
        this.tpaCmdInput = new EditBox(this.font, centerX, startY + 130, 260, 20, Component.literal("tpa命令"));
        this.tpaCmdInput.setMaxLength(32);
        this.tpaCmdInput.setValue(ChatAuthConfig.getTpaCommand());
        this.tpaCmdInput.setResponder(text -> ChatAuthConfig.setTpaCommand(text));
        this.addRenderableWidget(this.tpaCmdInput);
        // tpahere 命令
        this.tpahereCmdInput = new EditBox(this.font, centerX, startY + 156, 260, 20, Component.literal("tpahere命令"));
        this.tpahereCmdInput.setMaxLength(32);
        this.tpahereCmdInput.setValue(ChatAuthConfig.getTpahereCommand());
        this.tpahereCmdInput.setResponder(text -> ChatAuthConfig.setTpahereCommand(text));
        this.addRenderableWidget(this.tpahereCmdInput);
        // 小消息框无消息消失时间滑块（1-60 秒 / 永不消失）
        this.hideDelaySlider = new AutoHideDelaySlider(centerX, startY + 224, 260, 20);
        this.addRenderableWidget(this.hideDelaySlider);

        this.setInitialFocus(this.passwordInput);
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        graphics.fill(0, 0, this.width, this.height, 0xAA101318);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        int centerX = this.width / 2 - 130;
        int startY = TOP_OFFSET;
        // 关闭按钮（右上角 X）
        int closeX = this.width - 26;
        int closeY = 4;
        boolean closeHover = mouseX >= closeX && mouseX <= closeX + 22 && mouseY >= closeY && mouseY <= closeY + 22;
        graphics.fill(closeX, closeY, closeX + 22, closeY + 22, closeHover ? 0xFF8B3D3D : 0xFF6B2E2E);
        graphics.centeredText(this.font, "\u2715", closeX + 11, closeY + (22 - this.font.lineHeight) / 2, 0xFFFFFFFF);
        // 标题
        graphics.centeredText(this.font, this.title, this.width / 2, startY - 35, 0xFFFFFFFF);
        // 标签
        graphics.text(this.font, "登录密码", centerX - 5 - this.font.width("登录密码"), startY + 3, 0xFF8A9199, false);
        graphics.text(this.font, "登录命令", centerX - 5 - this.font.width("登录命令"), startY + 29, 0xFF8A9199, false);
        graphics.text(this.font, "死亡点命令", centerX - 5 - this.font.width("死亡点命令"), startY + 55, 0xFF8A9199, false);
        graphics.text(this.font, "返回命令", centerX - 5 - this.font.width("返回命令"), startY + 81, 0xFF8A9199, false);
        graphics.text(this.font, "回家命令", centerX - 5 - this.font.width("回家命令"), startY + 107, 0xFF8A9199, false);
        graphics.text(this.font, "tpa命令", centerX - 5 - this.font.width("tpa命令"), startY + 133, 0xFF8A9199, false);
        graphics.text(this.font, "tpahere命令", centerX - 5 - this.font.width("tpahere命令"), startY + 159, 0xFF8A9199, false);
        // 图片发送模式按钮
        int modeX = centerX;
        int modeY = startY + 185;
        boolean hover = mouseX >= modeX && mouseX <= modeX + 260 && mouseY >= modeY && mouseY <= modeY + 18;
        graphics.fill(modeX, modeY, modeX + 260, modeY + 18, hover ? 0xFF3A4048 : 0xFF2A2F38);
        graphics.horizontalLine(modeX, modeX + 260, modeY, 0x66AAAAAA);
        graphics.horizontalLine(modeX, modeX + 260, modeY + 18, 0x66AAAAAA);
        graphics.verticalLine(modeX, modeY, modeY + 18, 0x66AAAAAA);
        graphics.verticalLine(modeX + 260, modeY, modeY + 18, 0x66AAAAAA);
        String modeText = "图片发送模式：" + (com.tgzjdv.chat.config.ChatAuthConfig.isImageSafeMode() ? "\u00a7a安全（链接打断）" : "\u00a7e正常");
        graphics.text(this.font, modeText, modeX + 10, modeY + 5, 0xFFFFFFFF, false);
        // 小消息框消失时间标签（滑块本体由 widget 自动渲染）
        graphics.text(this.font, "小消息框消失时间", centerX, startY + 208, 0xFF8A9199, false);
        // 提示
        graphics.text(this.font, "回车保存" + (autoLogin ? " 并自动登录" : ""),
                centerX, startY + 250, 0xFF8A9199, false);
        graphics.text(this.font, "Esc 返回", centerX + 170, startY + 250, 0xFF6A7179, false);
        // ===== 更新检查区域 =====
        int upY = startY + 272;
        graphics.text(this.font, "\u00a78更新检查", centerX, upY, 0xFF8A9199, false);
        graphics.text(this.font, "\u00a77当前版本：\u00a7f" + UpdateChecker.getCurrentVersion(),
                centerX + 70, upY, 0xFF9AA0A6, false);
        int statusY = upY + 14;
        if (UpdateChecker.isChecking()) {
            graphics.text(this.font, "\u00a7e\u23f3 正在检查更新...", centerX, statusY, 0xFFFFD95A, false);
        } else if (UpdateChecker.isChecked()) {
            String latest = UpdateChecker.getLatestVersion();
            String srcName = UpdateChecker.getSource() != null
                    ? "\u00a77（" + UpdateChecker.getSource().displayName + "）" : "";
            if (UpdateChecker.hasUpdate()) {
                graphics.text(this.font, "\u00a7a\u2714 \u53d1\u73b0\u65b0\u7248\u672c \u00a7b" + latest + "\u00a7r" + srcName,
                        centerX, statusY, 0xFF7CD58A, false);
            } else {
                graphics.text(this.font, "\u00a7a\u2714 \u5df2\u662f\u6700\u65b0\u7248\u672c" + srcName,
                        centerX, statusY, 0xFF7CD58A, false);
            }
        } else if (UpdateChecker.getError() != null) {
            graphics.text(this.font, "\u00a7c\u2716 " + UpdateChecker.getError(),
                    centerX, statusY, 0xFFFF6B6B, false);
        } else {
            graphics.text(this.font, "\u00a77\u672a\u68c0\u67e5\uff08\u70b9\u51fb\u4e0b\u65b9\u6309\u94ae\u68c0\u67e5\uff09",
                    centerX, statusY, 0xFF9AA0A6, false);
        }
        // 检查更新按钮
        int btnY = upY + 28;
        int btnH = 18;
        boolean chkHover = mouseX >= centerX && mouseX <= centerX + 92 && mouseY >= btnY && mouseY <= btnY + btnH;
        graphics.fill(centerX, btnY, centerX + 92, btnY + btnH, chkHover ? 0xFF3A4048 : 0xFF2A2F38);
        graphics.horizontalLine(centerX, centerX + 92, btnY, 0x66AAAAAA);
        graphics.horizontalLine(centerX, centerX + 92, btnY + btnH, 0x66AAAAAA);
        graphics.verticalLine(centerX, btnY, btnY + btnH, 0x66AAAAAA);
        graphics.verticalLine(centerX + 92, btnY, btnY + btnH, 0x66AAAAAA);
        graphics.centeredText(this.font, "检查更新", centerX + 46, btnY + 4, 0xFFFFFFFF);
        // 打开下载页按钮（发现新版本时显示）
        if (UpdateChecker.isChecked() && UpdateChecker.hasUpdate()) {
            int openX = centerX + 100;
            int openW = 116;
            boolean openHover = mouseX >= openX && mouseX <= openX + openW && mouseY >= btnY && mouseY <= btnY + btnH;
            graphics.fill(openX, btnY, openX + openW, btnY + btnH, openHover ? 0xFF2E6B47 : 0xFF245A3A);
            graphics.horizontalLine(openX, openX + openW, btnY, 0x66AAAAAA);
            graphics.horizontalLine(openX, openX + openW, btnY + btnH, 0x66AAAAAA);
            graphics.verticalLine(openX, btnY, btnY + btnH, 0x66AAAAAA);
            graphics.verticalLine(openX + openW, btnY, btnY + btnH, 0x66AAAAAA);
            graphics.centeredText(this.font, "打开下载页", openX + openW / 2, btnY + 4, 0xFFFFFFFF);
        }
        // 关于（模组兼容信息）
        int aboutY = upY + 58;
        graphics.text(this.font, "\u00a78关于：", centerX, aboutY, 0xFF8A9199, false);
        graphics.text(this.font, "\u00a77已兼容 \u00a7bChat Pro\u00a77 的消息格式（回复消息 / 图片消息）", centerX, aboutY + 12, 0xFF9AA0A6, false);
        graphics.text(this.font, "\u00a77Chat Pro 作者: \u00a7bAbacbcdcnd", centerX, aboutY + 24, 0xFF9AA0A6, false);
        graphics.text(this.font, "\u00a77(Chat Pro - ModernChat, 由 Abacbcdcnd 开发)", centerX, aboutY + 36, 0xFF777777, false);
        super.extractRenderState(graphics, mouseX, mouseY, delta);
    }

    @Override
    public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event, boolean doubleClick) {
        if (event.button() == 0) {
            int mx = (int) event.x();
            int my = (int) event.y();
            // 关闭按钮（右上角 X）：保存并关闭
            if (mx >= this.width - 26 && mx <= this.width - 4 && my >= 4 && my <= 26) {
                confirm();
                return true;
            }
            int centerX = this.width / 2 - 130;
            int startY = TOP_OFFSET;
            int modeX = centerX;
            int modeY = startY + 185;
            if (mx >= modeX && mx <= modeX + 260 && my >= modeY && my <= modeY + 18) {
                com.tgzjdv.chat.config.ChatAuthConfig.setImageSafeMode(!com.tgzjdv.chat.config.ChatAuthConfig.isImageSafeMode());
                return true;
            }
            // 更新检查按钮
            int btnY = startY + 272 + 28;
            int btnH = 18;
            if (mx >= centerX && mx <= centerX + 92 && my >= btnY && my <= btnY + btnH) {
                UpdateChecker.checkUpdate(() -> {
                });
                return true;
            }
            // 打开下载页按钮（发现新版本时）
            if (UpdateChecker.isChecked() && UpdateChecker.hasUpdate()) {
                int openX = centerX + 100;
                int openW = 116;
                if (mx >= openX && mx <= openX + openW && my >= btnY && my <= btnY + btnH) {
                    UpdateChecker.openUpdatePage();
                    return true;
                }
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean keyPressed(KeyEvent key) {
        if (key.key() == 257 || key.key() == 335) { // Enter
            confirm();
            return true;
        }
        if (key.key() == 256) { // Esc：保存并关闭
            confirm();
            return true;
        }
        return super.keyPressed(key);
    }

    private void confirm() {
        ChatAuthConfig.setLoginPassword(this.passwordInput.getValue());
        ChatAuthConfig.setLoginCommand(this.loginCmdInput.getValue());
        ChatAuthConfig.setDbackCommand(this.dbackCmdInput.getValue());
        ChatAuthConfig.setBackCommand(this.backCmdInput.getValue());
        ChatAuthConfig.setHomeCommand(this.homeCmdInput.getValue());
        ChatAuthConfig.setTpaCommand(this.tpaCmdInput.getValue());
        ChatAuthConfig.setTpahereCommand(this.tpahereCmdInput.getValue());
        if (autoLogin && ChatAuthConfig.hasPassword()) {
            SideChatRenderer.sendCommand(ChatAuthConfig.getLoginCommand() + " " + ChatAuthConfig.getLoginPassword());
        }
        // 返回父界面（模组菜单）或关闭
        net.minecraft.client.gui.screens.Screen ret = returnScreen;
        returnScreen = null;
        com.tgzjdv.chat.render.SideChatRenderer.setScreenCompat(ret);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public boolean isAllowedInPortal() {
        return false;
    }

    /**
     * 小消息框无消息消失时间滑块
     * 档位 [0,60]：0=1 秒 … 59=60 秒，60=永不消失（配置存 -1）
     */
    private static final class AutoHideDelaySlider extends AbstractSliderButton {
        AutoHideDelaySlider(int x, int y, int width, int height) {
            super(x, y, width, height, Component.empty(), initialValue());
            updateMessage();
        }

        /** 根据当前配置计算滑块初始值（0.0=1秒，1.0=永不） */
        private static double initialValue() {
            int sec = SideChatConfig.autoHideDelaySeconds;
            if (sec < 0) {
                return 1.0; // 永不
            }
            return Math.max(0.0, Math.min(1.0, (sec - 1) / 60.0));
        }

        /** 当前档位 [0,60]，60 表示永不消失 */
        private int tick() {
            return (int) Math.round(this.value * 60.0);
        }

        @Override
        protected void updateMessage() {
            int t = tick();
            setMessage(Component.literal(t >= 60 ? "\u6c38\u4e0d\u6d88\u5931" : t + 1 + " \u79d2"));
        }

        @Override
        protected void applyValue() {
            int t = tick();
            SideChatConfig.setAutoHideDelaySeconds(t >= 60 ? -1 : t + 1);
        }
    }
}
