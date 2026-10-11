package io.github.thatone0502.keepadvancing.logic;

import java.util.HashMap;
import java.util.Map;

import net.minecraft.server.level.ServerPlayer;

/**
 * 每个玩家最近一次上报的客户端界面状态。
 *
 * <p>只在网络接收回调里写、只在服务端 tick 里读，两者都在服务端主线程上，所以用普通
 * {@code HashMap} 就够，不需要并发容器。玩家断线时由
 * {@code ServerPlayConnectionEvents.DISCONNECT} 清掉。
 *
 * <p>不落盘：这是纯粹的"此刻状态"，重登后客户端会重新上报。
 */
public final class ClientGuiReports {

    private static final Map<ServerPlayer, Report> BY_PLAYER = new HashMap<>();

    private ClientGuiReports() {
    }

    public static void set(ServerPlayer player, String screenClass, String customId) {
        BY_PLAYER.put(player, new Report(
                screenClass == null ? "" : screenClass,
                customId == null ? "" : customId));
    }

    /** 客户端此刻开着的 {@code Screen} 全限定类名；没有则空串。 */
    public static String screen(ServerPlayer player) {
        Report report = BY_PLAYER.get(player);
        return report == null ? "" : report.screenClass();
    }

    /** 其它模组通过 API 上报的自定义界面 id；没有则空串。 */
    public static String custom(ServerPlayer player) {
        Report report = BY_PLAYER.get(player);
        return report == null ? "" : report.customId();
    }

    public static void clear(ServerPlayer player) {
        BY_PLAYER.remove(player);
    }

    private record Report(String screenClass, String customId) {
    }
}
