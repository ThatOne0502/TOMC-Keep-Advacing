package io.github.thatone0502.keepadvancing.trigger;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.advancements.CriterionTrigger;
import net.minecraft.advancements.criterion.ContextAwarePredicate;
import net.minecraft.advancements.criterion.EntityPredicate;
import net.minecraft.advancements.criterion.SimpleCriterionTrigger;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.PlayerAdvancements;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.loot.LootContext;

import io.github.thatone0502.keepadvancing.trigger.timer.Check;
import io.github.thatone0502.keepadvancing.trigger.timer.Operator;
import io.github.thatone0502.keepadvancing.trigger.timer.PlayerSamples;
import io.github.thatone0502.keepadvancing.trigger.timer.Series;
import io.github.thatone0502.keepadvancing.trigger.timer.Source;

/**
 * {@code keepadvancing:timer_test}——把任意「可读成整数的东西」封装成一个限时检测器。
 *
 * <p>它的实例是一个**纯谓词**、不含任何状态：{@code source}（观测什么）、{@code check}
 * （怎么算）、{@code operator} + {@code threshold}（怎么比）、{@code window}（看多长的时间窗）。
 * 真正的状态（采样历史）全部活在本类里，按玩家与 source 分开保存。
 *
 * <p>为什么不能继承 {@code SimpleCriterionTrigger}：那个基类的 {@code players} 字段是 private，
 * 而本触发器必须自己遍历监听者才能做到"同一 tick 同一个 source 只读一次"。所以这里直接
 * 实现 {@link CriterionTrigger}，自己持有监听者账本。
 *
 * <p>每 tick 的流程：
 * <ol>
 *   <li>遍历在线玩家，取出他在本触发器上的监听者；</li>
 *   <li>按 {@link Source} 归组，每个 source 只读一次值；</li>
 *   <li>值发生变化时往 {@link Series} 里追加一笔增量，再对每条监听者求值；</li>
 *   <li>命中的授予**延迟到遍历结束之后**——{@code award()} 会反过来移除监听者，
 *       边遍历边授予会直接踩 {@code ConcurrentModificationException}。</li>
 * </ol>
 */
public class TimerTestTrigger implements CriterionTrigger<TimerTestTrigger.Instance> {

    /** window 上限：1 小时（72000 tick）。再长既没有意义，也会让稀疏表逼近上限。 */
    public static final int MAX_WINDOW = 72000;

    private final Map<PlayerAdvancements, Set<Listener<Instance>>> listeners = new HashMap<>();
    private final Map<PlayerAdvancements, PlayerSamples> samples = new HashMap<>();

    @Override
    public void addPlayerListener(PlayerAdvancements advancements, Listener<Instance> listener) {
        listeners.computeIfAbsent(advancements, key -> new HashSet<>()).add(listener);
    }

    @Override
    public void removePlayerListener(PlayerAdvancements advancements, Listener<Instance> listener) {
        Set<Listener<Instance>> owned = listeners.get(advancements);
        if (owned == null) {
            return;
        }
        owned.remove(listener);
        if (owned.isEmpty()) {
            listeners.remove(advancements);
            samples.remove(advancements);
        }
    }

    @Override
    public void removePlayerListeners(PlayerAdvancements advancements) {
        listeners.remove(advancements);
        samples.remove(advancements);
    }

    @Override
    public Codec<Instance> codec() {
        return Instance.CODEC;
    }

    /** 由 {@code ServerTickEvents.END_SERVER_TICK} 每 tick 调用一次。 */
    public void tick(MinecraftServer server) {
        List<ServerPlayer> players = server.getPlayerList().getPlayers();
        if (players.isEmpty()) {
            listeners.clear();
            samples.clear();
            return;
        }
        if (listeners.isEmpty()) {
            samples.clear();
            return;
        }

        long now = server.getTickCount();
        List<Award> awards = new ArrayList<>();

        for (ServerPlayer player : players) {
            Set<Listener<Instance>> owned = listeners.get(player.getAdvancements());
            if (owned == null || owned.isEmpty()) {
                continue;
            }
            PlayerSamples state = samples.computeIfAbsent(player.getAdvancements(), key -> new PlayerSamples());

            // JSON 里的 "player" 谓词必须在触发时校验：监听者是按进度注册的，与玩家是否
            // 满足条件无关。同一玩家共用一份 LootContext，且只在真有判据声明了 player
            // 时才构造（原版 SimpleCriterionTrigger.trigger 也是这个做法）。
            LootContext context = null;
            for (Listener<Instance> listener : owned) {
                if (listener.trigger().player().isPresent()) {
                    context = EntityPredicate.createContext(player, player);
                    break;
                }
            }

            Map<Source, List<Listener<Instance>>> bySource = new LinkedHashMap<>();
            for (Listener<Instance> listener : owned) {
                bySource.computeIfAbsent(listener.trigger().source(), key -> new ArrayList<>()).add(listener);
            }

            for (Map.Entry<Source, List<Listener<Instance>>> entry : bySource.entrySet()) {
                Source source = entry.getKey();
                source.prepare(player, state);

                Series series = state.series(source);
                series.accept(now, source.read(player, state));

                int longestWindow = 0;
                for (Listener<Instance> listener : entry.getValue()) {
                    longestWindow = Math.max(longestWindow, listener.trigger().windowTicks());
                }
                series.trim(now - longestWindow);

                for (Listener<Instance> listener : entry.getValue()) {
                    Instance instance = listener.trigger();
                    if (instance.player().isPresent() && !instance.player().get().matches(context)) {
                        continue;
                    }
                    if (instance.matches(series, now)) {
                        awards.add(new Award(player, listener));
                    }
                }
            }
        }

        for (Award award : awards) {
            award.grant();
        }

        // 兜底回收：正常路径下 vanilla 会在玩家退出时调用 removePlayerListener，
        // 这里再按"是否还在线"扫一遍，避免任何遗漏导致对象长期驻留。
        Set<PlayerAdvancements> online = new HashSet<>();
        for (ServerPlayer player : players) {
            online.add(player.getAdvancements());
        }
        listeners.keySet().removeIf(advancements -> !online.contains(advancements));
        samples.keySet().removeIf(advancements -> !listeners.containsKey(advancements));
    }

