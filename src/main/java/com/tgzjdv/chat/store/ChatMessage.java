package com.tgzjdv.chat.store;

import net.minecraft.client.multiplayer.chat.GuiMessageSource;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 侧边栏聊天消息数据
 *
 * @param timeMillis 消息到达时间（毫秒时间戳）
 * @param sender     发送者名称（玩家消息为玩家名，系统消息为空）
 * @param content    消息内容（原版完整组件，含名字前缀）
 * @param source     消息来源（玩家 / 服务器系统 / 客户端系统）
 */
public record ChatMessage(long timeMillis, String sender, Component content, GuiMessageSource source) {

    /** 玩家消息格式：&lt;名字&gt; 内容（原版） */
    private static final Pattern VANILLA_PLAYER_PATTERN = Pattern.compile("^<([^>]+)>\\s*(.*)$", Pattern.DOTALL);

    /** 服务器常见格式：[前缀...] 名字: 内容 或 名字: 内容（名字支持中文等任意非空白字符） */
    private static final Pattern PREFIXED_PLAYER_PATTERN = Pattern.compile(
            "^(?:\\[[^\\]]*\\]\\s*)*([^\\s:：]+?)\\s*[:：]\\s*(.*)$", Pattern.DOTALL);

    /** 头衔格式：[前缀...] &lt;名字&gt; 内容（头衔在尖括号外） */
    private static final Pattern PREFIXED_ANGLE_PLAYER_PATTERN = Pattern.compile(
            "^(?:\\[[^\\]]*\\]\\s*)*<([^>]+)>\\s*(.*)$", Pattern.DOTALL);

    /** 名字前缀剥离：&lt;名字&gt; 或 [前缀...] 名字: 或 [前缀...] &lt;名字&gt; */
    private static final Pattern NAME_PREFIX_PATTERN = Pattern.compile(
            "^(?:(?:\\[[^\\]]*\\]\\s*)*<.+?>\\s*|(?:\\[[^\\]]*\\]\\s*)*[^\\s:：]+?\\s*[:：]\\s*)", Pattern.DOTALL);

    /** URL 识别：http/https 链接 */
    private static final Pattern URL_PATTERN = Pattern.compile("https?://\\S+");

    /** Chat Heads 等模组在名字前添加的标记：如 [TGZJDV Head] */
    private static final Pattern CHAT_HEADS_MARKER = Pattern.compile("\\[[^\\]]*\\s[Hh]ead\\]");

    /** 是否是玩家消息（来源标记） */
    public boolean isPlayer() {
        return source == GuiMessageSource.PLAYER;
    }

    /**
     * 检测私聊消息，返回对方玩家名（非私聊返回 null）
     * 先清理 Chat Heads 标记，支持：悄悄地对你说/对你说/→ 你/[私聊] 等格式
     */
    public static String detectPrivateSender(String text) {
        if (text == null) {
            return null;
        }
        String cleaned = CHAT_HEADS_MARKER.matcher(text).replaceAll("");
        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("([^\\s：:]{1,16})悄悄地对(?:你|([^\\s：:]{1,16}))说").matcher(cleaned);
        if (m.find()) {
            String g1 = m.group(1);
            String g2 = m.group(2);
            return ("\u4f60".equals(g1)) ? g2 : g1;
        }
        m = java.util.regex.Pattern
                .compile("([^\\s：:]{1,16})对(?:你|([^\\s：:]{1,16}))说").matcher(cleaned);
        if (m.find()) {
            String g1 = m.group(1);
            String g2 = m.group(2);
            return ("\u4f60".equals(g1)) ? g2 : g1;
        }
        m = java.util.regex.Pattern
                .compile("\\[?([^\\s\\[\\]：:]{1,16})\\s*(?:→|->|»)\\s*(?:\u4f60|me)\\s*\\]?\\s*[:：]?").matcher(cleaned);
        if (m.find()) {
            return m.group(1);
        }
        m = java.util.regex.Pattern
                .compile("\\[(?:私聊|PM|msg|tell|沟通)\\]\\s*([^\\s：:]{1,16})").matcher(cleaned);
        if (m.find()) {
            return m.group(1);
        }
        return null;
    }

