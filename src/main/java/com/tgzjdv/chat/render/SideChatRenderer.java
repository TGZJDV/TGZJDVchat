package com.tgzjdv.chat.render;

import com.tgzjdv.chat.config.ChatAuthConfig;
import com.tgzjdv.chat.config.SideChatConfig;
import com.tgzjdv.chat.store.ChatMessage;
import com.tgzjdv.chat.store.ChatStore;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.util.FormattedCharSequence;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * 侧边栏聊天渲染器
 * <p>
 * 双模式：
 * - 收起模式（聊天未打开）：左下角小块，透明，长时间无消息自动隐藏（类似原版聊天框）
 * - 展开模式（按 T 打开聊天）：左侧微信样式面板（自己消息靠右、别人靠左、系统居中、带头像）
 */
public final class SideChatRenderer {

    // ================= 布局常量（展开模式） =================
    private static final int TITLE_BAR_HEIGHT = 24;
    private static final int INPUT_AREA_HEIGHT = 26;
    private static final int BOTTOM_RESERVED = 45;      // 底部预留（快捷栏区域）
    private static final int MESSAGE_GAP = 6;            // 消息间距
    private static final int BUBBLE_PADDING_X = 9;       // 气泡内边距（水平）
    private static final int BUBBLE_PADDING_Y = 4;       // 气泡内边距（垂直）
    private static final int BUBBLE_RADIUS = 4;          // 气泡圆角
    private static final int AVATAR_GAP = 5;             // 头像与气泡间距
    private static final int PANEL_PADDING = 8;          // 面板内边距

    // ============ 图片消息 ============
    private static final int IMAGE_BOX_W = 200;          // 图片显示宽度
    private static final int IMAGE_BOX_H = 112;          // 图片默认显示高度（未加载时 16:9 占位）
    private static final int IMAGE_PADDING = 4;          // 图片区域内边距

    // ================= 颜色（展开模式） =================
    private static final int COLOR_PANEL_BG = ARGB.color(200, 30, 32, 40);
    private static final int COLOR_TITLE_BG = ARGB.color(215, 40, 43, 54);
    private static final int COLOR_BORDER = ARGB.color(90, 255, 255, 255);
    private static final int COLOR_TITLE_TEXT = 0xFFFFFFFF;
    private static final int COLOR_COUNT_TEXT = 0xFF9AA0A6;
    private static final int COLOR_TIMESTAMP = 0xFF8A9199;
    private static final int COLOR_ACCENT = 0xFF4FC3F7;
    private static final int COLOR_DIVIDER = ARGB.color(70, 255, 255, 255);
    private static final int COLOR_INPUT_BG = ARGB.color(50, 255, 255, 255);
    private static final int COLOR_INPUT_HINT = 0xFF8A9199;
    private static final int COLOR_SELF_BUBBLE = 0xFF95EC69;   // 微信绿（自己）
    private static final int COLOR_OTHER_BUBBLE = 0xFFFFFFFF;  // 白色（别人）
    private static final int COLOR_BUBBLE_TEXT = 0xFF1A1A1A;   // 气泡文字
    private static final int COLOR_SYSTEM_TEXT = 0xFF9AA0A6;   // 系统消息

    // ================= 过渡动画 =================
    private static final long MESSAGE_ANIM_MS = 250;   // 新消息动画时长
    private static final int MESSAGE_ANIM_RISE = 8;     // 新消息上浮距离
    private static float panelFade = 1.0f;              // 面板淡入进度
    private static float panelSlide = 1.0f;              // 面板滑入进度（0→1，带弹跳）
    private static boolean exitAnimating = false;        // 是否退出动画中
    private static float exitProgress = 0.0f;            // 退出动画进度
    private static boolean pendingClose = false;         // 退出动画结束，待 tick 中关闭屏幕（避免渲染中切换导致闪烁）
    private static boolean allowClose = false;           // 允许走原版 onClose 关闭（动画结束后设置，防止循环触发动画）
    private static float channelFade = 1.0f;             // 频道切换消息渐入
    private static float listOpenAnim = 0.0f;            // 频道列表弹出动画

    /** 打开聊天时重置面板动画（淡入 + 滑入） */
    public static void resetPanelFade() {
        panelFade = 0.0f;
        panelSlide = 0.0f;
        exitAnimating = false;
        exitProgress = 0.0f;
        pendingClose = false;
        allowClose = false;
    }

    /** 开始退出动画（从左边滑出屏幕） */
    public static void startExitAnimation() {
        if (exitAnimating) {
            return;
        }
        exitAnimating = true;
        exitProgress = 0.0f;
    }

    /** 是否正在播放退出动画 */
    public static boolean isExitAnimating() {
        return exitAnimating;
    }

    /** 面板是否处于动画变换中（滑入/滑出） */
    private static boolean panelTransformed = false;

    /** 查询面板动画状态（供外部判断是否渲染玻璃按钮） */
    public static boolean isPanelTransformed() {
        return panelTransformed;
    }

    /** Back ease out 缓动（带轻微回弹） */
    private static float easeOutBack(float t) {
        float c1 = 1.70158f;
        float c3 = c1 + 1.0f;
        return 1.0f + c3 * (float) Math.pow(t - 1.0, 3) + c1 * (float) Math.pow(t - 1.0, 2);
    }

    /** 计算面板水平偏移（进入滑入+回弹 / 退出滑出） */
    private static float computePanelOffset() {
        if (exitAnimating) {
            exitProgress = Math.min(1.0f, exitProgress + 0.14f);
            if (exitProgress >= 1.0f) {
                exitAnimating = false;
                // 不在渲染中直接关闭屏幕（会导致闪烁），标记由 tick 阶段处理
                pendingClose = true;
                return -(SideChatConfig.panelWidth);
            }
            return -(SideChatConfig.panelWidth) * exitProgress;
        }
        if (pendingClose) {
            // 待关闭期间保持面板移出屏幕，避免面板弹回原位导致闪烁
            return -(SideChatConfig.panelWidth);
        }
        if (panelSlide < 1.0f) {
            panelSlide = Math.min(1.0f, panelSlide + 0.12f);
            return -(SideChatConfig.panelWidth) * (1.0f - easeOutBack(panelSlide));
        }
        return 0.0f;
    }

    /** 是否正在退出动画或等待关闭 */
    public static boolean isExiting() {
        return exitAnimating || pendingClose;
    }

    /** 是否等待关闭（面板已移出，等待 tick 关闭屏幕） */
    public static boolean isPendingClose() {
        return pendingClose;
    }

    /** 消费"待关闭"标记（在 tick 阶段调用，避免渲染中切换屏幕闪烁） */
    public static boolean consumePendingClose() {
        boolean pending = pendingClose;
        pendingClose = false;
        return pending;
    }

    /** 设置允许走原版 onClose 关闭（动画结束，tick 正式关闭前调用） */
    public static void setAllowClose() {
        allowClose = true;
    }

    /**
     * 获取当前屏幕（26.1.x 用 mc.screen 字段，26.2 用 mc.gui.screen()）
     * 反射兼容两版本
     */
    public static net.minecraft.client.gui.screens.Screen getCurrentScreenCompat() {
        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
        try {
            java.lang.reflect.Field f = net.minecraft.client.Minecraft.class.getDeclaredField("screen");
            f.setAccessible(true);
            return (net.minecraft.client.gui.screens.Screen) f.get(mc);
        } catch (Exception ignored) {
        }
        try {
            java.lang.reflect.Method m = mc.gui.getClass().getMethod("screen");
            return (net.minecraft.client.gui.screens.Screen) m.invoke(mc.gui);
        } catch (Exception ignored) {
        }
        return null;
    }

    /** 消费"允许关闭"标记（onClose 注入中调用，返回 true 则走原版关闭） */
    public static boolean consumeAllowClose() {
        boolean allow = allowClose;
        allowClose = false;
        return allow;
    }

    /** 对颜色应用透明度 */
    private static int withAlpha(int color, float alpha) {
        if (alpha >= 1.0f) {
            return color;
        }
        int a = (color >>> 24) & 0xFF;
        int newA = Math.max(0, Math.min(255, (int) (a * Math.max(0.0f, alpha))));
        return (newA << 24) | (color & 0x00FFFFFF);
    }

    // ================= 收起模式颜色 =================
    private static final int COLOR_COLLAPSED_BG = ARGB.color(90, 0, 0, 0);

    /** 自动隐藏动画进度（0=完全隐藏，1=完全显示） */
    private static float fadeAlpha = 1.0f;

    /** 时间格式化 */
    private static final SimpleDateFormat TIME_FORMAT = new SimpleDateFormat("HH:mm");

    /** 消息滚动偏移（像素），0 = 显示最新消息 */
    private static int scrollOffset = 0;

    /** 当前面板水平偏移（renderExpanded 时更新，供外部元素跟随滑入/滑出动画） */
    private static float currentPanelOffset = 0.0f;

    /** 获取当前面板偏移 */
    public static float getCurrentPanelOffset() {
        return currentPanelOffset;
    }

    /** 滚动条轨道区域（渲染时更新，供点击/拖动使用） */
    private static int scrollTrackX = -1;
    private static int scrollTrackY = -1;
    private static int scrollTrackH = -1;
    private static int scrollTotalH = 0;

    /** 滚动条拖动状态 */
    private static boolean scrollDragging = false;
    private static int dragStartMouseY = 0;
    private static int dragStartOffset = 0;
    private static int scrollThumbH = 0;   // 滑块高度（渲染时记录）

    /** 是否在滚动条轨道上 */
    public static boolean isInsideScrollTrack(int mouseX, int mouseY) {
        return scrollTrackX >= 0
                && mouseX >= scrollTrackX - 5 && mouseX <= scrollTrackX + 5
                && mouseY >= scrollTrackY && mouseY <= scrollTrackY + scrollTrackH;
    }

    /** 点击滚动条轨道：跳转到对应滚动位置（顶部=最早，底部=最新） */
    public static void scrollTrackClick(int mouseY) {
        if (scrollTrackH <= 0 || scrollTotalH <= 0) {
            return;
        }
        int viewport = getViewportHeight();
        if (scrollTotalH <= viewport) {
            scrollOffset = 0;
            return;
        }
        float t = (mouseY - scrollTrackY) / (float) scrollTrackH;
        scrollOffset = Math.max(0, Math.min((int) ((scrollTotalH - viewport) * (1.0f - t)), scrollTotalH - viewport));
    }

    /** 开始拖动滚动条（按下时记录起点） */
    public static void beginScrollDrag(int mouseY) {
        scrollDragging = true;
        dragStartMouseY = mouseY;
        dragStartOffset = scrollOffset;
    }

    /** 更新拖动（鼠标移动时跟随） */
    public static void updateScrollDrag(int mouseY) {
        if (!scrollDragging || scrollTrackH <= 0) {
            return;
        }
        int viewport = getViewportHeight();
        int maxOffset = Math.max(0, scrollTotalH - viewport);
        if (maxOffset <= 0) {
            return;
        }
        // 鼠标向下拖 → 滑块下移 → 显示最新（offset 减小）
        // 滑块可移动范围是 trackH - thumbH，因此用该值作为换算分母（保证 1:1 跟手）
        float moveRange = Math.max(1, scrollTrackH - scrollThumbH);
        float pixelsToOffset = maxOffset / moveRange;
        scrollOffset = Math.max(0, Math.min(dragStartOffset - (int) ((mouseY - dragStartMouseY) * pixelsToOffset), maxOffset));
    }

    /** 结束拖动 */
    public static void endScrollDrag() {
        scrollDragging = false;
    }

    /** 是否正在拖动滚动条 */
    public static boolean isScrollDragging() {
        return scrollDragging;
    }

    /**
     * 消息布局缓存（避免每帧重复文本布局/组件剥离，修复消息多时掉帧）
     * 消息对象不变时复用缓存
     */
    private static final Map<ChatMessage, CachedLayout> LAYOUT_CACHE = new java.util.IdentityHashMap<>();

    /** 缓存的消息布局 */
    private static final class CachedLayout {
        Component displayContent;   // 玩家消息剥离后的内容（系统消息为原始内容）
        List<FormattedCharSequence> lines; // split 结果
        int nameH;                  // 名字行高度
        int bubbleH;                // 气泡高度（不含引用行）
        int maxLineWidth = -1;      // 最大行宽（首次计算后缓存，避免每帧 font.width）
        // 回复解析缓存（避免每帧重复 getString/正则）
        String replyTo = null;      // 我们自己的回复目标
        String replyQuote = null;   // 引用内容
        boolean chatProReply = false; // Chat Pro 回复
        String chatProQuote = null;
    }

