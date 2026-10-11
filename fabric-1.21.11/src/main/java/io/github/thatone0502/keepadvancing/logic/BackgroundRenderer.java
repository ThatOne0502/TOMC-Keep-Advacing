package io.github.thatone0502.keepadvancing.logic;

import io.github.thatone0502.keepadvancing.KeepAdvancing;
import io.github.thatone0502.keepadvancing.content.BgAttachment;
import io.github.thatone0502.keepadvancing.content.BgMode;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.resources.Identifier;

/**
 * rev14 背景绘制器（原版进度界面 + BetterAdvancements 共用）。
 *
 * <p>调用前提：调用方已经把自己的局部坐标系 translate 到界面左上角，并且已经
 * {@code enableScissor} 到视口范围。本类只在 {@code (0,0)-(areaW,areaH)} 的局部坐标里画。
 *
 * <p>绘制区域与偏移的语义（PLAN §6.2）：
 * <ul>
 *   <li>{@link BgAttachment#FIXED} —— 区域 = {@code viewW x viewH}，{@code TILE} 的平铺偏移用
 *       {@code floor(scroll) % 16}（跟随内容滚动，与原版一致），其余模式从 (0,0) 起铺。</li>
 *   <li>{@link BgAttachment#LOCAL} —— 区域 = {@code max(viewW, contentW) x max(viewH, contentH)}
 *       （background 跟随内容包围盒一起长），{@code TILE} 偏移同样用 {@code floor(scroll) % 16}。</li>
 * </ul>
 *
 * <p>纹理像素尺寸拿不到（{@code texW<=0 || texH<=0}）时，{@code COVER/CONTAIN/FILL} 一律静默退回
 * {@link BgMode#TILE}：宁可画成平铺，也不能让渲染线程抛异常。
 */
public final class BackgroundRenderer {

    private BackgroundRenderer() {
    }

    /**
     * @param g         已 translate 到界面左上角的绘制上下文（调用方负责 scissor）
     * @param tex       背景纹理（原版是 {@code display.getBackground()} 的 texturePath）
     * @param mode      绘制模式
     * @param attachment FIXED（视口尺寸）/ LOCAL（内容包围盒尺寸）
     * @param viewW     视口宽（原版 234）
     * @param viewH     视口高（原版 113）
     * @param contentW  内容累计宽（{@code maxX - minX}，可能 &lt;= 0）
     * @param contentH  内容累计高（{@code maxY - minY}，可能 &lt;= 0）
     * @param scrollX   水平滚动量（double，调用方原始值；内部取 floor）
     * @param scrollY   垂直滚动量
     * @param texW      纹理像素宽；&lt;=0 表示未知（运行时读 TextureManager，再读不到就退回 TILE）
     * @param texH      纹理像素高
     */
    public static void draw(GuiGraphics g, Identifier tex, BgMode mode, BgAttachment attachment,
                            int viewW, int viewH, int contentW, int contentH,
                            double scrollX, double scrollY, int texW, int texH) {
        if (g == null || tex == null) {
            return;
        }
        if (mode == null) {
            mode = BgMode.DEFAULT;
        }
        if (attachment == null) {
            attachment = BgAttachment.DEFAULT;
        }

        boolean local = attachment == BgAttachment.LOCAL;
        int areaW = local ? Math.max(viewW, contentW) : viewW;
        int areaH = local ? Math.max(viewH, contentH) : viewH;
        if (areaW <= 0 || areaH <= 0) {
            return;
        }

        int sizeW = texW;
        int sizeH = texH;
        if (sizeW <= 0 || sizeH <= 0) {
            int[] measured = textureSize(tex);
            sizeW = measured[0];
            sizeH = measured[1];
        }

        BgMode effective = mode;
        if ((mode == BgMode.FILL || mode == BgMode.COVER || mode == BgMode.CONTAIN) && (sizeW <= 0 || sizeH <= 0)) {
            // 拿不到纹理尺寸 ⇒ 退化成平铺（PLAN §6.2）；记一次 debug 即可，避免刷屏。
            KeepAdvancing.LOGGER.debug("keepadvancing: background {} has unknown texture size, falling back to TILE", tex);
            effective = BgMode.TILE;
        }

        int offsetX = (int) Math.floor(scrollX) % 16;
        int offsetY = (int) Math.floor(scrollY) % 16;

        switch (effective) {
            case FILL -> g.blit(tex, 0, 0, areaW, areaH, 0.0F, 1.0F, 0.0F, 1.0F);
            case COVER -> drawScaled(g, tex, areaW, areaH, sizeW, sizeH, true);
            case CONTAIN -> drawScaled(g, tex, areaW, areaH, sizeW, sizeH, false);
            // FIXED 与 LOCAL 的区别在 areaW/areaH；TILE 的偏移在两种 attachment 下都跟 scroll 走。
            default -> drawTiled(g, tex, areaW, areaH, offsetX, offsetY);
        }
    }

    /** 按 16x16 平铺；{@code ox/oy} 是已经取过模的偏移（与 {@code AdvancementTab.drawContents} 原版一致）。 */
    private static void drawTiled(GuiGraphics g, Identifier tex, int areaW, int areaH, int ox, int oy) {
        for (int k = -1; k <= areaW / 16 + 1; k++) {
            for (int l = -1; l <= areaH / 16 + 1; l++) {
                g.blit(RenderPipelines.GUI_TEXTURED, tex, ox + 16 * k, oy + 16 * l, 0.0F, 0.0F, 16, 16, 16, 16);
            }
        }
    }

    /** 等比缩放并居中；{@code cover} 为 true 时取 max 比例（可能溢出被 scissor 裁掉），否则取 min（可能留白）。 */
    private static void drawScaled(GuiGraphics g, Identifier tex, int areaW, int areaH, int texW, int texH, boolean cover) {
        float scaleX = areaW / (float) texW;
        float scaleY = areaH / (float) texH;
        float s = cover ? Math.max(scaleX, scaleY) : Math.min(scaleX, scaleY);
        int w = Math.round(texW * s);
        int h = Math.round(texH * s);
        int x = (areaW - w) / 2;
        int y = (areaH - h) / 2;
        g.blit(tex, x, y, x + w, y + h, 0.0F, 1.0F, 0.0F, 1.0F);
    }

    /**
     * 运行时读纹理像素尺寸：{@code Minecraft.getInstance().getTextureManager().getTexture(tex)
     * .getTexture().getWidth(0)/getHeight(0)}。
     *
     * <p>任何异常/空值都返回 {@code {-1,-1}}，**绝不向外抛**（渲染线程异常会直接崩客户端）。
     */
    public static int[] textureSize(Identifier tex) {
        if (tex == null) {
            return new int[] {-1, -1};
        }
        try {
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft == null) {
                return new int[] {-1, -1};
            }
            AbstractTexture texture = minecraft.getTextureManager().getTexture(tex);
            if (texture == null || texture.getTexture() == null) {
                return new int[] {-1, -1};
            }
            return new int[] {texture.getTexture().getWidth(0), texture.getTexture().getHeight(0)};
        } catch (Throwable t) {
            KeepAdvancing.LOGGER.debug("keepadvancing: could not read background texture size for {}: {}", tex, t.toString());
            return new int[] {-1, -1};
        }
    }
}
