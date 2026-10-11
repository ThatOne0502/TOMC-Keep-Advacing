package io.github.thatone0502.keepadvancing.mixin;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.thatone0502.keepadvancing.api.KaAdvancement;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.advancements.Advancement;
import net.minecraft.advancements.AdvancementRequirements;
import net.minecraft.advancements.AdvancementRewards;
import net.minecraft.advancements.Criterion;
import net.minecraft.advancements.DisplayInfo;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * A 层（数据）+ B 层（协议）对 {@code Advancement} 的扩展（rev14）：{@code multi_parents}。
 *
 * <p>{@code Advancement} 是 **record**，构造完就不能 setter 了，所以扩展字段必须在
 * 重建后的 CODEC 的 apply lambda 内部写入（{@code RecordCodecBuilder.apply} 是直接 new）。
 *
 * <p>CODEC 的 6 个原字段逐字照抄 {@code Advancement.java} 第 39-53 行（含末尾的
 * {@code .validate(Advancement::validate)}），只在末尾追加 multi_parents。
 */
@Mixin(Advancement.class)
public abstract class AdvancementMixin implements KaAdvancement {

    @Unique
    private List<Identifier> ka$multiParents = List.of();

    @Shadow
    @Final
    @Mutable
    private static Codec<Advancement> CODEC;

    @Override
    public List<Identifier> ka$multiParents() {
        return this.ka$multiParents;
    }

    @Override
    public void ka$setMultiParents(List<Identifier> ids) {
        this.ka$multiParents = ids == null ? List.of() : List.copyOf(ids);
    }

    // ---- A 层：重建 CODEC ----

    @Inject(method = "<clinit>", at = @At("TAIL"))
    private static void ka$rebuildCodec(CallbackInfo ci) {
        CODEC = RecordCodecBuilder.<Advancement>create(instance -> instance.group(
                        Identifier.CODEC.optionalFieldOf("parent").forGetter(Advancement::parent),
                        DisplayInfo.CODEC.optionalFieldOf("display").forGetter(Advancement::display),
                        AdvancementRewards.CODEC.optionalFieldOf("rewards", AdvancementRewards.EMPTY).forGetter(Advancement::rewards),
                        // 照抄 Advancement.java:37-38 的 CRITERIA_CODEC（criteria 不能为空）
                        Codec.unboundedMap(Codec.STRING, Criterion.CODEC)
                                .validate(map -> map.isEmpty()
                                        ? DataResult.error(() -> "Advancement criteria cannot be empty")
                                        : DataResult.success(map))
                                .fieldOf("criteria").forGetter(Advancement::criteria),
                        AdvancementRequirements.CODEC.optionalFieldOf("requirements").forGetter(advancement -> Optional.of(advancement.requirements())),
                        Codec.BOOL.optionalFieldOf("sends_telemetry_event", false).forGetter(Advancement::sendsTelemetryEvent),
                        Identifier.CODEC.listOf().optionalFieldOf("multi_parents", List.of())
                                .forGetter(advancement -> ((KaAdvancement) (Object) advancement).ka$multiParents()))
                .apply(instance, (Optional<Identifier> parent, Optional<DisplayInfo> display, AdvancementRewards rewards,
                                  Map<String, Criterion<?>> criteria, Optional<AdvancementRequirements> requirements,
                                  Boolean sendsTelemetryEvent, List<Identifier> multiParents) -> {
                    AdvancementRequirements resolved = requirements.orElseGet(() -> AdvancementRequirements.allOf(criteria.keySet()));
                    Advancement advancement = new Advancement(parent, display, rewards, criteria, resolved, sendsTelemetryEvent);
                    // Advancement 是 record（final）：cast 到 duck interface 必须经过 Object。
                    ((KaAdvancement) (Object) advancement).ka$setMultiParents(multiParents);
                    return advancement;
                }))
                // 原版这里写的是 .validate(Advancement::validate)，但那个方法是 private static
                // （Advancement.java:67），Mixin 类在 javac 阶段引用不到，因此内联等价实现。
                .validate(advancement -> advancement.requirements()
                        .validate(advancement.criteria().keySet())
                        .map(requirements -> advancement));
    }

    // ---- B 层：协议追加 ----

    @Inject(method = "write", at = @At("TAIL"))
    private void ka$writeMultiParents(RegistryFriendlyByteBuf buf, CallbackInfo ci) {
        List<Identifier> ids = this.ka$multiParents;
        buf.writeVarInt(ids.size());
        for (Identifier id : ids) {
            buf.writeIdentifier(id);
        }
    }

    @Inject(method = "read", at = @At("RETURN"))
    private static void ka$readMultiParents(RegistryFriendlyByteBuf buf, CallbackInfoReturnable<Advancement> cir) {
        int count = buf.readVarInt();
        List<Identifier> ids = new ArrayList<>(Math.max(count, 0));
        for (int i = 0; i < count; i++) {
            ids.add(buf.readIdentifier());
        }
        Advancement advancement = cir.getReturnValue();
        if (advancement != null) {
            ((KaAdvancement) (Object) advancement).ka$setMultiParents(ids);
        }
    }
}
