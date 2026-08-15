package com.tgzjdv.chat.mixin;

import com.tgzjdv.chat.config.SideChatConfig;
import com.tgzjdv.chat.render.SideChatRenderer;
import com.tgzjdv.chat.store.ChatMessage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Style;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.gen.Invoker;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 聊天输入界面（ChatScreen）集成：
 * - 输入框移动到侧边栏底部
 * - 打开聊天时侧边栏消息面板保持显示
 * - 移除原版输入框背景条
 * - 鼠标滚轮滚动侧边栏消息
 */
@Mixin(ChatScreen.class)
public abstract class ChatScreenMixin {

    @Shadow
    protected EditBox input;

    /**
     * 初始化完成后，将输入框移动到侧边栏底部输入区域
     */
    @Inject(method = "init", at = @At("TAIL"))
    private void tgzjdvchat_repositionInput(CallbackInfo ci) {
        if (!SideChatConfig.enabled) {
            return;
        }
        this.input.setX(SideChatRenderer.getInputX());
        this.input.setY(SideChatRenderer.getInputY(this.input.getHeight()));
        this.input.setWidth(SideChatRenderer.getInputWidth());
        // 打开聊天时重置面板淡入动画
        SideChatRenderer.resetPanelFade();
    }



    /**
     * 渲染侧边栏消息面板（输入框由原版逻辑继续渲染在侧边栏底部）
     */
    @Inject(method = "extractRenderState", at = @At("HEAD"))
    private void tgzjdvchat_renderSideChat(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a, CallbackInfo ci) {
        if (!SideChatConfig.enabled) {
            return;
        }
        // 退出动画/待关闭期间：面板已滑出屏幕，隐藏输入框避免残留闪烁
        if (this.input != null) {
            this.input.setVisible(!SideChatRenderer.isExiting());
        }
        // 打开聊天时：左侧微信样式面板
        SideChatRenderer.renderExpanded(graphics, Minecraft.getInstance().font, false, mouseX, mouseY);
        // 输入框旁的图片按钮
        renderImageButton(graphics, Minecraft.getInstance().font, mouseX, mouseY);
        // 所有右键菜单（最后渲染，保证在按钮之上）
        SideChatRenderer.renderAllMenus(graphics, Minecraft.getInstance().font, mouseX, mouseY);
        // 悬停提示（成就详情、链接文字等）
        SideChatRenderer.renderHoverTooltip(graphics, Minecraft.getInstance().font, mouseX, mouseY);
    }

