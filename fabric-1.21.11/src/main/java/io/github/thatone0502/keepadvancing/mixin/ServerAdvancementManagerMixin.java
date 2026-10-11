package io.github.thatone0502.keepadvancing.mixin;

import io.github.thatone0502.keepadvancing.layout.DagLayout;
import java.util.Map;
import net.minecraft.advancements.Advancement;
import net.minecraft.resources.Identifier;
import net.minecraft.server.ServerAdvancementManager;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.profiling.ProfilerFiller;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * D 层：DAG 布局的入口（rev14）。
 *
 * <p>布局在**服务端**算：原版在 {@code apply} 里对每个 root 跑 {@code TreeNodePosition.run}
 * 得到基线坐标，客户端只用网络传来的 x/y。所以我们在 TAIL 处只对"含 multi_parents 的
 * 连通分量"做 DAG 布局覆盖，其余分量保留原版位置（观感不变）。
 */
@Mixin(ServerAdvancementManager.class)
public abstract class ServerAdvancementManagerMixin {

    @Inject(method = "apply", at = @At("TAIL"))
    private void ka$applyDagLayout(Map<Identifier, Advancement> advancements, ResourceManager resourceManager,
                                   ProfilerFiller profiler, CallbackInfo ci) {
        DagLayout.apply(((ServerAdvancementManager) (Object) this).tree());
    }
}
