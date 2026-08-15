package com.tgzjdv.chat.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 侧边栏聊天配置（持久化到 config/tgzjdvchat-side.json）
 */
public final class SideChatConfig {

    private static final Path CONFIG_PATH = FabricLoader.getInstance().getConfigDir().resolve("tgzjdvchat-side.json");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    /** 模组总开关 */
    public static boolean enabled = true;

    // ============ 展开模式（按 T 打开聊天时） ============
    /** 微信样式面板宽度（GUI 像素） */
    public static int panelWidth = 320;

    /** 面板与屏幕边缘的间距 */
    public static int panelMargin = 8;

    /** 面板背景不透明度 (0-255) */
    public static int backgroundOpacity = 200;

    // ============ 收起模式（未打开聊天时，左下角小块） ============
    /** 收起模式宽度 */
    public static int collapsedWidth = 260;

    /** 收起模式最大高度 */
    public static int collapsedMaxHeight = 130;

    /** 收起模式背景不透明度（比展开模式更透明） */
    public static int collapsedOpacity = 110;

    /** 无新消息后自动隐藏的延迟（秒），负值表示永不消失 */
    public static int autoHideDelaySeconds = 20;

    /** 是否启用自动隐藏（false 表示永不消失） */
    public static boolean isAutoHideEnabled() {
        return autoHideDelaySeconds >= 0;
    }

    /** 淡入淡出动画时长（帧） */
    public static int fadeTicks = 30;

    /** 收起模式最多显示的消息条数 */
    public static int collapsedMaxMessages = 6;

    // ============ 显示选项 ============
    /** 是否显示时间戳 */
    public static boolean showTimestamp = true;

    /** 是否显示玩家头像 */
    public static boolean showAvatar = true;

    /** 头像尺寸（GUI 像素） */
    public static int avatarSize = 18;

    // ============ 图片发送 ============
    /** 图片发送安全模式（true=安全：链接打断绕过服务器链接检测；false=正常） */
    public static boolean imageSafeMode = false;

    private SideChatConfig() {
    }

    static {
        load();
    }

    /** 加载配置（从 config/tgzjdvchat-side.json，缺失字段用默认值） */
    public static void load() {
        try {
            if (Files.exists(CONFIG_PATH)) {
                JsonObject obj = GSON.fromJson(Files.readString(CONFIG_PATH), JsonObject.class);
                if (obj != null) {
                    enabled = obj.get("enabled") != null ? obj.get("enabled").getAsBoolean() : enabled;
                    panelWidth = obj.get("panelWidth") != null ? obj.get("panelWidth").getAsInt() : panelWidth;
                    panelMargin = obj.get("panelMargin") != null ? obj.get("panelMargin").getAsInt() : panelMargin;
                    backgroundOpacity = obj.get("backgroundOpacity") != null ? obj.get("backgroundOpacity").getAsInt() : backgroundOpacity;
                    collapsedWidth = obj.get("collapsedWidth") != null ? obj.get("collapsedWidth").getAsInt() : collapsedWidth;
                    collapsedMaxHeight = obj.get("collapsedMaxHeight") != null ? obj.get("collapsedMaxHeight").getAsInt() : collapsedMaxHeight;
                    collapsedOpacity = obj.get("collapsedOpacity") != null ? obj.get("collapsedOpacity").getAsInt() : collapsedOpacity;
                    autoHideDelaySeconds = obj.get("autoHideDelaySeconds") != null ? obj.get("autoHideDelaySeconds").getAsInt() : autoHideDelaySeconds;
                    fadeTicks = obj.get("fadeTicks") != null ? obj.get("fadeTicks").getAsInt() : fadeTicks;
                    collapsedMaxMessages = obj.get("collapsedMaxMessages") != null ? obj.get("collapsedMaxMessages").getAsInt() : collapsedMaxMessages;
                    showTimestamp = obj.get("showTimestamp") != null ? obj.get("showTimestamp").getAsBoolean() : showTimestamp;
                    showAvatar = obj.get("showAvatar") != null ? obj.get("showAvatar").getAsBoolean() : showAvatar;
                    avatarSize = obj.get("avatarSize") != null ? obj.get("avatarSize").getAsInt() : avatarSize;
                    imageSafeMode = obj.get("imageSafeMode") != null ? obj.get("imageSafeMode").getAsBoolean() : imageSafeMode;
                }
            }
        } catch (Exception ignored) {
        }
    }

    /** 保存配置 */
    public static void save() {
        try {
            JsonObject obj = new JsonObject();
            obj.addProperty("enabled", enabled);
            obj.addProperty("panelWidth", panelWidth);
            obj.addProperty("panelMargin", panelMargin);
            obj.addProperty("backgroundOpacity", backgroundOpacity);
            obj.addProperty("collapsedWidth", collapsedWidth);
            obj.addProperty("collapsedMaxHeight", collapsedMaxHeight);
            obj.addProperty("collapsedOpacity", collapsedOpacity);
            obj.addProperty("autoHideDelaySeconds", autoHideDelaySeconds);
            obj.addProperty("fadeTicks", fadeTicks);
            obj.addProperty("collapsedMaxMessages", collapsedMaxMessages);
            obj.addProperty("showTimestamp", showTimestamp);
            obj.addProperty("showAvatar", showAvatar);
            obj.addProperty("avatarSize", avatarSize);
            obj.addProperty("imageSafeMode", imageSafeMode);
            Files.writeString(CONFIG_PATH, GSON.toJson(obj));
        } catch (Exception ignored) {
        }
    }

    /** 设置自动隐藏延迟（秒，负值表示永不消失）并持久化 */
    public static void setAutoHideDelaySeconds(int seconds) {
        autoHideDelaySeconds = seconds;
        save();
    }
}