    /** 系统消息换行宽度 */
    private static int getSysWidth() {
        return Math.max(20, getPanelRight() - getPanelLeft() - 20);
    }

    /** 获取（或计算）消息布局缓存 */
    private static CachedLayout getLayout(ChatMessage msg, Font font, int textMaxWidth, boolean showAvatar, int avatarSize) {
        CachedLayout cached = LAYOUT_CACHE.get(msg);
        if (cached != null) {
            return cached;
        }
        CachedLayout layout = new CachedLayout();
        if (msg.isImageMessage()) {
            // 图片消息：图片区域 + 名字行
            layout.displayContent = null;
            layout.lines = null;
            int[] dims = com.tgzjdv.chat.image.ImageCache.getDimensions(msg.getImageUrl());
            int imgH = IMAGE_BOX_H;
            if (dims != null && dims[0] > 0) {
                imgH = Math.max(40, (int) (IMAGE_BOX_W * (dims[1] / (float) dims[0])));
            }
            layout.bubbleH = imgH + IMAGE_PADDING * 2;
            layout.nameH = msg.sender().isEmpty() ? 0 : font.lineHeight + 1;
        } else if (msg.isPlayerMessage()) {
            layout.displayContent = msg.displayContent();
            layout.lines = font.split(layout.displayContent, textMaxWidth);
            layout.bubbleH = Math.max(layout.lines.size() * font.lineHeight + BUBBLE_PADDING_Y * 2, showAvatar ? avatarSize : 0);
            layout.nameH = msg.sender().isEmpty() ? 0 : font.lineHeight + 1;
            // 一次性解析回复信息（避免每帧重复 getString/正则/遍历）
            layout.replyTo = msg.getReplyTarget();
            if (layout.replyTo != null) {
                layout.replyQuote = msg.getQuoteContent();
                if (layout.replyQuote == null) {
                    layout.replyQuote = ChatStore.findLastMessageFrom(layout.replyTo);
                }
            }
            if (layout.replyQuote == null && msg.isChatProReply()) {
                layout.chatProReply = true;
                layout.chatProQuote = msg.getChatProReplyQuote();
            }
        } else {
            layout.displayContent = msg.content();
            layout.lines = font.split(msg.content(), getSysWidth());
        }
        LAYOUT_CACHE.put(msg, layout);
        return layout;
    }

    /** 清空布局缓存（服务器切换/频道切换时调用） */
    public static void clearLayoutCache() {
        LAYOUT_CACHE.clear();
    }

    /** 消息点击区域记录（每次渲染重建，供点击检测使用） */
    private static final List<HitArea> HIT_AREAS = new java.util.ArrayList<>();

    /** 头像点击区域记录（右键 @ 玩家用） */
    private static final List<AvatarHitArea> AVATAR_HIT_AREAS = new java.util.ArrayList<>();

    /** 行级点击区域（按行记录，支持字符级链接点击） */
    public static final class HitArea {
        public final int x;
        public final int y;
        public final FormattedCharSequence line;
        public final ChatMessage message;

        HitArea(int x, int y, FormattedCharSequence line, ChatMessage message) {
            this.x = x;
            this.y = y;
            this.line = line;
            this.message = message;
        }
    }

    /** 头像点击区域 */
    public static final class AvatarHitArea {
        public final int x;
        public final int y;
        public final int size;
        public final String playerName;

        AvatarHitArea(int x, int y, int size, String playerName) {
            this.x = x;
            this.y = y;
            this.size = size;
            this.playerName = playerName;
        }
    }

    /** 图片点击区域（点击放大查看） */
    public static final class ImageHitArea {
        public final int x;
        public final int y;
        public final int width;
        public final int height;
        public final String imageUrl;
        public final String sender;

        ImageHitArea(int x, int y, int width, int height, String imageUrl, String sender) {
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
            this.imageUrl = imageUrl;
            this.sender = sender;
        }
    }

    /** 图片点击区域列表（每次渲染重建） */
    private static final List<ImageHitArea> IMAGE_HIT_AREAS = new java.util.ArrayList<>();

    /** 检测点击位置命中的图片（放大查看） */
    public static String pickImageAt(int mouseX, int mouseY) {
        ImageHitArea area = pickImageAreaAt(mouseX, mouseY);
        return area == null ? null : area.imageUrl;
    }

    /** 检测点击位置命中的图片区域（含发送者） */
    public static ImageHitArea pickImageAreaAt(int mouseX, int mouseY) {
        for (int i = IMAGE_HIT_AREAS.size() - 1; i >= 0; i--) {
            ImageHitArea area = IMAGE_HIT_AREAS.get(i);
            if (mouseX >= area.x && mouseX <= area.x + area.width
                    && mouseY >= area.y && mouseY <= area.y + area.height) {
                return area;
            }
        }
        return null;
    }

    // ============ 图片右键菜单 ============
    private static String imgMenuUrl = null;
    private static String imgMenuSender = null;
    private static int imgMenuX = 0;
    private static int imgMenuY = 0;
    private static String imgMenuActionUrl = null;
    private static float imgMenuAnim = 0.0f;  // 图片菜单弹出动画
    private static final int IMG_MENU_WIDTH = 130;
    private static final int IMG_MENU_ITEM_HEIGHT = 16;
    private static final int IMG_MENU_ITEM_COUNT = 3;

    /** 打开图片右键菜单 */
    public static void openImageMenu(String url, String sender, int x, int y) {
        imgMenuUrl = url;
        imgMenuSender = sender;
        imgMenuX = x;
        imgMenuY = y;
        imgMenuActionUrl = null;
        imgMenuAnim = 0.0f;
    }

    /** 图片菜单是否打开 */
    public static boolean isImageMenuOpen() {
        return imgMenuUrl != null;
    }

    /** 处理图片菜单点击，返回操作类型：reply / copyurl / copyfile / close / null */
    public static String handleImageMenuClick(int mouseX, int mouseY) {
        if (imgMenuUrl == null) {
            return null;
        }
        if (mouseX >= imgMenuX && mouseX <= imgMenuX + IMG_MENU_WIDTH
                && mouseY >= imgMenuY && mouseY <= imgMenuY + IMG_MENU_ITEM_COUNT * IMG_MENU_ITEM_HEIGHT) {
            int idx = (mouseY - imgMenuY) / IMG_MENU_ITEM_HEIGHT;
            // 先保存 URL/Sender 供调用方使用，再关闭菜单
            imgMenuActionUrl = imgMenuUrl;
            imgMenuSender = imgMenuSender;
            imgMenuUrl = null;
            if (idx == 0) {
                return "reply";
            }
            if (idx == 1) {
                return "copyurl";
            }
            if (idx == 2) {
                return "copyfile";
            }
            return "close";
        }
        imgMenuUrl = null;
        return "close";
    }

    /** 图片菜单 URL（点击后的目标，菜单关闭后仍可用） */
    public static String getImageMenuUrl() {
        return imgMenuActionUrl;
    }

    /** 图片菜单发送者 */
    public static String getImageMenuSender() {
        return imgMenuSender;
    }

    /** 渲染所有右键菜单（玩家/图片/消息），在按钮之后调用保证最顶层 */
    public static void renderAllMenus(GuiGraphicsExtractor graphics, Font font, int mouseX, int mouseY) {
        renderMenuIfOpen(graphics, font, mouseX, mouseY);
        renderImageMenu(graphics, font, mouseX, mouseY);
        renderMessageMenu(graphics, font, mouseX, mouseY);
    }

    /** 渲染图片右键菜单 */
    public static void renderImageMenu(GuiGraphicsExtractor graphics, Font font, int mouseX, int mouseY) {
        if (imgMenuUrl == null) {
            return;
        }
        imgMenuAnim = Math.min(1.0f, imgMenuAnim + 0.3f);
        int slide = (int) ((1.0f - imgMenuAnim) * 8);
        int mY = imgMenuY + slide;
        int menuBottom = mY + IMG_MENU_ITEM_COUNT * IMG_MENU_ITEM_HEIGHT;
        drawRoundedRect(graphics, imgMenuX, mY, imgMenuX + IMG_MENU_WIDTH, menuBottom, 3, withAlpha(COLOR_MENU_BG, imgMenuAnim));
        graphics.horizontalLine(imgMenuX, imgMenuX + IMG_MENU_WIDTH, mY, withAlpha(0xFFAAAAAA, imgMenuAnim));
        graphics.horizontalLine(imgMenuX, imgMenuX + IMG_MENU_WIDTH, menuBottom, withAlpha(0xFFAAAAAA, imgMenuAnim));
        graphics.verticalLine(imgMenuX, mY, menuBottom, withAlpha(0xFFAAAAAA, imgMenuAnim));
        graphics.verticalLine(imgMenuX + IMG_MENU_WIDTH, mY, menuBottom, withAlpha(0xFFAAAAAA, imgMenuAnim));

        String[] items = {"\u56de\u590d", "\u590d\u5236\u56fe\u7247\u5730\u5740", "\u590d\u5236\u56fe\u7247\u6587\u4ef6"};
        for (int i = 0; i < items.length; i++) {
            int itemY = mY + i * IMG_MENU_ITEM_HEIGHT;
            if (mouseX >= imgMenuX && mouseX <= imgMenuX + IMG_MENU_WIDTH
                    && mouseY >= itemY && mouseY <= itemY + IMG_MENU_ITEM_HEIGHT) {
                graphics.fill(imgMenuX, itemY, imgMenuX + IMG_MENU_WIDTH, itemY + IMG_MENU_ITEM_HEIGHT, withAlpha(COLOR_MENU_HOVER, imgMenuAnim));
            }
            graphics.text(font, items[i], imgMenuX + 6, itemY + 3, withAlpha(0xFFFFFFFF, imgMenuAnim), false);
        }
    }

    // ============ 消息右键菜单 ============
    private static ChatMessage msgMenuTarget = null;
    private static int msgMenuX = 0;
    private static int msgMenuY = 0;
    private static ChatMessage msgMenuActionMsg = null;
    private static float msgMenuAnim = 0.0f;  // 消息菜单弹出动画
    private static final int MSG_MENU_WIDTH = 140;
    private static final int MSG_MENU_ITEM_HEIGHT = 16;

    /** 打开消息右键菜单（玩家消息：回复+复制；系统消息：仅复制） */
    public static void openMessageMenu(ChatMessage msg, int x, int y) {
        msgMenuTarget = msg;
        msgMenuX = x;
        msgMenuY = y;
        msgMenuActionMsg = null;
        msgMenuAnim = 0.0f;
    }

    /** 消息菜单是否打开 */
    public static boolean isMessageMenuOpen() {
        return msgMenuTarget != null;
    }

    /** 处理消息菜单点击，返回：reply / copy / close / null */
    public static String handleMessageMenuClick(int mouseX, int mouseY) {
        if (msgMenuTarget == null) {
            return null;
        }
        boolean isPlayer = msgMenuTarget.isPlayerMessage();
        int itemCount = isPlayer ? 2 : 1;
        if (mouseX >= msgMenuX && mouseX <= msgMenuX + MSG_MENU_WIDTH
                && mouseY >= msgMenuY && mouseY <= msgMenuY + itemCount * MSG_MENU_ITEM_HEIGHT) {
            int idx = (mouseY - msgMenuY) / MSG_MENU_ITEM_HEIGHT;
            msgMenuActionMsg = msgMenuTarget;
            msgMenuTarget = null;
            if (isPlayer && idx == 0) {
                return "reply";
            }
            if (idx == (isPlayer ? 1 : 0)) {
                return "copy";
            }
            return "close";
        }
        msgMenuTarget = null;
        return "close";
    }

    /** 消息菜单目标消息（点击后） */
    public static ChatMessage getMessageMenuTarget() {
        return msgMenuActionMsg;
    }

