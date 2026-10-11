package io.github.thatone0502.keepadvancing.trigger.advancement;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import io.github.thatone0502.keepadvancing.trigger.timer.Operator;

/**
 * {@code advancements_made} 的 {@code require}：把"选中集合里已完成了多少个"折算成一个数，
 * 再和 {@code value} 比较。
 *
 * <pre>
 *   type = "count"       实际值 = 已完成的个数                    （分子，0 .. total）
 *   type = "proportion"  实际值 = 已完成的个数 / 选中集合的大小    （0.0 .. 1.0）
 *   operator             比较运算符，缺省 "&gt;="
 *   value                比较的阈值
 * </pre>
 *
 * <p>{@code operator} 缺省为 {@code ">="}：这是最不容易写错的语义（"达到即算完成"）。
 *
 * <p>比较用的是 double，但 {@code ==} / {@code !=} 带 1e-9 容差——{@code 3.0 / 10.0} 与
 * JSON 里的 {@code 0.3} 在 IEEE 754 下确实是同一个 double，但没必要把正确性押在这上面。
 *
 * <p><b>选中集合为空时本判据恒不成立</b>（见 {@link #test} 的第一行）。这是刻意的：
 * 空集应当理解为"这条判据没有意义"，而不是"0 项里完成了 0 项，所以自动达成"。
 */
public record Requirement(Type type, Operator operator, double value) {

    public enum Type {

        PROPORTION("proportion"),
        COUNT("count");

        private static final Map<String, Type> BY_NAME;
        static final Codec<Type> CODEC;

        static {
            Map<String, Type> byName = new LinkedHashMap<>();
            for (Type type : values()) {
                byName.put(type.jsonName, type);
            }
            BY_NAME = Collections.unmodifiableMap(byName);
            CODEC = Codec.STRING.comapFlatMap(
                    name -> {
                        Type type = BY_NAME.get(name);
                        return type != null
                                ? DataResult.success(type)
                                : DataResult.error(() -> "unknown require.type '" + name
                                        + "', expected one of " + BY_NAME.keySet());
                    },
                    Type::jsonName);
        }

        private final String jsonName;

        Type(String jsonName) {
            this.jsonName = jsonName;
        }

        public String jsonName() {
            return jsonName;
        }
    }

    /** 注意书写顺序：{@link #RAW} 必须先于 {@link #CODEC}（Java 禁止静态字段前向引用）。 */
    private static final Codec<Requirement> RAW = RecordCodecBuilder.create(instance -> instance.group(
            Type.CODEC.fieldOf("type").forGetter(Requirement::type),
            Operator.CODEC.optionalFieldOf("operator", Operator.GREATER_OR_EQUAL).forGetter(Requirement::operator),
            Codec.DOUBLE.fieldOf("value").forGetter(Requirement::value)
    ).apply(instance, Requirement::new));

    public static final Codec<Requirement> CODEC = RAW.flatXmap(Requirement::checked, DataResult::success);

    /**
     * @param done  选中集合里<b>已完成</b>的进度条数
     * @param total 选中集合的大小（经过 include_hidden / require_display 过滤之后）
     * @return 判据是否成立
     */
    public boolean test(int done, int total) {
        if (total <= 0) {
            return false;
        }
        double actual = type == Type.COUNT ? done : (double) done / (double) total;
        return operator.test(actual, value);
    }

    private static DataResult<Requirement> checked(Requirement requirement) {
        if (requirement.type() == Type.PROPORTION) {
            if (requirement.value() < 0.0 || requirement.value() > 1.0) {
                return DataResult.error(() -> "'require.value' must be between 0.0 and 1.0 for type 'proportion', got "
                        + requirement.value());
            }
        } else if (requirement.value() < 0.0) {
            return DataResult.error(() -> "'require.value' must not be negative, got " + requirement.value());
        }
        return DataResult.success(requirement);
    }
}
