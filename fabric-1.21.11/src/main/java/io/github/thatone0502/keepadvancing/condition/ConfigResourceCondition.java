package io.github.thatone0502.keepadvancing.condition;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.thatone0502.keepadvancing.KeepAdvancing;
import io.github.thatone0502.keepadvancing.config.KeepAdvancingConfig;
import net.fabricmc.fabric.api.resource.conditions.v1.ResourceCondition;
import net.fabricmc.fabric.api.resource.conditions.v1.ResourceConditionType;
import net.fabricmc.fabric.api.resource.conditions.v1.ResourceConditions;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.RegistryOps;

/**
 * 一个把<b>配置文件</b>接进数据包加载流程的资源条件。
 *
 * <p>Fabric 的 {@code fabric-resource-conditions-api-v1} 允许数据文件在根对象上带一个
 * {@code fabric:load_conditions} 数组，加载器（{@code RegistryDataLoader} 与
 * {@code SimpleJsonResourceReloadListener}）会先求值，任何一条为假就整个文件不加载。
 * 1.20.5 起进度与战利品表都是<b>注册表</b>，因此两者都吃这一套。
 *
 * <p>用法（每条内置数据文件的开头）：
 * <pre>{@code
 * "fabric:load_conditions": [
 *   { "condition": "keepadvancing:config", "option": "load_builtin_content" }
 * ]
 * }</pre>
 *
 * <p>条件为假时，原版同名进度（我们覆盖的那几个）会退回原版版本 —— 所以那些覆盖文件
 * 也必须带上同一个条件，否则它们会指向不存在的父进度。
 *
 * <p>注册时机：必须早于第一次数据包加载，也就是在 {@code onInitialize()} 里。
 */
public record ConfigResourceCondition(String option) implements ResourceCondition {

    /** {@code load_builtin_content}：对应 {@link KeepAdvancingConfig#loadBuiltInContent()}。 */
    public static final String OPTION_LOAD_BUILT_IN_CONTENT = "load_builtin_content";

    public static final ResourceConditionType<ConfigResourceCondition> TYPE = ResourceConditionType.create(
            Identifier.fromNamespaceAndPath(KeepAdvancing.MOD_ID, "config"),
            RecordCodecBuilder.mapCodec(instance -> instance.group(
                    Codec.STRING.fieldOf("option").forGetter(ConfigResourceCondition::option)
            ).apply(instance, ConfigResourceCondition::new)));

    @Override
    public ResourceConditionType<?> getType() {
        return TYPE;
    }

    @Override
    public boolean test(RegistryOps.RegistryInfoLookup registryInfo) {
        if (OPTION_LOAD_BUILT_IN_CONTENT.equals(option)) {
            return KeepAdvancingConfig.loadBuiltInContent();
        }

        // 拼错选项名时宁可什么都不加载，也不要「看起来加载了其实没有」。
        KeepAdvancing.LOGGER.warn(
                "Unknown keepadvancing:config option \"{}\"; the condition evaluates to false.",
                option);
        return false;
    }

    /** 注册条件类型。必须在任何数据包加载之前调用。 */
    public static void init() {
        ResourceConditions.register(TYPE);
    }
}
