package io.github.thatone0502.keepadvancing.trigger.timer;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;

/**
 * timer_test 的比较运算符。
 *
 * <p>因为采样是每 tick 一次的<b>离散观测</b>，{@code "=="} 在一 tick 内跨越多个整数时
 * 会被整段跳过（例如一 tick 内受到 47 点伤害，永远看不到中间的整数）。除确实在做
 * "恰好等于某个值"的判定外，一律优先用 {@code ">="} / {@code "<="}。
 */
public enum Operator {

    GREATER_OR_EQUAL(">="),
    GREATER(">"),
    LESS_OR_EQUAL("<="),
    LESS("<"),
    EQUAL("=="),
    NOT_EQUAL("!=");

    private static final Map<String, Operator> BY_NAME;
    public static final Codec<Operator> CODEC;

    static {
        Map<String, Operator> byName = new LinkedHashMap<>();
        for (Operator operator : values()) {
            byName.put(operator.symbol, operator);
        }
        BY_NAME = Collections.unmodifiableMap(byName);
        CODEC = Codec.STRING.comapFlatMap(
                symbol -> {
                    Operator operator = BY_NAME.get(symbol);
                    return operator != null
                            ? DataResult.success(operator)
                            : DataResult.error(() -> "unknown operator '" + symbol + "', expected one of " + BY_NAME.keySet());
                },
                Operator::symbol);
    }

    private final String symbol;

    Operator(String symbol) {
        this.symbol = symbol;
    }

    public String symbol() {
        return symbol;
    }

    /** @param actual 由 {@link Check#compute} 算出的值 @param threshold 进度 JSON 里的 {@code threshold} */
    public boolean test(int actual, int threshold) {
        switch (this) {
            case GREATER_OR_EQUAL:
                return actual >= threshold;
            case GREATER:
                return actual > threshold;
            case LESS_OR_EQUAL:
                return actual <= threshold;
            case LESS:
                return actual < threshold;
            case EQUAL:
                return actual == threshold;
            case NOT_EQUAL:
                return actual != threshold;
            default:
                throw new IllegalStateException("unhandled operator " + name());
        }
    }

    /**
     * double 版本（{@code advancements_made} 的比例比较用）。
     *
     * <p>大小比较按原样做，只有 {@code "=="} / {@code "!="} 带 1e-9 容差——比例是除出来的，
     * 不该把 {@code 3/10} 与 JSON 里的 {@code 0.3} 是否落在同一个 double 上当成正确性问题。
     *
     * @param actual    实际值（count 模式是完成个数，proportion 模式是完成比例）
     * @param threshold 进度 JSON 里的 {@code require.value}
     */
    public boolean test(double actual, double threshold) {
        switch (this) {
            case GREATER_OR_EQUAL:
                return actual >= threshold;
            case GREATER:
                return actual > threshold;
            case LESS_OR_EQUAL:
                return actual <= threshold;
            case LESS:
                return actual < threshold;
            case EQUAL:
                return Math.abs(actual - threshold) <= 1.0e-9;
            case NOT_EQUAL:
                return Math.abs(actual - threshold) > 1.0e-9;
            default:
                throw new IllegalStateException("unhandled operator " + name());
        }
    }
}
