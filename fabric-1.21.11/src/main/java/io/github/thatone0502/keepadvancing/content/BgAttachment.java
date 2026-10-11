package io.github.thatone0502.keepadvancing.content;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import io.github.thatone0502.keepadvancing.KeepAdvancing;

/**
 * 背景的附着方式（rev14 起 {@code background} 对象形态的新字段 {@code attachment}）。
 *
 * <p>{@link #FIXED} = 背景固定跟随视口（原版行为，平铺偏移用 scroll 取模）；
 * {@link #LOCAL} = 背景跟随内容包围盒，绘制区域取
 * {@code max(视口宽, 内容累计宽) × max(视口高, 内容累计高)}。
 *
 * <p>未知取值降级到 {@link #FIXED} 并 warn，绝不抛异常。
 */
public enum BgAttachment {
    FIXED,
    LOCAL;

    public static final BgAttachment DEFAULT = FIXED;

    public static final Codec<BgAttachment> CODEC = Codec.STRING.comapFlatMap(
            name -> {
                BgAttachment attachment = byName(name);
                if (attachment != null) {
                    return DataResult.success(attachment);
                }
                KeepAdvancing.LOGGER.warn("keepadvancing: unknown background attachment '{}', falling back to {}", name, DEFAULT.name().toLowerCase());
                return DataResult.success(DEFAULT);
            },
            attachment -> attachment.name().toLowerCase());

    /** 名字查表（大小写不敏感）；未知返回 {@code null}。 */
    public static BgAttachment byName(String name) {
        if (name == null) {
            return null;
        }
        for (BgAttachment attachment : values()) {
            if (attachment.name().equalsIgnoreCase(name)) {
                return attachment;
            }
        }
        return null;
    }

    /** 网络按 ordinal 传输；越界一律降级到默认并 warn。 */
    public static BgAttachment byOrdinal(int ordinal) {
        BgAttachment[] values = values();
        if (ordinal < 0 || ordinal >= values.length) {
            KeepAdvancing.LOGGER.warn("keepadvancing: bad background attachment ordinal {} over the wire, using {}", ordinal, DEFAULT);
            return DEFAULT;
        }
        return values[ordinal];
    }
}