    /** 渲染消息右键菜单 */
    public static void renderMessageMenu(GuiGraphicsExtractor graphics, Font font, int mouseX, int mouseY) {
        if (msgMenuTarget == null) {
            return;
        }
        msgMenuAnim = Math.min(1.0f, msgMenuAnim + 0.3f);
        int slide = (int) ((1.0f - msgMenuAnim) * 8);
        int mY = msgMenuY + slide;
        boolean isPlayer = msgMenuTarget.isPlayerMessage();
        int itemCount = isPlayer ? 2 : 1;
        int menuBottom = mY + itemCount * MSG_MENU_ITEM_HEIGHT;
        drawRoundedRect(graphics, msgMenuX, mY, msgMenuX + MSG_MENU_WIDTH, menuBottom, 3, withAlpha(COLOR_MENU_BG, msgMenuAnim));
        graphics.horizontalLine(msgMenuX, msgMenuX + MSG_MENU_WIDTH, mY, withAlpha(0xFFAAAAAA, msgMenuAnim));
        graphics.horizontalLine(msgMenuX, msgMenuX + MSG_MENU_WIDTH, menuBottom, withAlpha(0xFFAAAAAA, msgMenuAnim));
        graphics.verticalLine(msgMenuX, mY, menuBottom, withAlpha(0xFFAAAAAA, msgMenuAnim));
        graphics.verticalLine(msgMenuX + MSG_MENU_WIDTH, mY, menuBottom, withAlpha(0xFFAAAAAA, msgMenuAnim));

        String[] items = isPlayer ? new String[]{"\u56de\u590d", "\u590d\u5236\u6d88\u606f\u5185\u5bb9"}
                : new String[]{"\u590d\u5236\u6d88\u606f\u5185\u5bb9"};
        for (int i = 0; i < items.length; i++) {
            int itemY = mY + i * MSG_MENU_ITEM_HEIGHT;
            if (mouseX >= msgMenuX && mouseX <= msgMenuX + MSG_MENU_WIDTH
                    && mouseY >= itemY && mouseY <= itemY + MSG_MENU_ITEM_HEIGHT) {
                graphics.fill(msgMenuX, itemY, msgMenuX + MSG_MENU_WIDTH, itemY + MSG_MENU_ITEM_HEIGHT, withAlpha(COLOR_MENU_HOVER, msgMenuAnim));
            }
            graphics.text(font, items[i], msgMenuX + 6, itemY + 3, withAlpha(0xFFFFFFFF, msgMenuAnim), false);
        }
    }

    /** Chat Heads 等模组的名字标记：如 [TGZJDV head] */
    private static final java.util.regex.Pattern CHAT_HEADS_MARKER =
            java.util.regex.Pattern.compile("\\[[^\\]]*\\s[Hh]ead\\]");

    // ============ 频道状态（公共 / 私聊，支持多个私聊对象） ============
    private static String privateTarget = null;            // 当前频道（null = 公共）
    private static final java.util.Set<String> PRIVATE_TARGETS = new java.util.HashSet<>(); // 所有私聊过的对象

    /** 未读标记："public" 表示公共频道，玩家名表示私聊频道 */
    private static final java.util.Map<String, Boolean> UNREAD = new java.util.HashMap<>();

    // 频道按钮区域（标题栏，供点击检测）
    private static int channelBtnX = -1;
    private static int channelBtnY = -1;
    private static int channelBtnW = 0;
    private static int channelBtnH = 14;

    // 服务器功能按钮区域（标题栏）
    private static int serverBtnX = -1;
    private static int serverBtnY = -1;
    private static int serverBtnW = 0;
    private static int serverBtnH = 14;
    private static boolean serverListOpen = false;

    /** 是否有列表菜单打开（服务器/频道/家列表） */
    public static boolean isAnyListMenuOpen() {
        return serverListOpen || homeListOpen || channelListOpen;
    }

    private static int homeListX = 0;
    private static int homeListY = 0;

    /** 点击位置是否在服务器功能按钮上 */
    public static boolean isInsideServerButton(int mouseX, int mouseY) {
        return serverBtnX >= 0
                && mouseX >= serverBtnX && mouseX <= serverBtnX + serverBtnW
                && mouseY >= serverBtnY && mouseY <= serverBtnY + serverBtnH;
    }

    private static float serverListAnim = 0.0f; // 服务器列表弹出动画
    private static boolean homeListOpen = false;  // 家列表是否打开
    private static float homeListAnim = 0.0f;     // 家列表弹出动画

    /** 打开/关闭家列表（在服务器按钮下方弹出） */
    public static void toggleHomeList() {
        homeListOpen = !homeListOpen;
        if (homeListOpen) {
            homeListAnim = 0.0f;
            homeListX = serverBtnX;
            homeListY = serverBtnY + serverBtnH + 2;
            serverListOpen = false;
        }
    }

    /** 处理家列表点击：返回 "add" 或家名 或 null */
    public static String handleHomeListClick(int mouseX, int mouseY) {
        if (!homeListOpen) {
            return null;
        }
        int itemCount = ChatAuthConfig.getHomes().size() + 1; // 家 + 添加按钮
        int listX = homeListX;
        int listY = homeListY;
        int listW = 150;
        int listH = itemCount * 18 + 4;
        if (mouseX >= listX && mouseX <= listX + listW && mouseY >= listY && mouseY <= listY + listH) {
            int idx = (mouseY - listY - 2) / 18;
            homeListOpen = false;
            if (idx >= 0 && idx < ChatAuthConfig.getHomes().size()) {
                return ChatAuthConfig.getHomes().get(idx);
            }
            if (idx == ChatAuthConfig.getHomes().size()) {
                return "add";
            }
        }
        homeListOpen = false;
        return "close";
    }

    /** 打开/关闭服务器功能列表 */
    public static void toggleServerList() {
        serverListOpen = !serverListOpen;
        if (serverListOpen) {
            serverListAnim = 0.0f;
        }
        channelListOpen = false;
    }

    /** 发送服务器命令（/dback、/back、/l 等） */
    public static void sendCommand(String command) {
        if (command == null || command.isEmpty()) {
            return;
        }
        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
        if (mc.player != null && mc.player.connection != null) {
            mc.player.connection.sendCommand(command);
        }
    }

    /**
     * 设置屏幕（26.1.x 用 setScreen，26.2+ 用 setScreenAndShow）
     * 反射兼容两版本
     */
    public static void setScreenCompat(net.minecraft.client.gui.screens.Screen screen) {
        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
        try {
            // 注意：必须用 (Object) screen 强制 varargs 单元素数组；直接传 null 会被当作空参数数组导致调用失败
            mc.getClass().getMethod("setScreenAndShow", net.minecraft.client.gui.screens.Screen.class).invoke(mc, (Object) screen);
            return;
        } catch (Exception ignored) {
        }
        try {
            mc.getClass().getMethod("setScreen", net.minecraft.client.gui.screens.Screen.class).invoke(mc, (Object) screen);
        } catch (Exception e) {
            // 兜底：直接写字段
            try {
                java.lang.reflect.Field f = net.minecraft.client.Minecraft.class.getDeclaredField("screen");
                f.setAccessible(true);
                f.set(mc, screen);
            } catch (Exception ignored) {
            }
        }
    }

    /** 发送登录命令（/l 密码） */
    public static void sendLogin(String password) {
        if (password == null || password.isEmpty()) {
            return;
        }
        sendCommand(com.tgzjdv.chat.config.ChatAuthConfig.getLoginCommand() + " " + password);
    }

    /**
     * 处理服务器功能列表点击
     *
     * @return "login" 登录 / "settings" 设置密码 / "close" 关闭 / null 未点击
     */
    public static String handleServerListClick(int mouseX, int mouseY) {
        if (!serverListOpen) {
            return null;
        }
        int listX = serverBtnX;
        int listY = serverBtnY + serverBtnH + 2;
        int listH = 4 * 18 + 4;
        if (mouseX >= listX && mouseX <= listX + 150 && mouseY >= listY && mouseY <= listY + listH) {
            int idx = (mouseY - listY - 2) / 18;
            serverListOpen = false;
            if (idx == 0) {
                return "login";
            }
            if (idx == 1) {
                return "dback";
            }
            if (idx == 2) {
                return "back";
            }
            if (idx == 3) {
                return "home";
            }
            if (idx == 4) {
                return "close";
            }
        }
        serverListOpen = false;
        return "close";
    }

    /** 渲染标题栏的服务器功能按钮 */
    private static void renderServerButton(GuiGraphicsExtractor graphics, Font font, int right, int top, int mouseX, int mouseY) {
        String text = "服务器";
        int btnW = font.width(text) + 14;
        int btnX = channelBtnX - btnW - 4;
        int btnY = top + (TITLE_BAR_HEIGHT - serverBtnH) / 2;
        serverBtnX = btnX;
        serverBtnY = btnY;
        serverBtnW = btnW;
        boolean hovered = mouseX >= btnX && mouseX <= btnX + btnW && mouseY >= btnY && mouseY <= btnY + serverBtnH;
        graphics.fill(btnX, btnY, btnX + btnW, btnY + serverBtnH,
                hovered ? ARGB.color(90, 90, 150, 255) : ARGB.color(50, 255, 255, 255));
        graphics.text(font, text, btnX + 7, btnY + 2, 0xFFFFFFFF, false);
    }

    /** 渲染家列表（已保存的家 + 添加新家按钮），带弹出动画 */
    private static void renderHomeList(GuiGraphicsExtractor graphics, Font font) {
        if (!homeListOpen) {
            return;
        }
        homeListAnim = Math.min(1.0f, homeListAnim + 0.25f);
        int itemCount = ChatAuthConfig.getHomes().size() + 1;
        int listX = homeListX;
        int listY = homeListY + (int) ((1.0f - homeListAnim) * 6);
        int listW = 150;
        int listH = itemCount * 18 + 4;
        int bg = withAlpha(COLOR_MENU_BG, homeListAnim);
        int border = withAlpha(0xFFAAAAAA, homeListAnim);
        int textColor = withAlpha(0xFFFFFFFF, homeListAnim);
        drawRoundedRect(graphics, listX, listY, listX + listW, listY + listH, 3, bg);
        graphics.horizontalLine(listX, listX + listW, listY, border);
        graphics.horizontalLine(listX, listX + listW, listY + listH, border);
        graphics.verticalLine(listX, listY, listY + listH, border);
        graphics.verticalLine(listX + listW, listY, listY + listH, border);
        int y = listY + 2;
        // 已保存的家
        for (String home : ChatAuthConfig.getHomes()) {
            graphics.text(font, "\u26f8 " + home, listX + 8, y + 2, textColor, false);
            y += 18;
        }
        // 添加新家按钮
        graphics.text(font, "\uff0b 添加新家", listX + 8, y + 2, withAlpha(0xFF4FC3F7, homeListAnim), false);
    }

    /** 渲染服务器功能列表（登录 / 死亡点 / 返回 / 设置），带弹出动画 */
    private static void renderServerList(GuiGraphicsExtractor graphics, Font font) {
        if (!serverListOpen) {
            return;
        }
        serverListAnim = Math.min(1.0f, serverListAnim + 0.25f);
        int listX = serverBtnX;
        int listY = serverBtnY + serverBtnH + 2 + (int) ((1.0f - serverListAnim) * 6);
        int listW = 150;
        int listH = 4 * 18 + 4;
        int bg = withAlpha(COLOR_MENU_BG, serverListAnim);
        int border = withAlpha(0xFFAAAAAA, serverListAnim);
        int textColor = withAlpha(0xFFFFFFFF, serverListAnim);
        drawRoundedRect(graphics, listX, listY, listX + listW, listY + listH, 3, bg);
        graphics.horizontalLine(listX, listX + listW, listY, border);
        graphics.horizontalLine(listX, listX + listW, listY + listH, border);
        graphics.verticalLine(listX, listY, listY + listH, border);
        graphics.verticalLine(listX + listW, listY, listY + listH, border);
        // 登录
        String loginText = "登录 ( /l 密码 )" + (ChatAuthConfig.hasPassword() ? "" : " [未设置]");
        graphics.text(font, loginText, listX + 8, listY + 3, textColor, false);
        // 回到死亡点
        graphics.text(font, "回到死亡点 ( /dback )", listX + 8, listY + 18 + 3, textColor, false);
        // 返回上一个位置
        graphics.text(font, "返回上一个位置 ( /back )", listX + 8, listY + 36 + 3, textColor, false);
        // 回家
        graphics.text(font, "回家", listX + 8, listY + 54 + 3, textColor, false);
    }




    // 频道切换列表状态
    private static boolean channelListOpen = false;
    private static int listX = 0;
    private static int listY = 0;
    private static final int LIST_ITEM_H = 18;
    private static final int LIST_W = 140;

