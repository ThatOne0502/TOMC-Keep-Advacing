package io.github.thatone0502.keepadvancing.logic;

import net.minecraft.advancements.DisplayInfo;
import org.jspecify.annotations.Nullable;

/**
 * rev14 渲染期的"当前选项卡图标来源"暂存（只给标签页图标用）。
 *
 * <p>原版 {@code AdvancementTabType.drawIcon} 只收到 {@code ItemStack}，拿不到 {@code DisplayInfo}；
 * 而 {@code AdvancementScreen}/{@code BetterAdvancementsScreen} 都是**逐个选项卡串行**调
 * {@code drawIcon}，所以用这个渲染线程上的静态槽位把"当前正在画图标的是哪个选项卡"传进去。
 *
 * <p>为什么不做成 Mixin 的 {@code @Unique} 静态字段：那样第二个 Mixin 要跨类读第一个 Mixin 的
 * {@code @Unique} 字段，Mixin 会把这类字段搬到目标类上，跨类引用容易踩坑。放普通类里最稳。
 *
 * <p>{@code null} 表示"没有处于标签页图标绘制上下文"，此时任何带纹理的 icon 都不该被改写。
 */
public final class IconContext {

    private static volatile DisplayInfo current;

    private IconContext() {
    }

    public static @Nullable DisplayInfo get() {
        return current;
    }

    public static void set(@Nullable DisplayInfo display) {
        current = display;
    }

    public static void clear() {
        current = null;
    }
}