    /** 是否是私聊消息（系统消息通道的私聊） */
    public boolean isPrivateMessage() {
        if (isPlayer()) {
            return false;
        }
        String text = content.getString();
        if (detectPrivateSender(text) != null) {
            return true;
        }
        // /tell 命令形式（自己发送的私聊记录）
        return text.toLowerCase().contains("/tell ");
    }

    /**
     * 是否应作为玩家消息显示（气泡+头像）
     * 兼容"有头衔功能的服务器"：系统消息通道发送的玩家消息也按玩家消息处理，
     * 私聊消息也按玩家消息（气泡）显示
     */
    public boolean isPlayerMessage() {
        if (isPlayer()) {
            return true;
        }
        // 私聊消息按玩家消息（气泡）显示
        if (isPrivateMessage()) {
            return true;
        }
        String text = content.getString();
        // URL 不是玩家消息（避免 https:// 被误认为"名字:"格式）
        if (text.startsWith("http://") || text.startsWith("https://")) {
            return false;
        }
        String cleaned = CHAT_HEADS_MARKER.matcher(text).replaceAll("");
        // 尖括号格式（<名字> / [前缀] <名字>）一定是玩家消息，不做在线验证
        if (VANILLA_PLAYER_PATTERN.matcher(cleaned).matches()
                || PREFIXED_ANGLE_PLAYER_PATTERN.matcher(cleaned).matches()) {
            return true;
        }
        // 冒号格式（名字:内容）：验证发送者是真实在线玩家（排除"传送到家:home"等系统提示）
        if (PREFIXED_PLAYER_PATTERN.matcher(cleaned).matches()) {
            String sender = extractSender(content);
            return isRealPlayer(sender);
        }
        return false;
    }

    /** 判断名字是否为真实在线玩家（本地玩家或玩家列表中的玩家） */
    private static boolean isRealPlayer(String name) {
        if (name == null || name.isEmpty()) {
            return false;
        }
        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
        // 本地玩家
        if (mc.player != null && mc.player.getGameProfile().name().equalsIgnoreCase(name)) {
            return true;
        }
        // 玩家列表
        return mc.getConnection() != null && mc.getConnection().getPlayerInfo(name) != null;
    }

    /**
     * 气泡中显示的内容：剥离名字/头衔前缀，自动识别 URL 链接，保留其余部分样式
     * 非玩家消息返回原样
     */
    public Component displayContent() {
        if (!isPlayerMessage()) {
            return content;
        }
        // 私聊消息：剥离私聊前缀（[名字 head]名字悄悄地对你说：内容 → 内容）
        if (isPrivateMessage()) {
            Component stripped = stripPrivatePrefix(content);
            if (stripped.getString().isEmpty()) {
                return content;
            }
            // 私聊中的回复：剥离 @被回复者「引用」前缀，避免气泡内容重复显示
            Component noMention = stripLeadingMention(stripped);
            if (!noMention.getString().isEmpty()) {
                stripped = noMention;
            }
            return addUrlLinks(stripped);
        }
        Component stripped = stripNamePrefix(content, sender());
        // 剥离出错时回退原始内容，避免空白
        if (stripped.getString().isEmpty()) {
            return content;
        }
        // 剥离回复提及前缀（@玩家名 ），避免气泡内容重复显示
        Component noMention = stripLeadingMention(stripped);
        if (!noMention.getString().isEmpty()) {
            stripped = noMention;
        }
        // Chat Pro 兼容：剥离"回复[引用] | "前缀，只显示新内容
        String chatProContent = getChatProReplyContent();
        if (chatProContent != null && !chatProContent.isEmpty()) {
            return Component.literal(chatProContent).withStyle(stripped.getStyle());
        }
        return addUrlLinks(stripped);
    }

