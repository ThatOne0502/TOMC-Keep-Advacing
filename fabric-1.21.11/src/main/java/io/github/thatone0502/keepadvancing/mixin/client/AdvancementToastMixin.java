package io.github.thatone0502.keepadvancing.mixin.client;

import io.github.thatone0502.keepadvancing.logic.IconRenderer;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.DisplayInfo;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.toasts.AdvancementToast;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * rev14 客户端渲染 —— 进度弹窗（toast）里的字符串 icon。
 *
 * <p>对照 {@code AdvancementToast.java:84}：{@code guiGraphics.renderFakeItem(displayInfo.getIcon(), 8, 8)}
 * （3 参重载 {@code (ItemStack,int,int)}）。{@code AdvancementToast} 没有
 * {@code DisplayInfo} 字段，只有 {@code AdvancementHolder}，所以 {@code @Shadow} 那个字段再取 display。
 *
 * <p>⚠️ {@code require = 0}：用户实例里装了 {@code AdvancementPlaques}，它很可能也改这一处；
 * 命中不了就静默跳过（弹窗图标退回原版物品渲染），**绝不能因此崩**。
 */
@Mixin(AdvancementToast.class)
public abstract class AdvancementToastMixin {

    @Shadow
    @Final
    private AdvancementHolder advancement;

    @Redirect(require = 0, method = "render", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/GuiGraphics;renderFakeItem(Lnet/minecraft/world/item/ItemStack;II)V"))
    private void ka$iconInToast(GuiGraphics guiGraphics, ItemStack itemStack, int i, int j) {
        DisplayInfo display = ka$display();
        if (display == null) {
            guiGraphics.renderFakeItem(itemStack, i, j);
            return;
        }
        IconRenderer.draw(guiGraphics, display, itemStack, i, j, IconRenderer.SIZE);
    }

    private @Nullable DisplayInfo ka$display() {
        return this.advancement == null ? null : this.advancement.value().display().orElse(null);
    }
}
