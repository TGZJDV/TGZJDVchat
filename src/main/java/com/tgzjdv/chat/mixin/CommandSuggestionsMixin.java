package com.tgzjdv.chat.mixin;

import com.tgzjdv.chat.config.SideChatConfig;
import net.minecraft.client.gui.components.CommandSuggestions;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * 命令补全建议框跟随侧边栏输入框位置
 * 原版建议框固定在屏幕底部（anchorToBottom），侧边栏模式下改为显示在输入框上方
 */
@Mixin(CommandSuggestions.class)
public abstract class CommandSuggestionsMixin {

    @Shadow
    @Final
    private EditBox input;

    /**
     * 修改建议框的 y 基准位置：从"屏幕底部"改为"输入框上方"
     * 原版逻辑：y = screen.height - 12（底部），建议框从 y 向上排列
     */
    @Redirect(method = "showSuggestions",
            at = @At(value = "FIELD", target = "Lnet/minecraft/client/gui/screens/Screen;height:I", ordinal = 0))
    private int tgzjdvchat_adjustSuggestionY(Screen screen) {
        if (!SideChatConfig.enabled) {
            return screen.height;
        }
        // 让 y = 输入框顶部 - 1，建议框将显示在输入框上方
        return this.input.getY() - 1;
    }
}