    /** 服务器按钮位置（供外部定位设置按钮） */
    public static int getServerBtnX() {
        return serverBtnX;
    }

    public static int getServerBtnY() {
        return serverBtnY;
    }

    public static int getServerBtnH() {
        return serverBtnH;
    }

    /** 设置按钮位置（服务器按钮左侧，样式与服务器/频道按钮一致） */
    private static int settingsBtnX = -1;
    private static int settingsBtnY = -1;
    private static int settingsBtnW = 0;
    private static int settingsBtnH = 14;

    public static int getSettingsBtnX() {
        return settingsBtnX;
    }

    public static int getSettingsBtnY() {
        return settingsBtnY;
    }

    public static int getSettingsBtnW() {
        return settingsBtnW;
    }

    public static int getSettingsBtnH() {
        return settingsBtnH;
    }

    /** 渲染标题栏设置按钮（文字"设置"，样式与服务器/频道按钮一致） */
    private static void renderTitleSettingsButton(GuiGraphicsExtractor graphics, Font font, int mouseX, int mouseY) {
        String text = "\u8bbe\u7f6e";
        int btnW = font.width(text) + 14;
        int btnX = serverBtnX >= 0 ? serverBtnX - btnW - 4 : -1000;
        int btnY = serverBtnY;
        settingsBtnX = btnX;
        settingsBtnY = btnY;
        settingsBtnW = btnW;
        settingsBtnH = serverBtnH;
        if (btnX < 0) {
            return;
        }
        boolean hovered = mouseX >= btnX && mouseX <= btnX + btnW && mouseY >= btnY && mouseY <= btnY + settingsBtnH;
        // 样式与服务器/频道按钮一致：半透明背景 + 文字
        graphics.fill(btnX, btnY, btnX + btnW, btnY + settingsBtnH,
                hovered ? ARGB.color(90, 90, 150, 255) : ARGB.color(50, 255, 255, 255));
        graphics.text(font, text, btnX + 7, btnY + 2, 0xFFFFFFFF, false);
    }

    /** 是否私聊频道 */
    public static boolean isPrivateChannel() {
        return privateTarget != null;
    }

    /** 当前私聊对象（公共频道返回 null） */
    public static String getPrivateTarget() {
        return privateTarget;
    }

    /** 处理收到的私聊：加入私聊列表 + 标记未读（不自动切换频道） */
    public static void handlePrivateMessage(String target) {
        if (target == null || target.isEmpty()) {
            return;
        }
        PRIVATE_TARGETS.add(target);
        // 当前不在该私聊频道时标记未读
        if (!target.equalsIgnoreCase(privateTarget)) {
            UNREAD.put(target, true);
        }
    }

    /** 收到公共消息时标记公共频道未读（仅当当前不在公共频道时） */
    public static void markPublicUnread() {
        if (privateTarget != null) {
            UNREAD.put("public", true);
        }
    }

    /** 将玩家加入私聊对象列表 */
    public static void addPrivateTarget(String name) {
        if (name != null && !name.isEmpty()) {
            PRIVATE_TARGETS.add(name);
        }
    }

    /** 切换频道（target = null 表示公共频道），并清除该频道未读 */
    public static void switchChannel(String target) {
        privateTarget = target;
        UNREAD.remove(target == null ? "public" : target);
        channelListOpen = false;
        channelFade = 0.0f; // 触发频道切换渐入动画
        resetScroll();
    }

    /** 打开/关闭频道切换列表 */
    public static void toggleChannelList() {
        channelListOpen = !channelListOpen;
        if (channelListOpen) {
            listOpenAnim = 0.0f; // 触发弹出动画
        }
    }

    /** 是否有其他频道未读（当前频道之外） */
    public static boolean hasUnread() {
        for (java.util.Map.Entry<String, Boolean> e : UNREAD.entrySet()) {
            if (!Boolean.TRUE.equals(e.getValue())) {
                continue;
            }
            String key = e.getKey();
            if ("public".equals(key)) {
                if (privateTarget != null) {
                    return true;
                }
            } else {
                if (!key.equalsIgnoreCase(privateTarget)) {
                    return true;
                }
            }
        }
        return false;
    }

    /** 点击位置是否在频道按钮上 */
    public static boolean isInsideChannelButton(int mouseX, int mouseY) {
        return channelBtnX >= 0
                && mouseX >= channelBtnX && mouseX <= channelBtnX + channelBtnW
                && mouseY >= channelBtnY && mouseY <= channelBtnY + channelBtnH;
    }

    /** 处理频道列表点击，返回选中的频道（"public" 或玩家名，或 null） */
    public static String handleChannelListClick(int mouseX, int mouseY) {
        if (!channelListOpen) {
            return null;
        }
        int itemCount = 1 + PRIVATE_TARGETS.size();
        int listH = itemCount * LIST_ITEM_H + 4;
        if (mouseX >= listX && mouseX <= listX + LIST_W
                && mouseY >= listY && mouseY <= listY + listH) {
            int idx = (mouseY - listY - 2) / LIST_ITEM_H;
            channelListOpen = false;
            if (idx == 0) {
                return "public";
            }
            int i = 1;
            for (String name : PRIVATE_TARGETS) {
                if (i++ == idx) {
                    return name;
                }
            }
        }
        channelListOpen = false;
        return "close";
    }

    /** 根据当前频道过滤消息：私聊频道只显示相关私聊；公共频道过滤掉私聊 */
    // 频道过滤缓存（消息数/私聊目标不变时复用，避免每帧遍历）
    private static List<ChatMessage> filterCache = null;
    private static int filterCacheSize = -1;
    private static String filterCacheTarget = null;

    private static List<ChatMessage> filterForChannel(List<ChatMessage> messages) {
        if (filterCache != null && filterCacheSize == messages.size()
                && java.util.Objects.equals(filterCacheTarget, privateTarget)) {
            return filterCache;
        }
        List<ChatMessage> result;
        if (privateTarget != null) {
            List<ChatMessage> filtered = new java.util.ArrayList<>();
            for (ChatMessage m : messages) {
                if (isPrivateRelevant(m, privateTarget)) {
                    filtered.add(m);
                }
            }
            result = filtered;
        } else {
            // 公共频道：过滤掉私聊消息
            List<ChatMessage> publicOnly = new java.util.ArrayList<>();
            for (ChatMessage m : messages) {
                if (!m.isPrivateMessage()) {
                    publicOnly.add(m);
                }
            }
            result = publicOnly;
        }
        filterCache = result;
        filterCacheSize = messages.size();
        filterCacheTarget = privateTarget;
        return result;
    }

    /** 消息是否与私聊相关（私聊频道过滤用，只包含真正的私聊消息） */
    public static boolean isPrivateRelevant(ChatMessage msg, String target) {
        if (target == null || target.isEmpty()) {
            return false;
        }
        String text = msg.content().getString();
        if (isSelf(msg.sender())) {
            // 自己发的私聊：/tell 目标 或 私聊回显（你悄悄地对X说 / 你 → X）
            String lower = text.toLowerCase();
            if (lower.contains("/tell " + target.toLowerCase())) {
                return true;
            }
            return (text.contains("\u6084\u6084\u5730\u5bf9") || text.contains("\u2192") || text.contains("->"))
                    && text.contains(target);
        }
        // 对方发的：必须是私聊格式且指向目标
        String sender = detectPrivateMessage(text);
        return sender != null && sender.equalsIgnoreCase(target);
    }

    /** 渲染标题栏的频道按钮 */

    /** 按钮用的液态玻璃样式（较亮、小型） */

    /** 渲染频道切换列表（点击频道按钮后弹出：公共 + 每个私聊对象带头像） */
    private static void renderChannelList(GuiGraphicsExtractor graphics, Font font) {
        if (!channelListOpen) {
            return;
        }
        // 弹出动画：渐入 + 从按钮位置轻微展开
        listOpenAnim = Math.min(1.0f, listOpenAnim + 0.25f);
        int itemCount = 1 + PRIVATE_TARGETS.size();
        int listH = itemCount * LIST_ITEM_H + 4;
        listX = channelBtnX;
        listY = channelBtnY + channelBtnH + 2 + (int) ((1.0f - listOpenAnim) * 6);
        int listBg = withAlpha(COLOR_MENU_BG, listOpenAnim);
        int listBorder = withAlpha(0xFFAAAAAA, listOpenAnim);
        int listHover = withAlpha(COLOR_MENU_HOVER, listOpenAnim);
        drawRoundedRect(graphics, listX, listY, listX + LIST_W, listY + listH, 3, listBg);
        graphics.horizontalLine(listX, listX + LIST_W, listY, listBorder);
        graphics.horizontalLine(listX, listX + LIST_W, listY + listH, listBorder);
        graphics.verticalLine(listX, listY, listY + listH, listBorder);
        graphics.verticalLine(listX + LIST_W, listY, listY + listH, listBorder);

        int y = listY + 2;
        // 公共频道
        boolean currentPublic = privateTarget == null;
        if (currentPublic) {
            graphics.fill(listX, y, listX + LIST_W, y + LIST_ITEM_H, listHover);
        }
        graphics.text(font, currentPublic ? "✔ 公共" : "公共", listX + 8, y + 2, withAlpha(0xFFFFFFFF, listOpenAnim), false);
        // 公共频道未读红点
        if (Boolean.TRUE.equals(UNREAD.get("public"))) {
            graphics.fill(listX + LIST_W - 14, y + 5, listX + LIST_W - 6, y + 13, withAlpha(0xFFFF4444, listOpenAnim));
        }
        y += LIST_ITEM_H;
        // 每个私聊对象（带头像）
        for (String name : PRIVATE_TARGETS) {
            boolean current = name.equalsIgnoreCase(privateTarget);
            if (current) {
                graphics.fill(listX, y, listX + LIST_W, y + LIST_ITEM_H, listHover);
            }
            renderAvatar(graphics, name, listX + 4, y + 1, 14, 1.0f);
            graphics.text(font, (current ? "✔ " : "") + name, listX + 22, y + 2, withAlpha(0xFFFFFFFF, listOpenAnim), false);
            // 私聊未读红点
            if (Boolean.TRUE.equals(UNREAD.get(name))) {
                graphics.fill(listX + LIST_W - 14, y + 5, listX + LIST_W - 6, y + 13, withAlpha(0xFFFF4444, listOpenAnim));
            }
            y += LIST_ITEM_H;
        }
    }

    /** 渲染标题栏的频道按钮 */
    private static void renderChannelButton(GuiGraphicsExtractor graphics, Font font, int left, int right, int top, int mouseX, int mouseY) {
        String channelText = privateTarget != null ? "\u79c1\u804a:" + privateTarget : "\u516c\u5171";
        int btnW = font.width(channelText) + 14;
        int btnX = right - 12 - btnW;
        int btnY = top + (TITLE_BAR_HEIGHT - channelBtnH) / 2;
        channelBtnX = btnX;
        channelBtnY = btnY;
        channelBtnW = btnW;
        // 按钮背景
        graphics.fill(btnX, btnY, btnX + btnW, btnY + channelBtnH,
                privateTarget != null ? ARGB.color(90, 79, 195, 247) : ARGB.color(60, 255, 255, 255));
        graphics.text(font, channelText, btnX + 7, btnY + 2, 0xFFFFFFFF, false);
        // 其他频道有未读时按钮显示红点（右上角）
        if (hasUnread()) {
            graphics.fill(btnX + btnW - 5, btnY + 1, btnX + btnW - 1, btnY + 5, 0xFFFF4444);
        }
    }

            /**
     * 检测私聊消息，返回对方玩家名（委托给 ChatMessage.detectPrivateSender）
     */
    public static String detectPrivateMessage(String text) {
        return ChatMessage.detectPrivateSender(text);
    }

    // ============ 右键菜单状态 ============
    private static String menuTarget = null;
    private static String menuActionTarget = null; // 菜单操作目标玩家（供点击处理后获取）
    private static int menuX = 0;
    private static int menuY = 0;
    private static float menuAnim = 0.0f;          // 玩家菜单弹出动画
    private static final int MENU_WIDTH = 120;
    private static final int MENU_ITEM_HEIGHT = 16;
    private static final int MENU_ITEM_COUNT = 5;

    private static final int COLOR_MENU_BG = ARGB.color(230, 45, 48, 58);
    private static final int COLOR_MENU_HOVER = ARGB.color(80, 255, 255, 255);