    /**
     * 移除原版输入框背景条（屏幕底部中间的灰色条）
     */
    @Redirect(method = "extractRenderState",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;fill(IIIII)V"))
    private void tgzjdvchat_removeInputBackground(GuiGraphicsExtractor graphics, int x1, int y1, int x2, int y2, int color) {
        if (!SideChatConfig.enabled) {
            graphics.fill(x1, y1, x2, y2, color);
        }
        // 侧边栏启用时不绘制原版输入背景条
    }

    /**
     * 鼠标滚轮：在侧边栏消息区域滚动消息列表
     */
    @Inject(method = "mouseScrolled", at = @At("HEAD"), cancellable = true)
    private void tgzjdvchat_scrollSideChat(double mouseX, double mouseY, double horizontalAmount, double verticalAmount,
                                           CallbackInfoReturnable<Boolean> cir) {
        if (!SideChatConfig.enabled) {
            return;
        }
        boolean inside = SideChatRenderer.isInsideMessageArea(mouseX, mouseY);
        if (inside) {
            SideChatRenderer.scrollBy((int) (verticalAmount * 24.0));
            cir.setReturnValue(true);
        }
    }

    /**
     * 关闭聊天：播放退出动画（从左边滑出屏幕）
     * 动画结束后由 tick 阶段调用原版 onClose 真正关闭（避免闪烁）
     */
    @Inject(method = "onClose", at = @At("HEAD"), cancellable = true)
    private void tgzjdvchat_onClose(CallbackInfo ci) {
        if (!SideChatConfig.enabled) {
            return;
        }
        // 动画结束后 tick 触发的正式关闭：走原版关闭流程
        if (SideChatRenderer.consumeAllowClose()) {
            return;
        }
        // 动画中重复调用：直接走原版（用户想立即关闭）
        if (SideChatRenderer.isExiting()) {
            return;
        }
        ci.cancel();
        SideChatRenderer.startExitAnimation();
    }

    /**
     * 鼠标点击处理：
     * - 左键：点击链接 / 点 ✕ 取消回复
     * - 右键：右键菜单（@玩家、私聊 /tell、复制名字）、右键消息回复、右键头像菜单
     */
    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void tgzjdvchat_mouseClicked(MouseButtonEvent event, boolean doubleClick, CallbackInfoReturnable<Boolean> cir) {
        if (!SideChatConfig.enabled) {
            return;
        }
        int mouseX = (int) event.x();
        int mouseY = (int) event.y();

        // -6. 列表菜单优先（服务器/频道/家列表展开时，避免被图片/消息点击抢占）
        if (event.button() == 0 && SideChatRenderer.isAnyListMenuOpen()) {
            // 服务器功能按钮：切换
            if (SideChatRenderer.isInsideServerButton(mouseX, mouseY)) {
                SideChatRenderer.toggleServerList();
                cir.setReturnValue(true);
                return;
            }
            // 服务器功能列表选择
            String serverAction = SideChatRenderer.handleServerListClick(mouseX, mouseY);
            if (serverAction != null) {
                if ("login".equals(serverAction)) {
                    if (com.tgzjdv.chat.config.ChatAuthConfig.hasPassword()) {
                        SideChatRenderer.sendLogin(com.tgzjdv.chat.config.ChatAuthConfig.getLoginPassword());
                    } else {
                        SideChatRenderer.setScreenCompat(new com.tgzjdv.chat.screen.SettingsScreen(true));
                    }
                    cir.setReturnValue(true);
                    return;
                }
                if ("dback".equals(serverAction)) {
                    SideChatRenderer.sendCommand(com.tgzjdv.chat.config.ChatAuthConfig.getDbackCommand());
                    cir.setReturnValue(true);
                    return;
                }
                if ("back".equals(serverAction)) {
                    SideChatRenderer.sendCommand(com.tgzjdv.chat.config.ChatAuthConfig.getBackCommand());
                    cir.setReturnValue(true);
                    return;
                }
                if ("home".equals(serverAction)) {
                    SideChatRenderer.toggleHomeList();
                    cir.setReturnValue(true);
                    return;
                }
                if ("settings".equals(serverAction)) {
                    SideChatRenderer.setScreenCompat(new com.tgzjdv.chat.screen.SettingsScreen(false));
                    cir.setReturnValue(true);
                    return;
                }
                cir.setReturnValue(true);
                return;
            }
            // 家列表选择
            String homeSel = SideChatRenderer.handleHomeListClick(mouseX, mouseY);
            if (homeSel != null) {
                if ("add".equals(homeSel)) {
                    SideChatRenderer.setScreenCompat(new com.tgzjdv.chat.screen.HomeNameScreen());
                } else if (!"close".equals(homeSel)) {
                    SideChatRenderer.sendCommand(com.tgzjdv.chat.config.ChatAuthConfig.getHomeCommand() + " " + homeSel);
                }
                cir.setReturnValue(true);
                return;
            }
            // 频道按钮点击
            if (SideChatRenderer.isInsideChannelButton(mouseX, mouseY)) {
                SideChatRenderer.toggleChannelList();
                cir.setReturnValue(true);
                return;
            }
            // 频道列表选择
            String channelSel = SideChatRenderer.handleChannelListClick(mouseX, mouseY);
            if (channelSel != null) {
                if (!"close".equals(channelSel)) {
                    SideChatRenderer.switchChannel("public".equals(channelSel) ? null : channelSel);
                }
                cir.setReturnValue(true);
                return;
            }
            // 点菜单外：菜单已关闭，拦截本次点击（不再触发图片/消息等）
            cir.setReturnValue(true);
            return;
        }

        // -5. 消息右键菜单点击优先
        String msgAction = SideChatRenderer.handleMessageMenuClick(mouseX, mouseY);
        if (msgAction != null) {
            if ("reply".equals(msgAction)) {
                ChatMessage target = SideChatRenderer.getMessageMenuTarget();
                if (target != null && !target.sender().isEmpty()) {
                    SideChatRenderer.setReplyTarget(target.sender(), target.displayContent().getString());
                    String base = "@" + target.sender() + " ";
                    this.input.setValue(base);
                    this.input.moveCursorTo(base.length(), false);
                }
                cir.setReturnValue(true);
                return;
            }
            if ("copy".equals(msgAction)) {
                ChatMessage target = SideChatRenderer.getMessageMenuTarget();
                if (target != null) {
                    Minecraft.getInstance().keyboardHandler.setClipboard(target.content().getString());
                }
                cir.setReturnValue(true);
                return;
            }
            // close
            cir.setReturnValue(true);
            return;
        }

        // -4. 图片右键菜单点击优先
        String imgAction = SideChatRenderer.handleImageMenuClick(mouseX, mouseY);
        if (imgAction != null) {
            if ("reply".equals(imgAction)) {
                // 回复：对图片发送者设置回复
                String sender = SideChatRenderer.getImageMenuSender();
                if (sender != null && !sender.isEmpty()) {
                    SideChatRenderer.setReplyTarget(sender, "\u56fe\u7247");
                    String base = "@" + sender + " ";
                    this.input.setValue(base);
                    this.input.moveCursorTo(base.length(), false);
                }
                cir.setReturnValue(true);
                return;
            }
            if ("copyurl".equals(imgAction)) {
                String url = SideChatRenderer.getImageMenuUrl();
                if (url != null) {
                    Minecraft.getInstance().keyboardHandler.setClipboard(url);
                }
                cir.setReturnValue(true);
                return;
            }
            if ("copyfile".equals(imgAction)) {
                tgzjdvchat_copyImageFile(SideChatRenderer.getImageMenuUrl());
                cir.setReturnValue(true);
                return;
            }
            // close
            cir.setReturnValue(true);
            return;
        }

        // -3. 菜单点击优先（菜单打开时优先处理，避免和下方缩略图/其他点击冲突）
        String menuAction = SideChatRenderer.handleMenuClick(mouseX, mouseY);
        if (menuAction != null) {
            String target = SideChatRenderer.getMenuActionTarget();
            if ("@".equals(menuAction) && target != null) {
                this.input.insertText("@" + target + " ");
                cir.setReturnValue(true);
                return;
            }
            if ("tell".equals(menuAction) && target != null) {
                // 私聊：切换到私聊频道 + 预填 /tell
                SideChatRenderer.switchChannel(target);
                SideChatRenderer.addPrivateTarget(target);
                String base = "/tell " + target + " ";
                this.input.setValue(base);
                this.input.moveCursorTo(base.length(), false);
                cir.setReturnValue(true);
                return;
            }
            if ("tpa".equals(menuAction) && target != null) {
                // tpa：自动发送自定义 tpa 命令（默认 /tpa 玩家名，不带 @）
                SideChatRenderer.sendCommand(com.tgzjdv.chat.config.ChatAuthConfig.getTpaCommand() + " " + target);
                cir.setReturnValue(true);
                return;
            }
            if ("tpahere".equals(menuAction) && target != null) {
                // tpahere：自动发送自定义 tpahere 命令（默认 /tpahere 玩家名，不带 @）
                SideChatRenderer.sendCommand(com.tgzjdv.chat.config.ChatAuthConfig.getTpahereCommand() + " " + target);
                cir.setReturnValue(true);
                return;
            }
            if ("copy".equals(menuAction) && target != null) {
                Minecraft.getInstance().keyboardHandler.setClipboard(target);
                cir.setReturnValue(true);
                return;
            }
            // close 或未识别：仅关闭菜单
            cir.setReturnValue(true);
            return;
        }

        // -2. 图片按钮点击（打开图片选择器）
        if (event.button() == 0 && isInsideImageButton(mouseX, mouseY)) {
            SideChatRenderer.resetPanelFade();
            com.tgzjdv.chat.screen.ImagePickerScreen.open();
            cir.setReturnValue(true);
            return;
        }

        // -2.5 标题栏设置按钮点击（打开设置界面）
        if (event.button() == 0 && isInsideTitleSettingsButton(mouseX, mouseY)) {
            SideChatRenderer.resetPanelFade();
            SideChatRenderer.setScreenCompat(new com.tgzjdv.chat.screen.SettingsScreen(false));
            cir.setReturnValue(true);
            return;
        }

        // -1. 图片点击（左键放大查看 / 右键菜单）
        com.tgzjdv.chat.render.SideChatRenderer.ImageHitArea imgArea = SideChatRenderer.pickImageAreaAt(mouseX, mouseY);
        if (imgArea != null) {
            if (event.button() == 0) {
                com.tgzjdv.chat.screen.ImageViewerScreen.open(imgArea.imageUrl);
                cir.setReturnValue(true);
                return;
            }
            if (event.button() == 1) {
                // 右键：打开图片菜单
                SideChatRenderer.openImageMenu(imgArea.imageUrl, imgArea.sender, mouseX, mouseY);
                cir.setReturnValue(true);
                return;
            }
        }

        // -0. 消息右键：普通玩家消息 → 回复/复制菜单；系统消息 → 复制菜单
        if (event.button() == 1) {
            com.tgzjdv.chat.store.ChatMessage msg = SideChatRenderer.pickMessageAt(mouseX, mouseY);
            if (msg != null) {
                SideChatRenderer.openMessageMenu(msg, mouseX, mouseY);
                cir.setReturnValue(true);
                return;
            }
        }

        // 0. 滚动条：左键按下开始拖动 + 跳转（松开由渲染时 GLFW 检测）
        if (SideChatRenderer.isInsideScrollTrack(mouseX, mouseY) && event.button() == 0) {
            // 先跳转，再记录拖动起点（否则拖动逻辑会用旧起点覆盖跳转结果）
            SideChatRenderer.scrollTrackClick(mouseY);
            SideChatRenderer.beginScrollDrag(mouseY);
            cir.setReturnValue(true);
            return;
        }

        // 1. 点 ✕ 取消回复（左键或右键）
        if (SideChatRenderer.isInsideCancelReply(mouseX, mouseY)) {
            SideChatRenderer.cancelReply();
            cir.setReturnValue(true);
            return;
        }

        if (event.button() == 0) {
            // 服务器/频道按钮（菜单未打开时点击打开；已打开由列表菜单优先块处理）
            if (SideChatRenderer.isInsideServerButton(mouseX, mouseY) && !SideChatRenderer.isAnyListMenuOpen()) {
                SideChatRenderer.toggleServerList();
                cir.setReturnValue(true);
                return;
            }
            if (SideChatRenderer.isInsideChannelButton(mouseX, mouseY) && !SideChatRenderer.isAnyListMenuOpen()) {
                SideChatRenderer.toggleChannelList();
                cir.setReturnValue(true);
                return;
            }
            // 左键：点击链接
            Style clicked = SideChatRenderer.pickClickableStyle(mouseX, mouseY);
            if (clicked != null && clicked.getClickEvent() != null) {
                boolean handled = this.tgzjdvchat_invokeHandleComponentClicked(clicked, this.tgzjdvchat_isInsertionClickMode());
                if (handled) {
                    cir.setReturnValue(true);
                }
            }
        } else if (event.button() == 1) {
            // 右键
            // 右键头像 → 弹出菜单（@玩家、私聊 /tell、复制名字）
            String avatarPlayer = SideChatRenderer.pickAvatarAt(mouseX, mouseY);
            if (avatarPlayer != null) {
                SideChatRenderer.openMenu(avatarPlayer, mouseX + 2, mouseY + 2);
                cir.setReturnValue(true);
                return;
            }
            // 右键玩家消息 → 回复（普通消息 + @提及，不私聊）
            ChatMessage msg = SideChatRenderer.pickMessageAt(mouseX, mouseY);
            if (msg != null && msg.isPlayerMessage() && !msg.sender().isEmpty()) {
                SideChatRenderer.setReplyTarget(msg.sender(), msg.displayContent().getString());
                cir.setReturnValue(true);
            }
        }
    }

    /**
     * 发送消息时：如果处于回复模式，自动附加 @回复目标（普通玩家也能看懂）
     * 并清除回复状态
     */
    @ModifyVariable(method = "handleChatInput", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private String tgzjdvchat_modifyChatInput(String text) {
        if (!SideChatConfig.enabled) {
            return text;
        }
        if (text == null || text.isEmpty()) {
            return text;
        }
        // 1. 私聊频道优先：无论是否回复，都发私聊（回复时附带引用）
        if (SideChatRenderer.isPrivateChannel() && SideChatRenderer.getPrivateTarget() != null
                && !text.startsWith("/")) {
            String replyTo = SideChatRenderer.getReplyTargetName();
            String preview = SideChatRenderer.getReplyPreview();
            SideChatRenderer.cancelReply();
            if (replyTo != null && preview != null && !preview.isEmpty()) {
                if (preview.length() > 40) {
                    preview = preview.substring(0, 40) + "\u2026";
                }
                return "/tell " + SideChatRenderer.getPrivateTarget()
                        + " @" + replyTo + "\u300c" + preview + "\u300d" + text;
            }
            return "/tell " + SideChatRenderer.getPrivateTarget() + " " + text;
        }
        // 2. 回复模式（公共频道）：自动附加 @回复目标「引用」
        String replyTo = SideChatRenderer.getReplyTargetName();
        if (replyTo != null) {
            String preview = SideChatRenderer.getReplyPreview();
            SideChatRenderer.cancelReply();
            if (preview != null && !preview.isEmpty()) {
                if (preview.length() > 40) {
                    preview = preview.substring(0, 40) + "\u2026";
                }
                return "@" + replyTo + "\u300c" + preview + "\u300d " + text;
            }
            return "@" + replyTo + " " + text;
        }
        return text;
    }

    @Invoker("handleComponentClicked")
    protected abstract boolean tgzjdvchat_invokeHandleComponentClicked(Style style, boolean allowInsertions);

    @Invoker("insertionClickMode")
    protected abstract boolean tgzjdvchat_isInsertionClickMode();

    /** 复制图片文件：下载图片保存到 images 目录，复制文件路径到剪贴板 */
    private void tgzjdvchat_copyImageFile(String url) {
        com.tgzjdv.chat.image.ImageCache.copyImageToFile(url, path -> {
            if (path != null) {
                Minecraft.getInstance().keyboardHandler.setClipboard(path);
                if (Minecraft.getInstance().player != null) {
                    Minecraft.getInstance().player.sendSystemMessage(
                            net.minecraft.network.chat.Component.literal("\u00a7a图片已保存并复制路径: " + path));
                }
            } else if (Minecraft.getInstance().player != null) {
                Minecraft.getInstance().player.sendSystemMessage(
                        net.minecraft.network.chat.Component.literal("\u00a7c图片保存失败"));
            }
        });
    }

    /**
     * 聊天输入：检测图片发送命令（/tgcimg 或 #img）→ 打开图片选择器
     */
    @Inject(method = "handleChatInput", at = @At("HEAD"), cancellable = true)
    private void tgzjdvchat_handleChatInput(String input, boolean addToHistory, CallbackInfo ci) {
        if (!SideChatConfig.enabled) {
            return;
        }
        String trimmed = input.trim();
        if (trimmed.equalsIgnoreCase("/tgcimg") || trimmed.equalsIgnoreCase("#img") || trimmed.equalsIgnoreCase("/img")) {
            ci.cancel();
            SideChatRenderer.resetPanelFade();
            SideChatRenderer.setScreenCompat(null);
            com.tgzjdv.chat.screen.ImagePickerScreen.open();
        }
    }

    /**
     * 标题栏设置按钮点击检测（SideChatRenderer 记录的位置）
     */
    private static boolean isInsideTitleSettingsButton(int mouseX, int mouseY) {
        return mouseX >= SideChatRenderer.getSettingsBtnX()
                && mouseX <= SideChatRenderer.getSettingsBtnX() + SideChatRenderer.getSettingsBtnW()
                && mouseY >= SideChatRenderer.getSettingsBtnY()
                && mouseY <= SideChatRenderer.getSettingsBtnY() + SideChatRenderer.getSettingsBtnH();
    }
    private static boolean isInsideImageButton(int mouseX, int mouseY) {
        int offset = (int) SideChatRenderer.getCurrentPanelOffset();
        int x = SideChatRenderer.getInputX() + SideChatRenderer.getInputWidth() + 6 + offset;
        return mouseX >= x && mouseX <= x + 20
                && mouseY >= SideChatRenderer.getInputY(20)
                && mouseY <= SideChatRenderer.getInputY(20) + 20;
    }

    /**
     * 渲染输入框旁的图片按钮（跟随面板动画偏移）
     */
    private static void renderImageButton(GuiGraphicsExtractor graphics, net.minecraft.client.gui.Font font, int mouseX, int mouseY) {
        if (!SideChatConfig.enabled) {
            return;
        }
        int offset = (int) SideChatRenderer.getCurrentPanelOffset();
        int x = SideChatRenderer.getInputX() + SideChatRenderer.getInputWidth() + 6 + offset;
        int y = SideChatRenderer.getInputY(20);
        boolean hover = mouseX >= x && mouseX <= x + 20 && mouseY >= y && mouseY <= y + 20;
        graphics.fill(x, y, x + 20, y + 20, hover ? 0xFF3A4048 : 0xFF2A2F38);
        graphics.horizontalLine(x, x + 20, y, 0x66AAAAAA);
        graphics.horizontalLine(x, x + 20, y + 20, 0x66AAAAAA);
        graphics.verticalLine(x, y, y + 20, 0x66AAAAAA);
        graphics.verticalLine(x + 20, y, y + 20, 0x66AAAAAA);
        // 图标（简化的图片符号：山 + 太阳）
        graphics.fill(x + 4, y + 5, x + 16, y + 15, 0xFFDDDDDD);
        graphics.fill(x + 6, y + 10, x + 10, y + 13, 0xFF2A2F38); // 地面
        graphics.fill(x + 6, y + 7, x + 9, y + 10, 0xFF2A2F38);   // 山1
        graphics.fill(x + 12, y + 6, x + 14, y + 10, 0xFF2A2F38); // 山2
        if (hover) {
            // 提示
            graphics.setTooltipForNextFrame(font,
                    java.util.List.of(net.minecraft.network.chat.Component.literal("发送图片").getVisualOrderText()), mouseX, mouseY);
        }
    }
}
