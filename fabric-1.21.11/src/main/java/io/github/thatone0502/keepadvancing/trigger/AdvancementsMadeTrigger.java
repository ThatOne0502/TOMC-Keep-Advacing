package io.github.thatone0502.keepadvancing.trigger;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.WeakHashMap;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.AdvancementNode;
import net.minecraft.advancements.AdvancementTree;
import net.minecraft.advancements.CriterionTrigger;
import net.minecraft.advancements.DisplayInfo;
import net.minecraft.advancements.criterion.ContextAwarePredicate;
import net.minecraft.advancements.criterion.EntityPredicate;
import net.minecraft.advancements.criterion.SimpleCriterionTrigger;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.PlayerAdvancements;
import net.minecraft.server.ServerAdvancementManager;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.loot.LootContext;

import io.github.thatone0502.keepadvancing.KeepAdvancing;
import io.github.thatone0502.keepadvancing.trigger.advancement.Requirement;
import io.github.thatone0502.keepadvancing.trigger.advancement.Selector;
import io.github.thatone0502.keepadvancing.trigger.advancement.SelectorMode;

/**
 * {@code keepadvancing:advancements_made}——把「某组进度的完成情况」封装成一个判据。
 *
 * <p>实例是纯谓词：{@code list}（选哪组进度）+ {@code require}（怎么比）+ 两个过滤器。
 * 真正的状态（解析出来的进度集合）活在本类里，按实例缓存，并在数据包重载（换了一棵
 * {@code AdvancementTree}）时自动失效。
 *
 * <p><b>为什么是轮询而不是事件。</b>Fabric 没有"玩家完成了某进度"的服务端事件，而
 * {@code PlayerAdvancements.award} 是所有进度变动的唯一汇合点、却又要靠 Mixin 才能挂上去
 * （本项目刻意不带 Mixin）。所以走定时评估：每 {@link #EVALUATION_INTERVAL_TICKS} tick
 * 扫一次，且只处理<b>真正注册了本触发器监听者</b>的玩家——没有玩家关心时一次都不算。
 *
 * <p>读取进度完成度用 {@code getOrStartProgress(holder).isDone()}。这不是"额外开销"：
 * 原版在每次刷新标签页可见性时自己就会对整棵树调用 {@code getOrStartProgress}
 * （{@code PlayerAdvancements.updateTreeVisibility} → {@code AdvancementVisibilityEvaluator}），
 * 所以那张进度表在正常游玩中本来就是满的，我们只是白蹭一次查表。
 *
 * <p>为什么直接实现 {@link CriterionTrigger} 而不是继承 {@code SimpleCriterionTrigger}：
 * 后者的 {@code players} 字段是 private，而本触发器必须自己遍历监听者，才能把
 * "同一个玩家的多个监听者共用一次 {@code LootContext}" 做掉。
 */
public class AdvancementsMadeTrigger implements CriterionTrigger<AdvancementsMadeTrigger.Instance> {

    /** 评估间隔（tick）。20 = 1 秒；完成进度到弹出提示延迟最多 1 秒。 */
    public static final int EVALUATION_INTERVAL_TICKS = 20;

    /** 单个判据最终允许包含多少条进度。超过就判定为无法满足并告警一次。 */
    public static final int MAX_RESOLVED_NODES = 4096;

    private final Map<PlayerAdvancements, Set<Listener<Instance>>> listeners = new HashMap<>();

