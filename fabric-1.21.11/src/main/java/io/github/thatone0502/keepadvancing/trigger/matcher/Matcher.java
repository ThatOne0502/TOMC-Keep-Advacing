package io.github.thatone0502.keepadvancing.trigger.matcher;

import java.util.Optional;
import java.util.regex.Pattern;

import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.util.ExtraCodecs;

/**
 * 共用的匹配元素，在 {@code mod_loaded.package}、{@code gui_open.menu/screen/custom} 四处通用。
 *
 * <pre>
 *   "minecraft:crafting"                 字面量，精确相等
 *   {"regex": "minecraft:generic_9x[1-6]"}  正则
 * </pre>
 *
 * <p><b>为什么字面量是精确相等。</b>作者写下一个 id 或类名时，期待的就是"就是这个"。同一份
 * JSON 里的 {@code net.minecraft.client.gui.screens.inventory.InventoryScreen} 带 6 个
 * {@code .}，若默认当正则，{@code .} 会静默变成"任意字符"，作者以为写的是精确匹配却拿到通配
 * 匹配 —— 这是最难受的一类 bug（不报错、只是行为不对）。所以想要正则必须显式包一层。
 *
 * <p><b>为什么正则用 {@code find()} 而不是 {@code matches()}。</b>历史原因：{@code mod_loaded}
 * 最初的实现就是 {@code Pattern.find()}，作者们已经按"子串查找"写数据（例：
 * {@code "io\.github\.thatone0502"} 并不等于任何真实包名，只有子串语义才命中）。保持
 * {@code find()} 可以让这些数据一字不改地继续工作；想锚定就自己写 {@code ^...$}。
 * 代价是 {@code "regex"} 里没写锚点时是"包含即命中"，这一点在文档里说清楚。
 */
public record Matcher(Optional<String> literal, Optional<Pattern> regex) {

    private static final Codec<Matcher> RAW_OBJECT = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.optionalFieldOf("literal").forGetter(Matcher::literal),
            ExtraCodecs.PATTERN.optionalFieldOf("regex").forGetter(Matcher::regex)
    ).apply(instance, Matcher::new));

    /** 对象形态：必须<b>恰好</b>给一个字段，两个都给或都不给都在解码期硬失败。 */
    private static final Codec<Matcher> OBJECT = RAW_OBJECT.flatXmap(Matcher::checked, DataResult::success);

    /** 对外编解码：JSON 字符串＝字面量，JSON 对象＝正则（也接受 {@code {"literal": ...}}）。 */
    public static final Codec<Matcher> CODEC = Codec.either(Codec.STRING, OBJECT).xmap(
            either -> either.map(Matcher::ofLiteral, matcher -> matcher),
            matcher -> matcher.regex().isPresent()
                    ? Either.right(matcher)
                    : Either.left(matcher.literal().orElseThrow()));

    public static Matcher ofLiteral(String value) {
        return new Matcher(Optional.of(value), Optional.empty());
    }

    public static Matcher ofRegex(String pattern) {
        return new Matcher(Optional.empty(), Optional.of(Pattern.compile(pattern)));
    }

    private static DataResult<Matcher> checked(Matcher matcher) {
        int present = (matcher.literal().isPresent() ? 1 : 0) + (matcher.regex().isPresent() ? 1 : 0);
        if (present != 1) {
            return DataResult.error(() -> "a matcher takes exactly one of 'literal' or 'regex', but "
                    + present + " were given");
        }
        return DataResult.success(matcher);
    }

    /** 字面量走 {@code equals}，正则走 {@link Pattern#find()}（子串查找，不是全匹配）。 */
    public boolean matches(String candidate) {
        if (candidate == null) {
            return false;
        }
        Optional<String> plain = this.literal;
        if (plain.isPresent()) {
            return plain.get().equals(candidate);
        }
        return this.regex.get().matcher(candidate).find();
    }

    /** 写日志用；不参与匹配。 */
    public String describe() {
        return this.literal.map(value -> '"' + value + '"')
                .orElseGet(() -> "regex(" + this.regex.get().pattern() + ")");
    }
}
