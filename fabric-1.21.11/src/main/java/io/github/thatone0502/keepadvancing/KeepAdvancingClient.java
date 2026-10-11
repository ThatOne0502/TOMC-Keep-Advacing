package io.github.thatone0502.keepadvancing;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.resources.Identifier;

import io.github.thatone0502.keepadvancing.api.KeepAdvancingGuiApi;
import io.github.thatone0502.keepadvancing.network.GuiReportPayload;

/**
 * 客户端入口。只做一件事：把"现在开着哪个界面"上报给服务端。
 *
 * <p>为什么必须由客户端上报：服务端对界面的全部认知只有 {@code Player.containerMenu} 这一个字段，
 * 而它只覆盖<b>容器类</b>界面。物品栏（{@code InventoryMenu}）的 {@code getType()} 是 null，
 * 告示牌编辑页、书本编辑页、各模组自己的 {@code Screen} 在服务端<b>完全没有痕迹</b>。
 * 所以「任意界面」这件事只能让客户端看见什么就报什么。
 *
 * <p>上报的是 {@code Screen} 的类全名（{@code net.minecraft.client.gui.screens.inventory.CraftingScreen}
 * 这种），不是翻译键 —— 因为翻译键会随语言变化，作者没法稳定匹配。
 *
 * <p>节流：只在值变化时才发包（界面变化本来就是低频事件）。服务端那边也允许完全不装这个模组，
 * 所以发送前一律先问 {@code ClientPlayNetworking.canSend}。
 */
public class KeepAdvancingClient implements ClientModInitializer {

    /** 没有界面时上报的空串。用空串而不是 null，省掉一层 Optional。 */
    private static final String NONE = "";

    private static String reportedScreen;
    private static String reportedCustom;

    @Override
    public void onInitializeClient() {
        // ⚠ 这里**不要**再注册载荷类型。main 入口（KeepAdvancing.onInitialize）已经注册过
        // keepadvancing:gui_report 的 C2S 类型，而客户端上两个入口在同一个 JVM 里都会执行
        // —— 重复注册会直接抛 IllegalArgumentException: Packet type ... is already registered!
        // 让客户端在启动阶段就崩掉。C2S 通道两端照样协商得上，因为 main 入口在客户端也会跑。

        // 重连后要重新报一次：服务端那份状态在断线时被清掉了。
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            reportedScreen = null;
            reportedCustom = null;
        });

        ClientTickEvents.END_CLIENT_TICK.register(KeepAdvancingClient::report);
    }

    private static void report(Minecraft client) {
        if (client.player == null || client.getConnection() == null) {
            return;
        }
        Screen screen = client.screen;
        String screenClass = screen == null ? NONE : screen.getClass().getName();
        Identifier custom = KeepAdvancingGuiApi.customGui();
        String customId = custom == null ? NONE : custom.toString();
        if (screenClass.equals(reportedScreen) && customId.equals(reportedCustom)) {
            return;
        }
        if (!ClientPlayNetworking.canSend(GuiReportPayload.TYPE)) {
            return;
        }
        reportedScreen = screenClass;
        reportedCustom = customId;
        ClientPlayNetworking.send(new GuiReportPayload(screenClass, customId));
    }
}