    /**
     * 解析结果缓存。用 {@code WeakHashMap}：实例被进度树强引用着，数据包重载后旧树被换掉、
     * 旧实例随之变成弱可达，条目自己就掉了，不需要显式失效钩子。同一棵树内再用
     * {@link Resolution#tree()} 做一次身份比对，兜住"实例存活但树已换"的那一刻。
     */
    private final Map<Instance, Resolution> resolutions = new WeakHashMap<>();

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
        }
    }

    @Override
    public void removePlayerListeners(PlayerAdvancements advancements) {
        listeners.remove(advancements);
    }

    @Override
    public Codec<Instance> codec() {
        return Instance.CODEC;
    }

    /** 由 {@code ServerTickEvents.END_SERVER_TICK} 每 tick 调用一次（自己按间隔取模）。 */
    public void tick(MinecraftServer server) {
        if (server.getTickCount() % EVALUATION_INTERVAL_TICKS != 0) {
            return;
        }

        List<ServerPlayer> players = server.getPlayerList().getPlayers();
        if (players.isEmpty()) {
            listeners.clear();
            resolutions.clear();
            return;
        }
        if (listeners.isEmpty()) {
            resolutions.clear();
            return;
        }

        ServerAdvancementManager manager = server.getAdvancements();
        List<Award> awards = new ArrayList<>();

        for (ServerPlayer player : players) {
            PlayerAdvancements advancements = player.getAdvancements();
            Set<Listener<Instance>> owned = listeners.get(advancements);
            if (owned == null || owned.isEmpty()) {
                continue;
            }

            // LootContext 只在该玩家至少有一条判据声明了 player 时才构造，且同一玩家共用一份。
            LootContext context = null;

            for (Listener<Instance> listener : owned) {
                Instance instance = listener.trigger();

                if (instance.player().isPresent()) {
                    if (context == null) {
                        context = EntityPredicate.createContext(player, player);
                    }
                    if (!instance.player().get().matches(context)) {
                        continue;
                    }
                }

                List<AdvancementHolder> nodes = resolve(instance, manager);
                if (nodes.isEmpty()) {
                    continue;
                }

                int done = 0;
                for (AdvancementHolder holder : nodes) {
                    if (advancements.getOrStartProgress(holder).isDone()) {
                        done++;
                    }
                }

                if (instance.require().test(done, nodes.size())) {
                    awards.add(new Award(player, listener));
                }
            }
        }

        // 授予必须延迟到遍历结束之后：award() 会反过来移除监听者，边遍历边授予会踩
        // ConcurrentModificationException。
        for (Award award : awards) {
            award.grant();
        }

        // 兜底回收：正常路径下 vanilla 会在玩家退出时调用 removePlayerListener。
        Set<PlayerAdvancements> online = new HashSet<>();
        for (ServerPlayer player : players) {
            online.add(player.getAdvancements());
        }
        listeners.keySet().removeIf(advancements -> !online.contains(advancements));
    }

    /** 解析（并缓存）某条判据选中的进度集合。过滤发生在解析的最后一步。 */
    private List<AdvancementHolder> resolve(Instance instance, ServerAdvancementManager manager) {
        AdvancementTree tree = manager.tree();
        Resolution cached = resolutions.get(instance);
        if (cached != null && cached.tree() == tree) {
            return cached.nodes();
        }

        List<AdvancementHolder> nodes = compute(instance, tree);
        if (nodes.size() > MAX_RESOLVED_NODES) {
            KeepAdvancing.LOGGER.warn("advancements_made: a criterion resolved to {} advancements, "
                    + "above the {} limit; it can never be satisfied. Narrow 'list' or raise the limit.",
                    nodes.size(), MAX_RESOLVED_NODES);
            nodes = List.of();
        } else if (nodes.isEmpty()) {
            KeepAdvancing.LOGGER.warn("advancements_made: a criterion resolved to no advancements "
                    + "(unknown ids, or everything filtered out by include_hidden/require_display); "
                    + "it can never be satisfied.");
        }

        resolutions.put(instance, new Resolution(tree, nodes));
        return nodes;
    }

    private static List<AdvancementHolder> compute(Instance instance, AdvancementTree tree) {        Set<Identifier> seen = new LinkedHashSet<>();
        List<AdvancementHolder> raw = new ArrayList<>();

        for (Selector selector : instance.list()) {
            collect(selector, tree, raw, seen);
        }

        List<AdvancementHolder> out = new ArrayList<>(raw.size());
        for (AdvancementHolder holder : raw) {
            Optional<DisplayInfo> display = holder.value().display();
            if (instance.requireDisplay() && display.isEmpty()) {
                continue;
            }
            if (!instance.includeHidden() && display.map(DisplayInfo::isHidden).orElse(false)) {
                continue;
            }
            out.add(holder);
        }
        return out;
    }

    private static void collect(Selector selector, AdvancementTree tree,
            List<AdvancementHolder> out, Set<Identifier> seen) {
        AdvancementNode anchor = tree.get(selector.first());
        if (anchor == null) {
            KeepAdvancing.LOGGER.warn("advancements_made: selector '{}' names unknown advancement '{}'; "
                    + "it contributes nothing.", selector.mode().jsonName(), selector.first());
            return;
        }

        if (selector.mode().isBetween()) {
            Identifier otherId = selector.second().orElseThrow();
            AdvancementNode other = tree.get(otherId);
            if (other == null) {
                KeepAdvancing.LOGGER.warn("advancements_made: selector 'between' names unknown advancement '{}'; "
                        + "it contributes nothing.", otherId);
                return;
            }
            collectChain(anchor, other, out, seen);
            return;
        }

        switch (selector.mode()) {
            case ONLY -> add(anchor, out, seen);
            case FROM -> {
                add(anchor, out, seen);
                addDescendants(anchor, out, seen);
            }
            case THROUGH -> {
                addAncestors(anchor, out, seen);
                add(anchor, out, seen);
                addDescendants(anchor, out, seen);
            }
            case UNTIL -> {
                addAncestors(anchor, out, seen);
                add(anchor, out, seen);
            }
            case AFTER -> addDescendants(anchor, out, seen);
            case BEFORE -> addAncestors(anchor, out, seen);
            default -> throw new IllegalStateException("unhandled selector mode " + selector.mode());
        }
    }

    /**
     * {@code between}：A 到 B 链路上的全部节点，<b>含两端</b>。由于每个进度只有一个 parent，
     * "之间"只在两者有祖先关系时才有意义；因此两个方向都试一遍，都不成立就告警并且不贡献节点。
     */
    private static void collectChain(AdvancementNode a, AdvancementNode b,
            List<AdvancementHolder> out, Set<Identifier> seen) {
        List<AdvancementNode> up = new ArrayList<>();
        if (walkUpTo(b, a.holder().id(), up) || walkUpTo(a, b.holder().id(), up)) {
            // up 是"由下往上"收集的，倒序添加让结果是根向顺序（只是为了输出稳定）。
            for (int index = up.size() - 1; index >= 0; index--) {
                add(up.get(index), out, seen);
            }
            return;
        }
        KeepAdvancing.LOGGER.warn("advancements_made: selector 'between' got '{}' and '{}', which are not "
                + "on the same parent chain; it contributes nothing.", a.holder().id(), b.holder().id());
    }

    /**
     * 从 {@code from} 沿 parent 向上走，遇到 {@code target} 就停下（含 {@code target}）。
     *
     * @return 是否走到了 {@code target}
     */
    private static boolean walkUpTo(AdvancementNode from, Identifier target, List<AdvancementNode> up) {
        up.clear();
        for (AdvancementNode node = from; node != null; node = node.parent()) {
            up.add(node);
            if (node.holder().id().equals(target)) {
                return true;
            }
        }
        return false;
    }

    private static void add(AdvancementNode node, List<AdvancementHolder> out, Set<Identifier> seen) {
        if (seen.add(node.holder().id())) {
            out.add(node.holder());
        }
    }

    private static void addAncestors(AdvancementNode node, List<AdvancementHolder> out, Set<Identifier> seen) {
        for (AdvancementNode parent = node.parent(); parent != null; parent = parent.parent()) {
            add(parent, out, seen);
        }
    }

    private static void addDescendants(AdvancementNode node, List<AdvancementHolder> out, Set<Identifier> seen) {
        for (AdvancementNode child : node.children()) {
            add(child, out, seen);
            addDescendants(child, out, seen);
        }
    }

    private record Award(ServerPlayer player, Listener<Instance> listener) {
        void grant() {
            player.getAdvancements().award(listener.advancement(), listener.criterion());
        }
    }

    /** 一份解析结果，连同它是在哪棵树上算出来的。 */
    private record Resolution(AdvancementTree tree, List<AdvancementHolder> nodes) {
    }

    /**
     * advancements_made 的判据实例（进度 JSON 的 {@code conditions}）。
     *
     * <p>字段即公开契约：
     * <pre>
     *   player           标准玩家谓词，可选
     *   list             选择器数组，至少一项；各项取并集
     *   require          {type, operator, value}
     *   include_hidden   可选，默认 true。为 false 时剔除 DisplayInfo.isHidden() 为真的进度
     *   require_display  可选，默认 true。为 false 时把"没有 display"的进度也纳入
     *                    （原版 1584 条进度里有 1459 条是这种配方解锁节点，且全部挂在
     *                     minecraft:recipes/root 之下；5 个标签页下面一条都没有）
     * </pre>
     */
    public record Instance(
            Optional<ContextAwarePredicate> player,
            List<Selector> list,
            Requirement require,
            boolean includeHidden,
            boolean requireDisplay) implements SimpleCriterionTrigger.SimpleInstance {

        /**
         * 注意书写顺序：{@link #RAW} 必须<b>先于</b> {@link #CODEC} 出现在源码里。
         * Java 禁止静态字段初始化表达式前向引用后声明的静态字段（"非法前向引用"），
         * 而静态字段是按书写顺序初始化的。
         */
        private static final Codec<Instance> RAW = RecordCodecBuilder.create(instance -> instance.group(
                ContextAwarePredicate.CODEC.optionalFieldOf("player").forGetter(Instance::player),
                Selector.CODEC.listOf().fieldOf("list").forGetter(Instance::list),
                Requirement.CODEC.fieldOf("require").forGetter(Instance::require),
                Codec.BOOL.optionalFieldOf("include_hidden", true).forGetter(Instance::includeHidden),
                Codec.BOOL.optionalFieldOf("require_display", true).forGetter(Instance::requireDisplay)
        ).apply(instance, Instance::new));

        public static final Codec<Instance> CODEC = RAW.flatXmap(Instance::checked, DataResult::success);

        /**
         * 空 {@code list} 做成解码期硬失败：写错只会让这一份进度加载失败并在日志里说明原因，
         * 而不是安静地永不触发。
         */
        private static DataResult<Instance> checked(Instance instance) {
            if (instance.list().isEmpty()) {
                return DataResult.error(() -> "'list' must contain at least one selector");
            }
            return DataResult.success(instance);
        }
    }
}
