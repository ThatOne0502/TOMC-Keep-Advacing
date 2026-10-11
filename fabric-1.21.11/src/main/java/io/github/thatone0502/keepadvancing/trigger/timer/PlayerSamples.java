package io.github.thatone0502.keepadvancing.trigger.timer;

import java.util.HashMap;
import java.util.Map;

/**
 * 一个玩家（以 {@code PlayerAdvancements} 为生命周期单位）在 timer_test 里的全部采样状态。
 *
 * <p>当该玩家不再有任何 timer_test 监听者时，整个对象会被丢弃——历史是<b>会话语义</b>的，
 * 不做持久化。真正需要跨会话的那部分由 {@link Check#VALUE} 直接读原版统计文件提供。
 */
public final class PlayerSamples {

    private final Map<String, Series> series = new HashMap<>();

    /** {@link Gauge#SNEAK_PRESS} 的累计按下次数（只增不减）。 */
    private int sneakPresses;
    private boolean sneakWasDown;

    public Series series(Source source) {
        return series.computeIfAbsent(source.raw(), key -> new Series());
    }

    public int sneakPresses() {
        return sneakPresses;
    }

    public void addSneakPress() {
        sneakPresses++;
    }

    public boolean sneakWasDown() {
        return sneakWasDown;
    }

    public void setSneakWasDown(boolean down) {
        sneakWasDown = down;
    }

    /** 是否还没有任何采样序列（用于判断状态是否可以整体回收）。 */
    public boolean isEmpty() {
        return series.isEmpty();
    }
}