    /**
     * 剥离私聊消息前缀（到第一个冒号为止）：
     * [名字 head]名字悄悄地对你说：内容 → 内容
     * 用 visit 片段累积定位（与剥离使用同一套片段，保证对齐）
     */
    private static Component stripPrivatePrefix(Component full) {
        int[] prefixLen = {-1};
        int[] accum = {0};
        full.visit((style, text) -> {
            if (prefixLen[0] >= 0) {
                return Optional.empty();
            }
            int colon = text.indexOf('\uFF1A'); // 全角：
            if (colon < 0) {
                colon = text.indexOf(':');
            }
            if (colon >= 0) {
                prefixLen[0] = accum[0] + colon + 1;
                return Optional.empty();
            }
            accum[0] += text.length();
            return Optional.empty();
        }, full.getStyle());
        if (prefixLen[0] < 0) {
            return full;
        }
        return stripByPrefixLen(full, prefixLen[0]);
    }

    /** 按字符偏移剥离组件前缀（保留后续段样式） */
    private static Component stripByPrefixLen(Component full, int prefixLen) {
        if (prefixLen <= 0) {
            return full;
        }
        MutableComponent result = Component.literal("").withStyle(full.getStyle().withColor((net.minecraft.network.chat.TextColor) null));
        int[] remaining = {prefixLen};
        full.visit((style, text) -> {
            int len = text.length();
            if (remaining[0] > 0) {
                if (remaining[0] >= len) {
                    remaining[0] -= len;
                    return Optional.empty();
                }
                String keep = text.substring(remaining[0]);
                remaining[0] = 0;
                if (!keep.isEmpty()) {
                    result.append(Component.literal(keep).withStyle(style.withColor((net.minecraft.network.chat.TextColor) null).withItalic(false)));
                }
                return Optional.empty();
            }
            if (!text.isEmpty()) {
                result.append(Component.literal(text).withStyle(style.withColor((net.minecraft.network.chat.TextColor) null).withItalic(false)));
            }
            return Optional.empty();
        }, full.getStyle());
        if (result.getString().isEmpty()) {
            return full;
        }
        return result;
    }

    /** 剥离消息开头的回复提及（@玩家名 ） */
    private static Component stripLeadingMention(Component comp) {
        // 用 visit 片段累积定位 @名字 结束位置（与剥离用同一套片段）
        int[] prefixLen = {-1};
        int[] accum = {0};
        comp.visit((style, text) -> {
            if (prefixLen[0] >= 0) {
                return Optional.empty();
            }
            if (accum[0] == 0) {
                // 仅剥离 @名字「引用内容」前缀（回复消息），普通 @ 提及保留
                java.util.regex.Matcher m = java.util.regex.Pattern.compile("^@\\S+\\s*\u300c.*?\u300d\\s*").matcher(text);
                if (m.find()) {
                    prefixLen[0] = m.end();
                    return Optional.empty();
                }
            }
            accum[0] += text.length();
            return Optional.empty();
        }, comp.getStyle());
        if (prefixLen[0] < 0 || prefixLen[0] >= comp.getString().length()) {
            return comp;
        }
        MutableComponent result = Component.literal("").withStyle(comp.getStyle().withColor((net.minecraft.network.chat.TextColor) null));
        int[] remaining = {prefixLen[0]};
        comp.visit((style, text) -> {
            int len = text.length();
            if (remaining[0] > 0) {
                if (remaining[0] >= len) {
                    remaining[0] -= len;
                    return Optional.empty();
                }
                String keep = text.substring(remaining[0]);
                remaining[0] = 0;
                if (!keep.isEmpty()) {
                    result.append(Component.literal(keep).withStyle(style.withColor((net.minecraft.network.chat.TextColor) null).withItalic(false)));
                }
                return Optional.empty();
            }
            if (!text.isEmpty()) {
                result.append(Component.literal(text).withStyle(style.withColor((net.minecraft.network.chat.TextColor) null).withItalic(false)));
            }
            return Optional.empty();
        }, comp.getStyle());
        return result;
    }

