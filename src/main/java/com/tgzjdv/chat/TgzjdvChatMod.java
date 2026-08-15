package com.tgzjdv.chat;

import com.tgzjdv.chat.render.AvatarProvider;
import com.tgzjdv.chat.render.SideChatRenderer;
import com.tgzjdv.chat.store.ChatStore;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * TGZJDV的聊天框 (TGZJDV's Chat)
 * 一款改进 Minecraft 聊天框的 Fabric 客户端模组
 */
public class TgzjdvChatMod implements ClientModInitializer {

    public static final String MOD_ID = "tgzjdvchat";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitializeClient() {
        LOGGER.info("[TGZJDV's Chat] 模组已加载！TGZJDV的聊天框正在改善您的聊天体验...");


        // 切换服务器/退出世界时清空聊天记录和头像缓存
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            ChatStore.clear();
            AvatarProvider.clearCache();
            SideChatRenderer.resetScroll();
            SideChatRenderer.clearLayoutCache();
            LOGGER.info("[TGZJDV's Chat] 已断开连接，聊天记录已清空");
        });
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            ChatStore.clear();
            AvatarProvider.clearCache();
            SideChatRenderer.resetScroll();
            SideChatRenderer.clearLayoutCache();
        });

        // 退出动画结束后关闭聊天屏幕（tick 阶段，走原版 onClose 完整关闭流程，避免闪烁）
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (SideChatRenderer.consumePendingClose()) {
                SideChatRenderer.setAllowClose();
                net.minecraft.client.gui.screens.Screen screen = SideChatRenderer.getCurrentScreenCompat();
                if (screen != null) {
                    screen.onClose();
                }
            }
        });
    }
}
