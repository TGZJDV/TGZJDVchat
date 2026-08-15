package com.tgzjdv.chat.mixin;

import com.tgzjdv.chat.TgzjdvChatMod;
import com.tgzjdv.chat.config.SideChatConfig;
import com.tgzjdv.chat.render.SideChatRenderer;
import com.tgzjdv.chat.store.ChatMessage;
import com.tgzjdv.chat.store.ChatStore;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.multiplayer.chat.GuiMessageSource;
import net.minecraft.client.multiplayer.chat.GuiMessageTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MessageSignature;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 捕获所有进入聊天框的消息，同步到侧边栏消息存储
 */
@Mixin(ChatComponent.class)
public abstract class ChatComponentMixin {

    /** 捕获玩家消息 */
    @Inject(method = "addPlayerMessage", at = @At("HEAD"))
    private void tgzjdvchat_capturePlayerMessage(Component message, MessageSignature signature, GuiMessageTag tag, CallbackInfo ci) {
        ChatStore.add(new ChatMessage(System.currentTimeMillis(), ChatMessage.extractSender(message), message, GuiMessageSource.PLAYER));
        SideChatRenderer.wakeUp();
    }

    /** 捕获客户端系统消息 */
    @Inject(method = "addClientSystemMessage", at = @At("HEAD"))
    private void tgzjdvchat_captureClientSystemMessage(Component message, CallbackInfo ci) {
        ChatStore.add(new ChatMessage(System.currentTimeMillis(), ChatMessage.extractSender(message), message, GuiMessageSource.SYSTEM_CLIENT));
        SideChatRenderer.wakeUp();
    }

    /** 捕获服务器系统消息 */
    @Inject(method = "addServerSystemMessage", at = @At("HEAD"))
    private void tgzjdvchat_captureServerSystemMessage(Component message, CallbackInfo ci) {
        ChatStore.add(new ChatMessage(System.currentTimeMillis(), ChatMessage.extractSender(message), message, GuiMessageSource.SYSTEM_SERVER));
        // 检测私聊消息：加入私聊列表 + 标记未读（不自动切换频道）
        String text = message.getString();
        String privateSender = SideChatRenderer.detectPrivateMessage(text);
        if (privateSender != null) {
            SideChatRenderer.handlePrivateMessage(privateSender);
        } else {
            // 公共消息：标记公共频道未读
            SideChatRenderer.markPublicUnread();
        }
        SideChatRenderer.wakeUp();
    }


    /**
     * 禁用原版聊天框渲染（HUD 和 ChatScreen 中都会调用此方法）
     * 侧边栏启用时，原版聊天消息不再渲染，全部由 SideChatRenderer 接管
     */
    @Inject(method = "extractRenderState", at = @At("HEAD"), cancellable = true)
    private void tgzjdvchat_disableVanillaRendering(GuiGraphicsExtractor graphics, Font font, int ticks,
                                                   int mouseX, int mouseY, ChatComponent.DisplayMode displayMode,
                                                   boolean changeCursorOnInsertions, CallbackInfo ci) {
        if (SideChatConfig.enabled) {
            ci.cancel();
        }
    }
}