    private record Award(ServerPlayer player, Listener<Instance> listener) {
        void grant() {
            player.getAdvancements().award(listener.advancement(), listener.criterion());
        }
    }

    /**
     * timer_test 的判据实例（进度 JSON 的 {@code conditions}）。
     *
     * <p>字段即公开契约：
     * <pre>
     *   source     观测对象：原版统计准则名，或内置仪表 id
     *   check      怎么算（见 {@link Check}）
     *   operator   比较运算符（见 {@link Operator}）
     *   threshold  比较阈值
     *   window     时间窗长度，单位 tick；只有 check = "value" 时**不允许**出现
     * </pre>
     */
    public record Instance(
            Optional<ContextAwarePredicate> player,
            Source source,
            Check check,
            Operator operator,
            int threshold,
            Optional<Integer> window) implements SimpleCriterionTrigger.SimpleInstance {

        /**
         * 注意书写顺序：{@link #RAW} 必须**先于** {@link #CODEC} 出现在源码里。
         * Java 禁止静态字段初始化表达式前向引用后声明的静态字段（"非法前向引用"），
         * 而静态字段是按书写顺序初始化的。
         */
        private static final Codec<Instance> RAW = RecordCodecBuilder.create(instance -> instance.group(
                ContextAwarePredicate.CODEC.optionalFieldOf("player").forGetter(Instance::player),
                Source.CODEC.fieldOf("source").forGetter(Instance::source),
                Check.CODEC.fieldOf("check").forGetter(Instance::check),
                Operator.CODEC.fieldOf("operator").forGetter(Instance::operator),
                Codec.INT.fieldOf("threshold").forGetter(Instance::threshold),
                Codec.INT.optionalFieldOf("window").forGetter(Instance::window)
        ).apply(instance, Instance::new));

        /** 对外暴露的编解码器：在 RAW 之上叠加 window/check 一致性校验。 */
        public static final Codec<Instance> CODEC = RAW.flatXmap(Instance::checked, DataResult::success);

        /** window 的 int 视图；{@link Check#VALUE} 时为 0。 */
        public int windowTicks() {
            return window.orElse(0);
        }

        public boolean matches(Series series, long now) {
            if (!series.initialized()) {
                return false;
            }
            return operator.test(check.compute(series, now, windowTicks()), threshold);
        }

        /**
         * window 与 check 的一致性校验。
         *
         * <p>刻意做成**解码期硬失败**：写错只会让这一份进度加载失败并在日志里说明原因，
         * 而不是安静地永不触发。这也正是当初用 Java 写触发器相对手写 JSON 的、
         * 唯一被承认的收益——把它拿回来。
         */
        private static DataResult<Instance> checked(Instance instance) {
            if (instance.check().isWindowed()) {
                if (instance.window().isEmpty()) {
                    return DataResult.error(() -> "check '" + instance.check().jsonName() + "' requires a 'window' (in ticks)");
                }
                int ticks = instance.window().get();
                if (ticks <= 0) {
                    return DataResult.error(() -> "'window' must be positive, got " + ticks);
                }
                if (ticks > MAX_WINDOW) {
                    return DataResult.error(() -> "'window' must be at most " + MAX_WINDOW + " ticks, got " + ticks);
                }
            } else if (instance.window().isPresent()) {
                return DataResult.error(() -> "check 'value' must not declare a 'window' (it reads the value directly)");
            }
            return DataResult.success(instance);
        }
    }
}