    /** 打开右键菜单 */
    public static void openMenu(String player, int x, int y) {
        menuTarget = player;
        menuX = x;
        menuY = y;
        menuAnim = 0.0f;
    }

    /**
     * 处理菜单点击，返回操作类型
     *
     * @return "@"=插入@  "tell"=私聊  "copy"=复制名字  "close"=关闭  null=未点击菜单
     */
    public static String handleMenuClick(int mouseX, int mouseY) {
        if (menuTarget == null) {
            return null;
        }
        String target = menuTarget;
        if (mouseX >= menuX && mouseX <= menuX + MENU_WIDTH
                && mouseY >= menuY && mouseY <= menuY + MENU_ITEM_COUNT * MENU_ITEM_HEIGHT) {
            int idx = (mouseY - menuY) / MENU_ITEM_HEIGHT;
            menuTarget = null;
            menuActionTarget = target;
            if (idx == 0) {
                return "@";
            }
            if (idx == 1) {
                return "tell";
            }
            if (idx == 2) {
                return "tpa";
            }
            if (idx == 3) {
                return "tpahere";
            }
            if (idx == 4) {
                return "copy";
            }
            return "close";
        }
        // 点击菜单外：关闭
        menuTarget = null;
        return "close";
    }

    /** 菜单操作目标玩家 */
    public static String getMenuActionTarget() {
        return menuActionTarget;
    }

    /** 当前菜单目标玩家 */
    public static String getMenuTarget() {
        return menuTarget;
    }

    /** 菜单目标（供点击处理使用） */
    public static String getMenuTargetPlayer() {
        return menuTarget;
    }

    /** 渲染右键菜单 */
    private static void renderMenu(GuiGraphicsExtractor graphics, Font font, int mouseX, int mouseY) {
        if (menuTarget == null) {
            return;
        }
        menuAnim = Math.min(1.0f, menuAnim + 0.3f);  // 弹出动画进度
        int slide = (int) ((1.0f - menuAnim) * 8);   // 从下方滑入
        int mY = menuY + slide;
        int menuBottom = mY + MENU_ITEM_COUNT * MENU_ITEM_HEIGHT;
        drawRoundedRect(graphics, menuX, mY, menuX + MENU_WIDTH, menuBottom, 3, withAlpha(COLOR_MENU_BG, menuAnim));
        // 边框
        graphics.horizontalLine(menuX, menuX + MENU_WIDTH, mY, withAlpha(0xFFAAAAAA, menuAnim));
        graphics.horizontalLine(menuX, menuX + MENU_WIDTH, menuBottom, withAlpha(0xFFAAAAAA, menuAnim));
        graphics.verticalLine(menuX, mY, menuBottom, withAlpha(0xFFAAAAAA, menuAnim));
        graphics.verticalLine(menuX + MENU_WIDTH, mY, menuBottom, withAlpha(0xFFAAAAAA, menuAnim));

        String[] items = {"@" + menuTarget, "私聊 /tell", "tpa 传送到TA", "tpahere 传送到我这", "复制名字"};
        for (int i = 0; i < items.length; i++) {
            int itemY = mY + i * MENU_ITEM_HEIGHT;
            // 悬停高亮
            if (mouseX >= menuX && mouseX <= menuX + MENU_WIDTH
                    && mouseY >= itemY && mouseY <= itemY + MENU_ITEM_HEIGHT) {
                graphics.fill(menuX, itemY, menuX + MENU_WIDTH, itemY + MENU_ITEM_HEIGHT, withAlpha(COLOR_MENU_HOVER, menuAnim));
            }
            graphics.text(font, items[i], menuX + 6, itemY + 3, withAlpha(0xFFFFFFFF, menuAnim), false);
        }
    }

    /** 渲染右键菜单（供 renderExpanded 调用） */
    public static void renderMenuIfOpen(GuiGraphicsExtractor graphics, Font font, int mouseX, int mouseY) {
        renderMenu(graphics, font, mouseX, mouseY);
    }

    // ============ 回复功能状态 ============
    private static final int REPLY_BAR_BASE_HEIGHT = 8;
    private static String replyTarget = null;     // 回复目标玩家
    private static String replyPreview = null;    // 被回复消息预览
    private static int replyCancelX = -1;         // 取消按钮 x 坐标
    private static int replyCancelY = -1;         // 取消按钮 y 坐标

    private static final int COLOR_REPLY_BG = ARGB.color(120, 70, 130, 80);
    private static final int COLOR_REPLY_TEXT = 0xFFFFFFFF;

    /** 设置回复目标 */
    public static void setReplyTarget(String player, String preview) {
        replyTarget = player;
        replyPreview = preview;
    }

    /** 当前回复目标玩家名（无回复时返回 null） */
    public static String getReplyTargetName() {
        return replyTarget;
    }

    /** 被回复消息的预览内容 */
    public static String getReplyPreview() {
        return replyPreview;
    }

    /** 是否有回复目标 */
    public static boolean hasReply() {
        return replyTarget != null;
    }

    /** 取消回复 */
    public static void cancelReply() {
        replyTarget = null;
        replyPreview = null;
        replyCancelX = -1;
        replyCancelY = -1;
    }

    /** 点击位置是否在取消按钮上 */
    public static boolean isInsideCancelReply(int mouseX, int mouseY) {
        if (replyTarget == null || replyCancelX < 0) {
            return false;
        }
        return Math.abs(mouseX - replyCancelX) < 6 && Math.abs(mouseY - replyCancelY) < 6;
    }

    /** 获取点击位置命中的消息（右键回复用） */
    public static ChatMessage pickMessageAt(int mouseX, int mouseY) {
        Font font = Minecraft.getInstance().font;
        int lineHeight = font.lineHeight;
        for (int i = HIT_AREAS.size() - 1; i >= 0; i--) {
            HitArea area = HIT_AREAS.get(i);
            if (mouseY < area.y || mouseY >= area.y + lineHeight) {
                continue;
            }
            int lineWidth = font.width(area.line);
            if (mouseX < area.x || mouseX >= area.x + lineWidth) {
                continue;
            }
            if (area.message != null) {
                return area.message;
            }
        }
        return null;
    }

    /** 获取点击位置命中的头像所属玩家（右键 @ 用） */
    public static String pickAvatarAt(int mouseX, int mouseY) {
        for (int i = AVATAR_HIT_AREAS.size() - 1; i >= 0; i--) {
            AvatarHitArea area = AVATAR_HIT_AREAS.get(i);
            if (mouseX >= area.x && mouseX <= area.x + area.size
                    && mouseY >= area.y && mouseY <= area.y + area.size) {
                return area.playerName;
            }
        }
        return null;
    }

    /**
     * 获取点击位置命中的样式（链接等）
     * 逐字符像素范围比较，避免索引累计误差
     */
    public static Style pickClickableStyle(int mouseX, int mouseY) {
        return pickStyle(mouseX, mouseY, true);
    }

    /**
     * 获取鼠标位置命中的悬停样式（成就详情等）
     */
    private static Style pickHoverableStyle(int mouseX, int mouseY) {
        return pickStyle(mouseX, mouseY, false);
    }

    /**
     * 逐字符像素范围检测命中样式
     *
     * @param wantClick 是否查找点击样式（true）或悬停样式（false）
     */
    private static Style pickStyle(int mouseX, int mouseY, boolean wantClick) {
        Font font = Minecraft.getInstance().font;
        int lineHeight = font.lineHeight;
        // 倒序遍历（后渲染的在上面，优先匹配）
        for (int i = HIT_AREAS.size() - 1; i >= 0; i--) {
            HitArea area = HIT_AREAS.get(i);
            // 行高度范围检查
            if (mouseY < area.y || mouseY >= area.y + lineHeight) {
                continue;
            }
            int lineWidth = font.width(area.line);
            if (mouseX < area.x || mouseX >= area.x + lineWidth) {
                continue;
            }
            // 逐字符计算像素范围，点击位置命中且样式符合则返回
            int relX = mouseX - area.x;
            int[] currentX = {0};
            final Style[] result = {null};
            area.line.accept((index, style, codePoint) -> {
                String s = new String(Character.toChars(codePoint));
                int w = font.width(s);
                int charStart = currentX[0];
                int charEnd = currentX[0] + w;
                currentX[0] = charEnd;
                boolean within = relX >= charStart && relX <= charEnd;
                boolean matches = wantClick ? style.getClickEvent() != null : style.getHoverEvent() != null;
                if (within && matches) {
                    result[0] = style;
                    return false;
                }
                return true;
            });
            if (result[0] != null) {
                return result[0];
            }
        }
        return null;
    }

    /**
     * 渲染悬停提示（成就详情、链接文字等）
     * 在消息渲染后调用，检测鼠标位置命中的悬停样式
     */
    public static void renderHoverTooltip(GuiGraphicsExtractor graphics, Font font, int mouseX, int mouseY) {
        if (!SideChatConfig.enabled || HIT_AREAS.isEmpty()) {
            return;
        }
        Style style = pickHoverableStyle(mouseX, mouseY);
        if (style == null || style.getHoverEvent() == null) {
            return;
        }
        HoverEvent event = style.getHoverEvent();
        if (event instanceof HoverEvent.ShowText showText) {
            graphics.setTooltipForNextFrame(font, List.of(showText.value().getVisualOrderText()), mouseX, mouseY);
        } else if (event instanceof HoverEvent.ShowItem showItem) {
            try {
                graphics.setTooltipForNextFrame(font, showItem.item().create(), mouseX, mouseY);
            } catch (Exception ignored) {
                // 物品数据异常时忽略
            }
        } else if (event instanceof HoverEvent.ShowEntity showEntity) {
            List<FormattedCharSequence> lines = showEntity.entity().getTooltipLines().stream()
                    .map(Component::getVisualOrderText)
                    .toList();
            graphics.setTooltipForNextFrame(font, lines, mouseX, mouseY);
        }
    }


    private SideChatRenderer() {
    }

    // ================= 滚动控制 =================

    public static int getScrollOffset() {
        return scrollOffset;
    }

    public static void scrollBy(int deltaPixels) {
        int total = getTotalContentHeight();
        int viewport = getViewportHeight();
        scrollOffset = Math.max(0, Math.min(scrollOffset + deltaPixels, Math.max(0, total - viewport)));
    }

    public static void resetScroll() {
        scrollOffset = 0;
    }

    // ================= 布局计算（展开模式） =================

    public static int getPanelLeft() {
        return SideChatConfig.panelMargin;
    }

    public static int getPanelRight() {
        return SideChatConfig.panelMargin + SideChatConfig.panelWidth;
    }

    public static int getPanelTop() {
        return 8;
    }

    public static int getPanelBottom() {
        return Minecraft.getInstance().getWindow().getGuiScaledHeight() - BOTTOM_RESERVED;
    }

    /** 消息可视区域顶部 */
    private static int getViewportTop() {
        return getPanelTop() + TITLE_BAR_HEIGHT + 4;
    }

    /** 回复条高度（自适应：标题行 + 最多2行引用内容） */
    private static int getReplyBarHeight() {
        Font font = Minecraft.getInstance().font;
        int lines = 1;
        if (replyTarget != null && replyPreview != null && !replyPreview.isEmpty()) {
            int maxW = Math.max(40, SideChatConfig.panelWidth - 24);
            lines = Math.min(2, Math.max(1, font.split(Component.literal(replyPreview), maxW).size()));
        }
        return REPLY_BAR_BASE_HEIGHT + font.lineHeight * (lines + 1);
    }

    /** 消息可视区域底部（输入区域上方，回复条显示时上移） */
    private static int getViewportBottom() {
        int base = getPanelBottom() - INPUT_AREA_HEIGHT - 4;
        if (replyTarget != null) {
            base -= getReplyBarHeight();
        }
        return base;
    }

    /** 消息可视区域高度 */
    private static int getViewportHeight() {
        return Math.max(1, getViewportBottom() - getViewportTop());
    }

    /** 文本最大宽度（气泡内） */
    private static int getTextMaxWidth() {
        return SideChatConfig.panelWidth - PANEL_PADDING * 2
                - (SideChatConfig.showAvatar ? SideChatConfig.avatarSize + AVATAR_GAP : 0)
                - BUBBLE_PADDING_X * 2 - 4;
    }

    /** 判断点是否在消息区域内 */
    public static boolean isInsideMessageArea(double mouseX, double mouseY) {
        return mouseX >= getPanelLeft() && mouseX <= getPanelRight()
                && mouseY >= getViewportTop() && mouseY <= getViewportBottom();
    }

