package com.tgzjdv.chat.screen;

import com.tgzjdv.chat.image.ImageCache;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

/**
 * 图片放大查看界面
 * 全屏半透明背景 + 等比缩放显示图片
 */
public class ImageViewerScreen extends Screen {

    private static final Component TITLE = Component.literal("图片查看");

    private final String imageUrl;
    private Identifier texture;
    private int texW = 1;
    private int texH = 1;
    private boolean loaded = false;

    // 缩放和平移
    private float zoom = 1.0f;
    private int offsetX = 0;
    private int offsetY = 0;
    private boolean dragging = false;
    private int dragStartMouseX = 0;
    private int dragStartMouseY = 0;
    private int dragStartOffsetX = 0;
    private int dragStartOffsetY = 0;

    // 右键菜单
    private boolean menuOpen = false;
    private int menuX = 0;
    private int menuY = 0;
    private static final int MENU_W = 120;
    private static final int MENU_ITEM_H = 16;
    private static final int MENU_COUNT = 2;

    public ImageViewerScreen(String imageUrl) {
        super(TITLE);
        this.imageUrl = imageUrl;
    }

    @Override
    protected void init() {
        // 请求图片（已缓存则立即显示）
        Identifier cached = ImageCache.getCached(imageUrl);
        if (cached != null) {
            int[] dims = ImageCache.getDimensions(imageUrl);
            if (dims != null) {
                this.texture = cached;
                this.texW = dims[0];
                this.texH = dims[1];
                this.loaded = true;
            }
        } else {
            ImageCache.requestImage(imageUrl, id -> {
                if (id != null) {
                    int[] dims = ImageCache.getDimensions(imageUrl);
                    this.texture = id;
                    if (dims != null) {
                        this.texW = dims[0];
                        this.texH = dims[1];
                    }
                    this.loaded = true;
                }
            });
        }
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        // 全屏半透明黑
        graphics.fill(0, 0, this.width, this.height, 0xCC000000);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        // 标题提示
        graphics.centeredText(this.font, "图片查看 - Esc 关闭 / 滚轮缩放 / 左键拖动", this.width / 2, 10, 0xFFAAAAAA);

        // 关闭按钮（右上角 X）
        int closeX = this.width - 26;
        int closeY = 4;
        boolean closeHover = mouseX >= closeX && mouseX <= closeX + 22 && mouseY >= closeY && mouseY <= closeY + 22;
        graphics.fill(closeX, closeY, closeX + 22, closeY + 22, closeHover ? 0xFF8B3D3D : 0xFF6B2E2E);
        graphics.centeredText(this.font, "\u2715", closeX + 11, closeY + (22 - this.font.lineHeight) / 2, 0xFFFFFFFF);

        if (this.loaded && this.texture != null) {
            // 拖动中：GLFW 检测左键，更新平移偏移
            if (this.dragging) {
                long handle = Minecraft.getInstance().getWindow().handle();
                boolean leftDown = org.lwjgl.glfw.GLFW.glfwGetMouseButton(handle, org.lwjgl.glfw.GLFW.GLFW_MOUSE_BUTTON_LEFT)
                        == org.lwjgl.glfw.GLFW.GLFW_PRESS;
                if (!leftDown) {
                    this.dragging = false;
                } else {
                    double curX = Minecraft.getInstance().mouseHandler.getScaledXPos(Minecraft.getInstance().getWindow());
                    double curY = Minecraft.getInstance().mouseHandler.getScaledYPos(Minecraft.getInstance().getWindow());
                    this.offsetX = this.dragStartOffsetX + (int) (curX - this.dragStartMouseX);
                    this.offsetY = this.dragStartOffsetY + (int) (curY - this.dragStartMouseY);
                }
            }
            // 等比缩放（基础自适应 × 缩放倍率），围绕屏幕中心
            float maxW = this.width * 0.85f;
            float maxH = this.height * 0.85f;
            float baseScale = Math.min(maxW / texW, maxH / texH);
            float scale = baseScale * this.zoom;
            int drawW = Math.max(1, (int) (texW * scale));
            int drawH = Math.max(1, (int) (texH * scale));
            int x = (this.width - drawW) / 2 + this.offsetX;
            int y = (this.height - drawH) / 2 + this.offsetY;
            // 绘制图片（等比缩放）
            graphics.blit(RenderPipelines.GUI_TEXTURED, this.texture, x, y, 0.0f, 0.0f, drawW, drawH, texW, texH, texW, texH);
            // 边框
            graphics.horizontalLine(x, x + drawW, y, 0x66FFFFFF);
            graphics.horizontalLine(x, x + drawW, y + drawH, 0x66FFFFFF);
            graphics.verticalLine(x, y, y + drawH, 0x66FFFFFF);
            graphics.verticalLine(x + drawW, y, y + drawH, 0x66FFFFFF);
        } else {
            // 加载中
            graphics.centeredText(this.font, "加载中...", this.width / 2, this.height / 2, 0xFFAAAAAA);
        }
        // 右键菜单
        if (this.menuOpen) {
            int menuBottom = this.menuY + MENU_COUNT * MENU_ITEM_H;
            graphics.fill(this.menuX, this.menuY, this.menuX + MENU_W, menuBottom, 0xE62D303A);
            graphics.horizontalLine(this.menuX, this.menuX + MENU_W, this.menuY, 0xFFAAAAAA);
            graphics.horizontalLine(this.menuX, this.menuX + MENU_W, menuBottom, 0xFFAAAAAA);
            graphics.verticalLine(this.menuX, this.menuY, menuBottom, 0xFFAAAAAA);
            graphics.verticalLine(this.menuX + MENU_W, this.menuY, menuBottom, 0xFFAAAAAA);
            String[] items = {"复制图片地址", "复制图片文件"};
            for (int i = 0; i < items.length; i++) {
                int itemY = this.menuY + i * MENU_ITEM_H;
                if (mouseX >= this.menuX && mouseX <= this.menuX + MENU_W
                        && mouseY >= itemY && mouseY <= itemY + MENU_ITEM_H) {
                    graphics.fill(this.menuX, itemY, this.menuX + MENU_W, itemY + MENU_ITEM_H, 0x50222222);
                }
                graphics.text(this.font, items[i], this.menuX + 6, itemY + 3, 0xFFFFFFFF, false);
            }
        }
        super.extractRenderState(graphics, mouseX, mouseY, delta);
    }

