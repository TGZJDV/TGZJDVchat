package com.tgzjdv.chat.config;

/**
 * 侧边栏聊天配置（第一版：运行时配置，后续接入配置界面）
 */
public final class SideChatConfig {

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

    /** 无新消息后自动隐藏的延迟（秒） */
    public static int autoHideDelaySeconds = 20;

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
}
