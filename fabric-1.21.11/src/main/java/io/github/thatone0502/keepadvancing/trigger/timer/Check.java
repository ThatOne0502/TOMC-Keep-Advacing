package io.github.thatone0502.keepadvancing.trigger.timer;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;

/**
 * timer_test 的 {@code check}：在采样序列上到底算什么数。
 *
 * <p>设窗口内采样序列为 {@code v(t₀…tₙ)}，令 {@code dᵢ = v(tᵢ) − v(tᵢ₋₁)}：
 * <pre>
 *   increase_count   #{dᵢ &gt; 0}            正向变化次数
 *   decrease_count   #{dᵢ &lt; 0}            负向变化次数
 *   change_count     #{dᵢ ≠ 0}            有变化次数（= increase_count + decrease_count）
 *   total_increase   Σ max(dᵢ, 0)          总的增加量
 *   total_decrease   −Σ min(dᵢ, 0)         总的减少量（**非负幅度**，见下）
 *   delta            v(tₙ) − v(t₀)         窗口内净变化（带符号）
 *   value            v(tₙ)                 当前绝对值（不需要 window）
 * </pre>
 *
 * <p>{@code total_decrease} 刻意返回非负幅度而不是负数：这样除 {@code delta} 之外的所有
 * check 都是非负量，{@code operator} 几乎永远写 {@code ">="}，不会因为符号写反而静默失效。
 * 换算关系是 {@code delta = total_increase − total_decrease}。
 *
 * <p>只有 {@link #VALUE} 直接读统计文件里的终身值（可持久化、可在进服瞬间判定），
 * 其余六个都建立在本进程的采样历史上，因此必须给出 {@code window}。
 */
public enum Check {

    VALUE("value", false),
    INCREASE_COUNT("increase_count", true),
    DECREASE_COUNT("decrease_count", true),
    CHANGE_COUNT("change_count", true),
    TOTAL_INCREASE("total_increase", true),
    TOTAL_DECREASE("total_decrease", true),
    DELTA("delta", true);

    private static final Map<String, Check> BY_NAME;
    public static final Codec<Check> CODEC;

    static {
        Map<String, Check> byName = new LinkedHashMap<>();
        for (Check check : values()) {
            byName.put(check.jsonName, check);
        }
        BY_NAME = Collections.unmodifiableMap(byName);
        CODEC = Codec.STRING.comapFlatMap(
                name -> {
                    Check check = BY_NAME.get(name);
                    return check != null
                            ? DataResult.success(check)
                            : DataResult.error(() -> "unknown check '" + name + "', expected one of " + BY_NAME.keySet());
                },
                Check::jsonName);
    }

    private final String jsonName;
    private final boolean windowed;

    Check(String jsonName, boolean windowed) {
        this.jsonName = jsonName;
        this.windowed = windowed;
    }

    public String jsonName() {
        return jsonName;
    }

    /** 需要在采样序列上求值（即必须写 {@code window}）时为 true。 */
    public boolean isWindowed() {
        return windowed;
    }

    public int compute(Series series, long now, int window) {
        long minTick = now - window;
        switch (this) {
            case VALUE:
                return series.current();
            case DELTA:
                return series.sumSince(minTick);
            case INCREASE_COUNT:
                return series.countPositiveSince(minTick);
            case DECREASE_COUNT:
                return series.countNegativeSince(minTick);
            case CHANGE_COUNT:
                return series.countChangedSince(minTick);
            case TOTAL_INCREASE:
                return series.sumPositiveSince(minTick);
            case TOTAL_DECREASE:
                return -series.sumNegativeSince(minTick);
            default:
                throw new IllegalStateException("unhandled check " + name());
        }
    }
}
