package io.github.thatone0502.keepadvancing.mixin.ba;

import io.github.thatone0502.keepadvancing.logic.IconContext;
import io.github.thatone0502.keepadvancing.logic.IconRenderer;
import net.minecraft.advancements.DisplayInfo;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * rev14 客户端渲染（BetterAdvancements 路）—— 标签页图标（字符串 icon）。
 *
 * <p>BA 的 {@code BetterAdvancementTabType.drawIcon(class_332,int left,int top,int width,int height,int index,class_1799 stack)}
 * 末尾（BA 源码 {@code BetterAdvancementTabType.java:93}）调 {@code guiGraphics.method_51445(stack, i, j)}，
 * 即 3 参 {@code renderFakeItem(ItemStack,int,int)}。这里 Redirect 它，
 * "当前选项卡的 DisplayInfo" 由 {@link BetterAdvancementTabMixin} 在
 * {@code BetterAdvancementTab.drawIcon(...)} 期间塞进 {@link IconContext}
 * （BA 的 {@code BetterAdvancementsScreen.java:353-357} 是逐个 tab 串行调 drawIcon 的）。
 *
 * <p>⚠️ {@code require = 0}：BA 改版只丢图标纹理，不崩。
 */
@Mixin(targets = "betteradvancements.common.gui.BetterAdvancementTabType")
public abstract class BetterAdvancementTabTypeMixin {

    @Redirect(require = 0, method = "drawIcon", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/GuiGraphics;renderFakeItem(Lnet/minecraft/world/item/ItemStack;II)V"))
    private void ka$drawTabIcon(GuiGraphics guiGraphics, ItemStack itemStack, int i, int j) {
        DisplayInfo display = IconContext.get();
        if (IconRenderer.iconTexture(display) != null) {
            IconRenderer.draw(guiGraphics, display, itemStack, i, j, IconRenderer.SIZE);
            return;
        }
        guiGraphics.renderFakeItem(itemStack, i, j);
    }
}
