package io.github.thatone0502.keepadvancing.trigger.structure;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.WeakHashMap;

import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.advancements.CriterionTrigger;
import net.minecraft.advancements.criterion.ContextAwarePredicate;
import net.minecraft.advancements.criterion.EntityPredicate;
import net.minecraft.advancements.criterion.SimpleCriterionTrigger;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.PlayerAdvancements;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import net.minecraft.world.level.storage.loot.LootContext;

import io.github.thatone0502.keepadvancing.KeepAdvancing;

/**
 * {@code keepadvancing:structure_present}——「玩家附近存在指定的结构」。
 *
 * <p>结构来自数据包里的标准结构文件：{@code data/<namespace>/structure/<name>.nbt}
 * （也可以放人读的 {@code .snbt}，由 {@code StructureTemplateManager} 统一处理）。也就是用
 * 结构方块存下来的那种文件，判定用的是<b>原样精确比对</b>。
 *
 * <p><b>为什么要自己写匹配器。</b>原版的"检测"模式（结构方块里的 DETECT）在 1.21.11 已经被删掉了
 * ——{@code StructureMode} 只剩 {@code SAVE / LOAD / CORNER / DATA} 四个值。所以没有任何原版
 * 逻辑可以复用，这里按结构文件里的原始 NBT 自己比。
 *
 * <p><b>代价控制在哪。</b>暴力做法（区域里每个位置都当原点试一遍整份模板）在 32×16 半径下是
 * 十几万个原点 × 十二种朝向，一秒一次也扛不住。实际做法见
 * {@link StructureMatcher}：先在区域里找"锚点方块"，命中处才去对整份模板。再叠三层上限：
 * 区域体积上限 {@link #MAX_SCAN_VOLUME}、单次评估的锚点命中上限 {@link #MAX_ANCHOR_HITS}、
 * 以及按模板格数折算的总检查预算 {@link #ANCHOR_CHECK_BUDGET}；任一超限就告警一次并跳过本次，
 * 绝不拖住服务器。
 *
 * <p><b>语义是"附近有"</b>，不是"玩家站在里面"：只要以玩家为中心的盒子里存在这样一份结构就成立。
 * 判定的位置取玩家的脚下方块坐标。
 */
public class StructurePresentTrigger implements CriterionTrigger<StructurePresentTrigger.Instance> {

    /** 评估间隔（tick）。20 = 1 秒。 */
    public static final int EVALUATION_INTERVAL_TICKS = 20;

    /** 一次评估允许扫过的位置数上限。默认半径 32×16 是 139425，留了足够余量。 */
    public static final int MAX_SCAN_VOLUME = 500_000;

    /** 单次评估允许的锚点命中数上限（模板很小时才用得上）。 */
    public static final int MAX_ANCHOR_HITS = 4096;

    /** 单次评估允许的方块检查总数预算；锚点命中上限由它除以模板格数得出。 */
    public static final int ANCHOR_CHECK_BUDGET = 100_000;

    private final Map<PlayerAdvancements, Set<Listener<Instance>>> listeners = new HashMap<>();

    /**
     * 编译结果缓存。用 {@code WeakHashMap} 的理由与 {@code advancements_made} 相同：实例被进度树
     * 强引用着，数据包换掉之后旧实例变成弱可达，条目自己就掉了。
     *
     * <p>另外用一个身份比对兜住"模板文件被数据包重载替换成新对象"：缓存里存着上次编译用的那个
     * {@code StructureTemplate}，每次评估先 {@code manager.get(id)} 拿当前对象，不是同一个就重编。
     */
    private final Map<Instance, Cache> cache = new WeakHashMap<>();

    /** 每个判据只告警一次，避免每 20 tick 刷屏。 */
    private final Map<Instance, Boolean> warned = new WeakHashMap<>();

    private record Cache(StructureTemplate source, Optional<StructureMatcher> matcher) {
    }

    private record Award(ServerPlayer player, Listener<Instance> listener) {
    }

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

