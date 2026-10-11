package io.github.thatone0502.keepadvancing.api;

import net.minecraft.resources.Identifier;

/**
 * 给<b>别的模组</b>用的公开 API：上报一个"不是 Minecraft {@code Screen} 的界面"。
 *
 * <p>为什么需要它：{@code keepadvancing:gui_open} 的 {@code screen} 字段只能看见真正的
 * {@code Screen}（原版界面，以及用 GUI 系统画的模组界面）。但有一类界面根本不走 Screen
 * ——直接在 HUD 层画一个覆盖层（例如经验条风格的面板、自定义信息页）。服务端和 Screen
 * 事件都看不见它们，唯一办法就是让画它的模组自己说一声。
 *
 * <p>用法（客户端侧，每 tick 或状态变化时调用；不需要时传 {@code null} 清除）：
 *
 * <pre>{@code
 * // 正在画我的自定义界面
 * KeepAdvancingGuiApi.reportCustomGui(Identifier.fromNamespaceAndPath("mymod", "xp_panel"));
 * // 不画了
 * KeepAdvancingGuiApi.reportCustomGui(null);
 * }</pre>
 *
 * <p>然后把对方的 mod 作为 {@code compileOnly} 依赖即可。对应的进度写法：
 *
 * <pre>
 *   "custom": ["mymod:xp_panel", {"regex": "mymod:.*"}]
 * </pre>
 *
 * <p>本类是<b>双端共有</b>的普通静态方法，在专用服务器上调用也不会有副作用（值存在静态字段里，
 * 只有客户端入口会去读它）。传 {@code null} 是合法的，也是推荐的"清除"方式。
 */
public final class KeepAdvancingGuiApi {

    private static volatile Identifier customGui;

    private KeepAdvancingGuiApi() {
    }

    /**
     * 上报（或清除）本模组此刻正在显示的自定义界面。
     *
     * @param gui 界面 id；{@code null} 表示不再显示
     */
    public static void reportCustomGui(Identifier gui) {
        customGui = gui;
    }

    /** 当前上报的自定义界面 id，没有则为 {@code null}。由客户端入口读取。 */
    public static Identifier customGui() {
        return customGui;
    }
}
