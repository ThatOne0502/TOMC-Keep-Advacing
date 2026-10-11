package io.github.thatone0502.keepadvancing.layout;

import io.github.thatone0502.keepadvancing.KeepAdvancing;
import io.github.thatone0502.keepadvancing.api.KaAdvancement;
import io.github.thatone0502.keepadvancing.api.KaAdvancementNode;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.advancements.AdvancementNode;
import net.minecraft.advancements.AdvancementTree;
import net.minecraft.resources.Identifier;

/**
 * multi_parents 的建边器（rev14 D 层）。
 *
 * <p>由 {@code AdvancementTreeMixin} 在 {@code AdvancementTree.addAll} / {@code remove} 的 TAIL 调用。
 * 客户端也走这条路（{@code ClientAdvancements} 同样调 {@code tree.addAll}），所以同一份逻辑
 * 自动覆盖两端。
 *
 * <p>几条硬规矩：
 * <ul>
 *   <li>目标是"额外上游"，**不参与原版 {@code tryInsert} 的成败判定** —— 一个手滑的 id
 *       会让 {@code addAll} 的 removeIf 死循环并 break，丢掉整包进度。所以只在这里 warn + 跳过。</li>
 *   <li>加边前做一次可达性检查：<b>从本节点沿正向边走，能不能走到候选父</b>；能走到就说明加这条边
 *       会闭环（本节点 ⟶ … ⟶ 候选父，再加候选父 → 本节点），拒绝这条边。</li>
 *   <li>反向的"候选父能走到本节点"**不是环，是冗余边，必须放行** —— 那正是
 *       {@code multi_parents} 要画的那条跨越若干层的上游连线（例：{@code w←z←y←x} 上
 *       {@code w} 声明 {@code multi_parents:[x]}）。写成反向会在实机上把三父汇合整条吞掉。</li>
 *   <li>每次调用都是"全量重算"：先清空所有附加边再重建，保证幂等（{@code addAll} 可以多次调用）。</li>
 * </ul>
 */
public final class KaAdvancementTree {

    private KaAdvancementTree() {
    }

    /** 全量重算整个树的附加边。 */
    public static void linkExtraParents(AdvancementTree tree) {
        List<AdvancementNode> nodes = new ArrayList<>(tree.nodes());

        for (AdvancementNode node : nodes) {
            ((KaAdvancementNode) node).ka$clearExtra();
        }

        for (AdvancementNode node : nodes) {
            // 注意：Advancement 是 record（final），javac 不允许把 final 类直接 cast 到它没实现的接口，
            // 必须先经过 Object —— 这是 Mixin duck interface 的标准写法，别去掉 (Object)。
            List<Identifier> multiParents = ((KaAdvancement) (Object) node.advancement()).ka$multiParents();
            if (multiParents.isEmpty()) {
                continue;
            }
            for (Identifier id : multiParents) {
                AdvancementNode candidate = tree.get(id);
                if (candidate == null) {
                    KeepAdvancing.LOGGER.warn("keepadvancing: multi_parents target {} not found for {} (edge skipped)",
                            id, node.holder().id());
                    continue;
                }
                if (candidate == node) {
                    KeepAdvancing.LOGGER.warn("keepadvancing: multi_parents of {} points at itself (edge skipped)", node.holder().id());
                    continue;
                }
                if (candidate == node.parent() || ((KaAdvancementNode) node).ka$extraParents().contains(candidate)) {
                    // 已经是主父或已经连过这条附加边，跳过（避免重复 warn）。
                    continue;
                }
                if (reaches(node, candidate)) {
                    KeepAdvancing.LOGGER.warn("keepadvancing: multi_parents edge {} <- {} would create a cycle (edge skipped)",
                            node.holder().id(), id);
                    continue;
                }
                ((KaAdvancementNode) node).ka$addExtraParent(candidate);
                ((KaAdvancementNode) candidate).ka$addExtraChild(node);
            }
        }
    }

    /** {@code from} 沿正向边（children + extraChildren）能不能走到 {@code to}。 */
    private static boolean reaches(AdvancementNode from, AdvancementNode to) {
        Set<AdvancementNode> visited = new HashSet<>();
        Deque<AdvancementNode> queue = new ArrayDeque<>();
        queue.add(from);
        visited.add(from);
        while (!queue.isEmpty()) {
            AdvancementNode current = queue.poll();
            if (current == to) {
                return true;
            }
            for (AdvancementNode child : current.children()) {
                if (visited.add(child)) {
                    queue.add(child);
                }
            }
            for (AdvancementNode child : ((KaAdvancementNode) current).ka$extraChildren()) {
                if (visited.add(child)) {
                    queue.add(child);
                }
            }
        }
        return false;
    }
}
