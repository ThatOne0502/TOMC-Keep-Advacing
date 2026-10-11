package io.github.thatone0502.keepadvancing.api;

import java.util.Set;
import net.minecraft.advancements.AdvancementNode;

/**
 * {@code net.minecraft.advancements.AdvancementNode} 的 Duck 接口（rev14）。
 *
 * <p>由 {@code AdvancementNodeMixin} 实现，承载 {@code multi_parents} 在内存图中的
 * 附加边（原版 {@code AdvancementNode} 只有一棵"父亲 + children 集合"的树）。
 */
public interface KaAdvancementNode {

    /** 额外父节点（可迭代，插入序）。 */
    Set<AdvancementNode> ka$extraParents();

    /** 额外子节点（可迭代，插入序）。 */
    Set<AdvancementNode> ka$extraChildren();

    boolean ka$addExtraParent(AdvancementNode parent);

    boolean ka$addExtraChild(AdvancementNode child);

    /** 清空两条附加边集合（用于 {@code AdvancementTree.remove} 之后的重新连边）。 */
    void ka$clearExtra();
}
