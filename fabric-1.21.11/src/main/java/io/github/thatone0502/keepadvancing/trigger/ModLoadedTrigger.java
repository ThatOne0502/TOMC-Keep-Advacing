package io.github.thatone0502.keepadvancing.trigger;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.advancements.CriterionTrigger;
import net.minecraft.advancements.criterion.ContextAwarePredicate;
import net.minecraft.advancements.criterion.SimpleCriterionTrigger;
import net.minecraft.server.PlayerAdvancements;
import net.minecraft.server.level.ServerPlayer;

import io.github.thatone0502.keepadvancing.KeepAdvancing;
import io.github.thatone0502.keepadvancing.logic.ModScan;
import io.github.thatone0502.keepadvancing.trigger.matcher.Matcher;

/**
 * {@code keepadvancing:mod_loaded}——「环境里加载了某个模组」的通用判据。
 *
 * <p>Fabric 的模组元数据里<b>没有 Java 包名</b>，而模组 id / 显示名又未必能反映包名
 * （例如 TOMC 生态里 {@code creefern}、{@code meaningfulglyphs} 的 id 和名字都不含 TOMC）。
 * 因此这里照字面意思做：打开每个模组的根路径（jar 或开发目录），枚举其中的类，还原出
 * Java 包名，再用正则去匹配。这是唯一真正贴合"包名"原义的判据。
 *
 * <p>判定时机是玩家加入：模组集合在服务器启动后就固定了，无需每 tick 轮询。
 */
public class ModLoadedTrigger implements CriterionTrigger<ModLoadedTrigger.Instance> {

    private final Map<PlayerAdvancements, Set<Listener<Instance>>> listeners = new HashMap<>();

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

    /** 由 {@code ServerPlayConnectionEvents.JOIN} 调用。 */
    public void onPlayerJoin(ServerPlayer player) {
        Set<Listener<Instance>> owned = listeners.get(player.getAdvancements());
        if (owned == null || owned.isEmpty()) {
            return;
        }
        ModScan.Snapshot snapshot = ModScan.snapshot();

        List<Listener<Instance>> hits = new ArrayList<>();
        for (Listener<Instance> listener : new ArrayList<>(owned)) {
            String matched = listener.trigger().match(snapshot);
            if (matched != null) {
                KeepAdvancing.LOGGER.info(
                        "[keepadvancing] {} matched mod '{}' for {}",
                        listener.trigger().packageMatcher().describe(), matched, listener.advancement().id());
                hits.add(listener);
            }
        }
        for (Listener<Instance> listener : hits) {
            player.getAdvancements().award(listener.advancement(), listener.criterion());
        }
    }

    /**
     * mod_loaded 的判据实例。
     *
     * <pre>
     *   package       "io.github.thatone0502"                     包名精确相等
     *                 {"regex": "io\\.github\\.thatone0502"}        正则（find() 语义）
     *   exclude_self  是否把本模组自己排除在外，默认 true
     * </pre>
     *
     * <p><b>精确相等意味着什么。</b>包名是从每个模组的 {@code .class} 路径反推出来的，
     * 所以 {@code "package": "io.github.thatone0502"} 要求真的存在那一层包。类全在
     * {@code io.github.thatone0502.keepadvancing.*} 下的模组推不出 {@code io.github.thatone0502}
     * 这一层，匹配不到 —— 想要"包树前缀"就用正则。
     *
     * <p>{@code find()} 而不是 {@code matches()}：{@code "io\.github\.thatone0502"} 这种
     * "只想匹配前缀"的写法可以直接用，不必写成 {@code ".*io\.github\.thatone0502.*"}。
     */
    public record Instance(
            Optional<ContextAwarePredicate> player,
            Matcher packageMatcher,
            boolean excludeSelf) implements SimpleCriterionTrigger.SimpleInstance {

        public static final Codec<Instance> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                ContextAwarePredicate.CODEC.optionalFieldOf("player").forGetter(Instance::player),
                Matcher.CODEC.fieldOf("package").forGetter(Instance::packageMatcher),
                Codec.BOOL.optionalFieldOf("exclude_self", true).forGetter(Instance::excludeSelf)
        ).apply(instance, Instance::new));

        /** @return 命中的模组 id，未命中返回 null。 */
        public String match(ModScan.Snapshot snapshot) {
            return snapshot.match(packageMatcher, excludeSelf);
        }
    }
}
