package io.github.thatone0502.keepadvancing.trigger.timer;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;

/**
 * Keep Advancing 内置的「实时仪表」：一些不是原版统计、但每 tick 都能廉价读到的整数，
 * 补上原版统计覆盖不到的地带。
 *
 * <p>存在的必要性：原版统计<b>除了 {@code time_since_death} / {@code time_since_rest}
 * 会归零重置外，全部单调不减</b>。所以 {@code decrease_count} / {@code total_decrease} /
 * {@code change_count} 这三个 check 在原版统计上基本没有适用场景——它们要的是"会上下浮动的量"。
 * 血量、氧气、饱食度、坐标正是这类量。
 *
 * <p>命名空间的形态刻意与原版记分板准则保持一致（{@code keepadvancing:health}），
 * 这样 {@code source} 字段里两种来源看起来是同一种东西。
 *
 * <div>已知取舍：{@link #HEALTH} 取整到整数点血，因此不足 1 点的擦伤可能观察不到变化。</div>
 */
public enum Gauge {

    /** 生命值，整数点血（0..20）。不足 1 点的变化可能观察不到。 */
    HEALTH("health"),
    /** 氧气值，单位 tick；潜水时每 tick −1，是"溺水了多久"的天然来源。 */
    AIR("air"),
    /** 饱食度（0..20）。 */
    FOOD("food"),
    /** 经验等级。 */
    XP_LEVEL("xp_level"),
    /** 脚部方块坐标的整数部分；{@code total_decrease} 即"净坠落/下降了多少格"。 */
    Y("y"),
    /**
     * 潜行键的累计按下次数（只增不减），由 timer_test 自己在每 tick 做上升沿检测维护。
     *
     * <p>用 {@code isShiftKeyDown()} 而不是 {@code isCrouching()}：后者会被"头顶有方块"、
     * 飞行、骑乘等状态影响，不是按键本身。
     */
    SNEAK_PRESS("sneak_press");

    private static final String NAMESPACE = "keepadvancing:";
    private static final Map<String, Gauge> BY_ID;

    static {
        Map<String, Gauge> byId = new LinkedHashMap<>();
        for (Gauge gauge : values()) {
            byId.put(gauge.id(), gauge);
        }
        BY_ID = Collections.unmodifiableMap(byId);
    }

    private final String path;

    Gauge(String path) {
        this.path = path;
    }

    /** 完整的 source 字符串，例如 {@code keepadvancing:sneak_press}。 */
    public String id() {
        return NAMESPACE + path;
    }

    public static String ids() {
        return BY_ID.keySet().toString();
    }

    /** @return 匹配的仪表，未匹配返回 null。 */
    public static Gauge byId(String raw) {
        return BY_ID.get(raw);
    }

    /**
     * 采样前的每 tick 准备：需要自己维护计数的仪表在这里推进状态。
     * 必须早于 {@link #read} 调用。
     */
    public void prepare(ServerPlayer player, PlayerSamples samples) {
        if (this == SNEAK_PRESS) {
            boolean down = player.isShiftKeyDown();
            if (down && !samples.sneakWasDown()) {
                samples.addSneakPress();
            }
            samples.setSneakWasDown(down);
        }
    }

    public int read(ServerPlayer player, PlayerSamples samples) {
        switch (this) {
            case HEALTH:
                return Math.round(player.getHealth());
            case AIR:
                return player.getAirSupply();
            case FOOD:
                return player.getFoodData().getFoodLevel();
            case XP_LEVEL:
                return player.experienceLevel;
            case Y:
                return Mth.floor(player.getY());
            case SNEAK_PRESS:
                return samples.sneakPresses();
            default:
                throw new IllegalStateException("unhandled gauge " + name());
        }
    }
}