    // ================================================================
    //  收起模式（左下角小块，自动隐藏）
    // ================================================================

    /**
     * 渲染收起模式的左下角小块聊天
     */
    public static void renderCollapsed(GuiGraphicsExtractor graphics, Font font) {
        if (!SideChatConfig.enabled) {
            return;
        }
        // 收起模式不允许点击链接/头像，清空点击区域
        HIT_AREAS.clear();
        AVATAR_HIT_AREAS.clear();
        // 自动隐藏动画
        updateFadeAlpha();
        if (fadeAlpha <= 0.01f) {
            return;
        }

        List<ChatMessage> messages = ChatStore.getMessages();
        if (messages.isEmpty()) {
            return;
        }

        int width = SideChatConfig.collapsedWidth;
        int maxHeight = SideChatConfig.collapsedMaxHeight;
        int maxMessages = SideChatConfig.collapsedMaxMessages;
        int lineHeight = font.lineHeight;

        // 计算内容高度（显示最新 maxMessages 条）
        int startIndex = Math.max(0, messages.size() - maxMessages);
        int totalHeight = 0;
        for (int i = startIndex; i < messages.size(); i++) {
            int timeW = timeWidth(font, messages.get(i));
            List<FormattedCharSequence> lines = font.split(messages.get(i).content(), Math.max(20, width - 16 - timeW));
            totalHeight += lines.size() * lineHeight + 2;
        }
        int height = Math.min(maxHeight, totalHeight + 8);

        int left = 4;
        int bottom = Minecraft.getInstance().getWindow().getGuiScaledHeight() - 40;
        int top = bottom - height;
        int right = left + width;

        // 背景（透明）
        graphics.fill(left, top, right, bottom, applyAlpha(COLOR_COLLAPSED_BG, fadeAlpha));
        // 边框
        graphics.horizontalLine(left, right, top, applyAlpha(COLOR_BORDER, fadeAlpha));
        graphics.horizontalLine(left, right, bottom, applyAlpha(COLOR_BORDER, fadeAlpha));
        graphics.verticalLine(left, top, bottom, applyAlpha(COLOR_BORDER, fadeAlpha));
        graphics.verticalLine(right, top, bottom, applyAlpha(COLOR_BORDER, fadeAlpha));

        // 裁剪：防止长消息文字超出小框
        graphics.enableScissor(left, top, right, bottom);

        // 消息（从下到上）
        int y = bottom - 4;
        for (int i = messages.size() - 1; i >= startIndex; i--) {
            ChatMessage msg = messages.get(i);
            int timeW = timeWidth(font, msg);
            List<FormattedCharSequence> lines = font.split(msg.content(), Math.max(20, width - 16 - timeW));
            int msgHeight = lines.size() * lineHeight + 2;
            y -= msgHeight;
            if (y < top) {
                break;
            }
            // 时间戳 + 内容（白色无阴影，避免重影）
            int textX = left + 6;
            int textY = y + 1;
            if (SideChatConfig.showTimestamp) {
                String time = TIME_FORMAT.format(new Date(msg.timeMillis()));
                graphics.text(font, time, textX, textY, applyAlpha(COLOR_TIMESTAMP, fadeAlpha), false);
                textX += timeW;
            }
            for (FormattedCharSequence line : lines) {
                int color = msg.isPlayerMessage() ? 0xFFFFFFFF : 0xFFB8C0C8;
                graphics.text(font, line, textX, textY, applyAlpha(color, fadeAlpha), false);
                textY += lineHeight;
            }
        }

        graphics.disableScissor();
    }

    /** 收起模式中时间戳占用的宽度 */
    private static int timeWidth(Font font, ChatMessage msg) {
        if (!SideChatConfig.showTimestamp) {
            return 0;
        }
        return font.width(TIME_FORMAT.format(new Date(msg.timeMillis()))) + 5;
    }

    /** 更新淡入淡出动画 */
    private static void updateFadeAlpha() {
        float step = 1.0f / Math.max(1, SideChatConfig.fadeTicks);
        if (!SideChatConfig.isAutoHideEnabled()) {
            // 永不消失：始终显示
            fadeAlpha = Math.min(1.0f, fadeAlpha + step);
            return;
        }
        long idleMillis = System.currentTimeMillis() - ChatStore.getLastMessageTimeMillis();
        long hideDelayMillis = SideChatConfig.autoHideDelaySeconds * 1000L;
        if (idleMillis > hideDelayMillis) {
            fadeAlpha = Math.max(0.0f, fadeAlpha - step);
        } else {
            fadeAlpha = Math.min(1.0f, fadeAlpha + step);
        }
    }

    /** 新消息到达时立即恢复显示 */
    public static void wakeUp() {
        fadeAlpha = 1.0f;
    }

    // ================================================================
    //  展开模式（微信样式面板）
    // ================================================================

    /**
     * 渲染展开模式的微信样式聊天面板
     *
     * @param graphics      渲染提取器
     * @param font          字体
     * @param showInputHint 是否显示输入提示（false 时输入框由原版渲染）
     */
    public static void renderExpanded(GuiGraphicsExtractor graphics, Font font, boolean showInputHint, int mouseX, int mouseY) {
        if (!SideChatConfig.enabled) {
            return;
        }
        // 滚动条拖动中：GLFW 检测左键状态（按着则跟随鼠标，松开则结束拖动）
        if (isScrollDragging()) {
            net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
            long glfwWindow = mc.getWindow().handle();
            boolean leftPressed = org.lwjgl.glfw.GLFW.glfwGetMouseButton(glfwWindow, org.lwjgl.glfw.GLFW.GLFW_MOUSE_BUTTON_LEFT)
                    == org.lwjgl.glfw.GLFW.GLFW_PRESS;
            if (!leftPressed) {
                endScrollDrag();
            } else {
                updateScrollDrag((int) mc.mouseHandler.getScaledYPos(mc.getWindow()));
            }
        }
        // 面板滑入/滑出动画（整体水平偏移，pose 变换作用于所有绘制）
        float panelOffset = computePanelOffset();
        currentPanelOffset = panelOffset; // 记录当前面板偏移（供按钮等外部元素跟随动画）
        boolean transformed = panelOffset != 0.0f;
        panelTransformed = transformed;
        if (transformed) {
            graphics.pose().pushMatrix();
            graphics.pose().translate(panelOffset, 0.0f);
        }
        try {
        int left = getPanelLeft();
        int right = getPanelRight();
        int top = getPanelTop();
        int bottom = getPanelBottom();

        // ===== 1. 面板背景 =====
        panelFade = Math.min(1.0f, panelFade + 0.15f);
        int fadeBg = withAlpha(COLOR_PANEL_BG, panelFade);
        int fadeBorder = withAlpha(COLOR_BORDER, panelFade);
        graphics.fill(left, top, right, bottom, fadeBg);
        graphics.horizontalLine(left, right, top, fadeBorder);
        graphics.horizontalLine(left, right, bottom, fadeBorder);
        graphics.verticalLine(left, top, bottom, fadeBorder);
        graphics.verticalLine(right, top, bottom, fadeBorder);
        int fadeTitle = withAlpha(COLOR_TITLE_BG, panelFade);
        int fadeAccent = withAlpha(COLOR_ACCENT, panelFade);

        // ===== 2. 标题栏 =====
        int titleBottom = top + TITLE_BAR_HEIGHT;
        graphics.fill(left, top, right, titleBottom, fadeTitle);
        graphics.fill(left, top, left + 3, titleBottom, fadeAccent);
        graphics.text(font, Component.literal("聊天"), left + 12, top + (TITLE_BAR_HEIGHT - font.lineHeight) / 2, COLOR_TITLE_TEXT);
        // 服务器功能按钮（频道按钮左侧）
        renderServerButton(graphics, font, right, top, mouseX, mouseY);
        // 设置按钮（服务器按钮左侧，样式与服务器/频道按钮一致）
        renderTitleSettingsButton(graphics, font, mouseX, mouseY);
        // 频道切换按钮（右侧）
        renderChannelButton(graphics, font, left, right, top, mouseX, mouseY);

        // 消息计数（设置按钮左侧，避免遮挡按钮）
        String countText = ChatStore.getMessages().size() + " \u6761";
        int countX = settingsBtnX >= 0 ? settingsBtnX - 8 - font.width(countText) : left + 60;
        graphics.text(font, countText, countX, top + (TITLE_BAR_HEIGHT - font.lineHeight) / 2, COLOR_COUNT_TEXT);
        graphics.horizontalLine(left, right, titleBottom, COLOR_DIVIDER);

        // 上传进度条（面板顶部，标题栏下方）
        renderUploadProgress(graphics, font, left, right, titleBottom);

        // ===== 3. 消息列表（微信样式） =====
        renderWeChatMessages(graphics, font);

        // ===== 4. 输入区域 =====
        int inputTop = bottom - INPUT_AREA_HEIGHT;
        graphics.fill(left, inputTop, right, bottom, COLOR_INPUT_BG);
        graphics.horizontalLine(left, right, inputTop, COLOR_DIVIDER);

        // 回复提示条（在输入区域上方，引用样式：标题 + 原消息内容，白色文字、自动换行）
        if (replyTarget != null) {
            int barHeight = getReplyBarHeight();
            int replyBarTop = inputTop - barHeight;
            graphics.fill(left, replyBarTop, right, inputTop, COLOR_REPLY_BG);
            // 第一行：标题 + ✕
            String title = "\u21a9 回复 " + replyTarget;
            graphics.text(font, title, left + 10, replyBarTop + 2, COLOR_REPLY_TEXT, false);
            String cancel = "\u2715";
            replyCancelX = right - 14;
            replyCancelY = replyBarTop + 8;
            graphics.text(font, cancel, right - 10 - font.width(cancel), replyBarTop + 2, 0xFFFFB3B3, false);
            // 第二行起：被回复的原消息内容（白色引用样式，自动换行，最多2行）
            if (replyPreview != null && !replyPreview.isEmpty()) {
                List<FormattedCharSequence> quoteLines = font.split(
                        Component.literal("\u300c" + replyPreview + "\u300d"),
                        Math.max(40, SideChatConfig.panelWidth - 24));
                int lineY = replyBarTop + font.lineHeight + 1;
                int shown = 0;
                for (FormattedCharSequence line : quoteLines) {
                    if (shown >= 2) {
                        break;
                    }
                    graphics.text(font, line, left + 12, lineY, 0xFFFFFFFF, false);
                    lineY += font.lineHeight;
                    shown++;
                }
            }
        } else {
            replyCancelX = -1;
            replyCancelY = -1;
        }

        if (showInputHint) {
            String hint = "\u00a77点击输入消息... \u00a78(按 T / 回车)";
            int hintX = left + 12;
            int hintY = inputTop + (INPUT_AREA_HEIGHT - font.lineHeight) / 2;
            graphics.text(font, Component.literal(hint), hintX, hintY, COLOR_INPUT_HINT);
        }

        // ===== 5. 右键菜单（在输入区域上方绘制，保持最上层） =====
        // 渲染频道切换列表（点击频道按钮后弹出）
        renderChannelList(graphics, font);
        // 渲染服务器功能列表
        renderServerList(graphics, font);
        // 渲染家列表（菜单由 renderAllMenus 在最后渲染，保证在按钮之上）
        renderHomeList(graphics, font);
        } finally {
            if (transformed) {
                // 动画期间禁用点击（坐标随面板偏移）
                HIT_AREAS.clear();
                AVATAR_HIT_AREAS.clear();
                graphics.pose().popMatrix();
            }
        }
    }

