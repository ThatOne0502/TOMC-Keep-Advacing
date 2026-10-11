package io.github.thatone0502.keepadvancing.logic;

import io.github.thatone0502.keepadvancing.KeepAdvancing;
import io.github.thatone0502.keepadvancing.api.KaDisplayInfo;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.advancements.DisplayInfo;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * rev14 图标绘制器：{@code icon} 写成字符串时画纹理，否则回退原版 {@code renderFakeItem}。
 *
 * <p>字符串 icon 的解析规则与原版 {@code ClientAsset.ResourceTexture} **逐字一致**
 * （{@code ClientAsset.java:25-27}）：把裸资源 id 拼成真实纹理位置，见 {@link #toTexturePath}。
 * 数据里写 {@code katest:icon/star}，实际读 {@code assets/katest/textures/icon/star.png}。
 *
 * <p>纹理分支用归一化 UV 的 9 参 {@code blit}（{@code GuiGraphics.java:574}），把整张纹理拉到
 * {@code size x size} 的格子里。原版图标格子是 26x26、物品画在 {@code +8/+5} 处的 16x16，
 * 所以调用方一律传 {@code size = 16}，坐标也照原版传。
 */
public final class IconRenderer {

    /** 图标格子边长（与原版物品图标一致）。 */
    public static final int SIZE = 16;

    /** 原版 {@code ResourceTexture} 的路径前缀/后缀；改这里等于改数据格式，别动。 */
    private static final String TEXTURE_DIR = "textures/";
    private static final String TEXTURE_EXT = ".png";

    /** 已经就"数据里写成了完整纹理路径"告警过的 id —— 避免每帧刷屏。 */
    private static final Set<Identifier> FULL_PATH_WARNED = ConcurrentHashMap.newKeySet();

    private IconRenderer() {
    }

    /**
     * 数据里的裸资源 id → 真实纹理位置。
     *
     * <p>与原版 {@code ClientAsset.ResourceTexture} 的构造器一字不差：
     * <pre>identifier.withPath(p -&gt; "textures/" + p + ".png")</pre>
     *
     * <p><b>这一步不能省。</b>rev14 漏了它，{@code GuiGraphics.innerBlit} 里的
     * {@code getTextureManager().getTexture(id)}（{@code GuiGraphics.java:579}）会按字面路径去找
     * {@code assets/katest/icon/star}，找不到就渲染成**紫黑格**（缺纹理）。背景之所以没这个毛病，
     * 是因为它复用原版 {@code ResourceTexture}，拼接发生在那个构造器里。
     *
     * <p>取纹理的这条链路上**没有任何地方**会自动补前缀或后缀，取证见：
     * {@code TextureManager.java:93-102}（{@code getTexture} 直接把 id 交给 {@code SimpleTexture}）、
     * {@code SimpleTexture.java:16-18}（{@code TextureContents.load(resourceManager, resourceId())}）、
     * {@code TextureContents.java:17-18}（{@code resourceManager.getResourceOrThrow(identifier)}，
     * 按字面路径找资源）。加载失败**不抛异常也不崩**：{@code TextureManager.java:57-63} 的
     * {@code loadContentsSafe} 只打一条 `Failed to load texture ...` 然后
     * {@code return TextureContents.createMissing()} ⇒ 表现就是安静地画成紫黑格。
     *
     * @param id 数据里写的 id，**不含** {@code textures/} 前缀与 {@code .png} 后缀
     * @return 可直接喂给 {@code GuiGraphics.blit} 的纹理位置
     */
    public static Identifier toTexturePath(Identifier id) {
        String path = id.getPath();
        // 写成了完整路径的数据包作者只会看到一个没头没尾的紫黑格，所以在日志里点一句。
        if ((path.startsWith(TEXTURE_DIR) || path.endsWith(TEXTURE_EXT)) && FULL_PATH_WARNED.add(id)) {
            KeepAdvancing.LOGGER.warn(
                    "字符串 icon '{}' 看起来已经是完整纹理路径；这个字段要写裸路径（例如 katest:icon/star），"
                            + "现在会去找 assets/{}/{}{} 而渲染成紫黑格。",
                    id, id.getNamespace(), TEXTURE_DIR + path, TEXTURE_EXT);
        }
        return id.withPath(p -> TEXTURE_DIR + p + TEXTURE_EXT);
    }

    /**
     * @param g        绘制上下文
     * @param display  该进度的 DisplayInfo（可能为 null —— 例如 toast 里 display 缺失）
     * @param fallback 回退用的 ItemStack（字符串 icon 时是 {@code ItemStack.EMPTY}）
     * @param x        左上角 x（原版坐标系）
     * @param y        左上角 y
     * @param size     边长（原版传 16）
     * @return 恒为 true（已处理；调用方不该再画第二遍）
     */
    public static boolean draw(GuiGraphics g, @Nullable DisplayInfo display, ItemStack fallback, int x, int y, int size) {
        Identifier tex = iconTexture(display);
        if (tex != null) {
            g.blit(toTexturePath(tex), x, y, x + size, y + size, 0.0F, 1.0F, 0.0F, 1.0F);
            return true;
        }
        g.renderFakeItem(fallback, x, y);
        return true;
    }

    /** 读 duck 字段；非 KaDisplayInfo / null 一律当"没有纹理"处理。 */
    public static @Nullable Identifier iconTexture(@Nullable DisplayInfo display) {
        if (display instanceof KaDisplayInfo ka) {
            return ka.ka$iconTexture();
        }
        return null;
    }
}
