package io.github.thatone0502.keepadvancing.content;

import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.ClientAsset;
import net.minecraft.resources.Identifier;

/**
 * rev14 的 {@code background} 字段载荷。
 *
 * <p>两种 JSON 形态都吃：
 * <pre>
 * "background": "keepadvancing:gui/advancements/backgrounds/tomc"          // 老写法
 * "background": { "asset_id": "...", "mode": "cover", "attachment": "local", "width": 16, "height": 16 }
 * </pre>
 * 字符串形态一律展开成 {@code mode=TILE / attachment=FIXED / width=-1 / height=-1}（尺寸运行时自动探测）。
 *
 * <p>写入时做反向归一化：如果三个扩展字段都没被显式指定，就回到字符串形态，
 * 这样"没写新字段"的数据包序列化结果与原版逐字节一致。
 */
public record BackgroundSpec(Identifier assetId, BgMode mode, BgAttachment attachment, int width, int height) {

    public static final Codec<BackgroundSpec> CODEC = Codec.either(
                    Identifier.CODEC,
                    RecordCodecBuilder.<BackgroundSpec>create(i -> i.group(
                                    Identifier.CODEC.fieldOf("asset_id").forGetter(BackgroundSpec::assetId),
                                    BgMode.CODEC.optionalFieldOf("mode", BgMode.DEFAULT).forGetter(BackgroundSpec::mode),
                                    BgAttachment.CODEC.optionalFieldOf("attachment", BgAttachment.DEFAULT).forGetter(BackgroundSpec::attachment),
                                    Codec.INT.optionalFieldOf("width", -1).forGetter(BackgroundSpec::width),
                                    Codec.INT.optionalFieldOf("height", -1).forGetter(BackgroundSpec::height))
                            .apply(i, BackgroundSpec::new)))
            .xmap(
                    either -> either.map(BackgroundSpec::fromId, spec -> spec),
                    spec -> spec.isPlain() ? Either.left(spec.assetId()) : Either.right(spec));

    /** 字符串（老写法）形态。 */
    public static BackgroundSpec fromId(Identifier id) {
        return new BackgroundSpec(id, BgMode.DEFAULT, BgAttachment.DEFAULT, -1, -1);
    }

    /** 三个扩展字段都等于默认值 ⇒ 可以无损降级成字符串形态。 */
    public boolean isPlain() {
        return this.mode == BgMode.DEFAULT
                && this.attachment == BgAttachment.DEFAULT
                && this.width < 0
                && this.height < 0;
    }

    /** 转成原版 DisplayInfo 吃的类型（单参 public 构造会顺带推出 texturePath）。 */
    public ClientAsset.ResourceTexture toResourceTexture() {
        return new ClientAsset.ResourceTexture(this.assetId);
    }
}