    /** 渲染微信样式消息列表 */
    private static void renderWeChatMessages(GuiGraphicsExtractor graphics, Font font) {
        List<ChatMessage> messages = filterForChannel(ChatStore.getMessages());
        HIT_AREAS.clear();
        AVATAR_HIT_AREAS.clear();
        IMAGE_HIT_AREAS.clear();
        // 频道切换渐入动画
        channelFade = Math.min(1.0f, channelFade + 0.15f);
        if (messages.isEmpty()) {
            return;
        }

        int viewportTop = getViewportTop();
        int viewportBottom = getViewportBottom();
        int panelLeft = getPanelLeft();
        int panelRight = getPanelRight();
        int lineHeight = font.lineHeight;
        int textMaxWidth = getTextMaxWidth();
        boolean showAvatar = SideChatConfig.showAvatar;
        int avatarSize = SideChatConfig.avatarSize;

        // 计算每条消息的高度（缓存，避免重复计算）
        int[] heights = new int[messages.size()];
        for (int i = 0; i < messages.size(); i++) {
            ChatMessage msg = messages.get(i);
            CachedLayout layout = getLayout(msg, font, textMaxWidth, showAvatar, avatarSize);
            if (msg.isImageMessage()) {
                // 图片消息：图片框 + 名字行
                heights[i] = layout.nameH + layout.bubbleH + MESSAGE_GAP;
            } else if (!msg.isPlayerMessage()) {
                // 系统消息：居中，支持多行
                heights[i] = layout.lines.size() * lineHeight + 4;
            } else {
                // 引用行（使用布局缓存，避免每帧重新解析）
                String replyTo = layout.replyTo;
                String replyQuote = layout.chatProReply ? layout.chatProQuote : layout.replyQuote;
                int quoteH = (replyTo != null && replyQuote != null && !replyQuote.isEmpty()) ? lineHeight : 0;
                heights[i] = layout.nameH + quoteH + layout.bubbleH + MESSAGE_GAP;
            }
        }

        // 消息区域裁剪，防止文字画出面板
        graphics.enableScissor(panelLeft, viewportTop, panelRight, viewportBottom);

        // 计算内容总高度
        int totalHeight = 0;
        for (int h : heights) {
            totalHeight += h;
        }
        int viewportH = Math.max(1, viewportBottom - viewportTop);
        scrollOffset = Math.max(0, Math.min(scrollOffset, Math.max(0, totalHeight - viewportH)));

        // 从最老（顶部）往最新（底部）排，正确支持滚动
        int y = viewportBottom - totalHeight + scrollOffset;
        long now = System.currentTimeMillis(); // 每帧取一次当前时间
        for (int i = 0; i < messages.size(); i++) {
            ChatMessage msg = messages.get(i);
            int msgHeight = heights[i];
            int msgTop = y;
            int msgBottom = y + msgHeight;
            y = msgBottom;

            if (msgBottom < viewportTop) {
                continue; // 在视口上方（滚动后的老消息），跳过
            }
            if (msgTop > viewportBottom) {
                break; // 在视口下方（更新的消息），更后面的只会更下方
            }

            // 新消息动画：淡入 + 上浮；频道切换时整体渐入
            long age = now - msg.timeMillis();
            float animAlpha = channelFade;
            float animRise = (1.0f - channelFade) * MESSAGE_ANIM_RISE;
            if (age < MESSAGE_ANIM_MS) {
                animAlpha *= Math.max(0.05f, age / (float) MESSAGE_ANIM_MS);
                animRise += (1.0f - age / (float) MESSAGE_ANIM_MS) * MESSAGE_ANIM_RISE;
            }

            if (msg.isImageMessage()) {
                // 图片消息：渲染缩略图
                renderImageMessage(graphics, font, msg, panelLeft, panelRight, msgTop, msgHeight - MESSAGE_GAP,
                        lineHeight, showAvatar, avatarSize, animAlpha, animRise,
                        getLayout(msg, font, textMaxWidth, showAvatar, avatarSize));
            } else if (!msg.isPlayerMessage()) {
                // 系统消息：居中，保留原样式颜色，默认灰色，支持多行
                List<FormattedCharSequence> lines = getLayout(msg, font, textMaxWidth, showAvatar, avatarSize).lines;
                int lineY = (int) (msgTop + 2 + animRise);
                for (FormattedCharSequence line : lines) {
                    int x = (panelLeft + panelRight - font.width(line)) / 2;
                    graphics.text(font, line, x, lineY, withAlpha(COLOR_SYSTEM_TEXT, animAlpha), false);
                    // 记录行级点击区域
                    HIT_AREAS.add(new HitArea(x, lineY, line, msg));
                    lineY += lineHeight;
                }
            } else {
                renderWeChatBubble(graphics, font, msg, panelLeft, panelRight, msgTop, msgHeight - MESSAGE_GAP,
                        textMaxWidth, lineHeight, showAvatar, avatarSize, animAlpha, animRise,
                        getLayout(msg, font, textMaxWidth, showAvatar, avatarSize));
            }
        }

        // 垂直滚动条（消息超过一页时显示）
        if (totalHeight > viewportH) {
            int sbRight = panelRight - 2;
            int sbWidth = 4;
            int trackH = viewportBottom - viewportTop;
            // 滑块高度：视口占比
            int thumbH = Math.max(14, (int) (trackH * (viewportH / (float) totalHeight)));
            scrollThumbH = thumbH;
            // 滑块位置：滚动比例（滚动越大显示越早 → 滑块越靠上）
            float ratio = totalHeight <= viewportH ? 0.0f
                    : scrollOffset / (float) (totalHeight - viewportH);
            int thumbY = viewportTop + (int) ((trackH - thumbH) * (1.0f - ratio));
            // 记录滚动条区域（供点击/拖动使用）
            scrollTrackX = sbRight - sbWidth;
            scrollTrackY = viewportTop;
            scrollTrackH = trackH;
            scrollTotalH = totalHeight;
            // 轨道（淡色）
            graphics.fill(sbRight - sbWidth, viewportTop, sbRight, viewportBottom, 0x1FFFFFFF);
            // 滑块（亮色）
            graphics.fill(sbRight - sbWidth, thumbY, sbRight, thumbY + thumbH, 0x7FFFFFFF);
        } else {
            scrollTrackX = -1;
        }

        graphics.disableScissor();
    }

    /** 渲染单条微信气泡消息 */
    private static void renderWeChatBubble(GuiGraphicsExtractor graphics, Font font, ChatMessage msg,
                                           int panelLeft, int panelRight, int top, int height,
                                           int textMaxWidth, int lineHeight,
                                           boolean showAvatar, int avatarSize,
                                           float animAlpha, float animRise, CachedLayout layout) {
        boolean isSelf = isSelf(msg.sender());
        int nameH = layout.nameH; // 名字行高度
        String replyTo = layout.replyTo;
        String replyQuote = layout.chatProReply ? layout.chatProQuote : layout.replyQuote;
        int quoteH = (replyTo != null && replyQuote != null && !replyQuote.isEmpty()) ? lineHeight : 0; // 引用行高度
        int bubbleY = (int) (top + nameH + animRise);              // 气泡顶部（名字下方 + 动画上浮）
        int bubbleH = Math.max(1, height - nameH - quoteH);        // 气泡高度（下方预留引用行）

        // 头像位置（气泡外侧）
        int avatarX;
        int bubbleLeft;
        int bubbleRight;
        if (isSelf) {
            avatarX = panelRight - PANEL_PADDING - avatarSize;
            bubbleRight = avatarX - AVATAR_GAP;
            bubbleLeft = panelLeft + PANEL_PADDING;
        } else {
            avatarX = panelLeft + PANEL_PADDING;
            bubbleLeft = avatarX + avatarSize + AVATAR_GAP;
            bubbleRight = panelRight - PANEL_PADDING;
        }

        // 使用缓存的剥离内容与换行结果（避免重复计算）
        Component displayContent = layout.displayContent;
        List<FormattedCharSequence> contentLines = layout.lines;

        // 气泡宽度：自适应文本（最大行宽缓存，避免每帧 font.width）
        int maxLineWidth = layout.maxLineWidth;
        if (maxLineWidth < 0) {
            maxLineWidth = 0;
            for (FormattedCharSequence line : contentLines) {
                int w = font.width(line);
                if (w > maxLineWidth) {
                    maxLineWidth = w;
                }
            }
            layout.maxLineWidth = maxLineWidth;
        }
        int bubbleWidth = Math.min(maxLineWidth + BUBBLE_PADDING_X * 2, textMaxWidth + BUBBLE_PADDING_X * 2);
        if (isSelf) {
            bubbleLeft = bubbleRight - bubbleWidth;
        } else {
            bubbleRight = bubbleLeft + bubbleWidth;
        }

        // 气泡背景（圆角）
        int bubbleColor = isSelf ? COLOR_SELF_BUBBLE : COLOR_OTHER_BUBBLE;
        drawRoundedRect(graphics, bubbleLeft, bubbleY, bubbleRight, bubbleY + bubbleH, BUBBLE_RADIUS, withAlpha(bubbleColor, animAlpha));

        // 气泡文字（黑色，无阴影避免重影）
        int textX = bubbleLeft + BUBBLE_PADDING_X;
        int textY = bubbleY + BUBBLE_PADDING_Y;
        for (FormattedCharSequence line : contentLines) {
            graphics.text(font, line, textX, textY, withAlpha(COLOR_BUBBLE_TEXT, animAlpha), false);
            // 记录行级点击区域（字符级链接检测）
            HIT_AREAS.add(new HitArea(textX, textY, line, msg));
            textY += lineHeight;
        }

        // 头像（与气泡垂直居中）
        if (showAvatar) {
            int avatarY = bubbleY + Math.max(0, (bubbleH - avatarSize) / 2);
            renderAvatar(graphics, msg.sender(), avatarX, avatarY, avatarSize, animAlpha);
            // 记录头像点击区域（右键 @ 玩家）
            AVATAR_HIT_AREAS.add(new AvatarHitArea(avatarX, avatarY, avatarSize, msg.sender()));
        }

        // 玩家名（气泡上方，不同玩家不同颜色）
        if (!msg.sender().isEmpty()) {
            String nameText = msg.sender();
            int nameX = isSelf ? bubbleRight - font.width(nameText) : bubbleLeft;
            graphics.text(font, nameText, nameX, top, withAlpha(nameColor(nameText), animAlpha), false);
        }

        // 回复标签：消息以 @玩家名 开头时，在名字旁显示"回复 XXX"
        if (replyTo != null) {
            String replyText = "\u21a9 " + replyTo;
            int replyX;
            if (!msg.sender().isEmpty()) {
                // 名字后面
                int nameW = font.width(msg.sender());
                replyX = (isSelf ? bubbleRight - nameW - font.width(replyText) - 6 : bubbleLeft + nameW + 6);
            } else {
                replyX = isSelf ? bubbleRight - font.width(replyText) : bubbleLeft;
            }
            graphics.text(font, replyText, replyX, top, withAlpha(0xFFE67E22, animAlpha), false);
        }

        // 引用行：显示被回复的原消息内容（灰色小字，气泡下方）
        if ((replyTo != null || msg.isChatProReply()) && replyQuote != null && !replyQuote.isEmpty()) {
            String quoteText = "\u300c" + replyQuote + "\u300d";
            if (font.width(quoteText) > textMaxWidth + BUBBLE_PADDING_X * 2) {
                quoteText = font.plainSubstrByWidth(quoteText, textMaxWidth + BUBBLE_PADDING_X * 2, true) + "\u2026\u300d";
            }
            int quoteX = isSelf ? bubbleRight - font.width(quoteText) : bubbleLeft;
            graphics.text(font, quoteText, quoteX, top + nameH + bubbleH, withAlpha(0xFF909090, animAlpha), false);
        }

        // 时间戳（气泡旁边：自己消息在左，别人消息在右，垂直居中，避免被气泡遮挡）
        if (SideChatConfig.showTimestamp) {
            String time = TIME_FORMAT.format(new Date(msg.timeMillis()));
            int timeWidth = font.width(time);
            int timeY = bubbleY + Math.max(0, (bubbleH - font.lineHeight) / 2);
            int timeX = isSelf ? bubbleLeft - timeWidth - 4 : bubbleRight + 4;
            graphics.text(font, time, timeX, timeY, withAlpha(COLOR_TIMESTAMP, animAlpha), false);
        }
    }

