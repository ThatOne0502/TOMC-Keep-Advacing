package io.github.thatone0502.keepadvancing.mixin;

import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.thatone0502.keepadvancing.api.KaDisplayInfo;
import io.github.thatone0502.keepadvancing.content.BackgroundSpec;
import io.github.thatone0502.keepadvancing.content.BgAttachment;
import io.github.thatone0502.keepadvancing.content.BgMode;
import java.util.Optional;
import net.minecraft.advancements.AdvancementType;
import net.minecraft.advancements.DisplayInfo;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * A 层（数据）+ B 层（协议）对 {@code DisplayInfo} 的扩展（rev14）。
 *
 * <p>1.21.11 的 {@code DisplayInfo} 是**普通 class**（字段 private final，只有 x/y 可变），
 * 所以可以直接加 {@code @Unique} 字段。
 *
 * <p>原版 {@code CODEC} 是 static final，构建期在 {@code <clinit>} 里只赋值一次。
 * 我们在 {@code <clinit>} 的 TAIL 用 {@code @Mutable @Shadow @Final} 把它整个重建：
 * <ul>
 *   <li>{@code icon} 从 {@code ItemStack.STRICT_CODEC} 放宽成
 *       {@code Codec.either(Identifier.CODEC, ItemStack.STRICT_CODEC)}（left 优先 ⇒ 字符串走纹理分支）；</li>
 *   <li>{@code background} 从 {@code ClientAsset.ResourceTexture.CODEC} 换成语义更宽的
 *       {@link BackgroundSpec}（字符串形态与老数据包完全兼容）。</li>
 * </ul>
 * 其余 6 个字段的顺序 / getter 名 / 默认值逐字照抄
 * {@code DisplayInfo.java} 第 14-26 行（含 {@code AdvancementType.CODEC.optionalFieldOf("frame", AdvancementType.TASK)}）。
 */
@Mixin(DisplayInfo.class)
public abstract class DisplayInfoMixin implements KaDisplayInfo {

    @Unique
    private @Nullable Identifier ka$iconTexture;

    @Unique
    private BgMode ka$bgMode = BgMode.DEFAULT;

    @Unique
    private BgAttachment ka$bgAttachment = BgAttachment.DEFAULT;

    @Unique
    private int ka$bgWidth = -1;

    @Unique
    private int ka$bgHeight = -1;

    @Shadow
    @Final
    @Mutable
    private static Codec<DisplayInfo> CODEC;

    @Override
    public @Nullable Identifier ka$iconTexture() {
        return this.ka$iconTexture;
    }

    @Override
    public void ka$setIconTexture(@Nullable Identifier id) {
        this.ka$iconTexture = id;
    }

    @Override
    public BgMode ka$bgMode() {
        return this.ka$bgMode;
    }

    @Override
    public void ka$setBgMode(BgMode mode) {
        this.ka$bgMode = mode == null ? BgMode.DEFAULT : mode;
    }

    @Override
    public BgAttachment ka$bgAttachment() {
        return this.ka$bgAttachment;
    }

    @Override
    public void ka$setBgAttachment(BgAttachment attachment) {
        this.ka$bgAttachment = attachment == null ? BgAttachment.DEFAULT : attachment;
    }

    @Override
    public int ka$bgWidth() {
        return this.ka$bgWidth;
    }

    @Override
    public int ka$bgHeight() {
        return this.ka$bgHeight;
    }

    @Override
    public void ka$setBgSize(int width, int height) {
        this.ka$bgWidth = width;
        this.ka$bgHeight = height;
    }

    // ---- A 层：重建 CODEC ----

    @Inject(method = "<clinit>", at = @At("TAIL"))
    private static void ka$rebuildCodec(CallbackInfo ci) {
        CODEC = RecordCodecBuilder.create(instance -> instance.group(
                        Codec.either(Identifier.CODEC, ItemStack.STRICT_CODEC).fieldOf("icon")
                                .forGetter(d -> {
                                    Identifier tex = ((KaDisplayInfo) d).ka$iconTexture();
                                    return tex != null ? Either.left(tex) : Either.right(d.getIcon());
                                }),
                        ComponentSerialization.CODEC.fieldOf("title").forGetter(DisplayInfo::getTitle),
                        ComponentSerialization.CODEC.fieldOf("description").forGetter(DisplayInfo::getDescription),
                        BackgroundSpec.CODEC.optionalFieldOf("background")
                                .forGetter(d -> d.getBackground().map(tex -> {
                                    KaDisplayInfo k = (KaDisplayInfo) d;
                                    return new BackgroundSpec(tex.id(), k.ka$bgMode(), k.ka$bgAttachment(), k.ka$bgWidth(), k.ka$bgHeight());
                                })),
                        AdvancementType.CODEC.optionalFieldOf("frame", AdvancementType.TASK).forGetter(DisplayInfo::getType),
                        Codec.BOOL.optionalFieldOf("show_toast", true).forGetter(DisplayInfo::shouldShowToast),
                        Codec.BOOL.optionalFieldOf("announce_to_chat", true).forGetter(DisplayInfo::shouldAnnounceChat),
                        Codec.BOOL.optionalFieldOf("hidden", false).forGetter(DisplayInfo::isHidden))
                .apply(instance, (Either<Identifier, ItemStack> iconEither, Component title,
                                  Component description, Optional<BackgroundSpec> background, AdvancementType type,
                                  Boolean showToast, Boolean announceToChat, Boolean hidden) -> {
                    ItemStack stack = iconEither.left().isPresent() ? ItemStack.EMPTY : iconEither.right().orElse(ItemStack.EMPTY);
                    Identifier texture = iconEither.left().orElse(null);
                    DisplayInfo display = new DisplayInfo(stack, title, description,
                            background.map(BackgroundSpec::toResourceTexture), type,
                            showToast, announceToChat, hidden);
                    KaDisplayInfo k = (KaDisplayInfo) display;
                    k.ka$setIconTexture(texture);
                    background.ifPresent(bg -> {
                        k.ka$setBgMode(bg.mode());
                        k.ka$setBgAttachment(bg.attachment());
                        k.ka$setBgSize(bg.width(), bg.height());
                    });
                    return display;
                }));
    }

