package io.github.thatone0502.keepadvancing.trigger.advancement;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.resources.Identifier;

/**
 * {@code advancements_made} 的 {@code list} 里的一个选择器条目。
 *
 * <p>两种写法都收：
 * <pre>
 *   "minecraft:story/smelt_iron"                     ← 裸字符串，等价 {"only": "..."}
 *   {"only": "..."} {"from": "..."} {"through": "..."}
 *   {"until": "..."} {"after": "..."} {"before": "..."}
 *   {"between": ["A", "B"]}                          ← 恰好 2 个 id
 * </pre>
 *
 * <p>编译成 {@link SelectorMode} + 锚点。{"between"} 的第二个锚点放进 {@link #second()}，
 * 其余模式为空。
 *
 * <p>为什么不用 {@code Codec.either} 串七个：那会得到七个嵌套的 {@code Either}，错误信息也
 * 只能说出"全都不匹配"。这里改成七个可选字段 + {@code flatXmap} 校验，能直接报出
 * "声明了 2 个键（only, from）" 或 "between 需要恰好 2 个 id，实际 1 个"。
 */
public record Selector(SelectorMode mode, Identifier first, Optional<Identifier> second) {

    private static final Codec<Selector> OBJECT =
            Raw.CODEC.flatXmap(Selector::checked, Selector::raw);

    /**
     * 对外编解码器。解码时先试裸字符串（{@code only} 的简写），失败再试对象；
     * 编码时一律写成对象形式（属性集等价，不做无意义的简写还原）。
     */
    public static final Codec<Selector> CODEC = Codec.either(Identifier.CODEC, OBJECT).xmap(
            either -> either.map(
                    id -> new Selector(SelectorMode.ONLY, id, Optional.empty()),
                    Function.identity()),
            selector -> Either.right(selector));

    private static DataResult<Selector> checked(Raw raw) {
        List<String> present = new ArrayList<>(7);
        if (raw.only().isPresent()) {
            present.add(SelectorMode.ONLY.jsonName());
        }
        if (raw.from().isPresent()) {
            present.add(SelectorMode.FROM.jsonName());
        }
        if (raw.through().isPresent()) {
            present.add(SelectorMode.THROUGH.jsonName());
        }
        if (raw.until().isPresent()) {
            present.add(SelectorMode.UNTIL.jsonName());
        }
        if (raw.after().isPresent()) {
            present.add(SelectorMode.AFTER.jsonName());
        }
        if (raw.before().isPresent()) {
            present.add(SelectorMode.BEFORE.jsonName());
        }
        if (raw.between().isPresent()) {
            present.add(SelectorMode.BETWEEN.jsonName());
        }
        if (present.size() != 1) {
            String got = present.isEmpty() ? "none" : String.join(", ", present);
            return DataResult.error(() -> "a selector must declare exactly one of "
                    + SelectorMode.keyList() + ", got " + present.size() + " (" + got + ")");
        }

        if (raw.between().isPresent()) {
            List<Identifier> ids = raw.between().get();
            if (ids.size() != 2) {
                return DataResult.error(() -> "selector 'between' requires exactly 2 advancement ids, got "
                        + ids.size());
            }
            return DataResult.success(new Selector(
                    SelectorMode.BETWEEN, ids.get(0), Optional.of(ids.get(1))));
        }
        if (raw.only().isPresent()) {
            return DataResult.success(new Selector(SelectorMode.ONLY, raw.only().get(), Optional.empty()));
        }
        if (raw.from().isPresent()) {
            return DataResult.success(new Selector(SelectorMode.FROM, raw.from().get(), Optional.empty()));
        }
        if (raw.through().isPresent()) {
            return DataResult.success(new Selector(SelectorMode.THROUGH, raw.through().get(), Optional.empty()));
        }
        if (raw.until().isPresent()) {
            return DataResult.success(new Selector(SelectorMode.UNTIL, raw.until().get(), Optional.empty()));
        }
        if (raw.after().isPresent()) {
            return DataResult.success(new Selector(SelectorMode.AFTER, raw.after().get(), Optional.empty()));
        }
        return DataResult.success(new Selector(SelectorMode.BEFORE, raw.before().get(), Optional.empty()));
    }

    /** 编码方向。解码期已经校验过，这里不会失败。 */
    private DataResult<Raw> raw() {
        return DataResult.success(new Raw(
                mode == SelectorMode.ONLY ? Optional.of(first) : Optional.empty(),
                mode == SelectorMode.FROM ? Optional.of(first) : Optional.empty(),
                mode == SelectorMode.THROUGH ? Optional.of(first) : Optional.empty(),
                mode == SelectorMode.UNTIL ? Optional.of(first) : Optional.empty(),
                mode == SelectorMode.AFTER ? Optional.of(first) : Optional.empty(),
                mode == SelectorMode.BEFORE ? Optional.of(first) : Optional.empty(),
                mode == SelectorMode.BETWEEN ? Optional.of(List.of(first, second.orElseThrow())) : Optional.empty()));
    }

    /** 七个键各自可选的原始形态，只用于编解码。 */
    private record Raw(
            Optional<Identifier> only,
            Optional<Identifier> from,
            Optional<Identifier> through,
            Optional<Identifier> until,
            Optional<Identifier> after,
            Optional<Identifier> before,
            Optional<List<Identifier>> between) {

        private static final Codec<Raw> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Identifier.CODEC.optionalFieldOf("only").forGetter(Raw::only),
                Identifier.CODEC.optionalFieldOf("from").forGetter(Raw::from),
                Identifier.CODEC.optionalFieldOf("through").forGetter(Raw::through),
                Identifier.CODEC.optionalFieldOf("until").forGetter(Raw::until),
                Identifier.CODEC.optionalFieldOf("after").forGetter(Raw::after),
                Identifier.CODEC.optionalFieldOf("before").forGetter(Raw::before),
                Identifier.CODEC.listOf().optionalFieldOf("between").forGetter(Raw::between)
        ).apply(instance, Raw::new));
    }
}
