package io.github.thatone0502.keepadvancing.mixin.ba;

import io.github.thatone0502.keepadvancing.api.KaAdvancementNode;
import io.github.thatone0502.keepadvancing.logic.IconRenderer;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.AdvancementNode;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * rev14 客户端渲染（BetterAdvancements 路）—— ① 字符串 icon ② 多父连线。
 *
 * <p>目标类是 BA 自有类（{@code targets} 字符串；BA 自有类/字段/方法名不混淆，原版类型用 mojmap，
 * Loom 的 mixin remapper 负责把原版引用重映射成 intermediary）。
 *
 * <p>① 图标：{@code draw} 里 {@code guiGraphics.method_55231(this.displayInfo.getIcon(), x, y, color)}
 * （= 4 参 {@code renderFakeItem(ItemStack,int,int,int)}）与 {@code drawHover} 里
 * {@code guiGraphics.method_51445(this.displayInfo.getIcon(), x, y)}（= 3 参 {@code renderFakeItem(ItemStack,int,int)}）
 * 各下一个 {@code @Redirect(require = 0)}，交给 {@link IconRenderer}。
 * <b>⚠️ BA 下 icon 纹理不着色</b>：{@code color}（BA 的 icon 着色）在纹理分支被忽略 —— 这是刻意的，
 * 免得给 {@code IconRenderer} 加 tint 参数；命中不了（{@code require = 0}）时整段退回原版物品渲染。
 *
 * <p>② 多父连线：{@code drawConnectivity(GuiGraphics,int scrollX,int scrollY,boolean drawInside)}
 * HEAD+cancel，整段照抄 BA 原有结构（{@code if (!hideLines) { 主父; 事件额外连线; } } + 递归 children），
 * 只在主父之后插入 {@code ka$extraParents()} 的连线。
 *
 * <p><b>为什么连线全走反射</b>：BA 的 {@code drawConnection(GuiGraphics, BetterAdvancementWidget, int, int, boolean)}
 * 是 public，但参数类型是 BA 类，而 BA jar **不在本工程编译 classpath**（C 层不能改 build.gradle），
 * 所以编译期不能用它 —— 用 {@link Method} 反射调用（只解析一次，静态缓存，失败静默跳过）。
 * 递归 children 与额外父查 widget 同理走反射。
 */
@Mixin(targets = "betteradvancements.common.gui.BetterAdvancementWidget")
public abstract class BetterAdvancementWidgetMixin {

    private static final String KA$WIDGET_CLASS = "betteradvancements.common.gui.BetterAdvancementWidget";
    private static final String KA$TAB_CLASS = "betteradvancements.common.gui.BetterAdvancementTab";

    private static final Method KA$DRAW_CONNECTION = ka$lookup(KA$WIDGET_CLASS, "drawConnection",
            GuiGraphics.class, ka$class(KA$WIDGET_CLASS), int.class, int.class, boolean.class);
    private static final Method KA$DRAW_CONNECTIVITY = ka$lookup(KA$WIDGET_CLASS, "drawConnectivity",
            GuiGraphics.class, int.class, int.class, boolean.class);
    private static final Method KA$GET_WIDGET = ka$lookup(KA$TAB_CLASS, "getWidget", AdvancementHolder.class);

    @Shadow
    private AdvancementNode advancementNode;

    /** BA 的 {@code private final List<BetterAdvancementWidget> children;}（泛型参数是 BA 类型 ⇒ 用通配符）。 */
    @Shadow
    private List<?> children;

    /** BA 的 {@code private final class_185 displayInfo;}。 */
    @Shadow
    private net.minecraft.advancements.DisplayInfo displayInfo;

    // ⚠️ BA 的 betterAdvancementTabGui / parent 两个字段的类型是 **BA 自有类**，而 BA jar 不在本工程
    // 编译 classpath，所以**不能**用 @Shadow —— Mixin 要求 @Shadow 字段的 descriptor 与目标字段
    // 完全一致（MixinPreProcessorStandard.attachFields 最后一段：
    // "The field %s in the target class has a conflicting signature"），
    // 写成 Object 会在 apply 期直接抛，而不是静默降级。这两个字段因此走反射，只解析一次。
    private static final Field KA$F_TAB = ka$field(ka$class(KA$WIDGET_CLASS), "betterAdvancementTabGui");
    private static final Field KA$F_PARENT = ka$field(ka$class(KA$WIDGET_CLASS), "parent");

    // ---- ① 图标 ----

