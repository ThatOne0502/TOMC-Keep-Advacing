package io.github.thatone0502.keepadvancing.mixin.client;

import io.github.thatone0502.keepadvancing.api.KaDisplayInfo;
import io.github.thatone0502.keepadvancing.logic.BackgroundRenderer;
import io.github.thatone0502.keepadvancing.logic.IconContext;
import net.minecraft.advancements.DisplayInfo;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.advancements.AdvancementTab;
import net.minecraft.client.gui.screens.advancements.AdvancementWidget;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.core.ClientAsset;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * rev14 客户端渲染 —— 原版 {@code AdvancementTab}：① 背景 mode/attachment ② 标签页图标（字符串 icon）。
 *
 * <p>{@code drawContents(GuiGraphics,int,int)} 只有约 28 行但改动面大（视口、平铺、背景模式），
 * 所以 {@code @Inject(HEAD, cancellable = true)} 整体重写：居中初始化 → scissor 234x113 →
 * translate → 背景（{@link BackgroundRenderer}）→ 连线两遍 → 节点 → popMatrix/disableScissor。
 *
 * <p>标签页图标（PLAN §6.3 的方案 B 变体）：{@code AdvancementTab.type} 是**包私有枚举**
 * {@code AdvancementTabType}，跨包的 Mixin 类没法给它写 {@code @Shadow} 字段，所以把真正的
 * 重定向放在同包的 {@link AdvancementTabTypeMixin} 里；本类只负责在 {@code drawIcon} 前后把
 * {@code this.display} 塞进 {@link IconContext}（原版 {@code AdvancementsScreen.java:205-207}
 * 是逐个选项卡串行调 {@code drawIcon} 的，渲染线程上安全）。
 */
@Mixin(AdvancementTab.class)
public abstract class AdvancementTabMixin {

    private static final int KA$VIEW_WIDTH = 234;
    private static final int KA$VIEW_HEIGHT = 113;

    @Shadow
    private double scrollX;

    @Shadow
    private double scrollY;

    @Shadow
    private int minX;

    @Shadow
    private int minY;

    @Shadow
    private int maxX;

    @Shadow
    private int maxY;

    @Shadow
    private boolean centered;

    @Shadow
    @Final
    private AdvancementWidget root;

    @Shadow
    @Final
    private DisplayInfo display;

    @Inject(method = "drawContents", at = @At("HEAD"), cancellable = true)
    private void ka$drawContents(GuiGraphics guiGraphics, int i, int j, CallbackInfo ci) {
        if (!this.centered) {
            // 原版写死 117 / 56（= 234/2 与 113/2），保持原样。
            this.scrollX = 117 - (this.maxX + this.minX) / 2;
            this.scrollY = 56 - (this.maxY + this.minY) / 2;
            this.centered = true;
        }

        guiGraphics.enableScissor(i, j, i + KA$VIEW_WIDTH, j + KA$VIEW_HEIGHT);
        guiGraphics.pose().pushMatrix();
        guiGraphics.pose().translate(i, j);

        Identifier identifier = this.display.getBackground()
                .map(ClientAsset.ResourceTexture::texturePath)
                .orElse(TextureManager.INTENTIONAL_MISSING_TEXTURE);

        KaDisplayInfo ka = (KaDisplayInfo) (Object) this.display;
        BackgroundRenderer.draw(guiGraphics, identifier, ka.ka$bgMode(), ka.ka$bgAttachment(),
                KA$VIEW_WIDTH, KA$VIEW_HEIGHT,
                this.maxX - this.minX, this.maxY - this.minY,
                this.scrollX, this.scrollY, ka.ka$bgWidth(), ka.ka$bgHeight());

        int k = Mth.floor(this.scrollX);
        int l = Mth.floor(this.scrollY);
        this.root.drawConnectivity(guiGraphics, k, l, true);
        this.root.drawConnectivity(guiGraphics, k, l, false);
        this.root.draw(guiGraphics, k, l);

        guiGraphics.pose().popMatrix();
        guiGraphics.disableScissor();
        ci.cancel();
    }

    /** 对照 {@code AdvancementTab.java:93}：{@code public void drawIcon(GuiGraphics, int, int)}。 */
    @Inject(method = "drawIcon", at = @At("HEAD"))
    private void ka$pushIconDisplay(GuiGraphics guiGraphics, int i, int j, CallbackInfo ci) {
        IconContext.set(this.display);
    }

    @Inject(method = "drawIcon", at = @At("RETURN"))
    private void ka$popIconDisplay(GuiGraphics guiGraphics, int i, int j, CallbackInfo ci) {
        IconContext.clear();
    }
}