    @Override
    public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event, boolean doubleClick) {
        int mx = (int) event.x();
        int my = (int) event.y();
        // 关闭按钮（右上角 X）
        if (mx >= this.width - 26 && mx <= this.width - 4 && my >= 4 && my <= 26) {
            this.menuOpen = false;
            com.tgzjdv.chat.render.SideChatRenderer.setScreenCompat(null);
            return true;
        }
        // 菜单打开：点击处理
        if (this.menuOpen) {
            if (event.button() == 0 || event.button() == 1) {
                int idx = -1;
                if (mx >= this.menuX && mx <= this.menuX + MENU_W
                        && my >= this.menuY && my <= this.menuY + MENU_COUNT * MENU_ITEM_H) {
                    idx = (my - this.menuY) / MENU_ITEM_H;
                }
                this.menuOpen = false;
                if (idx == 0) {
                    Minecraft.getInstance().keyboardHandler.setClipboard(this.imageUrl);
                    return true;
                }
                if (idx == 1) {
                    com.tgzjdv.chat.image.ImageCache.copyImageToFile(this.imageUrl, path -> {
                        if (path != null) {
                            Minecraft.getInstance().keyboardHandler.setClipboard(path);
                            if (Minecraft.getInstance().player != null) {
                                Minecraft.getInstance().player.sendSystemMessage(
                                        net.minecraft.network.chat.Component.literal("\u00a7a图片已保存并复制路径: " + path));
                            }
                        } else if (Minecraft.getInstance().player != null) {
                            Minecraft.getInstance().player.sendSystemMessage(
                                    net.minecraft.network.chat.Component.literal("\u00a7c图片保存失败"));
                        }
                    });
                    return true;
                }
                return true; // 关闭菜单
            }
        }
        // 右键：打开菜单；左键：开始拖动
        if (event.button() == 1) {
            this.menuOpen = true;
            this.menuX = mx;
            this.menuY = my;
            return true;
        }
        if (event.button() == 0) {
            // 左键按下：开始拖动图片
            this.dragging = true;
            this.dragStartMouseX = mx;
            this.dragStartMouseY = my;
            this.dragStartOffsetX = this.offsetX;
            this.dragStartOffsetY = this.offsetY;
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        // 滚轮缩放：向上放大，向下缩小（限制范围 0.2 - 20）
        if (verticalAmount > 0) {
            this.zoom = Math.min(20.0f, this.zoom * 1.15f);
        } else if (verticalAmount < 0) {
            this.zoom = Math.max(0.2f, this.zoom / 1.15f);
        }
        return true;
    }

    /** 打开图片查看器 */
    public static void open(String imageUrl) {
        com.tgzjdv.chat.render.SideChatRenderer.setScreenCompat(new ImageViewerScreen(imageUrl));
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