    @Redirect(require = 0, method = "draw", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/GuiGraphics;renderFakeItem(Lnet/minecraft/world/item/ItemStack;III)V"))
    private void ka$iconInDraw(GuiGraphics guiGraphics, ItemStack itemStack, int i, int j, int k) {
        if (IconRenderer.iconTexture(this.displayInfo) != null) {
            // 纹理分支丢弃 BA 的着色参数 k（见类注释）。
            IconRenderer.draw(guiGraphics, this.displayInfo, itemStack, i, j, IconRenderer.SIZE);
            return;
        }
        guiGraphics.renderFakeItem(itemStack, i, j, k);
    }

    @Redirect(require = 0, method = "drawHover", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/GuiGraphics;renderFakeItem(Lnet/minecraft/world/item/ItemStack;II)V"))
    private void ka$iconInHover(GuiGraphics guiGraphics, ItemStack itemStack, int i, int j) {
        IconRenderer.draw(guiGraphics, this.displayInfo, itemStack, i, j, IconRenderer.SIZE);
    }

    // ---- ② 多父连线 ----

    @Inject(require = 0, method = "drawConnectivity", at = @At("HEAD"), cancellable = true)
    private void ka$drawConnectivity(GuiGraphics guiGraphics, int scrollX, int scrollY, boolean drawInside, CallbackInfo ci) {
        if (!ka$hideLines(this)) {
            Object parent = ka$getField(KA$F_PARENT, this);
            if (parent != null) {
                ka$invoke(KA$DRAW_CONNECTION, this, guiGraphics, parent, scrollX, scrollY, drawInside);
            }

            Object tab = ka$getField(KA$F_TAB, this);
            if (tab != null && this.advancementNode != null) {
                for (AdvancementNode extraParent : ((KaAdvancementNode) (Object) this.advancementNode).ka$extraParents()) {
                    if (extraParent == null) {
                        continue;
                    }
                    Object parentWidget = ka$invoke2(KA$GET_WIDGET, tab, extraParent.holder());
                    if (parentWidget == null) {
                        continue;
                    }
                    ka$invoke(KA$DRAW_CONNECTION, this, guiGraphics, parentWidget, scrollX, scrollY, drawInside);
                }
            }
        }

        if (this.children != null) {
            for (Object child : this.children) {
                ka$invoke(KA$DRAW_CONNECTIVITY, child, guiGraphics, scrollX, scrollY, drawInside);
            }
        }

        ci.cancel();
    }

    // ---- 反射桥（BA 类型不在编译 classpath）----

    private static boolean ka$hideLines(Object widget) {
        try {
            Object info = widget.getClass().getDeclaredField("betterDisplayInfo").get(widget);
            Object result = info.getClass().getMethod("hideLines").invoke(info);
            return result instanceof Boolean flag && flag;
        } catch (Throwable t) {
            return false;
        }
    }

    private static @Nullable Class<?> ka$class(String name) {
        try {
            return Class.forName(name);
        } catch (Throwable t) {
            return null;
        }
    }

    private static @Nullable Method ka$lookup(String className, String name, Class<?>... args) {
        try {
            Class<?> owner = Class.forName(className);
            for (Class<?> candidate : args) {
                if (candidate == null) {
                    return null;
                }
            }
            Method method = owner.getMethod(name, args);
            method.setAccessible(true);
            return method;
        } catch (Throwable t) {
            return null;
        }
    }

    private static @Nullable Field ka$field(@Nullable Class<?> owner, String name) {
        if (owner == null) {
            return null;
        }
        try {
            Field field = owner.getDeclaredField(name);
            field.setAccessible(true);
            return field;
        } catch (Throwable t) {
            return null;
        }
    }

    private static @Nullable Object ka$getField(@Nullable Field field, Object owner) {
        if (field == null || owner == null) {
            return null;
        }
        try {
            return field.get(owner);
        } catch (Throwable t) {
            return null;
        }
    }

    private static void ka$invoke(@Nullable Method method, Object target, Object... args) {
        if (method == null || target == null) {
            return;
        }
        try {
            method.invoke(target, args);
        } catch (Throwable t) {
            // 渲染期绝不抛异常；BA 版本不匹配时静默降级。
        }
    }

    private static @Nullable Object ka$invoke2(@Nullable Method method, Object target, Object... args) {
        if (method == null || target == null) {
            return null;
        }
        try {
            return method.invoke(target, args);
        } catch (Throwable t) {
            return null;
        }
    }
}
