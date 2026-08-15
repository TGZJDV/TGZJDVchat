package com.tgzjdv.chat.store;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 侧边栏聊天消息存储
 * 线程安全的消息列表，最多保留 MAX_MESSAGES 条历史消息
 */
public final class ChatStore {

    /** 最大保留消息数 */
    public static final int MAX_MESSAGES = 500;

    // CopyOnWriteArrayList：读免复制、线程安全（消息多时打开聊天不卡）
    private static final List<ChatMessage> MESSAGES = new java.util.concurrent.CopyOnWriteArrayList<>();

    /** 最后一条消息到达的时间戳（毫秒），用于自动隐藏判断 */
    private static volatile long lastMessageTimeMillis = System.currentTimeMillis();

    private ChatStore() {
    }

    /** 添加一条消息 */
    public static synchronized void add(ChatMessage message) {
        MESSAGES.add(message);
        lastMessageTimeMillis = message.timeMillis();
        while (MESSAGES.size() > MAX_MESSAGES) {
            MESSAGES.remove(0);
        }
    }

    /** 最后消息时间戳 */
    public static long getLastMessageTimeMillis() {
        return lastMessageTimeMillis;
    }

    /** 获取所有消息（不可修改视图） */
    public static List<ChatMessage> getMessages() {
        return MESSAGES;
    }

    /** 清空消息 */
    public static synchronized void clear() {
        MESSAGES.clear();
    }

    /**
     * 查找指定玩家最近发送的一条玩家消息内容（用于回复引用显示）
     */
    public static synchronized String findLastMessageFrom(String playerName) {
        if (playerName == null) {
            return null;
        }
        for (int i = MESSAGES.size() - 1; i >= 0; i--) {
            ChatMessage msg = MESSAGES.get(i);
            if (msg.isPlayerMessage() && playerName.equalsIgnoreCase(msg.sender())) {
                String text = msg.displayContent().getString();
                if (!text.isEmpty()) {
                    return text;
                }
            }
        }
        return null;
    }
}
