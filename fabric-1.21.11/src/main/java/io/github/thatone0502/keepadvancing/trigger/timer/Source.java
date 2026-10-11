package io.github.thatone0502.keepadvancing.trigger.timer;

import java.util.Optional;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stat;
import net.minecraft.world.scores.criteria.ObjectiveCriteria;

/**
 * timer_test 的观测对象：一个整数序列的来源。
 *
 * <p>两种形态，语法刻意统一：
 * <ol>
 *   <li><b>原版统计</b>——直接写记分板准则名，例如
 *       {@code "minecraft.used:minecraft.totem_of_undying"}、
 *       {@code "minecraft.mined:minecraft.stone"}、
 *       {@code "minecraft.custom:minecraft.damage_dealt"}。
 *       解析交给原版自己的 {@link ObjectiveCriteria#byName(String)}，也就是
 *       {@code /scoreboard objectives add} 用的同一套解析器，因此<b>玩家能在游戏里
 *       用同一条字符串先验证一遍</b>，不需要我们另造一套语法。</li>
 *   <li><b>内置仪表</b>——{@link Gauge}，例如 {@code "keepadvancing:health"}。</li>
 * </ol>
 *
 * <p>解析在<b>解码期</b>完成：写错 source 会让这一份进度文件加载失败并在日志里报出
 * 可用取值，而不是安静地永不触发。
 *
 * <p>相等性只看 {@link #raw()}：{@code Stat} 实例是原版缓存里的对象，不适合作为判等依据。
 */
public final class Source {

    public static final Codec<Source> CODEC = Codec.STRING.comapFlatMap(Source::parse, Source::raw);

    private final String raw;
    private final Stat<?> stat;
    private final Gauge gauge;

    private Source(String raw, Stat<?> stat, Gauge gauge) {
        this.raw = raw;
        this.stat = stat;
        this.gauge = gauge;
    }

    public String raw() {
        return raw;
    }

    public boolean isGauge() {
        return gauge != null;
    }

    public Gauge gauge() {
        return gauge;
    }

    public static DataResult<Source> parse(String raw) {
        Gauge gauge = Gauge.byId(raw);
        if (gauge != null) {
            return DataResult.success(new Source(raw, null, gauge));
        }
        try {
            Optional<ObjectiveCriteria> criteria = ObjectiveCriteria.byName(raw);
            if (criteria.isPresent() && criteria.get() instanceof Stat<?> stat) {
                return DataResult.success(new Source(raw, stat, null));
            }
        } catch (Exception e) {
            return DataResult.error(() -> "timer_test source '" + raw + "' failed to resolve: " + e);
        }
        return DataResult.error(() -> "unknown timer_test source '" + raw
                + "'; expected a statistic criteria id (e.g. 'minecraft.used:minecraft.totem_of_undying')"
                + " or a built-in gauge " + Gauge.ids());
    }

    /** 采样前的每 tick 准备（只有需要自维护计数的仪表会做事）。 */
    public void prepare(ServerPlayer player, PlayerSamples samples) {
        if (gauge != null) {
            gauge.prepare(player, samples);
        }
    }

    /** 读当前值。原版统计走内存里的 {@code StatsCounter}，是一次 O(1) 的 map 查找，不碰磁盘。 */
    public int read(ServerPlayer player, PlayerSamples samples) {
        if (gauge != null) {
            return gauge.read(player, samples);
        }
        return player.getStats().getValue(stat);
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof Source source && raw.equals(source.raw);
    }

    @Override
    public int hashCode() {
        return raw.hashCode();
    }

    @Override
    public String toString() {
        return raw;
    }
}