    /**
     * 自动识别文本中的 URL 并添加可点击链接（客户端行为）
     * 已有链接的段落保持不变
     */
    private static Component addUrlLinks(Component comp) {
        MutableComponent result = Component.literal("").withStyle(comp.getStyle());
        comp.visit((style, text) -> {
            Matcher matcher = URL_PATTERN.matcher(text);
            int lastEnd = 0;
            while (matcher.find()) {
                if (matcher.start() > lastEnd) {
                    result.append(Component.literal(text.substring(lastEnd, matcher.start())).withStyle(style));
                }
                String url = matcher.group();
                Style urlStyle = style;
                if (style.getClickEvent() == null) {
                    try {
                        urlStyle = style.withClickEvent(new net.minecraft.network.chat.ClickEvent.OpenUrl(java.net.URI.create(url)))
                                .withUnderlined(true);
                    } catch (Exception ignored) {
                        urlStyle = style;
                    }
                }
                result.append(Component.literal(url).withStyle(urlStyle));
                lastEnd = matcher.end();
            }
            if (lastEnd < text.length()) {
                result.append(Component.literal(text.substring(lastEnd)).withStyle(style));
            }
            return Optional.empty();
        }, comp.getStyle());
        return result;
    }

    /**
     * 剥离组件文本开头的名字/头衔前缀，保留剩余部分的样式
     * 用 visit 片段累积定位名字（与剥离使用同一套片段，保证对齐，兼容 Chat Heads）
     */
    private static Component stripNamePrefix(Component full, String sender) {
        if (sender == null || sender.isEmpty()) {
            return full;
        }

        // 第一遍：用 visit 片段累积计算名字结束位置（片段总长可能与 getString 不一致，必须用片段对齐）
        int[] prefixLen = {-1};
        int[] accum = {0};
        full.visit((style, text) -> {
            if (prefixLen[0] >= 0) {
                return Optional.empty(); // 已找到名字位置，跳过后续
            }
            if (text.contains(sender)) {
                prefixLen[0] = accum[0] + text.indexOf(sender) + sender.length();
                return Optional.empty();
            }
            accum[0] += text.length();
            return Optional.empty();
        }, full.getStyle());
        if (prefixLen[0] < 0) {
            return full; // 找不到名字，不剥离
        }

        // 清除父级样式颜色（避免继承的白色样式污染气泡文字）
        MutableComponent result = Component.literal("").withStyle(full.getStyle().withColor((net.minecraft.network.chat.TextColor) null));
        int[] remaining = {prefixLen[0]};
        full.visit((style, text) -> {
            int len = text.length();
            if (remaining[0] > 0) {
                if (remaining[0] >= len) {
                    remaining[0] -= len;
                    return Optional.empty(); // 整段在前缀内，跳过
                }
                // 前缀长度落在此段中间：此段是名字段，跳过名字部分、保留名字后的内容
                String after = text.substring(remaining[0]).replaceFirst("^[\\s:：>]*", "");
                remaining[0] = 0;
                if (!after.isEmpty()) {
                    result.append(Component.literal(after).withStyle(style.withColor((net.minecraft.network.chat.TextColor) null)));
                }
                return Optional.empty();
            }
            // 前缀已剥离完毕，完整保留后续段（清理分隔符）
            String keep = text;
            if (result.getString().isEmpty()) {
                keep = keep.replaceFirst("^[\\s:：>]*", "");
            }
            if (!keep.isEmpty()) {
                result.append(Component.literal(keep).withStyle(style.withColor((net.minecraft.network.chat.TextColor) null)));
            }
            return Optional.empty();
        }, full.getStyle());

        // 剥离结果为空时回退原始内容
        if (result.getString().isEmpty()) {
            return full;
        }
        return result;
    }

