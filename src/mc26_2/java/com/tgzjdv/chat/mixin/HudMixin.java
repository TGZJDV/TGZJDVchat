package com.tgzjdv.chat.mixin;

import com.tgzjdv.chat.config.SideChatConfig;
import com.tgzjdv.chat.render.SideChatRenderer;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Hud;
import net.minecraft.client.gui.screens.ChatScreen;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 接管 HUD 中聊天区域的渲染（Minecraft 26.2 版本，HUD 渲染迁移到 Hud 类）
 * 原版 extractChat 在左下角渲染聊天框，我们将其替换为侧边栏面板
 */
@Mixin(Hud.class)
public abstract class HudMixin {

    @Shadow
    @Final
    private Minecraft minecraft;

    /**
     * 替换原版聊天渲染为侧边栏面板
     * 仅聊天未聚焦（未打开输入框）时渲染收起模式；聚焦时由 ChatScreen 渲染展开模式
     */
    @Inject(method = "extractChat", at = @At("HEAD"), cancellable = true)
    private void tgzjdvchat_extractChat(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker, CallbackInfo ci) {
        if (!SideChatConfig.enabled) {
            return; // 未启用时走原版逻辑
        }
        if (this.minecraft.gui.screen() instanceof ChatScreen) {
            return; // 聊天已打开（ChatScreen），由 ChatScreen 渲染展开模式
        }
        ci.cancel();
        graphics.nextStratum();
        // 未打开聊天时：左下角收起小块（自动隐藏）
        SideChatRenderer.renderCollapsed(graphics, this.minecraft.font);
    }
}
