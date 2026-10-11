package io.github.thatone0502.keepadvancing.content;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import io.github.thatone0502.keepadvancing.KeepAdvancing;

/**
 * 背景绘制模式（rev14 起 {@code background} 对象形态的新字段 {@code mode}）。
 *
 * <p>解析策略：未知取值**降级到 {@link #TILE} 并记一条 warn**，绝不抛异常 ——
 * 数据包作者手滑不该炸服。因此这里不用 {@code Codec.STRING.xmap(::valueOf, ::name)}
 * （那个会抛 {@link IllegalArgumentException}），而是自己接住。
 */
public enum BgMode {
    /** 原版行为：按 16×16 平铺。 */
    TILE,
    /** 非等比拉伸铺满整个绘制区域。 */
    FILL,
    /** 等比缩放至铺满（可能被裁掉边缘）。 */
    COVER,
    /** 等比缩放至完整可见（可能留黑边）。 */
    CONTAIN;

    public static final BgMode DEFAULT = TILE;

    public static final Codec<BgMode> CODEC = Codec.STRING.comapFlatMap(
            name -> {
                BgMode mode = byName(name);
                if (mode != null) {
                    return DataResult.success(mode);
                }
                KeepAdvancing.LOGGER.warn("keepadvancing: unknown background mode '{}', falling back to {}", name, DEFAULT.name().toLowerCase());
                return DataResult.success(DEFAULT);
            },
            mode -> mode.name().toLowerCase());

    /** 名字查表（大小写不敏感）；未知返回 {@code null}。 */
    public static BgMode byName(String name) {
        if (name == null) {
            return null;
        }
        for (BgMode mode : values()) {
            if (mode.name().equalsIgnoreCase(name)) {
                return mode;
            }
        }
        return null;
    }

    /** 网络按 ordinal 传输；越界一律降级到默认并 warn。 */
    public static BgMode byOrdinal(int ordinal) {
        BgMode[] values = values();
        if (ordinal < 0 || ordinal >= values.length) {
            KeepAdvancing.LOGGER.warn("keepadvancing: bad background mode ordinal {} over the wire, using {}", ordinal, DEFAULT);
            return DEFAULT;
        }
        return values[ordinal];
    }
}
