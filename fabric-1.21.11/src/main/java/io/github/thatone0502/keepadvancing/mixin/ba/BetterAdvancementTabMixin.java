package io.github.thatone0502.keepadvancing.mixin.ba;

import io.github.thatone0502.keepadvancing.api.KaDisplayInfo;
import io.github.thatone0502.keepadvancing.logic.BackgroundRenderer;
import io.github.thatone0502.keepadvancing.logic.IconContext;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import net.minecraft.advancements.AdvancementNode;
import net.minecraft.advancements.DisplayInfo;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.core.ClientAsset;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * rev14 客户端渲染（BetterAdvancements 路）—— 背景 mode/attachment。
 *
 * <p>目标类用字符串 {@code targets} 指定（BA 自有类名不混淆；参数/返回类型用 mojmap，Loom 的
 * mixin remapper 会重映射成 intermediary）。
 *
 * <p>⚠️ <b>BA 的 jar 不在本工程编译 classpath</b>（运行时可选依赖：{@code fabric.mod.json} 软依赖 +
 * MixinPlugin 互斥，build.gradle 里没有 compileOnly，而 C 层不允许改 build 文件）。所以本类：
 * <ul>
 *   <li>只 {@code @Shadow} **原版类型**的字段（{@code rootNode}/{@code display}/{@code widgets}）；</li>
 *   <li>BA 自己的 {@code BetterAdvancementWidget.draw(…)/drawConnectivity(…)} 用**反射**调用
 *       （签名与 BA 源码 + {@code IBetterAdvancementEntryGui} 一致，都是 {@code (GuiGraphics,int,int[,boolean])}），
 *       {@link Method} 只解析一次并缓存。</li>
 * </ul>
 *
 * <p>整体重写 {@code drawContents(GuiGraphics,int left,int top,int width,int height,float zoom)}
 * （BA 源码 {@code BetterAdvancementTab.java:83-112}），**照抄它的顺序**：居中初始化 → scissor +
 * translate → 背景 → {@code scale(zoom,zoom)} → 连线两遍 → 节点 → popMatrix/disableScissor。
 * 背景因此用**未缩放**坐标、尺寸 {@code width x height}。
 *
 * <p>⚠️ 本类注入 {@code require = 0}：BA 改版只丢功能，不崩。
 */
@Mixin(targets = "betteradvancements.common.gui.BetterAdvancementTab")
public abstract class BetterAdvancementTabMixin {

    private static final String KA$WIDGET_CLASS = "betteradvancements.common.gui.BetterAdvancementWidget";

    private static final Method KA$DRAW_CONNECTIVITY =
            ka$findMethod("drawConnectivity", GuiGraphics.class, int.class, int.class, boolean.class);
    private static final Method KA$DRAW =
            ka$findMethod("draw", GuiGraphics.class, int.class, int.class);

    @Shadow
    private int scrollX;

    @Shadow
    private int scrollY;

    @Shadow
    private int minX;

    @Shadow
    private int minY;

    @Shadow
    private int maxX;

    @Shadow
    private int maxY;

    @Shadow
    private boolean centered;

    /** BA: {@code private final class_8781 rootNode;} —— mojmap 就是 {@link AdvancementNode}。 */
    @Shadow
    private AdvancementNode rootNode;

    /** BA: {@code private final class_185 display;} —— mojmap 就是 {@link DisplayInfo}（BA 用同一套原版字段）。 */
    @Shadow
    private DisplayInfo display;

    @Inject(require = 0, method = "drawContents", at = @At("HEAD"), cancellable = true)
    private void ka$drawContents(GuiGraphics guiGraphics, int left, int top, int width, int height, float zoom, CallbackInfo ci) {
        Object root = ka$rootWidget();
        if (root == null) {
            // 拿不到 root（BA 结构变了）⇒ 什么都不做，让 BA 自己的实现继续跑，绝不出现空白页。
            return;
        }

        if (!this.centered) {
            this.scrollX = (width - (this.maxX + this.minX)) / 2;
            this.scrollY = (height - (this.maxY + this.minY)) / 2;
            this.centered = true;
        }

        guiGraphics.enableScissor(left, top, left + width, top + height);
        guiGraphics.pose().pushMatrix();
        guiGraphics.pose().translate(left, top);

        DisplayInfo backgroundDisplay = this.display != null
                ? this.display
                : (this.rootNode == null ? null : this.rootNode.advancement().display().orElse(null));
        Identifier identifier = ka$backgroundTexture(backgroundDisplay);
        if (backgroundDisplay != null) {
            KaDisplayInfo ka = (KaDisplayInfo) (Object) backgroundDisplay;
            BackgroundRenderer.draw(guiGraphics, identifier, ka.ka$bgMode(), ka.ka$bgAttachment(),
                    width, height, this.maxX - this.minX, this.maxY - this.minY,
                    this.scrollX, this.scrollY, ka.ka$bgWidth(), ka.ka$bgHeight());
        }

        guiGraphics.pose().scale(zoom, zoom);
        ka$invoke(KA$DRAW_CONNECTIVITY, root, guiGraphics, this.scrollX, this.scrollY, true);
        ka$invoke(KA$DRAW_CONNECTIVITY, root, guiGraphics, this.scrollX, this.scrollY, false);
        ka$invoke(KA$DRAW, root, guiGraphics, this.scrollX, this.scrollY);

        guiGraphics.pose().popMatrix();
        guiGraphics.disableScissor();
        ci.cancel();
    }

    /**
     * BA: {@code public void drawIcon(class_332 guiGraphics, int left, int top, int width, int height)}。
     * 在绘制期间把 {@code display} 暂存到 {@link IconContext} 供
     * {@link BetterAdvancementTabTypeMixin} 读取（BA 逐个 tab 串行调 drawIcon）。
     */
    @Inject(require = 0, method = "drawIcon", at = @At("HEAD"))
    private void ka$pushIconDisplay(GuiGraphics guiGraphics, int left, int top, int width, int height, CallbackInfo ci) {
        IconContext.set(this.display);
    }

    @Inject(require = 0, method = "drawIcon", at = @At("RETURN"))
    private void ka$popIconDisplay(GuiGraphics guiGraphics, int left, int top, int width, int height, CallbackInfo ci) {
        IconContext.clear();
    }

    // ---- 反射桥（BA 类不在编译 classpath）----

    /** BA 的 {@code root} 字段。 */
    private @Nullable Object ka$rootWidget() {
        try {
            Field field = this.getClass().getDeclaredField("root");
            field.setAccessible(true);
            return field.get(this);
        } catch (Throwable t) {
            return null;
        }
    }

    private static Identifier ka$backgroundTexture(@Nullable DisplayInfo display) {
        if (display == null) {
            return TextureManager.INTENTIONAL_MISSING_TEXTURE;
        }
        return display.getBackground().map(ClientAsset.ResourceTexture::texturePath)
                .orElse(TextureManager.INTENTIONAL_MISSING_TEXTURE);
    }

    private static @Nullable Method ka$findMethod(String name, Class<?>... args) {
        try {
            Class<?> widgetClass = Class.forName(KA$WIDGET_CLASS);
            Method method = widgetClass.getMethod(name, args);
            method.setAccessible(true);
            return method;
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
}