    /** 由 {@code ServerTickEvents.END_SERVER_TICK} 调用；自己按间隔取模。 */
    public void tick(MinecraftServer server) {
        if (server.getTickCount() % EVALUATION_INTERVAL_TICKS != 0) {
            return;
        }
        if (listeners.isEmpty()) {
            return;
        }
        List<ServerPlayer> players = server.getPlayerList().getPlayers();
        if (players.isEmpty()) {
            return;
        }

        List<Award> awards = new ArrayList<>();
        for (ServerPlayer player : players) {
            Set<Listener<Instance>> owned = listeners.get(player.getAdvancements());
            if (owned == null || owned.isEmpty() || !(player.level() instanceof ServerLevel level)) {
                continue;
            }

            LootContext context = null;
            for (Listener<Instance> listener : new ArrayList<>(owned)) {
                Instance instance = listener.trigger();
                Optional<ContextAwarePredicate> predicate = instance.player();
                if (predicate.isPresent()) {
                    if (context == null) {
                        context = EntityPredicate.createContext(player, player);
                    }
                    if (!predicate.get().matches(context)) {
                        continue;
                    }
                }
                Optional<StructureMatcher> matcher = matcher(instance, level);
                if (matcher.isPresent() && foundNear(level, player, matcher.get(), instance)) {
                    awards.add(new Award(player, listener));
                }
            }
        }
        // 授予会摘掉监听者，边遍历边授予会 ConcurrentModificationException。
        for (Award award : awards) {
            award.player().getAdvancements().award(award.listener().advancement(), award.listener().criterion());
        }
    }

    /** 拿到（必要时编译）判据对应的匹配器；模板不存在或无法匹配时返回 empty 并告警一次。 */
    private Optional<StructureMatcher> matcher(Instance instance, ServerLevel level) {
        StructureTemplateManager manager = level.getStructureManager();
        Optional<StructureTemplate> found = manager.get(instance.structure());
        if (found.isEmpty()) {
            warnOnce(instance, "no structure template '" + instance.structure()
                    + "' is loaded; this criterion can never be satisfied");
            return Optional.empty();
        }
        StructureTemplate template = found.get();
        Cache cached = cache.get(instance);
        if (cached != null && cached.source() == template) {
            return cached.matcher();
        }
        Optional<StructureMatcher> matcher = StructureMatcher.build(
                template,
                level.registryAccess().lookupOrThrow(Registries.BLOCK),
                instance.allowRotation(),
                instance.allowMirror(),
                instance.matchNbt(),
                instance.includeAir());
        if (matcher.isEmpty()) {
            warnOnce(instance, "structure '" + instance.structure()
                    + "' has nothing to anchor on (empty, all air, or all structure void); skipping");
        }
        cache.put(instance, new Cache(template, matcher));
        return matcher;
    }

