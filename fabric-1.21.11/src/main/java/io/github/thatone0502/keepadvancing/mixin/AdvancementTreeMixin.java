package io.github.thatone0502.keepadvancing.mixin;

import io.github.thatone0502.keepadvancing.layout.KaAdvancementTree;
import java.util.Collection;
import java.util.Set;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.AdvancementTree;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * D 层：在 {@code AdvancementTree} 建图完成后补上 multi_parents 的附加边（rev14）。
 *
 * <p>必须挂在 {@code addAll} 的 TAIL：原版 {@code tryInsert} 有"父没就绪就 return false 重试"的
 * 逻辑，只有 TAIL 时全部节点才都就位。也**绝不能**让 multi_parents 参与 tryInsert 的成败判定。
 *
 * <p>{@code remove} 之后同样重算一遍，避免留下指向已删除节点的悬挂边。
 */
@Mixin(AdvancementTree.class)
public abstract class AdvancementTreeMixin {

    // ⚠ 必须写全描述符：AdvancementTree 里有两个同名方法
    //   :23 private void remove(AdvancementNode)
    //   :43 public  void remove(Set<Identifier>)
    // 只写 "remove" 时 Mixin 会解析到前者，然后在 APPLY 阶段抛
    //   InvalidInjectionException: Invalid descriptor ... Expected (Lnet/minecraft/advancements/AdvancementNode;...)
    // 因为本 mixin 配置是 required:true，这一条会**直接把整局游戏打崩**（实测：卡在"准备生成世界…"）。
    // 教训：凡是目标类里存在同名重载，method 一律写参数描述符。
    @Inject(method = "addAll(Ljava/util/Collection;)V", at = @At("TAIL"))
    private void ka$linkExtraParents(Collection<AdvancementHolder> advancements, CallbackInfo ci) {
        KaAdvancementTree.linkExtraParents((AdvancementTree) (Object) this);
    }

    @Inject(method = "remove(Ljava/util/Set;)V", at = @At("TAIL"))
    private void ka$relinkAfterRemove(Set<Identifier> removed, CallbackInfo ci) {
        KaAdvancementTree.linkExtraParents((AdvancementTree) (Object) this);
    }
}
