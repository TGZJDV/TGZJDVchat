package com.tgzjdv.chat;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import com.tgzjdv.chat.screen.SettingsScreen;

/**
 * Mod Menu 集成：从模组菜单进入设置界面
 */
public class TgzjdvModMenu implements ModMenuApi {

    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return parent -> {
            SettingsScreen.setReturnScreen(parent);
            return new SettingsScreen(false);
        };
    }
}