    // ---- B 层：协议追加 ----

    @Inject(method = "serializeToNetwork", at = @At("TAIL"))
    private void ka$writeExtra(RegistryFriendlyByteBuf buf, CallbackInfo ci) {
        int flags = 0;
        if (this.ka$iconTexture != null) {
            flags |= 0x01;
        }
        if (this.ka$bgMode != BgMode.DEFAULT) {
            flags |= 0x02;
        }
        if (this.ka$bgAttachment != BgAttachment.DEFAULT) {
            flags |= 0x04;
        }
        if (this.ka$bgWidth > 0 && this.ka$bgHeight > 0) {
            flags |= 0x08;
        }
        buf.writeByte(flags);
        if ((flags & 0x01) != 0) {
            buf.writeIdentifier(this.ka$iconTexture);
        }
        if ((flags & 0x02) != 0) {
            buf.writeByte(this.ka$bgMode.ordinal());
        }
        if ((flags & 0x04) != 0) {
            buf.writeByte(this.ka$bgAttachment.ordinal());
        }
        if ((flags & 0x08) != 0) {
            buf.writeVarInt(this.ka$bgWidth);
            buf.writeVarInt(this.ka$bgHeight);
        }
    }

    @Inject(method = "fromNetwork", at = @At("RETURN"))
    private static void ka$readExtra(RegistryFriendlyByteBuf buf, CallbackInfoReturnable<DisplayInfo> cir) {
        DisplayInfo display = cir.getReturnValue();
        if (display == null) {
            return;
        }
        KaDisplayInfo k = (KaDisplayInfo) display;
        int flags = buf.readByte();
        if ((flags & 0x01) != 0) {
            k.ka$setIconTexture(buf.readIdentifier());
        }
        if ((flags & 0x02) != 0) {
            k.ka$setBgMode(BgMode.byOrdinal(buf.readByte()));
        }
        if ((flags & 0x04) != 0) {
            k.ka$setBgAttachment(BgAttachment.byOrdinal(buf.readByte()));
        }
        if ((flags & 0x08) != 0) {
            k.ka$setBgSize(buf.readVarInt(), buf.readVarInt());
        }
    }

    // ---- B 层：空图标（icon 写成字符串）的编解码修正 ----

    /**
     * {@code DisplayInfo.java:109} 用的是 {@code ItemStack.STREAM_CODEC} —— 它只是
     * {@code OPTIONAL_STREAM_CODEC} 外面套的一层判空包装（{@code ItemStack.java:147-163}），
     * 空堆叠在 {@code :159} 直接 {@code throw new EncoderException("Empty ItemStack not allowed")}。
     *
     * <p>{@code icon} 写成字符串时我们构造出的正是 {@code ItemStack.EMPTY}，于是
     * {@code /advancement grant @a everything} 在发送 {@code update_advancements} 时炸掉整条连接
     * （玩家端日志：{@code io.netty.handler.codec.EncoderException: Empty ItemStack not allowed}）。
     *
     * <p>把这一次字段读改指向 {@code OPTIONAL_STREAM_CODEC}。因为原版 {@code STREAM_CODEC.encode}
     * 本来就是"判空 + 转调 OPTIONAL"，**非空堆叠的字节流一字不变**，只有空堆叠由"抛异常"变成
     * 多写一个 VarInt 0。读写两端必须成对修改（见 {@link #ka$iconCodecForRead()}），否则空图标会在
     * 客户端撞上 {@code DecoderException: Empty ItemStack not allowed}。
     *
     * <p>注：{@code @At("FIELD")} 不写 {@code opcode} 时 Mixin 用 -1，一样匹配 GETSTATIC
     * （{@code BeforeFieldAccess.java:21,41}），所以不需要引 ASM 的 {@code Opcodes}。
     */
    @Redirect(
            method = "serializeToNetwork",
            at = @At(
                    value = "FIELD",
                    target = "Lnet/minecraft/world/item/ItemStack;STREAM_CODEC:Lnet/minecraft/network/codec/StreamCodec;"))
    private static StreamCodec<RegistryFriendlyByteBuf, ItemStack> ka$iconCodecForWrite() {
        return ItemStack.OPTIONAL_STREAM_CODEC;
    }

    /** 读侧与 {@link #ka$iconCodecForWrite()} 对称；见那里的说明。 */
    @Redirect(
            method = "fromNetwork",
            at = @At(
                    value = "FIELD",
                    target = "Lnet/minecraft/world/item/ItemStack;STREAM_CODEC:Lnet/minecraft/network/codec/StreamCodec;"))
    private static StreamCodec<RegistryFriendlyByteBuf, ItemStack> ka$iconCodecForRead() {
        return ItemStack.OPTIONAL_STREAM_CODEC;
    }
}
