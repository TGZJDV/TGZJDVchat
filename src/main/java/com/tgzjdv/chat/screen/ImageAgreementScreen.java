package com.tgzjdv.chat.screen;

import com.tgzjdv.chat.config.ChatAuthConfig;
import com.tgzjdv.chat.render.SideChatRenderer;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * 图片上传同意界面（首次使用）
 * 提示：使用图片发送功能会把图片上传到服务器，需同意后才能发送
 */
public class ImageAgreementScreen extends Screen {

    private static final Component TITLE = Component.literal("图片上传协议");

    public ImageAgreementScreen() {
        super(TITLE);
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        graphics.fill(0, 0, this.width, this.height, 0xD0101318);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        int centerX = this.width / 2;
        // 关闭按钮（右上角 X）
        int closeX = this.width - 26;
        int closeY = 4;
        boolean closeHover = mouseX >= closeX && mouseX <= closeX + 22 && mouseY >= closeY && mouseY <= closeY + 22;
        graphics.fill(closeX, closeY, closeX + 22, closeY + 22, closeHover ? 0xFF8B3D3D : 0xFF6B2E2E);
        graphics.centeredText(this.font, "\u2715", closeX + 11, closeY + (22 - this.font.lineHeight) / 2, 0xFFFFFFFF);
        // 标题
        graphics.centeredText(this.font, "\u56fe\u7247\u4e0a\u4f20\u534f\u8bae", centerX, 70, 0xFFFFFFFF);
        graphics.centeredText(this.font, "\u2584\u2584\u2584\u2584\u2584\u2584\u2584\u2584\u2584\u2584\u2584\u2584\u2584\u2584\u2584\u2584\u2584\u2584", centerX, 90, 0xFF666666);

        // 协议内容
        String[] lines = {
                "\u4f7f\u7528\u56fe\u7247\u53d1\u9001\u529f\u80fd\u4f1a\u5c06\u56fe\u7247\u4e0a\u4f20\u5230\u670d\u52a1\u5668\uff0c",
                "\u5e76\u4e14\u53d1\u9001\u5230\u804a\u5929\u4e2d\u4f9b\u5176\u4ed6\u73a9\u5bb6\u67e5\u770b\u3002",
                "",
                "\u4e0a\u4f20\u540e\u7684\u56fe\u7247\u5c06\u4fdd\u5b58\u5728\u670d\u52a1\u5668\u4e0a\uff0c",
                "\u8bf7\u786e\u8ba4\u56fe\u7247\u5185\u5bb9\u4e0d\u6d89\u53ca\u4e2a\u4eba\u9690\u79c1\u6216\u654f\u611f\u4fe1\u606f\u3002",
                "",
                "\u8bf7\u5728\u670d\u52a1\u5668\u5185\u8c28\u614e\u53d1\u9001\uff0c\u82e5\u88ab\u8e22\u51fa\u6216\u5c01\u53f7\u6a21\u7ec4\u4f5c\u8005\u6982\u4e0d\u8d1f\u8d23\u3002",
                "",
                "\u540c\u610f\u540e\u624d\u80fd\u4f7f\u7528\u56fe\u7247\u53d1\u9001\u529f\u80fd\u3002"
        };
        int y = 110;
        for (String line : lines) {
            graphics.centeredText(this.font, line, centerX, y, 0xFFCCCCCC);
            y += 20;
        }

        // 按钮
        int btnW = 160;
        int btnH = 26;
        int btnY = this.height / 2 + 60;
        int agreeX = centerX - btnW - 12;
        int denyX = centerX + 12;

        boolean agreeHover = mouseX >= agreeX && mouseX <= agreeX + btnW && mouseY >= btnY && mouseY <= btnY + btnH;
        boolean denyHover = mouseX >= denyX && mouseX <= denyX + btnW && mouseY >= btnY && mouseY <= btnY + btnH;

        // 同意按钮（绿色）
        graphics.fill(agreeX, btnY, agreeX + btnW, btnY + btnH, agreeHover ? 0xFF3D8B4F : 0xFF2E6B3C);
        graphics.horizontalLine(agreeX, agreeX + btnW, btnY, 0x66FFFFFF);
        graphics.horizontalLine(agreeX, agreeX + btnW, btnY + btnH, 0x66FFFFFF);
        graphics.verticalLine(agreeX, btnY, btnY + btnH, 0x66FFFFFF);
        graphics.verticalLine(agreeX + btnW, btnY, btnY + btnH, 0x66FFFFFF);
        graphics.centeredText(this.font, "\u540c\u610f\u5e76\u7ee7\u7eed", agreeX + btnW / 2, btnY + 7, 0xFFFFFFFF);

        // 不同意按钮（红色）
        graphics.fill(denyX, btnY, denyX + btnW, btnY + btnH, denyHover ? 0xFF8B3D3D : 0xFF6B2E2E);
        graphics.horizontalLine(denyX, denyX + btnW, btnY, 0x66FFFFFF);
        graphics.horizontalLine(denyX, denyX + btnW, btnY + btnH, 0x66FFFFFF);
        graphics.verticalLine(denyX, btnY, btnY + btnH, 0x66FFFFFF);
        graphics.verticalLine(denyX + btnW, btnY, btnY + btnH, 0x66FFFFFF);
        graphics.centeredText(this.font, "\u4e0d\u540c\u610f", denyX + btnW / 2, btnY + 7, 0xFFFFFFFF);

        graphics.centeredText(this.font, "Esc \u4e5f\u53ef\u5173\u95ed\uff08\u89c6\u4e3a\u4e0d\u540c\u610f\uff09", centerX, btnY + btnH + 16, 0xFF777777);
        super.extractRenderState(graphics, mouseX, mouseY, delta);
    }

    @Override
    public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event, boolean doubleClick) {
        int mx = (int) event.x();
        int my = (int) event.y();
        // 关闭按钮（右上角 X）
        if (mx >= this.width - 26 && mx <= this.width - 4 && my >= 4 && my <= 26) {
            SideChatRenderer.setScreenCompat(null);
            return true;
        }
        if (event.button() != 0) {
            return super.mouseClicked(event, doubleClick);
        }
        int centerX = this.width / 2;
        int btnW = 160;
        int btnH = 26;
        int btnY = this.height / 2 + 60;
        int agreeX = centerX - btnW - 12;
        int denyX = centerX + 12;

        if (mx >= agreeX && mx <= agreeX + btnW && my >= btnY && my <= btnY + btnH) {
            // 同意：写入配置，打开图片选择器
            ChatAuthConfig.setImageUploadAgreed(true);
            SideChatRenderer.setScreenCompat(new ImagePickerScreen());
            return true;
        }
        if (mx >= denyX && mx <= denyX + btnW && my >= btnY && my <= btnY + btnH) {
            // 不同意：退出（下次再弹）
            SideChatRenderer.setScreenCompat(null);
            return true;
        }
        return super.mouseClicked(event, doubleClick);
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
