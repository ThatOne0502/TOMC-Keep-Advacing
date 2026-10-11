package io.github.thatone0502.keepadvancing.mixin;

import io.github.thatone0502.keepadvancing.api.KaAdvancementNode;
import java.util.Optional;
import java.util.function.Predicate;
import net.minecraft.advancements.Advancement;
import net.minecraft.advancements.AdvancementNode;
import net.minecraft.advancements.DisplayInfo;
import net.minecraft.server.advancements.AdvancementVisibilityEvaluator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * D 层：把可见性传播从"父亲树"扩成 DAG（rev14 §5.7）。
 *
 * <p>语义要求：节点可见当且仅当「主父可见」或「任一额外父可见」，深度衰减仍用"到最近可见祖先的距离"。
 *
 * <p>做法：整体接管 {@code AdvancementVisibilityEvaluator.evaluateVisibility(节点, 谓词, 输出)}
 * 这个 public 入口，用与原版递归完全同构的写法（同一套后序 {@code output.accept}、同一套
 * "栈顶三级取最近可见祖先"的规则），只把 {@code children()} 的遍历扩成
 * {@code children() ∪ extraChildren()}。
 *
 * <p>为什么不用"只往 children 循环里塞额外子节点"的小注入：一个节点可能同时是某人的主子和
 * 另一个人的额外子，小注入会让它被处理两次，把栈序打乱、并重复触发 {@code output.accept}。
 * 这里改成"主子先走完、再走额外子"的显式顺序：没有额外边的纯树与逐像素级原版行为一致
 * （multi_parents 不存在时 {@code extraChildren()} 恒为空），所以现有数据包观感不受影响。
 *
 * <p>只处理有 display 的节点、只往"额外子"方向扩散，天然不会因为额外边成环而无限递归。
 */
@Mixin(AdvancementVisibilityEvaluator.class)
public abstract class AdvancementVisibilityEvaluatorMixin {

    /** 与原版 {@code VisibilityRule} 同值的影子栈（枚举本身是包私有，取不到）。 */
    private static final int KA$NO_CHANGE = 0;
    private static final int KA$SHOW = 1;
    private static final int KA$HIDE = 2;
    private static final int KA$DEPTH = 2;

    @Inject(
            method = "evaluateVisibility(Lnet/minecraft/advancements/AdvancementNode;Ljava/util/function/Predicate;Lnet/minecraft/server/advancements/AdvancementVisibilityEvaluator$Output;)V",
            at = @At("HEAD"),
            cancellable = true)
    private static void ka$evaluateVisibilityWithExtraParents(AdvancementNode start, Predicate<AdvancementNode> predicate,
                                                             AdvancementVisibilityEvaluator.Output output, CallbackInfo ci) {
        int[] shadowStack = new int[KA$DEPTH + 1];
        for (int i = 0; i <= KA$DEPTH; i++) {
            shadowStack[i] = KA$NO_CHANGE;
        }
        ka$walk(start.root(), predicate, output, shadowStack, 0);
        ci.cancel();
    }

    /** 返回 true 表示以本节点为根的子树里至少有一个"已展示"节点（原版的 bl2）。 */
    private static boolean ka$walk(AdvancementNode node, Predicate<AdvancementNode> predicate,
                                   AdvancementVisibilityEvaluator.Output output, int[] shadowStack, int depth) {
        boolean shown = predicate.test(node);
        Optional<DisplayInfo> display = node.advancement().display();
        int rule;
        if (display.isEmpty()) {
            rule = KA$HIDE;
        } else if (shown) {
            rule = KA$SHOW;
        } else {
            rule = display.get().isHidden() ? KA$HIDE : KA$NO_CHANGE;
        }

        ka$push(shadowStack, depth, rule);
        boolean anyVisibleBelow = shown;

        for (AdvancementNode child : node.children()) {
            anyVisibleBelow |= ka$walk(child, predicate, output, shadowStack, depth + 1);
        }
        for (AdvancementNode extraChild : ((KaAdvancementNode) node).ka$extraChildren()) {
            anyVisibleBelow |= ka$walk(extraChild, predicate, output, shadowStack, depth + 1);
        }

        boolean visible = anyVisibleBelow || ka$unfinishedVisible(shadowStack, depth);
        output.accept(node, visible);
        return anyVisibleBelow;
    }

    private static void ka$push(int[] shadowStack, int depth, int rule) {
        if (depth < shadowStack.length) {
            shadowStack[depth] = rule;
        }
    }

    /** 原版 {@code evaluateVisiblityForUnfinishedNode}：从栈顶往下查 3 层。 */
    private static boolean ka$unfinishedVisible(int[] shadowStack, int depth) {
        for (int i = 0; i <= KA$DEPTH; i++) {
            int index = depth - i;
            int rule = index >= 0 && index < shadowStack.length ? shadowStack[index] : KA$NO_CHANGE;
            if (rule == KA$SHOW) {
                return true;
            }
            if (rule == KA$HIDE) {
                return false;
            }
        }
        return false;
    }
}
