package io.github.thatone0502.keepadvancing.trigger.gui;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
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
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.PlayerAdvancements;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.level.storage.loot.LootContext;

import io.github.thatone0502.keepadvancing.logic.ClientGuiReports;
import io.github.thatone0502.keepadvancing.trigger.matcher.Matcher;

/**
 * {@code keepadvancing:gui_open}——「玩家此刻正开着指定的界面」。
 *
 * <p><b>三条互补的识别途径，任意一条命中即成立（或的关系）：</b>
 * <ul>
 *   <li>{@code menu}：服务端自己的 {@code Player.containerMenu}，比的是
 *       {@code MenuType} 的注册 id，例如 {@code minecraft:crafting}。这一侧完全不需要客户端配合，
 *       覆盖所有真正的容器界面（原版 + 大多数模组界面）。</li>
 *   <li>{@code screen}：客户端上报的 {@code Screen} 全限定类名，例如
 *       {@code net.minecraft.client.gui.screens.inventory.InventoryScreen}。这一侧能覆盖
 *       <b>物品栏</b>、告示牌编辑页、书与笔、以及用 GUI 系统画的模组界面。</li>
 *   <li>{@code custom}：别的模组通过 {@code KeepAdvancingGuiApi.reportCustomGui(Identifier)}
 *       主动上报的 id，给那些<b>根本不走 Screen</b> 的 HUD 覆盖层用。</li>
 * </ul>
 *
 * <p><b>为什么 {@code menu} 检测不到物品栏。</b>{@code InventoryMenu} 的构造是
 * {@code AbstractCraftingMenu.<init>(null, 0, 2, 2)} —— 它的 {@code MenuType} 就是 {@code null}，
 * 而玩家什么都没开的时候 {@code containerMenu} 也正好是 {@code inventoryMenu}。所以服务端
 * 无法区分"空手"和"开着物品栏"，物品栏只能靠 {@code screen}。
 *
 * <p><b>语义是"此刻正开着"</b>，每 tick 判一次；界面对着的那一 tick 就授予，关掉即不再成立。
 * 触发器自己注册监听者，没人关心时一次都不判。
 */
public class GuiOpenTrigger implements CriterionTrigger<GuiOpenTrigger.Instance> {

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

    /** 由 {@code ServerTickEvents.END_SERVER_TICK} 每 tick 调用。 */
    public void tick(MinecraftServer server) {
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
            if (owned == null || owned.isEmpty()) {
                continue;
            }
            String menuId = currentMenuId(player);
            String screenClass = ClientGuiReports.screen(player);
            String customId = ClientGuiReports.custom(player);

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
                if (instance.matches(menuId, screenClass, customId)) {
                    awards.add(new Award(player, listener));
                }
            }
        }
        // 授予会摘掉监听者，边遍历边授予会 ConcurrentModificationException。
        for (Award award : awards) {
            award.player().getAdvancements().award(award.listener().advancement(), award.listener().criterion());
        }
    }

    /** 当前容器界面的注册 id；玩家背包（{@code getType() == null}）返回空串。 */
    private static String currentMenuId(ServerPlayer player) {
        AbstractContainerMenu menu = player.containerMenu;
        if (menu == null) {
            return "";
        }
        MenuType<?> type = menu.getType();
        if (type == null) {
            // 玩家背包：MenuType 为 null，服务端无从区分它与"什么都没开"。
            return "";
        }
        Identifier id = BuiltInRegistries.MENU.getKey(type);
        return id == null ? "" : id.toString();
    }

    private record Award(ServerPlayer player, Listener<Instance> listener) {
    }

    /**
     * {@code gui_open} 的判据实例。
     *
     * <pre>
     *   player  可省，原版玩家谓词
     *   menu    可省，列表；比 MenuType 注册 id
     *   screen  可省，列表；比客户端上报的 Screen 全限定类名
     *   custom  可省，列表；比其它模组上报的自定义界面 id
     * </pre>
     *
     * <p>每个列表元素都可以是字符串（字面量，精确相等）或 {@code {"regex": "..."}}
     * （正则，{@code find()} 语义）。四个字段不能全空 —— 全空的判据永远不成立，那一定是写错了，
     * 所以在解码期直接报错。
     */
    public record Instance(
            Optional<ContextAwarePredicate> player,
            List<Matcher> menu,
            List<Matcher> screen,
            List<Matcher> custom) implements SimpleCriterionTrigger.SimpleInstance {

        private static final Codec<Instance> RAW = RecordCodecBuilder.create(instance -> instance.group(
                ContextAwarePredicate.CODEC.optionalFieldOf("player").forGetter(Instance::player),
                Matcher.CODEC.listOf().optionalFieldOf("menu", List.of()).forGetter(Instance::menu),
                Matcher.CODEC.listOf().optionalFieldOf("screen", List.of()).forGetter(Instance::screen),
                Matcher.CODEC.listOf().optionalFieldOf("custom", List.of()).forGetter(Instance::custom)
        ).apply(instance, Instance::new));

        public static final Codec<Instance> CODEC = RAW.flatXmap(Instance::checked, DataResult::success);

        private static DataResult<Instance> checked(Instance instance) {
            if (instance.menu().isEmpty() && instance.screen().isEmpty() && instance.custom().isEmpty()) {
                return DataResult.error(() ->
                        "'menu', 'screen' and 'custom' are all empty, which can never be satisfied");
            }
            return DataResult.success(instance);
        }

        public boolean matches(String menuId, String screenClass, String customId) {
            return anyMatch(this.menu, menuId)
                    || anyMatch(this.screen, screenClass)
                    || anyMatch(this.custom, customId);
        }

        private static boolean anyMatch(List<Matcher> matchers, String candidate) {
            if (candidate == null || candidate.isEmpty()) {
                return false;
            }
            for (Matcher matcher : matchers) {
                if (matcher.matches(candidate)) {
                    return true;
                }
            }
            return false;
        }
    }
}