    /**
     * 剥离发送者名字前缀后的纯文本（用于解析 @提及 和「引用」）
     */
    private String contentAfterSender() {
        if (sender() == null || sender().isEmpty()) {
            return content.getString();
        }
        String text = content.getString();
        int idx = text.indexOf(sender());
        if (idx < 0) {
            return text;
        }
        return text.substring(idx + sender().length());
    }

    /**
     * 解析 @提及 /「引用」所用的文本：
     * 私聊消息剥离私聊前缀；普通消息剥离发送者前缀
     */
    private String contentForParse() {
        String text = content.getString();
        if (detectPrivateSender(text) != null) {
            String stripped = stripPrivatePrefix(content).getString();
            return stripped.isEmpty() ? text : stripped;
        }
        return contentAfterSender();
    }

    /**
     * 是否是回复消息：内容中包含 @玩家名「引用内容」（带引用才算回复，普通 @ 提及不算）
     * 返回被回复的玩家名，非回复消息返回 null（兼容服务器前缀）
     */
    public String getReplyTarget() {
        String text = contentForParse();
        // 必须有「引用」才是回复消息
        java.util.regex.Matcher quote = java.util.regex.Pattern.compile("\u300c(.+?)\u300d", java.util.regex.Pattern.DOTALL).matcher(text);
        if (!quote.find()) {
            return null;
        }
        // @名字 在「引用」之前
        String before = text.substring(0, quote.start());
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("@(\\S+)").matcher(before);
        if (m.find()) {
            return m.group(1);
        }
        return null;
    }

    /**
     * 提取消息中引用的原消息内容（「...」格式）
     * 回复消息格式：...@玩家名「原消息内容」回复内容
     */
    public String getQuoteContent() {
        String text = contentForParse();
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("\u300c(.+?)\u300d", java.util.regex.Pattern.DOTALL).matcher(text);
        if (m.find()) {
            return m.group(1);
        }
        return null;
    }

    /**
     * 从消息内容中提取发送者名称
     * 支持 &lt;名字&gt;、[前缀...] 名字:、[前缀...] &lt;名字&gt; 三种格式
     */
    public static String extractSender(Component message) {
        String text = message.getString();
        // 私聊消息：区分方向
        String cleaned = CHAT_HEADS_MARKER.matcher(text).replaceAll("");
        // 自己发出的私聊：你悄悄地对X说 → sender = 自己
        java.util.regex.Matcher selfPm = java.util.regex.Pattern.compile("\u4f60\u6084\u6084\u5730\u5bf9([^\\s：:]{1,16})\u8bf4").matcher(cleaned);
        if (selfPm.find()) {
            net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
            return (mc.player != null) ? mc.player.getGameProfile().name() : selfPm.group(1);
        }
        // 收到的私聊：X悄悄地对你说 → sender = X
        java.util.regex.Matcher pm = java.util.regex.Pattern.compile("([^\\s：:]{1,16})\u6084\u6084\u5730\u5bf9\u4f60\u8bf4").matcher(cleaned);
        if (pm.find()) {
            return pm.group(1);
        }
        // 其他私聊格式
        String privateSender = detectPrivateSender(text);
        if (privateSender != null) {
            return privateSender;
        }
        // URL 不是玩家消息
        if (text.startsWith("http://") || text.startsWith("https://")) {
            return "";
        }
        // 移除 Chat Heads 标记（如 [TGZJDV Head]）后再匹配
        String cleaned2 = CHAT_HEADS_MARKER.matcher(text).replaceAll("");
        Matcher angle = VANILLA_PLAYER_PATTERN.matcher(cleaned);
        if (angle.matches()) {
            return angle.group(1);
        }
        Matcher prefixedAngle = PREFIXED_ANGLE_PLAYER_PATTERN.matcher(cleaned);
        if (prefixedAngle.matches()) {
            return prefixedAngle.group(1);
        }
        Matcher prefixed = PREFIXED_PLAYER_PATTERN.matcher(cleaned);
        if (prefixed.matches()) {
            return prefixed.group(1);
        }
        return "";
    }

