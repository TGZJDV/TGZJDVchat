package com.tgzjdv.chat.render;

import com.mojang.authlib.GameProfile;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.PlayerSkin;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 玩家皮肤头像工具
 * 优先级：游戏内实体皮肤 > 玩家列表皮肤 > SkinManager 主动获取（支持 CustomSkinLoader 皮肤源）> 默认皮肤
 */
public final class AvatarProvider {

    private static final Map<String, Identifier> TEXTURE_CACHE = new ConcurrentHashMap<>();

    private AvatarProvider() {
    }

    /**
     * 获取玩家皮肤纹理路径
     * 默认皮肤不缓存（皮肤加载后可更新）
     */
    public static Identifier getSkinTexture(String playerName) {
        if (playerName == null || playerName.isEmpty()) {
            return DefaultPlayerSkin.getDefaultTexture();
        }
        // 缓存命中（仅缓存真实皮肤）
        Identifier cached = TEXTURE_CACHE.get(playerName);
        if (cached != null) {
            return cached;
        }

        Identifier texture = resolve(playerName);
        // 只缓存真实皮肤，默认皮肤不缓存
        if (!texture.equals(DefaultPlayerSkin.getDefaultTexture())) {
            TEXTURE_CACHE.put(playerName, texture);
        }
        return texture;
    }

    private static Identifier resolve(String playerName) {
        Minecraft mc = Minecraft.getInstance();

        // 1. 游戏内玩家实体（附近，支持服务器皮肤插件/资源包皮肤）
        if (mc.level != null) {
            for (AbstractClientPlayer player : mc.level.players()) {
                if (player.getGameProfile().name().equalsIgnoreCase(playerName)) {
                    PlayerSkin skin = player.getSkin();
                    if (skin != null && skin.body() != null && !isDefaultSkin(skin)) {
                        return skin.body().texturePath();
                    }
                }
            }
        }

        // 2. 本地玩家
        if (mc.player != null && mc.player.getGameProfile().name().equalsIgnoreCase(playerName)) {
            PlayerSkin skin = mc.player.getSkin();
            if (skin != null && skin.body() != null) {
                return skin.body().texturePath();
            }
        }

        // 3. 玩家列表（PlayerInfo，忽略大小写）
        if (mc.getConnection() != null) {
            PlayerInfo info = mc.getConnection().getPlayerInfoIgnoreCase(playerName);
            if (info != null) {
                // 服务器提供了皮肤（SkinsRestorer 等）
                if (info.getSkin() != null && info.getSkin().body() != null && !isDefaultSkin(info.getSkin())) {
                    return info.getSkin().body().texturePath();
                }
                // 无皮肤数据：异步通过 SkinManager 主动获取
                //（CustomSkinLoader 会注入皮肤源，离线 UUID 也能从皮肤站获取，与 Chat Heads 机制一致）
                GameProfile profile = info.getProfile();
                final String name = playerName;
                try {
                    mc.getSkinManager().get(profile).thenAccept(optional -> {
                        optional.ifPresent(skin -> {
                            if (skin.body() != null && !isDefaultSkin(skin)) {
                                TEXTURE_CACHE.put(name, skin.body().texturePath());
                            }
                        });
                    });
                } catch (Exception ignored) {
                    // 获取失败忽略
                }
            }
        }

        return DefaultPlayerSkin.getDefaultTexture();
    }

    /** 判断是否为默认皮肤（避免用默认皮肤覆盖真实皮肤） */
    private static boolean isDefaultSkin(PlayerSkin skin) {
        Identifier path = skin.body().texturePath();
        return path.getPath().startsWith("textures/entity/player/");
    }

    /** 清除缓存 */
    public static void clearCache() {
        TEXTURE_CACHE.clear();
    }
}
