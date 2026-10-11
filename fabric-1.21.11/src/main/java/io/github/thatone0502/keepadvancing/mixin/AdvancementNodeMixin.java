package io.github.thatone0502.keepadvancing.mixin;

import io.github.thatone0502.keepadvancing.api.KaAdvancementNode;
import java.util.LinkedHashSet;
import java.util.Set;
import net.minecraft.advancements.AdvancementNode;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/**
 * D 层：给原版 {@code AdvancementNode} 加"附加父 / 附加子"两条边集合（rev14）。
 *
 * <p>原版只有 {@code parent} 单亲 + {@code children} 一棵树；multi_parents 形成的 DAG 边
 * 存在这里，由 {@code KaAdvancementTree} 在 {@code AdvancementTree.addAll} 的 TAIL 统一建。
 * 用 {@code LinkedHashSet} 而不是 {@code ReferenceOpenHashSet}：插入序对渲染连线更友好。
 */
@Mixin(AdvancementNode.class)
public abstract class AdvancementNodeMixin implements KaAdvancementNode {

    @Unique
    private final Set<AdvancementNode> ka$extraParents = new LinkedHashSet<>();

    @Unique
    private final Set<AdvancementNode> ka$extraChildren = new LinkedHashSet<>();

    @Override
    public Set<AdvancementNode> ka$extraParents() {
        return this.ka$extraParents;
    }

    @Override
    public Set<AdvancementNode> ka$extraChildren() {
        return this.ka$extraChildren;
    }

    @Override
    public boolean ka$addExtraParent(AdvancementNode parent) {
        return parent != null && this.ka$extraParents.add(parent);
    }

    @Override
    public boolean ka$addExtraChild(AdvancementNode child) {
        return child != null && this.ka$extraChildren.add(child);
    }

    @Override
    public void ka$clearExtra() {
        this.ka$extraParents.clear();
        this.ka$extraChildren.clear();
    }
}