    /** 以玩家为中心扫一遍，看有没有任意一份朝向完全一致的结构。 */
    private boolean foundNear(ServerLevel level, ServerPlayer player, StructureMatcher matcher, Instance instance) {
        int horizontal = instance.radius().horizontal();
        int vertical = instance.radius().vertical();
        long volume = (long) (2 * horizontal + 1) * (2 * horizontal + 1) * (2 * vertical + 1);
        if (volume > MAX_SCAN_VOLUME) {
            warnOnce(instance, "radius " + horizontal + "x" + vertical + " covers " + volume
                    + " positions, over the cap of " + MAX_SCAN_VOLUME + "; skipping");
            return false;
        }

        BlockPos center = player.blockPosition();
        int minX = center.getX() - horizontal;
        int maxX = center.getX() + horizontal;
        int minZ = center.getZ() - horizontal;
        int maxZ = center.getZ() + horizontal;
        int minCX = minX >> 4;
        int maxCX = maxX >> 4;
        int minCZ = minZ >> 4;
        int maxCZ = maxZ >> 4;
        int czSpan = maxCZ - minCZ + 1;
        boolean[] loaded = new boolean[(maxCX - minCX + 1) * czSpan];
        for (int cx = minCX; cx <= maxCX; cx++) {
            for (int cz = minCZ; cz <= maxCZ; cz++) {
                // 只扫已加载的区块：getBlockState 对未加载的区块会同步加载，几百次就能卡住服务器。
                loaded[(cx - minCX) * czSpan + (cz - minCZ)] = level.hasChunk(cx, cz);
            }
        }

        int maxHits = Math.max(1, Math.min(MAX_ANCHOR_HITS, ANCHOR_CHECK_BUDGET / Math.max(1, matcher.entryCount())));
        int hits = 0;
        for (int dy = -vertical; dy <= vertical; dy++) {
            for (int dx = -horizontal; dx <= horizontal; dx++) {
                for (int dz = -horizontal; dz <= horizontal; dz++) {
                    BlockPos pos = new BlockPos(center.getX() + dx, center.getY() + dy, center.getZ() + dz);
                    if (!loaded[((pos.getX() >> 4) - minCX) * czSpan + ((pos.getZ() >> 4) - minCZ)]) {
                        continue;
                    }
                    if (!level.getBlockState(pos).is(matcher.anchor())) {
                        continue;
                    }
                    if (++hits > maxHits) {
                        warnOnce(instance, "more than " + maxHits + " anchor candidates near "
                                + player.getName().getString() + "; skipping this evaluation");
                        return false;
                    }
                    if (matcher.matchesAt(level, pos)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private void warnOnce(Instance instance, String message) {
        if (warned.putIfAbsent(instance, Boolean.TRUE) == null) {
            KeepAdvancing.LOGGER.warn("structure_present: {}", message);
        }
    }

    /**
     * {@code structure_present} 的判据实例。
     *
     * <pre>
     *   player          可省，原版玩家谓词
     *   structure       必填，结构文件 id，例如 keepadvancing:hut
     *   radius          可省，默认 {"horizontal": 16, "vertical": 16}；也可以直接写一个整数（水平垂直同值）
     *   allow_rotation  可省，默认 false；true 时额外认 3 种旋转
     *   allow_mirror    可省，默认 false；true 时额外认 2 种镜像
     *   match_nbt       可省，默认 false；true 时连方块实体 NBT 一起比（子集语义）
     *   include_air     可省，默认 true；true＝连空气格一起精确比对（原版语义），false＝忽略空气格
     * </pre>
     */
    public record Instance(
            Optional<ContextAwarePredicate> player,
            Identifier structure,
            Radius radius,
            boolean allowRotation,
            boolean allowMirror,
            boolean matchNbt,
            boolean includeAir) implements SimpleCriterionTrigger.SimpleInstance {

        /** 以玩家为中心的长方体半径：水平是 X/Z 两个方向，垂直是 Y 的上下。 */
        public record Radius(int horizontal, int vertical) {

            private static final Codec<Radius> OBJECT = RecordCodecBuilder.create(instance -> instance.group(
                    Codec.intRange(0, 256).optionalFieldOf("horizontal", 16).forGetter(Radius::horizontal),
                    Codec.intRange(0, 256).optionalFieldOf("vertical", 16).forGetter(Radius::vertical)
            ).apply(instance, Radius::new));

            /** 要么一个整数（水平垂直同值），要么 {@code {"horizontal": .., "vertical": ..}}。 */
            public static final Codec<Radius> CODEC = Codec.either(Codec.intRange(0, 256), OBJECT).xmap(
                    either -> either.map(single -> new Radius(single, single), radius -> radius),
                    Either::right);
        }

        private static final Codec<Instance> RAW = RecordCodecBuilder.create(instance -> instance.group(
                ContextAwarePredicate.CODEC.optionalFieldOf("player").forGetter(Instance::player),
                Identifier.CODEC.fieldOf("structure").forGetter(Instance::structure),
                Radius.CODEC.optionalFieldOf("radius", new Radius(16, 16)).forGetter(Instance::radius),
                Codec.BOOL.optionalFieldOf("allow_rotation", false).forGetter(Instance::allowRotation),
                Codec.BOOL.optionalFieldOf("allow_mirror", false).forGetter(Instance::allowMirror),
                Codec.BOOL.optionalFieldOf("match_nbt", false).forGetter(Instance::matchNbt),
                Codec.BOOL.optionalFieldOf("include_air", true).forGetter(Instance::includeAir)
        ).apply(instance, Instance::new));

        public static final Codec<Instance> CODEC = RAW;
    }
}
