package com.tgzjdv.chat.image;

/**
 * 图片链接安全编码/解码
 * 安全发送模式：将链接打断（插入分隔符），绕过服务器链接检测（删除含链接消息）
 * 模组接收时还原为真实 URL
 */
public final class ImageCodec {

    /** 分隔符（链接打断点） */
    public static final String SEP = "[TGC|]";

    /** 安全格式包裹标记 */
    public static final String TAG_START = "[TGCIMG]";
    public static final String TAG_END = "[/TGCIMG]";

    private ImageCodec() {
    }

    /**
     * 将图片 URL 编码为安全格式（打断 https:// 和域名中的点，绕过链接检测）
     * 例：https://img.famousmusic.asia/file/xxx.jpg
     *   → [TGCIMG]https[TGC|]://img[TGC|]famousmusic[TGC|]asia/file/xxx[TGC|]jpg[/TGCIMG]
     */
    public static String encodeUrlSafe(String url) {
        if (url == null || url.isEmpty()) {
            return url;
        }
        String s = url.replace(".", SEP);
        // 打断 https:// 中的 https 与 :（服务器检测 https:// 连续模式）
        s = s.replace("https" + SEP + "://", "https" + SEP + "://");
        s = s.replace("https://", "https" + SEP + "://");
        s = s.replace("http://", "http" + SEP + "://");
        return TAG_START + s + TAG_END;
    }

    /**
     * 解码安全格式为真实 URL
     * 例：https[TGC|]://img[TGC|]famousmusic[TGC|]asia/file/xxx[TGC|]jpg → https://img.famousmusic.asia/file/xxx.jpg
     */
    public static String decodeUrlSafe(String text) {
        if (text == null) {
            return null;
        }
        String s = text;
        if (s.startsWith(TAG_START)) {
            s = s.substring(TAG_START.length());
        }
        if (s.endsWith(TAG_END)) {
            s = s.substring(0, s.length() - TAG_END.length());
        }
        // 先还原打断的 https://（https[TGC|]:// → https://）
        s = s.replace("https" + SEP + "://", "https://");
        s = s.replace("http" + SEP + "://", "http://");
        // 再还原域名/扩展名中的点
        s = s.replace(SEP, ".");
        return s;
    }

    /** 判断文本是否包含安全格式的图片链接 */
    public static boolean isSafeEncoded(String text) {
        return text != null && text.contains(SEP);
    }

    /** 从消息文本中提取安全编码的图片链接（含 TAG 标记的部分） */
    public static String extractSafeUrl(String text) {
        if (text == null) {
            return null;
        }
        int start = text.indexOf(TAG_START);
        if (start < 0) {
            // 无 TAG 标记但含分隔符：从第一个分隔符往前找 https
            if (text.contains(SEP)) {
                int httpsIdx = text.indexOf("https");
                if (httpsIdx >= 0) {
                    int idx = httpsIdx;
                    // 找到含 SEP 的 https 片段结束
                    int end = text.indexOf(" ", idx);
                    if (end < 0) {
                        end = text.length();
                    }
                    return text.substring(idx, end);
                }
            }
            return null;
        }
        int end = text.indexOf(TAG_END, start);
        if (end < 0) {
            end = text.length();
        }
        return text.substring(start, end);
    }
}
