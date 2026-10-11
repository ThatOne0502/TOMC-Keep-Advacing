package io.github.thatone0502.keepadvancing.mixin.client;

import io.github.thatone0502.keepadvancing.api.KaAdvancementNode;
import io.github.thatone0502.keepadvancing.logic.IconRenderer;
import java.util.List;
import net.minecraft.advancements.AdvancementNode;
import net.minecraft.advancements.DisplayInfo;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.advancements.AdvancementTab;
import net.minecraft.client.gui.screens.advancements.AdvancementWidget;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * rev14 客户端渲染 —— 原版 {@code AdvancementWidget}：① 字符串 icon（画纹理）② 多父连线。
 *
 * <p>① 图标：{@code draw} 与 {@code drawHover} 里各有一处
 * {@code guiGraphics.renderFakeItem(this.display.getIcon(), ...)}（源码 {@code :163} 与 {@code :264}，
 * 两处都是 3 参重载 {@code (ItemStack,int,int)}），用 {@code @Redirect} 改走
 * {@link IconRenderer}（有纹理画纹理，没纹理原样回退 {@code renderFakeItem}）。
 *
 * <p>② 多父连线：{@code drawConnectivity} 只有约 28 行，整体重写 —— 先逐行照抄原版的
 * {@code this.parent} 折线逻辑，再对 {@code ka$extraParents()} 里每个能在 tab 里查到 widget 的
 * 额外父用**同一套几何常量、同一个 {@code bl} 语义**再画一遍，最后原样递归 {@code this.children}。
 * 新旧父共用同一段绘制代码（{@link #ka$drawParentLine}），避免描边/内线语义分叉。
 */
@Mixin(AdvancementWidget.class)
public abstract class AdvancementWidgetMixin {

    @Shadow
    @Nullable
    private AdvancementWidget parent;

    @Shadow
    @Final
    private List<AdvancementWidget> children;

    @Shadow
    @Final
    private AdvancementTab tab;

    @Shadow
    @Final
    private AdvancementNode advancementNode;

    @Shadow
    @Final
    private int x;

    @Shadow
    @Final
    private int y;

    // ---- ① 字符串 icon ----

    /** 对照 {@code AdvancementWidget.java:163}：{@code renderFakeItem(ItemStack,int,int)}。 */
    @Redirect(method = "draw", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/GuiGraphics;renderFakeItem(Lnet/minecraft/world/item/ItemStack;II)V"))
    private void ka$iconInDraw(GuiGraphics guiGraphics, ItemStack itemStack, int i, int j) {
        IconRenderer.draw(guiGraphics, ka$displayOf(this.advancementNode), itemStack, i, j, IconRenderer.SIZE);
    }

    /** 对照 {@code AdvancementWidget.java:264}：同样是 {@code renderFakeItem(ItemStack,int,int)}。 */
    @Redirect(method = "drawHover", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/GuiGraphics;renderFakeItem(Lnet/minecraft/world/item/ItemStack;II)V"))
    private void ka$iconInHover(GuiGraphics guiGraphics, ItemStack itemStack, int i, int j) {
        IconRenderer.draw(guiGraphics, ka$displayOf(this.advancementNode), itemStack, i, j, IconRenderer.SIZE);
    }

    // ---- ② 多父连线 ----

    @Inject(method = "drawConnectivity", at = @At("HEAD"), cancellable = true)
    private void ka$drawConnectivity(GuiGraphics guiGraphics, int i, int j, boolean bl, CallbackInfo ci) {
        if (this.parent != null) {
            this.ka$drawParentLine(guiGraphics, this.parent, i, j, bl);
        }

        for (AdvancementNode extraParent : ((KaAdvancementNode) (Object) this.advancementNode).ka$extraParents()) {
            if (extraParent == null) {
                continue;
            }
            AdvancementWidget widget = this.tab.getWidget(extraParent.holder());
            if (widget == null || widget == this.parent) {
                continue;
            }
            this.ka$drawParentLine(guiGraphics, widget, i, j, bl);
        }

        for (AdvancementWidget child : this.children) {
            child.drawConnectivity(guiGraphics, i, j, bl);
        }

        ci.cancel();
    }

    /**
     * 折线几何与配色**逐字照抄** {@code AdvancementWidget.java:123-150}（原版方法体）：
     * {@code k = i + parent.x + 13; l = i + parent.x + 26 + 4; m = j + parent.y + 13;
     * n = i + this.x + 13; o = j + this.y + 13;}。
     *
     * <p>父 widget 从参数传入，所以"主父"与"额外父"走完全相同的绘制路径；子节点自己的
     * {@code x/y} 仍然是 {@code this} 的（{@code n/o}）。
     */
    private void ka$drawParentLine(GuiGraphics guiGraphics, AdvancementWidget parentWidget, int i, int j, boolean bl) {
        AdvancementWidgetMixin parentAccessor = (AdvancementWidgetMixin) (Object) parentWidget;
        int k = i + parentAccessor.x + 13;
        int l = i + parentAccessor.x + 26 + 4;
        int m = j + parentAccessor.y + 13;
        int n = i + this.x + 13;
        int o = j + this.y + 13;
        int p = bl ? -16777216 : -1;
        if (bl) {
            guiGraphics.hLine(l, k, m - 1, p);
            guiGraphics.hLine(l + 1, k, m, p);
            guiGraphics.hLine(l, k, m + 1, p);
            guiGraphics.hLine(n, l - 1, o - 1, p);
            guiGraphics.hLine(n, l - 1, o, p);
            guiGraphics.hLine(n, l - 1, o + 1, p);
            guiGraphics.vLine(l - 1, o, m, p);
            guiGraphics.vLine(l + 1, o, m, p);
        } else {
            guiGraphics.hLine(l, k, m, p);
            guiGraphics.hLine(n, l, o, p);
            guiGraphics.vLine(l, o, m, p);
        }
    }

    private static @Nullable DisplayInfo ka$displayOf(AdvancementNode node) {
        return node.advancement().display().orElse(null);
    }
}
