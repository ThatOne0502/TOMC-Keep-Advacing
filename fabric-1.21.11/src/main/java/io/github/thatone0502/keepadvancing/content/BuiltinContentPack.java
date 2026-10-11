package io.github.thatone0502.keepadvancing.content;

import io.github.thatone0502.keepadvancing.KeepAdvancing;
import io.github.thatone0502.keepadvancing.config.KeepAdvancingConfig;
import net.fabricmc.fabric.api.resource.v1.ResourceLoader;
import net.fabricmc.fabric.api.resource.v1.pack.PackActivationType;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.minecraft.resources.Identifier;

/**
 * 承载「覆盖原版进度」的那 6 个 JSON 的内置数据包。
 *
 * <h2>为什么不能直接用资源条件</h2>
 * 那 6 个文件覆盖的是 {@code data/minecraft/advancement/...} 里的原版进度，用的
 * {@code fabric:load_conditions} 一旦判定为假，**低优先级数据包里的原版副本并不会接管**——那个注册表
 * id 会整体消失。实测：内置内容关掉后原版进度从 1584 掉到 1578，凭空少掉
 * {@code adventure/adventuring_time}、{@code adventure/spyglass_at_parrot}、
 * {@code adventure/spyglass_at_ghast}、{@code adventure/spyglass_at_dragon}、
 * {@code adventure/very_very_frightening}、{@code husbandry/obtain_netherite_hoe} 这 6 条
 * （其中 4 条是把原版节点挂到 TOMC 链上的重定父，2 条只是改图标）。
 *
 * <h2>所以改用内置数据包</h2>
 * 这 6 个文件被挪出默认数据包，放在 {@code resourcepacks/content-data/} 下；只有在
 * {@link KeepAdvancingConfig#loadBuiltInContent()} 为 {@code true} 时才注册这个包。
 * 关掉时包**根本不存在**，于是不会遮蔽任何东西，原版那 6 条照常加载。
 *
 * <p>{@code ALWAYS_ENABLED} 让这个包在数据包界面里不可关闭（{@code required = true}）——
 * 开关权只在配置文件手里，玩家不会看到两个互相矛盾的开关。
 *
 * <p>包根目录由 Fabric 决定：v1 的
 * {@code ResourceLoader.registerBuiltinPack(id, container, type)} 内部走的是
 * {@code "resourcepacks/" + id.getPath()}，所以这里的 id path 必须与目录名一致。
 */
public final class BuiltinContentPack {

    /** 包目录名，同时也是 id 的 path：{@code resourcepacks/content-data/}。 */
    public static final String PACK_PATH = "content-data";

    private static final Identifier PACK_ID =
            Identifier.fromNamespaceAndPath(KeepAdvancing.MOD_ID, PACK_PATH);

    private BuiltinContentPack() {
    }

    /** 在 {@code onInitialize()} 里调用，必须早于第一次数据包加载。 */
    public static void init() {
        if (!KeepAdvancingConfig.loadBuiltInContent()) {
            KeepAdvancing.LOGGER.info(
                    "Built-in content is disabled; the vanilla-advancement overrides in pack '{}' are not registered "
                            + "(vanilla keeps all of its own advancements).",
                    PACK_ID);
            return;
        }

        ModContainer self = FabricLoader.getInstance().getModContainer(KeepAdvancing.MOD_ID).orElseThrow();

        if (ResourceLoader.registerBuiltinPack(PACK_ID, self, PackActivationType.ALWAYS_ENABLED)) {
            KeepAdvancing.LOGGER.info(
                    "Registered built-in data pack '{}' (vanilla-advancement overrides).", PACK_ID);
        } else {
            KeepAdvancing.LOGGER.warn(
                    "Could not register built-in data pack '{}' (no resources found under resourcepacks/{}). "
                            + "The re-parented vanilla advancements will not be applied.",
                    PACK_ID, PACK_PATH);
        }
    }
}