    /** 渲染聊天栏顶部的上传进度条（标题栏下方） */
    private static void renderUploadProgress(GuiGraphicsExtractor graphics, Font font, int left, int right, int topBar) {
        boolean uploading = com.tgzjdv.chat.image.UploadState.isUploading();
        boolean error = com.tgzjdv.chat.image.UploadState.isError();
        // 上传完成或失败后 4 秒内仍显示
        long last = com.tgzjdv.chat.image.UploadState.getLastUpdateTime();
        boolean recent = System.currentTimeMillis() - last < 4000;
        if (!uploading && !error) {
            return;
        }
        if (!uploading && error && !recent) {
            return;
        }

        int barH = 3;
        int barY = topBar + 5;
        int barLeft = left + 6;
        int barRight = right - 6;
        int barW = barRight - barLeft;

        // 进度条背景
        graphics.fill(barLeft, barY, barRight, barY + barH, 0xFF252A33);
        // 填充
        float p = com.tgzjdv.chat.image.UploadState.getProgress();
        if (error) {
            graphics.fill(barLeft, barY, barRight, barY + barH, 0xFFE74C3C);
        } else if (p > 0.01f) {
            graphics.fill(barLeft, barY, barLeft + (int) (barW * p), barY + barH, 0xFF4FC3F7);
        }

        // 状态文字（进度条下方）
        String text;
        int textColor;
        if (error) {
            text = "\u00a7c" + com.tgzjdv.chat.image.UploadState.getErrorText();
            textColor = 0xFFE74C3C;
        } else {
            String name = com.tgzjdv.chat.image.UploadState.getFileName();
            if (name.length() > 20) {
                name = name.substring(0, 20) + "...";
            }
            text = "\u56fe\u7247\u4e0a\u4f20 " + name + " (" + (int) (p * 100) + "%)";
            textColor = 0xFF9AA0A6;
        }
        graphics.text(font, text, barLeft, barY + barH + 2, textColor, false);
    }

    /** 渲染图片消息（缩略图 + 点击放大） */
    private static void renderImageMessage(GuiGraphicsExtractor graphics, Font font, ChatMessage msg,
                                           int panelLeft, int panelRight, int top, int height,
                                           int lineHeight, boolean showAvatar, int avatarSize,
                                           float animAlpha, float animRise, CachedLayout layout) {
        boolean isSelf = isSelf(msg.sender());
        int nameH = layout.nameH;
        int bubbleY = (int) (top + nameH + animRise);
        int bubbleH = Math.max(1, height - nameH);
        String imageUrl = msg.getImageUrl();

        // 头像位置（气泡外侧）
        int avatarX;
        int bubbleLeft;
        int bubbleRight;
        if (isSelf) {
            avatarX = panelRight - PANEL_PADDING - avatarSize;
            bubbleRight = avatarX - AVATAR_GAP;
            bubbleLeft = panelLeft + PANEL_PADDING;
        } else {
            avatarX = panelLeft + PANEL_PADDING;
            bubbleLeft = avatarX + avatarSize + AVATAR_GAP;
            bubbleRight = panelRight - PANEL_PADDING;
        }

        // 气泡宽度固定为图片框宽度
        int boxW = Math.min(IMAGE_BOX_W + IMAGE_PADDING * 2, bubbleRight - bubbleLeft);
        int bubbleWidth = boxW;
        if (isSelf) {
            bubbleLeft = bubbleRight - bubbleWidth;
        } else {
            bubbleRight = bubbleLeft + bubbleWidth;
        }

        // 气泡背景
        int bubbleColor = isSelf ? COLOR_SELF_BUBBLE : COLOR_OTHER_BUBBLE;
        drawRoundedRect(graphics, bubbleLeft, bubbleY, bubbleRight, bubbleY + bubbleH, BUBBLE_RADIUS, withAlpha(bubbleColor, animAlpha));

        // 图片区域（气泡内）
        int imgX = bubbleLeft + IMAGE_PADDING;
        int imgY = bubbleY + IMAGE_PADDING;
        int imgBoxW = bubbleWidth - IMAGE_PADDING * 2;
        int imgBoxH = bubbleH - IMAGE_PADDING * 2;

        // 请求/获取图片纹理
        Identifier texture = com.tgzjdv.chat.image.ImageCache.getCached(imageUrl);
        if (texture == null) {
            com.tgzjdv.chat.image.ImageCache.requestImage(imageUrl,
                    p -> { /* 进度更新在渲染时读取 */ },
                    id -> { /* 加载完成后下次渲染显示 */ });
            // 加载中占位 + 下载进度条
            graphics.fill(imgX, imgY, imgX + imgBoxW, imgY + imgBoxH, withAlpha(0x33222222, animAlpha));
            Float progress = com.tgzjdv.chat.image.ImageCache.getDownloadProgress(imageUrl);
            String loading;
            if (progress != null) {
                loading = "\u00a77图片加载 " + (int) (progress * 100) + "%";
                // 气泡内进度条
                int pBarX = imgX + 10;
                int pBarY = imgY + imgBoxH - 12;
                int pBarW = imgBoxW - 20;
                graphics.fill(pBarX, pBarY, pBarX + pBarW, pBarY + 3, 0xFF252A33);
                if (progress > 0.01f) {
                    graphics.fill(pBarX, pBarY, pBarX + (int) (pBarW * Math.min(1.0f, progress)), pBarY + 3, 0xFF4FC3F7);
                }
            } else {
                loading = "\u00a77图片加载中...";
            }
            graphics.centeredText(font, loading, imgX + imgBoxW / 2, imgY + imgBoxH / 2 - font.lineHeight / 2, withAlpha(0xFF9AA0A6, animAlpha));
        } else {
            // 等比缩放显示（contain）
            int[] dims = com.tgzjdv.chat.image.ImageCache.getDimensions(imageUrl);
            int texW = dims != null && dims[0] > 0 ? dims[0] : 16;
            int texH = dims != null && dims[1] > 0 ? dims[1] : 9;
            float scale = Math.min(imgBoxW / (float) texW, imgBoxH / (float) texH);
            int drawW = Math.max(1, (int) (texW * scale));
            int drawH = Math.max(1, (int) (texH * scale));
            int drawX = imgX + (imgBoxW - drawW) / 2;
            int drawY = imgY + (imgBoxH - drawH) / 2;
            graphics.blit(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED, texture, drawX, drawY, 0.0f, 0.0f, drawW, drawH, texW, texH, texW, texH);
            // 图片边框
            graphics.horizontalLine(drawX, drawX + drawW, drawY, withAlpha(0x44FFFFFF, animAlpha));
            graphics.horizontalLine(drawX, drawX + drawW, drawY + drawH, withAlpha(0x44FFFFFF, animAlpha));
            graphics.verticalLine(drawX, drawY, drawY + drawH, withAlpha(0x44FFFFFF, animAlpha));
            graphics.verticalLine(drawX + drawW, drawY, drawY + drawH, withAlpha(0x44FFFFFF, animAlpha));
            // 提示小字
            String hint = "\u00a77点击放大";
            graphics.text(font, hint, bubbleLeft + 5, bubbleY + bubbleH - font.lineHeight - 2, withAlpha(0xFF8A9199, animAlpha), false);
        }

        // 头像
        if (showAvatar) {
            int avatarY = bubbleY + Math.max(0, (bubbleH - avatarSize) / 2);
            renderAvatar(graphics, msg.sender(), avatarX, avatarY, avatarSize, animAlpha);
            AVATAR_HIT_AREAS.add(new AvatarHitArea(avatarX, avatarY, avatarSize, msg.sender()));
        }

        // 玩家名（气泡上方）
        if (!msg.sender().isEmpty()) {
            String nameText = msg.sender();
            int nameX = isSelf ? bubbleRight - font.width(nameText) : bubbleLeft;
            graphics.text(font, nameText, nameX, top, withAlpha(nameColor(nameText), animAlpha), false);
        }

        // 记录图片点击区域（放大查看）
        IMAGE_HIT_AREAS.add(new ImageHitArea(bubbleLeft, bubbleY, bubbleWidth, bubbleH, imageUrl, msg.sender()));
    }

    /** 玩家名颜色（按名字散列取色） */
    private static int nameColor(String name) {
        int[] palette = {
                0xFFE74C3C, 0xFFE67E22, 0xFFF1C40F, 0xFF2ECC71, 0xFF1ABC9C,
                0xFF3498DB, 0xFF9B59B6, 0xFFE91E63, 0xFF00BCD4, 0xFFFF5722
        };
        return palette[Math.floorMod(name.hashCode(), palette.length)];
    }

    /** 渲染玩家头像（皮肤头部区域） */
    private static void renderAvatar(GuiGraphicsExtractor graphics, String playerName, int x, int y, int size, float alpha) {
        Identifier texture = AvatarProvider.getSkinTexture(playerName);
        try {
            // 确保纹理已注册/加载；未就绪时跳过绘制（等下载完成显示真实皮肤）
            Minecraft.getInstance().getTextureManager().getTexture(texture);
        } catch (Exception e) {
            return; // 纹理未就绪，暂不绘制
        }
        // 皮肤纹理 64x64，头部区域为 (8,8)-(16,16)，无需额外边框
        graphics.blit(RenderPipelines.GUI_TEXTURED, texture, x, y, 8.0f, 8.0f, size, size, 8, 8, 64, 64, withAlpha(0xFFFFFFFF, alpha));
    }

    // ================================================================
    //  工具方法
    // ================================================================

    /** 判断消息是否来自本地玩家 */
    private static boolean isSelf(String sender) {
        Minecraft mc = Minecraft.getInstance();
        return mc.player != null && sender != null && mc.player.getGameProfile().name().equalsIgnoreCase(sender);
    }

    /** 绘制圆角矩形（阶梯近似） */
    private static void drawRoundedRect(GuiGraphicsExtractor g, int left, int top, int right, int bottom, int radius, int color) {
        if (right <= left || bottom <= top) {
            return;
        }
        if (radius <= 0) {
            g.fill(left, top, right, bottom, color);
            return;
        }
        radius = Math.min(radius, Math.min((right - left) / 2, (bottom - top) / 2));
        // 主体
        g.fill(left + radius, top, right - radius, bottom, color);
        g.fill(left, top + radius, right, bottom - radius, color);
        // 四个角的阶梯
        for (int i = 0; i < radius; i++) {
            // 左上角
            g.fill(left + i, top + radius - 1 - i, left + radius, top + radius - i, color);
            // 右上角
            g.fill(right - radius, top + radius - 1 - i, right - i, top + radius - i, color);
            // 左下角
            g.fill(left + i, bottom - radius + i, left + radius, bottom - radius + 1 + i, color);
            // 右下角
            g.fill(right - radius, bottom - radius + i, right - i, bottom - radius + 1 + i, color);
        }
    }

    /** 对颜色应用透明度倍数 */
    private static int applyAlpha(int color, float alpha) {
        if (alpha >= 1.0f) {
            return color;
        }
        int a = (color >>> 24) & 0xFF;
        int newA = (int) (a * Math.max(0.0f, Math.min(1.0f, alpha)));
        return (newA << 24) | (color & 0x00FFFFFF);
    }

    /** 所有消息的总内容高度（用于滚动范围计算） */
    private static int getTotalContentHeight() {
        Font font = Minecraft.getInstance().font;
        int lineHeight = font.lineHeight;
        int textMaxWidth = getTextMaxWidth();
        boolean showAvatar = SideChatConfig.showAvatar;
        int avatarSize = SideChatConfig.avatarSize;
        int total = 0;
        for (ChatMessage msg : filterForChannel(ChatStore.getMessages())) {
            CachedLayout layout = getLayout(msg, font, textMaxWidth, showAvatar, avatarSize);
            if (!msg.isPlayerMessage()) {
                total += layout.lines.size() * lineHeight + 4;
            } else {
                // 引用行（使用布局缓存）
                String replyTo = layout.replyTo;
                String replyQuote = layout.chatProReply ? layout.chatProQuote : layout.replyQuote;
                int quoteH = (replyTo != null && replyQuote != null && !replyQuote.isEmpty()) ? lineHeight : 0;
                total += layout.nameH + quoteH + layout.bubbleH + MESSAGE_GAP;
            }
        }
        return total;
    }

    // ================================================================
    //  输入框定位（供 ChatScreen 使用）
    // ================================================================

    public static int getInputAreaTop() {
        return getPanelBottom() - INPUT_AREA_HEIGHT;
    }

    public static int getInputAreaBottom() {
        return getPanelBottom();
    }

    public static int getInputX() {
        return getPanelLeft() + 8;
    }

    public static int getInputY(int inputHeight) {
        return getInputAreaTop() + (INPUT_AREA_HEIGHT - inputHeight) / 2;
    }

    public static int getInputWidth() {
        // 预留图片按钮空间（按钮宽 20 + 间距 6）
        return SideChatConfig.panelWidth - 42;
    }

    /** 判断点是否在面板区域内 */
    public static boolean isInsidePanel(double mouseX, double mouseY) {
        return mouseX >= getPanelLeft() && mouseX <= getPanelRight()
                && mouseY >= getPanelTop() && mouseY <= getPanelBottom();
    }
}
