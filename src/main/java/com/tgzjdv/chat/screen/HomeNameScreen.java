package com.tgzjdv.chat.screen;

import com.tgzjdv.chat.config.ChatAuthConfig;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;

/**
 * 添加新家界面：输入家名 → 保存到配置
 */
public class HomeNameScreen extends Screen {

    private static final Component TITLE = Component.literal("添加新家");
    private EditBox nameInput;

    public HomeNameScreen() {
        super(TITLE);
    }

    @Override
    protected void init() {
        this.nameInput = new EditBox(this.font, this.width / 2 - 100, this.height / 2 - 10, 200, 20, Component.literal("家名"));
        this.nameInput.setMaxLength(32);
        this.addRenderableWidget(this.nameInput);
        this.setInitialFocus(this.nameInput);
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        graphics.fill(0, 0, this.width, this.height, 0xAA101318);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        graphics.centeredText(this.font, this.title, this.width / 2, this.height / 2 - 35, 0xFFFFFFFF);
        graphics.text(this.font, "输入家的名字", this.width / 2 - 100, this.height / 2 + 20, 0xFF8A9199, false);
        super.extractRenderState(graphics, mouseX, mouseY, delta);
    }

    @Override
    public boolean keyPressed(KeyEvent key) {
        if (key.key() == 257 || key.key() == 335) { // Enter
            confirm();
            return true;
        }
        return super.keyPressed(key);
    }

    private void confirm() {
        ChatAuthConfig.addHome(this.nameInput.getValue().trim());
        com.tgzjdv.chat.render.SideChatRenderer.setScreenCompat(null);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public boolean isAllowedInPortal() {
        return false;
    }
}
