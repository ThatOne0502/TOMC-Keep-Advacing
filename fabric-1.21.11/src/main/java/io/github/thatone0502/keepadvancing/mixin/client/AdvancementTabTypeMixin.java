package io.github.thatone0502.keepadvancing.mixin.client;

import io.github.thatone0502.keepadvancing.logic.IconContext;
import io.github.thatone0502.keepadvancing.logic.IconRenderer;
import net.minecraft.advancements.DisplayInfo;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * rev14 客户端渲染 —— 原版标签页图标（{@code AdvancementTabType.drawIcon}）。
 *
 * <p>PLAN §6.3 的方案 B 变体：整体 Redirect {@code AdvancementTabType.drawIcon} 的**调用**需要在
 * {@code AdvancementTab} 里拿到包私有枚举类型（跨包 Mixin 拿不到），所以改为在本枚举内部重定向
 * **最后一行的 {@code guiGraphics.renderFakeItem(itemStack, l, m)}**
 * （对照 {@code AdvancementTabType.java:134}，3 参重载 {@code (ItemStack,int,int)}）。
 *
 * <p>好处：{@code l/m} 已经是加过标签页类型偏移的最终图标坐标，纹理直接画在同一处，不用自己重算
 * ABOVE/BELOW/LEFT/RIGHT 的 +6/+9 之类常量。{@link IconContext} 由
 * {@link AdvancementTabMixin#ka$pushIconDisplay} 在 {@code AdvancementTab.drawIcon} 期间设置。
 * <p>注意：原版 {@code AdvancementTabType} 是**包私有**枚举（`enum AdvancementTabType`，无 public），
 * 所以这里只能用字符串 {@code targets}，不能写 {@code @Mixin(AdvancementTabType.class)}
 * （那会报「不是公共的; 无法从外部程序包中对其进行访问」）。注入句柄本身只碰公开的
 * {@code GuiGraphics} / {@code ItemStack}，不需要该枚举出现在签名里。
 */
@Mixin(targets = "net.minecraft.client.gui.screens.advancements.AdvancementTabType")
public abstract class AdvancementTabTypeMixin {

    @Redirect(method = "drawIcon", at = @At(value = "INVOKE",
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
