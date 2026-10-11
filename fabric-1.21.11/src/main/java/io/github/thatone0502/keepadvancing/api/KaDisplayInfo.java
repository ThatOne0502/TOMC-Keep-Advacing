package io.github.thatone0502.keepadvancing.api;

import io.github.thatone0502.keepadvancing.content.BgAttachment;
import io.github.thatone0502.keepadvancing.content.BgMode;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

/**
 * {@code net.minecraft.advancements.DisplayInfo} 的 Duck 接口（rev14）。
 *
 * <p>由 {@code DisplayInfoMixin} 实现。Mixin 往原版类里塞的字段只能通过接口暴露给
 * 非 Mixin 代码，业务侧一律用 {@code ((KaDisplayInfo) display).ka$xxx()} 访问。
 *
 * <p>语义：
 * <ul>
 *   <li>{@code iconTexture} —— {@code icon} 写成字符串时直接当纹理 ResourceLocation；
 *       为 {@code null} 表示用的是原版 {@code ItemStack} 形态，应回退 {@code renderFakeItem}。</li>
 *   <li>{@code bgWidth/bgHeight} —— 纹理像素尺寸，{@code -1} 表示未指定（运行时再从
 *       {@code TextureManager} 读，再读不到就退回 {@code TILE}）。</li>
 * </ul>
 */
public interface KaDisplayInfo {

    @Nullable
    Identifier ka$iconTexture();

    void ka$setIconTexture(@Nullable Identifier id);

    BgMode ka$bgMode();

    void ka$setBgMode(BgMode mode);

    BgAttachment ka$bgAttachment();

    void ka$setBgAttachment(BgAttachment attachment);

    /** 纹理像素宽；-1 表示 JSON 未指定。 */
    int ka$bgWidth();

    /** 纹理像素高；-1 表示 JSON 未指定。 */
    int ka$bgHeight();

    void ka$setBgSize(int width, int height);
}
