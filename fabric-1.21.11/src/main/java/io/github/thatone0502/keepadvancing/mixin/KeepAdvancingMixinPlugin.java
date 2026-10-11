package io.github.thatone0502.keepadvancing.mixin;

import java.util.List;
import java.util.Set;
import net.fabricmc.loader.api.FabricLoader;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

/**
 * 只被 {@code mixins.keepadvancing.ba.json} 引用的 Mixin 开关（rev14）。
 *
 * <p>那个配置里的 Mixin 全部指向 BetterAdvancements 的自有类，没装 BA 时一个都不该加载
 * （{@code required:false} 保证加载失败也不崩，但直接不应用更干净）。所以这里的
 * {@code shouldApplyMixin} 无条件返回 {@code isModLoaded("betteradvancements")}。
 */
public class KeepAdvancingMixinPlugin implements IMixinConfigPlugin {

    private static final boolean BA_LOADED = FabricLoader.getInstance().isModLoaded("betteradvancements");

    @Override
    public void onLoad(String mixinPackage) {
        // 无需初始化。
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        return BA_LOADED;
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {
        // 不参与目标合并。
    }

    @Override
    public List<String> getMixins() {
        return null;
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
        // 不做字节码预处理。
    }

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
        // 不做字节码后处理。
    }
}
