package com.tgzjdv.chat.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * 聊天模组配置（登录密码、自定义命令、家列表）
 * 配置文件：config/tgzjdvchat.json
 */
public final class ChatAuthConfig {

    private static final Path CONFIG_PATH = FabricLoader.getInstance().getConfigDir().resolve("tgzjdvchat.json");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    // 登录
    private static String loginPassword = "";
    private static String loginCommand = "l";

    // 服务器命令
    private static String dbackCommand = "dback";
    private static String backCommand = "back";
    private static String homeCommand = "home";

    // tpa 命令
    private static String tpaCommand = "tpa";
    private static String tpahereCommand = "tpahere";

    // 图片发送安全模式
    private static boolean imageSafeMode = false;

    // 图片上传同意
    private static boolean imageUploadAgreed = false;

    // 家列表
    private static final List<String> HOMES = new ArrayList<>();

    static {
        load();
    }

    private ChatAuthConfig() {
    }

    /** 加载配置 */
    public static void load() {
        try {
            if (Files.exists(CONFIG_PATH)) {
                JsonObject obj = GSON.fromJson(Files.readString(CONFIG_PATH), JsonObject.class);
                if (obj != null) {
                    if (obj.has("loginPassword")) {
                        loginPassword = obj.get("loginPassword").getAsString();
                    }
                    if (obj.has("loginCommand")) {
                        loginCommand = obj.get("loginCommand").getAsString();
                    }
                    if (obj.has("dbackCommand")) {
                        dbackCommand = obj.get("dbackCommand").getAsString();
                    }
                    if (obj.has("backCommand")) {
                        backCommand = obj.get("backCommand").getAsString();
                    }
                    if (obj.has("homeCommand")) {
                        homeCommand = obj.get("homeCommand").getAsString();
                    }
                    if (obj.has("tpaCommand")) {
                        tpaCommand = obj.get("tpaCommand").getAsString();
                    }
                    if (obj.has("tpahereCommand")) {
                        tpahereCommand = obj.get("tpahereCommand").getAsString();
                    }
                    if (obj.has("imageSafeMode")) {
                        imageSafeMode = obj.get("imageSafeMode").getAsBoolean();
                    }
                    if (obj.has("imageUploadAgreed")) {
                        imageUploadAgreed = obj.get("imageUploadAgreed").getAsBoolean();
                    }
                    if (obj.has("homes") && obj.get("homes").isJsonArray()) {
                        HOMES.clear();
                        for (var e : obj.get("homes").getAsJsonArray()) {
                            HOMES.add(e.getAsString());
                        }
                    }
                }
            }
        } catch (Exception ignored) {
        }
    }

    /** 保存配置 */
    public static void save() {
        try {
            JsonObject obj = new JsonObject();
            obj.addProperty("loginPassword", loginPassword);
            obj.addProperty("loginCommand", loginCommand);
            obj.addProperty("dbackCommand", dbackCommand);
            obj.addProperty("backCommand", backCommand);
            obj.addProperty("homeCommand", homeCommand);
            obj.addProperty("tpaCommand", tpaCommand);
            obj.addProperty("tpahereCommand", tpahereCommand);
            obj.addProperty("imageSafeMode", imageSafeMode);
            obj.addProperty("imageUploadAgreed", imageUploadAgreed);
            JsonArray homesArr = new JsonArray();
            for (String h : HOMES) {
                homesArr.add(h);
            }
            obj.add("homes", homesArr);
            Files.writeString(CONFIG_PATH, GSON.toJson(obj));
        } catch (Exception ignored) {
        }
    }

    // ===== 登录 =====
    public static String getLoginPassword() {
        return loginPassword;
    }

    public static void setLoginPassword(String password) {
        loginPassword = password == null ? "" : password;
        save();
    }

    public static boolean hasPassword() {
        return !loginPassword.isEmpty();
    }

    public static String getLoginCommand() {
        return loginCommand;
    }

    public static void setLoginCommand(String cmd) {
        loginCommand = cmd == null || cmd.isEmpty() ? "l" : cmd;
        save();
    }

    // ===== 服务器命令 =====
    public static String getDbackCommand() {
        return dbackCommand;
    }

    public static void setDbackCommand(String cmd) {
        dbackCommand = cmd == null || cmd.isEmpty() ? "dback" : cmd;
        save();
    }

    public static String getBackCommand() {
        return backCommand;
    }

    public static void setBackCommand(String cmd) {
        backCommand = cmd == null || cmd.isEmpty() ? "back" : cmd;
        save();
    }

    public static String getHomeCommand() {
        return homeCommand;
    }

    public static void setHomeCommand(String cmd) {
        homeCommand = cmd == null || cmd.isEmpty() ? "home" : cmd;
        save();
    }

    // ===== tpa 命令 =====
    public static String getTpaCommand() {
        return tpaCommand;
    }

    public static void setTpaCommand(String cmd) {
        tpaCommand = cmd == null || cmd.isEmpty() ? "tpa" : cmd;
        save();
    }

    public static String getTpahereCommand() {
        return tpahereCommand;
    }

    public static void setTpahereCommand(String cmd) {
        tpahereCommand = cmd == null || cmd.isEmpty() ? "tpahere" : cmd;
        save();
    }

    // ===== 图片发送模式 =====
    public static boolean isImageSafeMode() {
        return imageSafeMode;
    }

    public static void setImageSafeMode(boolean safe) {
        imageSafeMode = safe;
        save();
    }

    // ===== 图片上传同意 =====
    public static boolean isImageUploadAgreed() {
        return imageUploadAgreed;
    }

    public static void setImageUploadAgreed(boolean agreed) {
        imageUploadAgreed = agreed;
        save();
    }

    // ===== 家列表 =====
    public static List<String> getHomes() {
        return HOMES;
    }

    public static void addHome(String name) {
        if (name == null || name.isEmpty()) {
            return;
        }
        for (String h : HOMES) {
            if (h.equalsIgnoreCase(name)) {
                return;
            }
        }
        HOMES.add(name);
        save();
    }

    public static void removeHome(String name) {
        HOMES.removeIf(h -> h.equalsIgnoreCase(name));
        save();
    }
}