    // ================= 图片消息支持 =================

    /** 图床域名（Telegraph-Image） */
    private static final String IMAGE_HOST = com.tgzjdv.chat.image.ImageUploader.IMAGE_HOST;

    /** 图片 URL 正则（图床域名下的图片） */
    private static final Pattern IMAGE_URL_PATTERN = java.util.regex.Pattern.compile("https?://" + java.util.regex.Pattern.quote(IMAGE_HOST) + "/file/[\\w.\u4e00-\u9fa5-]+");

    /** 通用 URL 正则（兼容 Chat Pro 等任意图床） */
    private static final Pattern GENERAL_URL_PATTERN = java.util.regex.Pattern.compile("https?://\\S+");

    /** Chat Pro 图片标记 */
    private static final String CHATPRO_IMAGE_MARKER = "\u9700\u8981\u5b89\u88c5\u66f4\u597d\u7684\u804a\u5929\u680f\u6a21\u7ec4\u53ef\u89c1\u6b64\u56fe\u7247";

    /** Chat Pro 回复格式：回复[被回复内容] | 新内容 */
    private static final Pattern CHATPRO_REPLY_PATTERN = java.util.regex.Pattern.compile("(?:\u56de\u590d|Reply)\\[(.*?)\\] \\| (.*)$", Pattern.DOTALL);

    /** 是否是 Chat Pro 的回复消息 */
    public boolean isChatProReply() {
        return CHATPRO_REPLY_PATTERN.matcher(content.getString()).find();
    }

    /** Chat Pro 回复的引用内容 */
    public String getChatProReplyQuote() {
        Matcher m = CHATPRO_REPLY_PATTERN.matcher(content.getString());
        return m.find() ? m.group(1) : null;
    }

    /** Chat Pro 回复的新内容（去掉"回复[...] | "前缀） */
    public String getChatProReplyContent() {
        Matcher m = CHATPRO_REPLY_PATTERN.matcher(content.getString());
        return m.find() ? m.group(2) : null;
    }

    /** 是否是图片消息（含图床图片 URL） */
    public boolean isImageMessage() {
        return getImageUrl() != null;
    }

    /** 提取消息中的图片 URL（支持正常格式、安全编码、Chat Pro 格式）；非图片消息返回 null */
    public String getImageUrl() {
        if (source == net.minecraft.client.multiplayer.chat.GuiMessageSource.PLAYER
                || source == net.minecraft.client.multiplayer.chat.GuiMessageSource.SYSTEM_SERVER) {
            String text = content.getString();
            // 1. 正常格式（图床域名 URL）
            java.util.regex.Matcher m = IMAGE_URL_PATTERN.matcher(text);
            if (m.find()) {
                return m.group(0);
            }
            // 2. 安全编码格式（链接被打断，需还原）
            if (com.tgzjdv.chat.image.ImageCodec.isSafeEncoded(text)) {
                String safeUrl = com.tgzjdv.chat.image.ImageCodec.extractSafeUrl(text);
                if (safeUrl != null) {
                    String decoded = com.tgzjdv.chat.image.ImageCodec.decodeUrlSafe(safeUrl);
                    if (decoded != null && decoded.contains(com.tgzjdv.chat.image.ImageUploader.IMAGE_HOST)) {
                        return decoded;
                    }
                }
            }
            // 3. Chat Pro 兼容：检测其图片标记，移除打断符（删字符）还原 URL
            if (text.contains(CHATPRO_IMAGE_MARKER)) {
                String cleaned = text.replace("\u5220", "");
                java.util.regex.Matcher gm = GENERAL_URL_PATTERN.matcher(cleaned);
                if (gm.find()) {
                    String url = gm.group(0);
                    // 清理尾部标点
                    while (url.length() > 8 && "),.;:!?]}".indexOf(url.charAt(url.length() - 1)) >= 0) {
                        url = url.substring(0, url.length() - 1);
                    }
                    return url;
                }
            }
        }
        return null;
    }
}
